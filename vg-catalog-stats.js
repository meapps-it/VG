(function(){
 'use strict';
 const endpoint='https://qfjwtawsqfwmsmrrgqfi.supabase.co/functions/v1/vg-shop-catalog?action=stats';
 const opener=document.getElementById('vgCatalogStatsOpen');if(!opener)return;
 const hybrid=/\bVgHybrid\/\d/.test(navigator.userAgent);
 document.querySelectorAll('.vgNativeAction').forEach(link=>{
  const password=link.getAttribute('href')==='vgapp://password';
  link.hidden=!hybrid&&!password;
  link.addEventListener('click',()=>{if(typeof setTopMenuOpen==='function')setTopMenuOpen(false)});
  if(password)link.addEventListener('click',event=>{event.preventDefault();openPasswordDialog()});
 });
 const dialog=document.createElement('dialog');dialog.id='vgCatalogStats';dialog.className='vgStatsDialog';dialog.setAttribute('aria-labelledby','vgStatsTitle');
 dialog.innerHTML='<header class="vgStatsHeader"><div id="vgStatsTitle" class="vgStatsHeading" role="heading" aria-level="2">Statistiche catalogo</div><button type="button" id="vgStatsClose">Chiudi ✕</button></header><div class="vgStatsContent"><p class="vgStatsStatus" id="vgStatsStatus" role="status" aria-live="polite"></p><button type="button" class="vgStatsRetry" id="vgStatsRetry" hidden>Riprova</button><div id="vgStatsResults" hidden><div class="vgStatsSummary" id="vgStatsSummary"></div><figure class="vgStatsFigure" style="margin-top:16px"><figcaption>Andamento degli accessi</figcaption><p>Visite giornaliere al catalogo clienti</p><div class="vgStatsRange" role="group" aria-label="Periodo del grafico"><button type="button" data-vg-days="7" aria-pressed="true">7 giorni</button><button type="button" data-vg-days="30" aria-pressed="false">30 giorni</button></div><div class="vgStatsChartScroll"><ol class="vgStatsBars" id="vgStatsBars" aria-label="Visite per giorno"></ol></div><p id="vgStatsEmpty" hidden>Nessuna visita nel periodo selezionato.</p></figure><p class="vgStatsNote" id="vgStatsDefinition"></p><p class="vgStatsNote">I tuoi accessi tramite anteprima amministratore sono esclusi.</p></div></div>';
 document.body.append(dialog);
 const el=id=>document.getElementById(id),number=value=>Math.max(0,Math.trunc(Number(value)||0));
 const passwordDialog=document.createElement('dialog');passwordDialog.id='vgOwnerPassword';passwordDialog.className='vgStatsDialog';passwordDialog.setAttribute('aria-labelledby','vgOwnerPasswordTitle');
 passwordDialog.innerHTML='<header class="vgStatsHeader"><div id="vgOwnerPasswordTitle" class="vgStatsHeading" role="heading" aria-level="2">Password clienti</div><button type="button" id="vgOwnerPasswordClose">Chiudi ✕</button></header><form id="vgOwnerPasswordForm" class="vgStatsContent vgPasswordForm"><p class="vgStatsNote">Imposta la password del catalogo, di almeno 6 caratteri. Dopo la modifica i clienti dovranno reinserirla.</p><label for="vgOwnerPasswordValue">Nuova password<input id="vgOwnerPasswordValue" type="password" minlength="6" maxlength="128" required autocomplete="new-password"></label><label for="vgOwnerPasswordConfirm">Ripeti password<input id="vgOwnerPasswordConfirm" type="password" minlength="6" maxlength="128" required autocomplete="new-password"></label><button class="vgStatsRetry" id="vgOwnerPasswordSave" type="submit">Salva password</button><p id="vgOwnerPasswordStatus" class="vgStatsStatus" role="status" aria-live="polite"></p></form>';
 document.body.append(passwordDialog);
 function openPasswordDialog(){el('vgOwnerPasswordForm').reset();el('vgOwnerPasswordStatus').textContent='';if(!passwordDialog.open)passwordDialog.showModal()}
 el('vgOwnerPasswordClose').addEventListener('click',()=>passwordDialog.close());
 passwordDialog.addEventListener('close',()=>{el('vgOwnerPasswordForm').reset()});
 el('vgOwnerPasswordForm').addEventListener('submit',async event=>{
  event.preventDefault();const password=el('vgOwnerPasswordValue').value,status=el('vgOwnerPasswordStatus'),button=el('vgOwnerPasswordSave');
  if(password.length<6){status.textContent='Usa almeno 6 caratteri.';return}
  if(password!==el('vgOwnerPasswordConfirm').value){status.textContent='Le due password non coincidono.';return}
  button.disabled=true;status.textContent='Salvataggio…';
  try{
   const client=await window.VG_SUPABASE_READY;if(!client)throw Error('Connessione al gestionale non disponibile.');
   const result=await client.auth.getSession();if(result.error||!result.data?.session?.access_token)throw Error('Accedi con il tuo account amministratore.');
   const response=await fetch(endpoint.replace('action=stats','action=set-password'),{method:'POST',headers:{Authorization:'Bearer '+result.data.session.access_token,'Content-Type':'application/json'},body:JSON.stringify({password})});
   const data=await response.json();if(!response.ok)throw Error(response.status===403?'Operazione riservata al titolare del catalogo.':data.error||'Salvataggio non riuscito.');
   el('vgOwnerPasswordForm').reset();status.textContent='Password aggiornata. I clienti dovranno reinserirla.';
  }catch(error){status.textContent=error.message||'Salvataggio non riuscito. Riprova.'}finally{button.disabled=false}
 });
 let daily=[],days=7,controller=null;
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
  el('vgStatsStatus').textContent='Caricamento statistiche…';el('vgStatsStatus').classList.remove('vgStatsError');el('vgStatsResults').hidden=true;el('vgStatsRetry').hidden=true;
  try{
   const client=await window.VG_SUPABASE_READY;if(!client)throw Error('Connessione al gestionale non disponibile. Riprova.');
   const result=await client.auth.getSession();if(result.error||!result.data?.session?.access_token)throw Error('Accedi al gestionale con il tuo account amministratore.');
   const response=await fetch(endpoint,{headers:{Authorization:'Bearer '+result.data.session.access_token,Accept:'application/json'},cache:'no-store',signal:requestController.signal});
   const data=await response.json();if(!response.ok)throw Error(response.status===403?'Statistiche riservate al titolare del catalogo.':data.error||'Statistiche non disponibili. Riprova.');
   if(requestController.signal.aborted)return;
   daily=Array.isArray(data.daily)?data.daily:[];
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
