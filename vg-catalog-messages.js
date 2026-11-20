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
  <button type="button" id="vgMessagesLogin" hidden>Accedi come cliente</button>
  <button type="button" id="vgMessagesBack" hidden>← Tutte le conversazioni</button>
  <div id="vgMessagesList"></div>
  <div id="vgMessagesThread" role="log" aria-label="Conversazione" hidden></div>
  <button type="button" id="vgMessagesMore" hidden>Carica messaggi precedenti</button>
  <form id="vgMessagesCompose" hidden>
   <label for="vgMessagesText" id="vgMessagesSubject">Messaggio</label>
   <textarea id="vgMessagesText" name="message" required maxlength="2000" rows="5" placeholder="Scrivi il tuo messaggio…"></textarea>
   <button type="submit">Invia</button>
  </form>
  <details id="vgMessagesAlerts" hidden><summary>Avvisi per le risposte</summary>
   <button type="button" id="vgMessagesPush" hidden>Attiva notifiche delle risposte</button>
   <form id="vgMessagesEmail" hidden>
    <label for="vgMessagesEmailAddress">Email per gli avvisi (facoltativa)</label>
    <input id="vgMessagesEmailAddress" name="email" type="email" maxlength="254" autocomplete="email">
    <button type="submit">Salva email</button>
   </form>
   <p id="vgMessagesAlertStatus" role="status"></p>
  </details>`;
 const prompt=document.createElement('dialog');
 prompt.className='vgMessages vgMessagesPrompt';
 prompt.setAttribute('aria-labelledby','vgMessagesPromptTitle');
 prompt.innerHTML=`<h2 id="vgMessagesPromptTitle">Vuoi essere avvisato quando ti rispondiamo?</h2>
  <div class="vgMessagesChoices">
   <button type="button" id="vgMessagesYes" class="vgMessagesYes">Sì, avvisami</button>
   <button type="button" id="vgMessagesNo" class="vgMessagesNo">No, grazie</button>
  </div>`;
 document.body.append(dialog,prompt);
 const style=document.createElement('style');
 style.textContent=`
 .vgMessages{box-sizing:border-box;width:min(680px,calc(100% - 20px));max-height:92dvh;overflow:auto;border:1px solid #c9d4e0;border-radius:20px;padding:clamp(14px,4vw,24px);background:#fff;color:#172233;box-shadow:0 18px 50px #17223342}
 .vgMessages::backdrop{background:#121a278c}
 .vgMessages header{display:flex;align-items:center;justify-content:space-between;gap:8px}
 .vgMessages h2{margin:0;color:#172233;font-size:clamp(23px,5vw,30px);line-height:1.16}
 .vgMessages button{cursor:pointer;border:1px solid #9cabc0;border-radius:10px;background:#edf2f8;color:#172233;padding:10px 14px;font:inherit;min-height:42px}
 .vgMessages button:focus-visible,.vgMessages textarea:focus-visible{outline:3px solid #135aad;outline-offset:2px}
 .vgMessages button:disabled{opacity:.65;cursor:wait}
 .vgMessages form:not([hidden]){display:grid;gap:10px}
 .vgMessages textarea,.vgMessages input{box-sizing:border-box;width:100%;padding:12px;font:inherit;font-size:16px;color:#172233;background:#fff;border:1px solid #8997a6;border-radius:12px}
 .vgMessages textarea{min-height:132px;resize:vertical}
 .vgMessages #vgMessagesCompose{margin:12px 0;padding:12px;background:#f2f6fb;border-radius:14px}
 .vgMessages #vgMessagesCompose button{background:#1456a0;color:#fff;border-color:#1456a0;font-weight:800;font-size:18px;min-height:50px}
 .vgMessages #vgMessagesList{display:grid;gap:7px}
 .vgMessages #vgMessagesList button{text-align:left;background:#f3f7fb;width:100%;overflow-wrap:anywhere}
 .vgMessages #vgMessagesThread{display:flex;flex-direction:column;gap:10px;min-height:94px;max-height:42dvh;overflow:auto;padding:12px;background:#f0f4f8;border-radius:14px;margin:12px 0}
 .vgMessages article{align-self:flex-start;max-width:86%;min-width:0;padding:9px 12px;border-radius:14px;background:#fff;color:#172233}
 .vgMessages article.isMine{align-self:flex-end;background:#dcecff;color:#102d4f}
 .vgMessages article strong{display:block;font-size:.78rem}
 .vgMessages article p{margin:4px 0;white-space:pre-wrap;overflow-wrap:anywhere}
 .vgMessages details{margin:16px 0 0;border-top:1px solid #dce3eb;padding-top:12px}
 .vgMessages summary{cursor:pointer;color:#354b64}
 .vgMessages #vgMessagesStatus:empty,.vgMessages [hidden]{display:none!important}
 .vgMessagesPrompt{width:min(430px,calc(100% - 26px));padding:24px}
 .vgMessagesChoices{display:grid;grid-template-columns:1fr 1fr;gap:10px;margin-top:22px}
 .vgMessagesPrompt .vgMessagesYes,.vgMessagesPrompt .vgMessagesNo{color:#fff;font-weight:800;min-height:48px}
 .vgMessagesPrompt .vgMessagesYes{background:#08713e;border-color:#08713e}
 .vgMessagesPrompt .vgMessagesNo{background:#ae2435;border-color:#ae2435}
 `;
 document.head.append(style);
 const el=id=>document.getElementById('vgMessages'+id);
 const status=el('Status'), list=el('List'), compose=el('Compose'), alerts=el('Alerts');
 const formatter=new Intl.DateTimeFormat('it-IT',{dateStyle:'short',timeStyle:'short'});
 let page=0, rows=[], revision=0, context=null, pushKey=null, emailEnabled=false, sending=false;
 let attempted=null, refreshTimer=null, loading=false;
 const launchAdmin=()=>{
  document.querySelectorAll('[data-vg-admin-messages]').forEach(button=>{
   button.onclick=()=>{window.setTopMenuOpen?.(false);open(null)};
  });
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
  if(!response.ok||!data)throw Object.assign(Error(data?.error||'Messaggistica temporaneamente non disponibile.'),{status:response.status});
  return data;
 }
 function choose(item){
  context={replyTo:item.id,topic:item.topic,customerId:item.customer_id,subject:item.subject};
  attempted=null;el('Text').value='';
  render();el('Text').focus();
 }
 function showCompose(){
  compose.hidden=!context;
  el('Subject').textContent=context?.subject||'Messaggio';
 }
 async function markRead(thread){
  const ids=thread.filter(row=>!row.read_at&&row.sender===(admin?'customer':'shop')).map(row=>row.id);
  const epoch=revision;
  try{
   for(let i=0;i<ids.length;i+=50)await request('read',{ids:ids.slice(i,i+50)});
   if(epoch===revision)for(const row of thread)if(ids.includes(row.id))row.read_at=true;
  }catch(error){if(epoch===revision)status.textContent=error.message}
 }
 function render(){
  const history=el('Thread'), back=el('Back');
  list.replaceChildren();history.replaceChildren();
  list.hidden=Boolean(context);history.hidden=!context;back.hidden=!context;
  showCompose();
  if(context){
   const thread=rows.filter(row=> row.topic===(context.topic||context.productId||'general')
     &&(!context.customerId||row.customer_id===context.customerId));
   if(!thread.length)history.textContent='Inizia la conversazione con un messaggio.';
   for(const row of [...thread].reverse()){
    const article=document.createElement('article'), sender=document.createElement('strong'), body=document.createElement('p');
    const mine=row.sender===(admin?'shop':'customer');
    article.classList.toggle('isMine',mine);
    sender.textContent=(mine?'Tu':row.sender==='shop'?'Vanity & Glamour':'Cliente')+' · '+formatter.format(new Date(row.created_at));
    body.textContent=row.body;article.append(sender,body);history.append(article);
   }
   history.scrollTop=history.scrollHeight;
   void markRead(thread);
   return;
  }
  if(!rows.length){list.textContent='Nessun messaggio.';return}
  const groups=new Map();
  for(const row of rows){
   const key=row.customer_id+'|'+row.topic;
   if(!groups.has(key))groups.set(key,[]);
   groups.get(key).push(row);
  }
  for(const thread of groups.values()){
   const first=thread[0], button=document.createElement('button'),customer=first.vg_shop_customers;
   const label=admin?(customer?.profile?.name||customer?.phone||customer?.email||'Cliente')+' · ':'';
   const unread=thread.filter(row=>!row.read_at&&row.sender===(admin?'customer':'shop'));
   button.type='button';button.textContent=label+first.subject+(unread.length?' ('+unread.length+' da leggere)':'');
   button.onclick=()=>choose(first);list.append(button);
  }
 }
 el('Back').onclick=()=>{context=null;attempted=null;el('Text').value='';render()};
 let notifiedDevice=false, promptDismissed=false;
 function offerNotifications(){
  if(admin||el('Push').hidden||notifiedDevice||promptDismissed||Notification.permission==='denied'||prompt.open)return;
  prompt.showModal();
 }
 el('Yes').onclick=()=>{prompt.close();el('Push').click()};
 el('No').onclick=()=>{promptDismissed=true;prompt.close()};
 prompt.addEventListener('cancel',()=>{promptDismissed=true});
 async function refreshChat(){
  if(!dialog.open||document.hidden||sending||loading)return;
  const epoch=revision;
  loading=true;
  try{
   const {items}=await request('list');
   if(epoch!==revision)return;
   const known=new Set(rows.map(row=>row.id));
   const fresh=items.filter(row=>!known.has(row.id));
   if(fresh.length){rows=[...fresh,...rows];render()}
  }catch{/* The next poll can retry without disturbing the current draft. */}
  finally{loading=false}
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
   if(reset&&!admin)el('EmailAddress').value=data.email??window.shopAccess.customer?.()?.email??'';
   alerts.hidden=admin||(!emailEnabled&&el('Push').hidden)||!rows.length;
   render();
   return true;
  }catch(error){
   if(epoch===revision){
    status.textContent=error.message;
    if(!admin&&[401,403].includes(error.status)){
     context=null;showCompose();el('Back').hidden=true;el('Thread').hidden=true;
     status.textContent='La sessione aperta in questo catalogo non risulta associata a un account cliente. Accedi qui con il tuo numero.';
     el('Login').hidden=false;
    }
   }
   return false;
  }
  finally{if(epoch===revision)el('More').disabled=false}
 }
 async function open(topic){
  revision++;context=topic;attempted=null;rows=[];page=0;
  compose.hidden=true;el('Login').hidden=true;
  el('Text').value='';list.replaceChildren();el('More').hidden=true;alerts.hidden=true;alerts.open=false;el('AlertStatus').textContent='';
  if(!admin)el('EmailAddress').value='';
  status.textContent='Caricamento messaggi…';
  if(!dialog.open)dialog.showModal();
  clearInterval(refreshTimer);
  refreshTimer=setInterval(refreshChat,10000);
  const epoch=revision;
  if(await load(true)&&epoch===revision)status.textContent='';
 }
 dialog.addEventListener('close',()=>{clearInterval(refreshTimer);if(prompt.open)prompt.close();revision++;rows=[];context=null;attempted=null;list.replaceChildren();showCompose()});
 el('More').onclick=()=>load(false);
 el('Login').onclick=async()=>{dialog.close();await window.shopAccess.logout()};
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
   if(dialog.open)offerNotifications();
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
   notifiedDevice=true;
   status.textContent='Notifiche delle risposte attivate su questo dispositivo.';
  }catch(error){status.textContent=error.message}
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
 window.addEventListener('vg-shop-logout',()=>{notifiedDevice=false;promptDismissed=false;if(dialog.open)dialog.close()});
 document.querySelectorAll('[data-vg-message]').forEach(button=>button.onclick=()=>{
  const kind=button.dataset.vgMessage;
  open(kind==='inbox'?null:{productId:button.dataset.productId,subject:kind==='product'?document.getElementById('productTitle').textContent:'Informazioni generali'});
 });
 window.addEventListener('vg-customer-ready',()=>{
  const url=new URL(location.href);
  if(url.searchParams.get('messaggi')==='1'){
   url.searchParams.delete('messaggi');history.replaceState(history.state,'',url);
   open(null);
  }
 });
})();
