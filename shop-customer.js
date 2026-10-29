/* One personal identity for catalogue access and order history. */
(()=>{'use strict';
const API='https://qfjwtawsqfwmsmrrgqfi.supabase.co/functions/v1/vg-shop-catalog',el=id=>document.getElementById(id);
let profile=null,offset=0,timer,ordersBusy=false;
const esc=v=>String(v??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
function state(){el('customerAuth').hidden=!!profile;el('customerAccount').hidden=!profile;el('customerPhoneLabel').textContent=profile?(profile.phone?'Telefono: +'+profile.phone:profile.email||'Account Google'):'';el('checkoutAccountHint').textContent=profile?'La richiesta sarà visibile in I miei ordini.':''}
async function request(url,options={}){return shopAccess.fetch(url,options)}
async function action(name,body){const r=await request(API+'?action=customer-'+name,body===undefined?{cache:'no-store'}:{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(body)});const data=await r.json();if(!r.ok)throw Error(data.error||'Operazione non riuscita');return data}
async function restore(){profile=shopAccess.customer();state();if(profile){try{const r=await request(API+'?action=profile-book',{cache:'no-store'}),d=await r.json();if(r.ok)profile={...profile,profile:{...d.profile,...(d.addresses?.[0]||{}),phone:d.addresses?.[0]?.phone||d.profile?.phone||d.phone||''}}}catch{}await shopShipping.applyProfile(profile.profile,{ifUntouched:true,ignoreRememberPreference:true})}}
async function orders(append=false,silent=false){if(!profile||ordersBusy)return;ordersBusy=true;const identity=profile;try{const start=append?offset:0;if(!silent)el('customerStatus').textContent='Caricamento ordini…';const data=await action('orders&offset='+start);if(profile!==identity)return;recordOrderUpdates(data.orders);if(!append){offset=0;el('customerOrders').replaceChildren();}for(const o of data.orders){const card=document.createElement('article');card.className='customer-order';const date=new Intl.DateTimeFormat('it-IT',{timeZone:'Europe/Rome',dateStyle:'short',timeStyle:'short'}).format(new Date(o.requestedAt));card.innerHTML='<strong>'+esc(o.number)+'</strong><p>'+esc(date)+' · ora italiana</p><p class="order-state">'+esc(o.displayStatus||({spedito:'Spedito',consegnato:'Consegnato',annullato:'Annullato'})[o.status]||(o.paid?'Pagamento confermato':'In attesa di conferma'))+'</p><ul>'+o.items.map(i=>'<li>'+esc(i.name)+' × '+esc(i.qty)+(i.size?' · Misura '+esc(i.size):'')+(i.color?' · '+esc(i.color):'')+' · '+esc(i.quality)+' · '+Number(i.price).toLocaleString('it-IT',{style:'currency',currency:'EUR'})+'</li>').join('')+'</ul><strong>Totale: '+Number(o.total).toLocaleString('it-IT',{style:'currency',currency:'EUR'})+'</strong>'+(o.tracking?'<p>Spedizione '+esc(o.carrier)+' · '+esc(o.tracking)+'</p>':'');if(o.editable){const edit=document.createElement('button');edit.type='button';edit.className='secondary amend-order';edit.textContent='Modifica ordine';edit.onclick=()=>window.dispatchEvent(new CustomEvent('vg-amend-order',{detail:o}));card.append(edit)}el('customerOrders').append(card)}offset+=data.orders.length;el('customerMore').hidden=!data.hasMore;el('customerStatus').textContent=offset?'':'Non hai ancora richieste collegate al tuo account. Accedi prima di inviare il prossimo ordine.'}catch(e){if(!silent)el('customerStatus').textContent=e.message}finally{ordersBusy=false}}

function recordOrderUpdates(rows){
 const key='vg_order_updates_v1:'+String(profile?.phone||profile?.email||'');
 let saved={};try{saved=JSON.parse(localStorage.getItem(key)||'{}')}catch{}
 if(!saved||typeof saved!=='object'||Array.isArray(saved))saved={};
 let banner=el('customerOrderUpdates');if(!banner){banner=document.createElement('section');banner.id='customerOrderUpdates';banner.setAttribute('role','status');banner.setAttribute('aria-live','polite');banner.style.cssText='padding:16px;border:2px solid #245fbe;border-radius:16px;background:#edf4ff;margin-bottom:16px';el('customerAccount').prepend(banner)}
 for(const o of rows){const label=o.displayStatus||(o.paid?'Pagamento confermato':o.status),signature=JSON.stringify([label,!!o.paid,o.tracking||'']);
  const previous=saved[o.id];saved[o.id]={signature,label,number:o.number,unread:previous?.unread||false};
  if(previous&&previous.signature!==signature)saved[o.id].unread=true;
 }
 banner.replaceChildren();const unread=Object.values(saved).filter(v=>v.unread);banner.hidden=!unread.length;
 if(unread.length){const title=document.createElement('strong');title.textContent='Aggiornamenti dei tuoi ordini';banner.append(title);
 for(const item of unread){const p=document.createElement('p');p.textContent=item.number+' · '+item.label;banner.append(p)}
 const clear=document.createElement('button');clear.type='button';clear.textContent='Ho letto';clear.onclick=()=>{Object.values(saved).forEach(v=>v.unread=false);try{localStorage.setItem(key,JSON.stringify(saved))}catch{}banner.hidden=true};banner.append(clear)}
 try{localStorage.setItem(key,JSON.stringify(saved))}catch{}
}
setInterval(()=>{if(profile&&!document.hidden&&el('customerView').classList.contains('open')&&offset<=20&&!el('customerOrders').querySelector('input:focus,textarea:focus'))orders(false,true)},30000);
document.addEventListener('visibilitychange',()=>{if(!document.hidden&&profile&&el('customerView').classList.contains('open')&&offset<=20)orders(false,true)});

function show(open,navigate=true){if(open&&navigate)window.dispatchEvent(new Event('vg-customer-open'));el('customerView').classList.toggle('open',open);el('customerView').setAttribute('aria-hidden',String(!open));if(open){document.body.style.overflow='hidden';el('customerView').scrollTop=0;el('customerBack').focus();restore().then(()=>orders())}else if(!document.querySelector('#overlay.open,#productView.open'))document.body.style.overflow=''}
window.shopCustomer={fetch:request,restore,show};
el('openCustomer').onclick=()=>show(true);el('checkoutAccount').onclick=()=>show(true);el('customerBack').onclick=()=>history.back();el('customerMore').onclick=()=>orders(true);
el('customerLogout').onclick=()=>shopAccess.logout();
window.addEventListener('vg-shipping-saved',e=>{clearTimeout(timer);if(!profile)return;const data=e.detail;if(!data)return;if(data&&!['name','phone','address','province','city','cap'].every(k=>String(data[k]||'').trim()))return;timer=setTimeout(()=>action('profile',{profile:data||{}}).catch(()=>{}),700)});
window.addEventListener('vg-catalog-ready',restore);window.addEventListener('vg-customer-ready',restore);window.addEventListener('vg-order-sent',()=>{if(profile)profile.profile=shopShipping.current()});window.addEventListener('vg-shop-logout',()=>{profile=null;el('customerOrderUpdates')?.remove();clearTimeout(timer);el('customerOrders').replaceChildren();el('customerStatus').textContent='';shopShipping.clear();state()});state();
})();
