(()=>{
'use strict';
const API='https://qfjwtawsqfwmsmrrgqfi.supabase.co/functions/v1/vg-shop-presence?action=stats';
function render(){
 const stat=document.querySelector('#c_profit_total')?.closest('.stat');
 if(!stat)return false;
 stat.removeAttribute('data-go');
 stat.setAttribute('role','button');
 stat.setAttribute('tabindex','0');
 stat.setAttribute('aria-label','Apri clienti online e storico accessi');
 stat.style.cursor='pointer';
 stat.onclick=showDetails;
 stat.onkeydown=e=>{if(e.key==='Enter'||e.key===' '){e.preventDefault();showDetails()}};

 stat.innerHTML='<div class="k">Clienti online <span aria-hidden="true" style="color:#1a9b59">●</span></div><div id="vg_online_customers" class="v big" style="color:#168e51">—</div><div id="vg_online_subtitle" class="sub">Attivi negli ultimi 5 minuti · Tocca per i dettagli</div><span id="c_profit_total" hidden aria-hidden="true"></span>';
 return true;
}
function escapeHTML(value){return String(value??'').replace(/[&<>"']/g,ch=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[ch]));}
function dateTime(value){const d=new Date(value);return Number.isNaN(d.getTime())?'—':d.toLocaleString('it-IT',{day:'2-digit',month:'2-digit',year:'numeric',hour:'2-digit',minute:'2-digit'});}
const withTimeout=(promise,ms=12000)=>Promise.race([promise,new Promise((_,reject)=>setTimeout(()=>reject(new Error('Tempo di attesa superato')),ms))]);
let modal=null;
async function showDetails(){
 if(modal)return;
 modal=document.createElement('div');
 modal.id='vgOnlineDialog';
 modal.setAttribute('role','dialog');modal.setAttribute('aria-modal','true');modal.setAttribute('aria-label','Clienti online e storico accessi');
 modal.style.cssText='position:fixed;inset:0;z-index:2147483646;background:#0c1939a8;display:flex;align-items:center;justify-content:center;padding:16px';
 modal.innerHTML='<div style="background:#fff;color:#172b4d;border-radius:20px;width:min(100%,560px);max-height:85vh;overflow:auto;padding:22px;box-shadow:0 12px 45px #0003"><div style="display:flex;justify-content:space-between;gap:12px;align-items:center"><h2 style="margin:0;font-size:23px">👥 Clienti catalogo</h2><button id="vgOnlineClose" type="button" style="padding:8px 12px;border:1px solid #cbd5e1;border-radius:10px;background:#fff;color:#172b4d">Chiudi ✕</button></div><div id="vgOnlineBody" style="margin-top:20px">Caricamento dati…</div></div>';
 document.body.appendChild(modal);
 const close=()=>{modal?.remove();modal=null;document.removeEventListener('keydown',esc)};
 const esc=e=>{if(e.key==='Escape')close()};
 modal.querySelector('#vgOnlineClose').onclick=close;
 modal.addEventListener('click',e=>{if(e.target===modal)close()});
 document.addEventListener('keydown',esc);
 try{
  const client=await withTimeout(window.VG_SUPABASE_READY,8000);
  const {data:{session}}=await withTimeout(client.auth.getSession(),8000);
  if(!session?.access_token)throw Error('Sessione amministratore non disponibile');
  const controller=new AbortController();
  const timer=setTimeout(()=>controller.abort(),12000);
  let response;
  try{response=await fetch(API.replace('action=stats','action=details'),{headers:{Authorization:'Bearer '+session.access_token},cache:'no-store',signal:controller.signal});}finally{clearTimeout(timer)}
  if(!response.ok)throw Error('Impossibile leggere lo storico');
  const data=await withTimeout(response.json(),5000);
  const active=data.online||[],history=data.history||[];
  const onlineRows=active.map(x=>'<div style="padding:10px 0;border-bottom:1px solid #e2e8f0"><b>🟢 '+escapeHTML(x.name)+'</b><div style="font-size:13px;color:#64748b">Ultima attività: '+escapeHTML(dateTime(x.lastSeenAt))+'</div></div>').join('');
  const historyRows=history.map(x=>'<div style="padding:9px 0;border-bottom:1px solid #e2e8f0"><b>'+escapeHTML(x.name)+'</b><div style="font-size:13px;color:#64748b">Visita · '+escapeHTML(dateTime(x.at))+'</div></div>').join('');
  const ranking=data.ranking||[],never=data.neverVisited||[];
  const rankingRows=ranking.map((x,i)=>'<div style="padding:10px 0;border-bottom:1px solid #e2e8f0"><b>'+(i+1)+'. '+escapeHTML(x.name)+'</b><div style="font-size:13px;color:#64748b">'+x.visits+' '+(x.visits===1?'visita':'visite')+' · Ultima: '+escapeHTML(dateTime(x.lastAt))+'</div></div>').join('');
  const neverRows=never.map(x=>'<div style="padding:9px 0;border-bottom:1px solid #e2e8f0"><b>'+escapeHTML(x.name)+'</b><div style="font-size:13px;color:#64748b">Nessun accesso registrato</div></div>').join('');
  const body=modal?.querySelector('#vgOnlineBody');
  if(body)body.innerHTML='<h3 style="margin:0 0 8px">🟢 Online adesso ('+active.length+')</h3><p style="font-size:13px;color:#64748b;margin:0 0 8px">Attivi negli ultimi 5 minuti, escluso amministratore</p>'+(onlineRows||'<p>Nessun cliente online.</p>')+'<h3 style="margin:24px 0 8px">🏆 Chi entra più spesso</h3><p style="font-size:13px;color:#64748b;margin:0 0 8px">Visite registrate, unendo gli eventi dello stesso cliente entro 5 minuti</p>'+(rankingRows||'<p>Nessuna visita registrata.</p>')+'<h3 style="margin:24px 0 8px">💤 Chi non è mai entrato ('+never.length+')</h3>'+(neverRows||'<p>Tutti i clienti hanno almeno un accesso registrato.</p>')+'<h3 style="margin:24px 0 8px">📋 Storico visite</h3><p style="font-size:13px;color:#64748b;margin:0 0 8px">Eventi ravvicinati raggruppati per cliente, escluso amministratore</p>'+(historyRows||'<p>Nessuna visita registrata.</p>')+(data.truncated?'<p style="color:#9f1239">Storico parziale: oltre 5000 eventi.</p>':'');
 }catch(e){console.error('Storico clienti:',e);const body=modal?.querySelector('#vgOnlineBody');if(body)body.textContent='Impossibile caricare i dati: '+(e?.name==='AbortError'?'connessione scaduta':e?.message||'errore di rete')+'. Chiudi e riprova.'}
}
let busy=false;
async function refresh(){
 if(busy||document.hidden||!document.getElementById('vg_online_customers'))return;
 busy=true;
 try{
  const client=await window.VG_SUPABASE_READY;
  const {data:{session}}=await client.auth.getSession();
  if(!session?.access_token)throw Error('Sessione amministratore non disponibile');
  const response=await fetch(API,{headers:{Authorization:'Bearer '+session.access_token},cache:'no-store'});
  if(!response.ok)throw Error('Verifica non riuscita');
  const data=await response.json();
  document.getElementById('vg_online_customers').textContent=String(data.online);
  document.getElementById('vg_online_subtitle').textContent='Attivi negli ultimi 5 minuti · aggiornamento automatico';
 }catch(e){
  const el=document.getElementById('vg_online_customers');if(el)el.textContent='—';
  const label=document.getElementById('vg_online_subtitle');if(label)label.textContent='Conteggio temporaneamente non disponibile';
 }finally{busy=false}
}
function start(){
 if(!render())return;
 refresh();setInterval(refresh,30000);
 document.addEventListener('visibilitychange',()=>{if(!document.hidden)refresh()});
}
if(document.readyState==='loading')document.addEventListener('DOMContentLoaded',start);else start();
})();