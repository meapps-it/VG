(()=>{
'use strict';
const API='https://qfjwtawsqfwmsmrrgqfi.supabase.co/functions/v1/vg-shop-presence?action=stats';
function render(){
 const stat=document.querySelector('#c_profit_total')?.closest('.stat');
 if(!stat)return false;
 stat.removeAttribute('data-go');
 stat.innerHTML='<div class="k">Clienti online <span aria-hidden="true" style="color:#1a9b59">●</span></div><div id="vg_online_customers" class="v big" style="color:#168e51">—</div><div id="vg_online_subtitle" class="sub">Attivi negli ultimi 5 minuti</div><span id="c_profit_total" hidden aria-hidden="true"></span>';
 return true;
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