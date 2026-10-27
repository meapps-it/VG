(function(){
 'use strict';
 const pageStyle=document.createElement('link');pageStyle.rel='stylesheet';pageStyle.href='./vg-fullscreen-pages.css?v=1';document.head.append(pageStyle);const pageScript=document.createElement('script');pageScript.src='./vg-fullscreen-pages.js?v=1';document.head.append(pageScript);
 const customerStyle=document.createElement('link');customerStyle.rel='stylesheet';customerStyle.href='./vg-customer-panel.css?v=3';document.head.append(customerStyle);const customerScript=document.createElement('script');customerScript.src='./vg-customer-panel.js?v=3';document.head.append(customerScript);
 const endpoint='https://qfjwtawsqfwmsmrrgqfi.supabase.co/functions/v1/vg-shop-catalog?action=stats';
 const opener=document.getElementById('vgCatalogStatsOpen');if(!opener)return;
 const hybrid=/\bVgHybrid\/\d/.test(navigator.userAgent);
 document.querySelectorAll('.vgNativeAction').forEach(link=>{
  const password=link.getAttribute('href')==='vgapp://password';if(password){const title=link.querySelector('.topMenuTitle');if(title)title.textContent='Autorizza clienti';}
  link.hidden=!hybrid&&!password;
  link.addEventListener('click',()=>{if(typeof setTopMenuOpen==='function')setTopMenuOpen(false)});
  if(password)link.addEventListener('click',event=>{event.preventDefault();openCustomerAccess()});
 });
 const dialog=document.createElement('dialog');dialog.id='vgCatalogStats';dialog.className='vgStatsDialog';dialog.setAttribute('aria-labelledby','vgStatsTitle');
 dialog.innerHTML='<header class="vgStatsHeader"><div id="vgStatsTitle" class="vgStatsHeading" role="heading" aria-level="2">Statistiche catalogo</div><button type="button" id="vgStatsClose">Chiudi ✕</button></header><div class="vgStatsContent"><p class="vgStatsStatus" id="vgStatsStatus" role="status" aria-live="polite"></p><button type="button" class="vgStatsRetry" id="vgStatsRetry" hidden>Riprova</button><div id="vgStatsResults" hidden><div class="vgStatsSummary" id="vgStatsSummary"></div><figure class="vgStatsFigure" style="margin-top:16px"><figcaption>Andamento degli accessi</figcaption><p>Visite giornaliere al catalogo clienti</p><div class="vgStatsRange" role="group" aria-label="Periodo del grafico"><button type="button" data-vg-days="7" aria-pressed="true">7 giorni</button><button type="button" data-vg-days="30" aria-pressed="false">30 giorni</button></div><div class="vgStatsChartScroll"><ol class="vgStatsBars" id="vgStatsBars" aria-label="Visite per giorno"></ol></div><p id="vgStatsEmpty" hidden>Nessuna visita nel periodo selezionato.</p></figure><section id="vgCustomerStats" hidden><h3>Clienti e ordini del catalogo</h3><div class="vgStatsSummary" id="vgCustomerStatsSummary"></div><h4>Clienti registrati</h4><div id="vgRegisteredCustomers"></div><button type="button" id="vgCustomersMore" class="vgStatsRetry" hidden>Altri clienti</button><h4>Richieste ricevute</h4><p class="vgStatsNote">Comprende anche le richieste del catalogo inviate senza account. Gli ordini precedenti non vengono attribuiti automaticamente ai nuovi account.</p><div id="vgCustomerOrders"></div><button type="button" id="vgOrdersMore" class="vgStatsRetry" hidden>Altri ordini</button><p id="vgCustomersStatus" role="status" aria-live="polite"></p></section><p class="vgStatsNote" id="vgStatsDefinition"></p><p class="vgStatsNote">I tuoi accessi tramite anteprima amministratore sono esclusi.</p></div></div>';
 document.body.append(dialog);
 const el=id=>document.getElementById(id),number=value=>Math.max(0,Math.trunc(Number(value)||0));
 function openCustomerAccess(){window.VG_CUSTOMERS?window.VG_CUSTOMERS.open():opener.click()}
 async function authorizeCustomer(id,status,button,label){
  button.disabled=true;try{
   const client=await window.VG_SUPABASE_READY,result=await client.auth.getSession();if(!result.data?.session?.access_token)throw Error('Accedi come amministratore.');
   const response=await fetch(endpoint.replace('action=stats','action=customer-authorize'),{method:'POST',headers:{Authorization:'Bearer '+result.data.session.access_token,'Content-Type':'application/json'},body:JSON.stringify({id,status})});const data=await response.json();if(!response.ok)throw Error(data.error||'Operazione non riuscita');label.textContent=status==='approved'?'Autorizzato':status==='blocked'?'Bloccato':'In attesa';
  }catch(error){label.textContent=error.message}finally{button.disabled=false}
 }
 let daily=[],days=7,controller=null,customerOffset=0,orderOffset=0;
 const statusName=value=>({in_lavorazione:'In lavorazione / da confermare',spedito:'Spedito',consegnato:'Consegnato',annullato:'Annullato'})[value]||value||'Da confermare';
 const date=value=>{const d=new Date(value);return value&&!Number.isNaN(d.getTime())?new Intl.DateTimeFormat('it-IT',{timeZone:'Europe/Rome',dateStyle:'short',timeStyle:'short'}).format(d)+' (Italia)':'—'};
 const money=value=>Number(value||0).toLocaleString('it-IT',{style:'currency',currency:'EUR'});
 function line(root,text){const p=document.createElement('p');p.textContent=text;root.append(p)}
 function renderCustomerStats(data,append=''){
  el('vgCustomerStats').hidden=!data;if(!data)return;
  if(!append){customerOffset=0;orderOffset=0;el('vgRegisteredCustomers').replaceChildren();el('vgCustomerOrders').replaceChildren();const root=el('vgCustomerStatsSummary');root.replaceChildren();for(const [label,value] of [['Clienti registrati',data.summary?.registeredCustomers],['Ordini dal catalogo',data.summary?.catalogOrders],['Ordini aperti',data.summary?.pendingOrders]]){const card=document.createElement('div');card.className='vgStatsCard';const title=document.createElement('span'),count=document.createElement('strong');title.textContent=label;count.textContent=number(value).toLocaleString('it-IT');card.append(title,count);root.append(card)}}
  if(append!=='orders'){
   for(const c of data.customers||[]){const card=document.createElement('details');card.className='vgCustomerStatRow';const heading=document.createElement('summary');heading.textContent=(c.profile?.name||'Cliente registrato')+' · '+(c.phone?'+'+c.phone:c.email||'Google');card.append(heading);const accessLabel=document.createElement('p');accessLabel.textContent=({approved:'Autorizzato',blocked:'Bloccato',pending:'In attesa di autorizzazione'})[c.accessStatus]||'In attesa di autorizzazione';card.append(accessLabel);for(const [title,status] of [['Autorizza','approved'],['Blocca','blocked']]){const button=document.createElement('button');button.type='button';button.className='vgStatsRetry';button.textContent=title;button.onclick=()=>authorizeCustomer(c.id,status,button,accessLabel);card.append(button)}line(card,'Registrazione: '+date(c.registeredAt));line(card,'Ultima autenticazione: '+date(c.lastLoginAt));line(card,'Richieste dell’account: '+number(c.orders));line(card,['Indirizzo:',c.profile?.address,c.profile?.city,c.profile?.province,c.profile?.cap].filter(Boolean).join(' '));el('vgRegisteredCustomers').append(card)}
   customerOffset=data.customerOffset||0;el('vgCustomersMore').hidden=!data.hasMoreCustomers;if(!el('vgRegisteredCustomers').children.length)el('vgRegisteredCustomers').textContent='Nessun cliente ancora registrato.';
  }
  if(append!=='customers'){
   for(const o of data.orders||[]){const card=document.createElement('details');card.className='vgCustomerStatRow';const heading=document.createElement('summary');heading.textContent=o.number+' · '+statusName(o.status)+' · '+money(o.total);card.append(heading);line(card,'Richiesta: '+date(o.requestedAt));line(card,[o.customer?.nome,o.customer?.telefono].filter(Boolean).join(' · '));line(card,[o.customer?.indirizzo,o.customer?.citta,o.customer?.provincia,o.customer?.cap].filter(Boolean).join(' · '));for(const i of o.items||[])line(card,i.qty+' × '+i.name+' · '+i.quality+(i.size?' · Misura '+i.size:'')+(i.color?' · Colore '+(i.colorNumber?i.colorNumber+' - ':'')+i.color:'')+' · '+money(i.price));line(card,o.paid?'Pagato':'Da pagare');if(o.tracking)line(card,'Tracking: '+o.tracking);el('vgCustomerOrders').append(card)}
   orderOffset=data.orderOffset||0;el('vgOrdersMore').hidden=!data.hasMoreOrders;if(!el('vgCustomerOrders').children.length)el('vgCustomerOrders').textContent='Nessuna richiesta dal catalogo.';
  }
 }
 async function moreCustomers(kind){const button=el(kind==='customers'?'vgCustomersMore':'vgOrdersMore'),requestController=controller;button.disabled=true;el('vgCustomersStatus').textContent='Caricamento…';try{const client=await window.VG_SUPABASE_READY,result=await client.auth.getSession();if(!result.data?.session?.access_token)throw Error('Accedi al gestionale come amministratore.');const url=endpoint.replace('action=stats','action=customer-stats')+'&customers='+(customerOffset+(kind==='customers'?50:0))+'&orders='+(orderOffset+(kind==='orders'?50:0));const response=await fetch(url,{headers:{Authorization:'Bearer '+result.data.session.access_token},cache:'no-store',signal:requestController?.signal});const data=await response.json();if(!response.ok)throw Error(data.error||'Caricamento non riuscito');if(!requestController?.signal.aborted){renderCustomerStats(data,kind);el('vgCustomersStatus').textContent=''}}catch(error){if(error.name!=='AbortError')el('vgCustomersStatus').textContent=error.message}finally{button.disabled=false}}
 el('vgCustomersMore').onclick=()=>moreCustomers('customers');el('vgOrdersMore').onclick=()=>moreCustomers('orders');
 function renderChart(){
  const rows=daily.slice(-days),max=Math.max(1,...rows.map(row=>number(row.visits))),list=el('vgStatsBars');list.replaceChildren();list.classList.toggle('vgStatsMonth',days===30);
  for(const row of rows){
   const count=number(row.visits),date=new Date(String(row.day)+'T12:00:00Z');
   const short=Number.isNaN(date.getTime())?'—':date.toLocaleDateString('it-IT',{day:'2-digit',month:'2-digit',timeZone:'Europe/Rome'});
   const item=document.createElement('li');item.setAttribute('aria-label',short+': '+count+' visite');item.title=short+': '+count+' visite';
   const value=document.createElement('span');value.className='vgStatsValue';value.textContent=count;
   const track=document.createElement('div');track.className='vgStatsTrack';track.setAttribute('aria-hidden','true');
   const bar=document.createElement('div');bar.className='vgStatsBar';bar.style.height=(count/max*100)+'%';track.append(bar);
   const label=document.createElement('span');label.className='vgStatsDate';label.textContent=short;
   item.append(value,track,label);list.append(item);
  }
  el('vgStatsEmpty').hidden=rows.some(row=>number(row.visits)>0);
  dialog.querySelectorAll('[data-vg-days]').forEach(button=>button.setAttribute('aria-pressed',String(Number(button.dataset.vgDays)===days)));
 }
 async function load(){
  controller?.abort();const requestController=new AbortController();controller=requestController;
  el('vgCustomersStatus').textContent='';el('vgStatsStatus').textContent='Caricamento statistiche…';el('vgStatsStatus').classList.remove('vgStatsError');el('vgStatsResults').hidden=true;el('vgStatsRetry').hidden=true;
  try{
   const client=await window.VG_SUPABASE_READY;if(!client)throw Error('Connessione al gestionale non disponibile. Riprova.');
   const result=await client.auth.getSession();if(result.error||!result.data?.session?.access_token)throw Error('Accedi al gestionale con il tuo account amministratore.');
   const response=await fetch(endpoint,{headers:{Authorization:'Bearer '+result.data.session.access_token,Accept:'application/json'},cache:'no-store',signal:requestController.signal});
   const data=await response.json();if(!response.ok)throw Error(response.status===403?'Statistiche riservate al titolare del catalogo.':data.error||'Statistiche non disponibili. Riprova.');
   if(requestController.signal.aborted)return;
   daily=Array.isArray(data.daily)?data.daily:[];renderCustomerStats(data.customerStats);
   const summary=el('vgStatsSummary');summary.replaceChildren();
   for(const [label,value] of [['Visite oggi',data.today],['Ultimi 7 giorni',data.week],['Ultimi 30 giorni',data.month],['Dispositivi in 30 giorni',data.devicesMonth]]){
    const card=document.createElement('div');card.className='vgStatsCard';const title=document.createElement('span');title.textContent=label;const count=document.createElement('strong');count.textContent=number(value).toLocaleString('it-IT');card.append(title,count);summary.append(card);
   }
   el('vgStatsDefinition').textContent=data.definition||'Una visita per dispositivo ogni 30 minuti.';
   renderChart();el('vgStatsResults').hidden=false;el('vgStatsStatus').textContent='';
  }catch(error){if(error.name==='AbortError')return;el('vgStatsStatus').textContent=error.message||'Statistiche non disponibili. Riprova.';el('vgStatsStatus').classList.add('vgStatsError');el('vgStatsRetry').hidden=false;}
 }
 opener.addEventListener('click',()=>{if(typeof setTopMenuOpen==='function')setTopMenuOpen(false);if(!dialog.open)dialog.showModal();days=7;load()});
 el('vgStatsClose').addEventListener('click',()=>dialog.close());dialog.addEventListener('close',()=>{controller?.abort();opener.focus()});
 el('vgStatsRetry').addEventListener('click',load);
 dialog.querySelectorAll('[data-vg-days]').forEach(button=>button.addEventListener('click',()=>{days=Number(button.dataset.vgDays);renderChart()}));
})();
