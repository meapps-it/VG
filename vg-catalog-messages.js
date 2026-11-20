(()=>{
 'use strict';
 const admin=Boolean(document.querySelector('.topMenuGrid'));
 const endpoint='https://qfjwtawsqfwmsmrrgqfi.supabase.co/functions/v1/vg-shop-messages';
 const dialog=document.createElement('dialog');
 dialog.className='vgMessages';
 dialog.setAttribute('aria-labelledby','vgMessagesTitle');
 dialog.innerHTML=`
  <header><h2 id="vgMessagesTitle">Messaggi</h2><form method="dialog"><button type="submit">Chiudi</button></form></header>
  <p id="vgMessagesStatus" role="status"></p>
  <div id="vgMessagesList"></div>
  <button type="button" id="vgMessagesMore" hidden>Carica messaggi precedenti</button>
  <form id="vgMessagesCompose" hidden>
   <label for="vgMessagesText" id="vgMessagesSubject">Messaggio</label>
   <textarea id="vgMessagesText" name="message" required maxlength="2000" rows="4"></textarea>
   <button type="submit">Invia</button>
  </form>
  <details id="vgMessagesAlerts" hidden><summary>Avvisi per le risposte</summary>
   <p>Vuoi sapere quando ti rispondiamo?</p>
   <button type="button" id="vgMessagesPush" hidden>Attiva notifiche delle risposte</button>
   <form id="vgMessagesEmail" hidden>
    <label for="vgMessagesEmailAddress">Email per gli avvisi (facoltativa)</label>
    <input id="vgMessagesEmailAddress" name="email" type="email" maxlength="254" autocomplete="email">
    <button type="submit">Salva email</button>
   </form>
   <p id="vgMessagesAlertStatus" role="status"></p>
  </details>`;
 document.body.append(dialog);
 const style=document.createElement('style');
 style.textContent='.vgMessages{box-sizing:border-box;width:min(560px,calc(100% - 24px));max-height:85dvh;overflow:auto;border:1px solid #bfcdd9;border-radius:14px;padding:16px;background:white;color:#172233}.vgMessages::backdrop{background:#121a278c}.vgMessages header{display:flex;align-items:center;justify-content:space-between;gap:8px}.vgMessages h2{margin:0}.vgMessages button{cursor:pointer;border:1px solid #9cabc0;border-radius:8px;background:#edf2f8;color:#172233;padding:8px}.vgMessages form:not([hidden]){display:grid;gap:8px}.vgMessages textarea,.vgMessages input{box-sizing:border-box;width:100%;padding:8px;color:inherit;background:white;border:1px solid #8997a6;border-radius:8px}.vgMessages details{margin:10px 0}.vgMessages summary{cursor:pointer}.vgMessages article{padding:8px 0;border-bottom:1px solid #e3e8ef}.vgMessages article p{white-space:pre-wrap;overflow-wrap:anywhere}.vgMessages [hidden]{display:none!important}';
 document.head.append(style);
 const el=id=>document.getElementById('vgMessages'+id);
 const status=el('Status'), list=el('List'), compose=el('Compose'), alerts=el('Alerts');
 const formatter=new Intl.DateTimeFormat('it-IT',{dateStyle:'short',timeStyle:'short'});
 let page=0, rows=[], revision=0, context=null, pushKey=null, emailEnabled=false, sending=false;
 let attempted=null;
 const launchAdmin=()=>{
  const menu=document.querySelector('.topMenuGrid');
  const button=document.createElement('button');
  button.type='button';button.className='topMenuLink';button.textContent='Messaggi catalogo';
  button.onclick=()=>{window.setTopMenuOpen?.(false);open(null)};
  menu.append(button);
 };
 async function request(action,body,pageNumber=0){
  const url=endpoint+'?'+new URLSearchParams({action:admin?'admin-'+action:action,page:String(pageNumber)});
  const options={cache:'no-store',signal:AbortSignal.timeout(12000)};
  if(body!==undefined)Object.assign(options,{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(body)});
  let response;
  if(admin){
   const client=await window.VG_SUPABASE_READY;
   const token=(await client?.auth.getSession())?.data?.session?.access_token;
   if(!token)throw Error('Accedi al gestionale come amministratore.');
   options.headers={...options.headers,Authorization:'Bearer '+token};
   response=await fetch(url,options);
  }else response=await window.shopAccess.fetch(url,options);
  const data=await response.json().catch(()=>null);
  if(!response.ok||!data)throw Error(data?.error||'Messaggistica temporaneamente non disponibile.');
  return data;
 }
 function choose(item){
  context={replyTo:item.id,subject:item.subject};
  attempted=null;el('Text').value='';
  showCompose();el('Text').focus();
 }
 function showCompose(){
  compose.hidden=!context;
  el('Subject').textContent=context?.subject||'Messaggio';
 }
 function render(){
  list.replaceChildren();
  if(!rows.length){list.textContent='Nessun messaggio.';return}
  const groups=new Map();
  for(const row of rows){
   const key=row.customer_id+'|'+row.topic;
   if(!groups.has(key))groups.set(key,[]);
   groups.get(key).push(row);
  }
  for(const thread of groups.values()){
   const first=thread[0], group=document.createElement('details'), summary=document.createElement('summary');
   const customer=first.vg_shop_customers;
   const label=admin?(customer?.profile?.name||customer?.phone||customer?.email||'Cliente')+' · ':'';
   const unread=thread.filter(row=>!row.read_at&&row.sender===(admin?'customer':'shop'));
   summary.textContent=label+first.subject+(unread.length?' ('+unread.length+' da leggere)':'');
   group.append(summary);
   for(const row of [...thread].reverse()){
    const article=document.createElement('article'), sender=document.createElement('strong'), body=document.createElement('p');
    sender.textContent=(row.sender==='shop'?'Vanity & Glamour':'Cliente')+' · '+formatter.format(new Date(row.created_at));
    body.textContent=row.body;article.append(sender,body);group.append(article);
   }
   group.addEventListener('toggle',async()=>{
    const epoch=revision;
    if(!group.open||!unread.length||unread.every(row=>row.read_at))return;
    try{
     await request('read',{ids:unread.map(row=>row.id)});
     if(epoch!==revision)return;
     for(const row of unread)row.read_at=true;
     summary.textContent=label+first.subject;
    }catch(error){if(epoch===revision)status.textContent=error.message}
   });
   const reply=document.createElement('button');
   reply.type='button';reply.textContent='Rispondi';reply.onclick=()=>choose(first);
   group.append(reply);list.append(group);
  }
 }
 async function load(reset){
  const epoch=revision, next=reset?0:page+1;
  el('More').disabled=true;
  try{
   const data=await request('list',undefined,next);
   if(epoch!==revision)return;
   page=next;rows=reset?data.items:[...rows,...data.items.filter(row=>!rows.some(old=>old.id===row.id))];
   el('More').hidden=!data.more;pushKey=data.pushKey;emailEnabled=data.emailEnabled;
   el('Push').hidden=!(pushKey&&window.Notification&&navigator.serviceWorker&&window.PushManager);
   el('Email').hidden=!emailEnabled;
   if(reset&&!admin)el('EmailAddress').value=data.email??window.shopAccess.customer()?.email??'';
   alerts.hidden=admin||(!emailEnabled&&el('Push').hidden)||!rows.length;
   render();
   return true;
  }catch(error){if(epoch===revision)status.textContent=error.message;return false}
  finally{if(epoch===revision)el('More').disabled=false}
 }
 async function open(topic){
  if(!admin&&!window.shopAccess?.customer()){
   dialog.showModal();status.textContent='Accedi con il tuo account cliente per usare i messaggi.';
   return;
  }
  revision++;context=topic;attempted=null;rows=[];page=0;
  showCompose();
  el('Text').value='';list.replaceChildren();el('More').hidden=true;alerts.hidden=true;alerts.open=false;el('AlertStatus').textContent='';
  if(!admin)el('EmailAddress').value=window.shopAccess.customer()?.email||'';
  status.textContent='Caricamento messaggi…';
  if(!dialog.open)dialog.showModal();
  if(await load(true))status.textContent='';
 }
 dialog.addEventListener('close',()=>{revision++;rows=[];context=null;attempted=null;list.replaceChildren();showCompose()});
 el('More').onclick=()=>load(false);
 compose.onsubmit=async event=>{
  event.preventDefault();
  const message=el('Text').value.trim();
  if(sending||!context||!message)return;
  const payload={message,...(context.replyTo?{replyTo:context.replyTo}:context.productId?{productId:context.productId}:{})};
  const key=JSON.stringify(payload);
  if(attempted?.key!==key)attempted={key,id:crypto.randomUUID()};
  const submit=compose.querySelector('button'), epoch=revision;
  sending=true;submit.disabled=true;
  try{
   const result=await request('send',{...payload,id:attempted.id});
   if(epoch!==revision)return;
   attempted=null;el('Text').value='';
   const loaded=await load(true);
   if(epoch!==revision)return;
   if(loaded)status.textContent=admin?(result.notified?'Risposta inviata. Avviso consegnato al servizio.':'Risposta visibile nel catalogo.'):'Messaggio inviato.';
   if(!admin&&!alerts.hidden)alerts.open=true;
  }catch(error){if(epoch===revision)status.textContent=error.message}
  finally{sending=false;submit.disabled=false}
 };
 el('Push').onclick=async()=>{
  const button=el('Push');button.disabled=true;
  try{
   const epoch=revision;
   if(await Notification.requestPermission()!=='granted')throw Error('Permesso per le notifiche non concesso.');
   const root=new URL('.',location.href);
   const existing=await navigator.serviceWorker.getRegistration(root.href);
   const registration=existing||await navigator.serviceWorker.register(new URL('vg-push-sw.js',root));
   const ready=registration.active?registration:await navigator.serviceWorker.ready;
   const key=pushKey.replace(/-/g,'+').replace(/_/g,'/');
   const bytes=Uint8Array.from(atob(key.padEnd(Math.ceil(key.length/4)*4,'=')),c=>c.charCodeAt(0));
   const subscription=await ready.pushManager.getSubscription()||await ready.pushManager.subscribe({userVisibleOnly:true,applicationServerKey:bytes});
   if(subscription.options.applicationServerKey&&String(new Uint8Array(subscription.options.applicationServerKey))!==String(bytes))throw Error('Le notifiche delle risposte non sono disponibili con questa iscrizione.');
   if(epoch!==revision)return;
   await request('alerts',{subscription:subscription.toJSON()});
   el('AlertStatus').textContent='Notifiche delle risposte attivate su questo dispositivo.';
  }catch(error){el('AlertStatus').textContent=error.message}
  finally{button.disabled=false}
 };
 el('Email').onsubmit=async event=>{
  event.preventDefault();
  const email=el('EmailAddress').value.trim();
  try{await request('alerts',{email});el('AlertStatus').textContent=email?'Email per le risposte salvata.':'Avvisi email disattivati.'}
  catch(error){el('AlertStatus').textContent=error.message}
 };
 if(admin){launchAdmin();return}
 window.vgMessageAlertsLogout=()=>request('logout-alerts').catch(()=>{});
 window.addEventListener('vg-shop-logout',()=>{if(dialog.open)dialog.close()});
 document.querySelectorAll('[data-vg-message]').forEach(button=>button.onclick=()=>{
  const kind=button.dataset.vgMessage;
  open(kind==='inbox'?null:{productId:button.dataset.productId,subject:kind==='product'?document.getElementById('productTitle').textContent:'Informazioni generali'});
 });
 window.addEventListener('vg-customer-ready',()=>{
  const url=new URL(location.href);
  if(url.searchParams.get('messaggi')==='1'&&window.shopAccess.customer()){
   url.searchParams.delete('messaggi');history.replaceState(history.state,'',url);
   open(null);
  }
 });
})();
