(()=>{
'use strict';
const API='https://qfjwtawsqfwmsmrrgqfi.supabase.co/functions/v1/vg-shop-presence?action=heartbeat';
let busy=false;
async function ping(){
 if(busy||document.hidden||document.body.classList.contains('shop-locked')||document.body.classList.contains('shop-checking'))return;
 let token='';try{token=localStorage.getItem('vg-shop-customer-v1')||''}catch{}
 if(!/^[a-f0-9]{64}$/.test(token))return;
 busy=true;
 try{await fetch(API,{method:'POST',headers:{'x-vg-customer-token':token},cache:'no-store',keepalive:false})}catch(e){console.info('[VG Presence] Connessione non disponibile')}
 finally{busy=false}
}
window.addEventListener('vg-catalog-ready',ping);
document.addEventListener('visibilitychange',()=>{if(!document.hidden)ping()});
setInterval(ping,60000);
})();