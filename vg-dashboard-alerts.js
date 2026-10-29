/* Opening an alert never dismisses it. Dismissals are scoped to the signed-in account. */
(()=>{'use strict';
 const host=document.getElementById('vgDashboardAlerts');if(!host)return;
 const list=document.createElement('div');host.append(list);
 const style=document.createElement('style');style.textContent='#vgDashboardAlerts [hidden]{display:none!important}#vgDashboardAlerts .vgCustomersTile,#vgDashboardAlerts .vgOrderAlert{background:#fff0d3!important;border:3px solid #bb303c!important;margin:12px 0;box-shadow:3px 4px 0 #7f2330!important}#vgDashboardAlerts .vgOrderAlert{display:flex;gap:10px;align-items:center;border-radius:20px;padding:12px}#vgDashboardAlerts button{font:inherit;cursor:pointer}#vgDashboardAlerts .vgOrderOpen{flex:1;text-align:left;background:transparent;border:0;font-weight:800;color:#182b45;min-width:0}#vgDashboardAlerts .vgOrderDismiss{background:#fff;border:2px solid #bb303c;border-radius:12px;padding:10px;color:#84222c}';document.head.append(style);
 let signature='';
 function refresh(){
  if(typeof cloudSession==='undefined'||!cloudSession?.user?.id){list.replaceChildren();signature='';return}
  const key='vg_dismissed_order_alerts_v1:'+cloudSession.user.id;
  let dismissed;try{dismissed=JSON.parse(localStorage.getItem(key)||'[]');if(!Array.isArray(dismissed))dismissed=[]}catch{dismissed=[]}
  const orders=(loadDB().ordini||[]).filter(o=>/Richiesta da catalogo/i.test(o.note||'')&&!dismissed.includes(String(o.id)));
  const next=JSON.stringify(orders.map(o=>[o.id,o.numeroOrdine,o.data]));if(next===signature)return;signature=next;list.replaceChildren();
  for(const order of orders){
   const card=document.createElement('article');card.className='vgOrderAlert';
   const open=document.createElement('button');open.type='button';open.className='vgOrderOpen';open.textContent='Nuovo ordine · '+(order.numeroOrdine||order.id)+' · '+(order.data||'');open.onclick=()=>openOrdView(order.id);
   const cancel=document.createElement('button');cancel.type='button';cancel.className='vgOrderDismiss';cancel.textContent='Cancella avviso';cancel.setAttribute('aria-label','Cancella avviso ordine '+(order.numeroOrdine||order.id));
   cancel.onclick=()=>{try{const saved=JSON.parse(localStorage.getItem(key)||'[]');localStorage.setItem(key,JSON.stringify([...new Set([...saved,String(order.id)])]));signature='';refresh()}catch{cancel.textContent='Riprova'}};
   card.append(open,cancel);list.append(card);
  }
 }
 window.VG_DASHBOARD_ALERTS={refresh};refresh();setInterval(refresh,5000);window.addEventListener('storage',refresh);document.addEventListener('visibilitychange',refresh);
})();
