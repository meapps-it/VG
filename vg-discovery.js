/* Vanity & Glamour discovery: new arrivals, real promotions, device-local favourites. */
(()=>{'use strict';
const STORE='vg-catalog-favorites-v1';
const root=document.createElement('section');
root.id='vgDiscovery';
root.className='vg-discovery';
root.setAttribute('aria-label','Scopri gli articoli');
const css=document.createElement('style');
css.textContent=`
.vg-discovery{margin:16px 0 22px;padding:17px 14px;background:#fff;border:1px solid #eee2d9;border-radius:19px}
.vg-discovery-tabs{display:flex;gap:8px;overflow-x:auto;padding:2px 0 14px}
.vg-discovery-tabs button{border:1px solid #dccfc3;border-radius:22px;background:#fffaf6;padding:10px 12px;white-space:nowrap;font:inherit;font-weight:750;cursor:pointer;color:#342620}
.vg-discovery-tabs button[aria-pressed=true]{background:#392820;color:#fff}
.vg-discovery h2{margin:0 0 7px;font-size:20px}.vg-discovery p{font-size:13px;margin:0 0 12px;color:#675b56}
.vg-discovery-grid{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:10px}
.vg-discovery-item{position:relative;min-width:0;border:1px solid #ede3db;border-radius:12px;overflow:hidden;background:#fff}
.vg-discovery-open{border:0;background:none;text-align:left;font:inherit;padding:0;width:100%;cursor:pointer}
.vg-discovery-open img{width:100%;aspect-ratio:1;object-fit:cover;background:#f5f1ed}
.vg-discovery-name{display:block;padding:8px;font-size:12px;line-height:1.3;font-weight:700;min-height:45px}
.vg-fav-button{border:1px solid #e1d2c6;border-radius:50%;width:32px;height:32px;display:grid;place-items:center;font-size:19px;background:#fff;color:#6c3d46;cursor:pointer}
.vg-discovery-item .vg-fav-button{position:absolute;right:5px;top:5px}
.vg-fav-button[aria-pressed=true]{background:#ffe8ee;color:#a91f4c}
.vg-fav-button{touch-action:manipulation;pointer-events:auto!important;user-select:none;-webkit-tap-highlight-color:transparent}
#catalog .vg-fav-card{pointer-events:auto!important;z-index:40!important}
#catalog .vg-fav-card button{pointer-events:auto!important;position:relative;z-index:41}

.vg-header-favorites{width:40px;height:40px;min-width:40px;border:1px solid #e1d9d2;border-radius:11px;background:#fff;color:#9d2852;display:grid;place-items:center;font-size:23px;cursor:pointer;position:relative}
.vg-header-favorites:focus-visible{outline:2px solid #9d2852;outline-offset:2px}
.vg-header-favorites-count{position:absolute;top:-5px;right:-5px;min-width:17px;padding:1px 3px;background:#9d2852;color:#fff;border-radius:20px;font:700 10px/15px system-ui;text-align:center}

.catalog .item{position:relative}.vg-fav-card{position:absolute;right:7px;top:7px;z-index:2}
.vg-discovery-item .vg-price{display:block;padding:0 8px 9px;font-size:13px;font-weight:800;color:#2e2521}
@media(max-width:600px){.vg-discovery-grid{grid-template-columns:repeat(2,minmax(0,1fr))}.vg-discovery{padding:12px 10px}.vg-discovery-tabs button{font-size:12px;padding:9px}}
`;
document.head.append(css);
document.getElementById('catalogSection')?.before(root);
let items=[],promoIds=new Set(),tab='new';
function read(){try{const ids=JSON.parse(localStorage.getItem(STORE)||'[]');return new Set(Array.isArray(ids)?ids.filter(x=>typeof x==='string').slice(0,1000):[])}catch{return new Set()}}
let fav=read();
const headerFav=document.getElementById('vgHeaderFavorites');
function syncHeader(){
 if(headerFav){const count=headerFav.querySelector('[data-fav-count]');if(count){count.textContent=String(fav.size);count.hidden=!fav.size}headerFav.setAttribute('aria-label','Apri preferiti ('+fav.size+')')}
 const detail=document.getElementById('vgProductFavorite');
 if(detail?.dataset.vgFav){const selected=fav.has(detail.dataset.vgFav);detail.setAttribute('aria-pressed',String(selected));detail.setAttribute('aria-label',selected?'Rimuovi dai preferiti':'Aggiungi ai preferiti');detail.innerHTML=selected?'♥ <span>Salvato</span>':'♡ <span>Salva</span>'}
}
if(headerFav){headerFav.addEventListener('click',()=>{tab='fav';render();root.scrollIntoView({behavior:window.matchMedia('(prefers-reduced-motion: reduce)').matches?'auto':'smooth',block:'start'});});syncHeader();}
const detailFavorite=document.getElementById('vgProductFavorite');if(detailFavorite)detailFavorite.addEventListener('click',e=>{e.stopPropagation();toggleFavorite(detailFavorite.dataset.vgFav)});
const catalogueRoot=document.getElementById('catalog');
if(catalogueRoot)catalogueRoot.addEventListener('click',e=>{
 const target=e.target instanceof Element?e.target:e.target?.parentElement;
 const button=target?.closest('button[data-vg-fav]');
 if(!button)return;
 e.preventDefault();
 e.stopImmediatePropagation();
 toggleFavorite(button.dataset.vgFav);
},true);


const fmt=n=>n===null?'Info in privato':Number(n||0).toLocaleString('it-IT',{style:'currency',currency:'EUR'});
function isPromo(p){return promoIds.has(p.id)&&p.promotionActive===true&&p.promotion===true}
function candidates(){
 const cutoff=Date.now()-7*86400000;
 if(tab==='new')return items.filter(p=>{const t=Date.parse(p.createdAt||'');return Number.isFinite(t)&&t>=cutoff&&t<=Date.now()}).sort((a,b)=>Date.parse(b.createdAt)-Date.parse(a.createdAt));
 if(tab==='promo')return items.filter(isPromo);
 return items.filter(p=>fav.has(String(p.id)));
}
function heart(id){const on=fav.has(String(id));return '<button type="button" class="vg-fav-button" data-vg-fav="'+id+'" aria-label="'+(on?'Rimuovi dai preferiti':'Aggiungi ai preferiti')+'" aria-pressed="'+on+'">'+(on?'♥':'♡')+'</button>'}
function render(){
 const opts=[['new','✨ Appena arrivati'],['promo','🏷️ Promozioni'],['fav','❤️ Preferiti']];
 root.innerHTML='<div class="vg-discovery-tabs" role="group" aria-label="Esplora il catalogo">'+opts.map(([id,label])=>'<button type="button" data-vg-tab="'+id+'" aria-pressed="'+(id===tab)+'">'+label+'</button>').join('')+'</div><h2>'+({new:'Appena arrivati',promo:'In promozione',fav:'I tuoi preferiti'}[tab])+'</h2>';
 const selected=candidates();
 const note=document.createElement('p');
 note.textContent=selected.length?'':tab==='new'?'Nessun nuovo articolo negli ultimi 7 giorni. Esplora la collezione qui sotto.':tab==='promo'?'Nessuna promozione attiva in questo momento.':'Tocca il cuoricino sugli articoli che ti piacciono per ritrovarli qui, su questo dispositivo.';
 root.append(note);
 const grid=document.createElement('div');grid.className='vg-discovery-grid';
 selected.slice(0,12).forEach(p=>{
  const box=document.createElement('article');box.className='vg-discovery-item';
  const open=document.createElement('button');open.type='button';open.className='vg-discovery-open';open.dataset.vgOpen=p.id;
  if(p.image){const img=document.createElement('img');img.src=p.image;img.loading='lazy';img.alt=p.name||'Articolo';open.append(img)}
  const name=document.createElement('span');name.className='vg-discovery-name';name.textContent=p.name||'Articolo';open.append(name);
  const price=document.createElement('span');price.className='vg-price';price.textContent=fmt(p.price);
  box.append(open,price);
  const like=document.createElement('button');like.type='button';like.className='vg-fav-button';like.dataset.vgFav=p.id;like.setAttribute('aria-pressed',String(fav.has(String(p.id))));like.setAttribute('aria-label',fav.has(String(p.id))?'Rimuovi dai preferiti':'Aggiungi ai preferiti');like.textContent=fav.has(String(p.id))?'♥':'♡';like.addEventListener('click',e=>{e.stopPropagation();toggleFavorite(p.id)});box.append(like);grid.append(box);
 });root.append(grid);
}
root.addEventListener('click',e=>{
 const tabBtn=e.target.closest('[data-vg-tab]');if(tabBtn){tab=tabBtn.dataset.vgTab;render();return}
 const open=e.target.closest('[data-vg-open]');if(open){window.vgDiscovery?.open?.(open.dataset.vgOpen);return}
});
function toggleFavorite(id){
 id=String(id||'');
 if(!id)return;
 if(fav.has(id))fav.delete(id);else fav.add(id);
 try{localStorage.setItem(STORE,JSON.stringify([...fav]))}catch(error){console.warn('Preferiti non persistenti su questo browser',error)}
 document.querySelectorAll('[data-vg-fav]').forEach(b=>{
  if(b.dataset.vgFav!==id)return;
  if(b.id!=='vgProductFavorite')b.textContent=fav.has(id)?'♥':'♡';
  b.setAttribute('aria-pressed',String(fav.has(id)));
  b.setAttribute('aria-label',fav.has(id)?'Rimuovi dai preferiti':'Aggiungi ai preferiti');
 });
 syncHeader();
 if(tab==='fav')render();
 let status=document.getElementById('vgFavFeedback');
 if(!status){status=document.createElement('div');status.id='vgFavFeedback';status.setAttribute('role','status');status.style.cssText='position:fixed;bottom:72px;left:50%;transform:translateX(-50%);z-index:99999;background:#372821;color:white;padding:11px 18px;border-radius:22px;font:600 14px system-ui;box-shadow:0 4px 18px #0003;pointer-events:none';document.body.append(status)}
 status.textContent=fav.has(id)?'♥ Aggiunto ai preferiti':'♡ Rimosso dai preferiti';
 clearTimeout(toggleFavorite.feedbackTimer);
 toggleFavorite.feedbackTimer=setTimeout(()=>{status.textContent='';status.hidden=true},1800);
 status.hidden=false;
}
window.addEventListener('storage',e=>{if(e.key===STORE){fav=read();syncHeader();render()}});
window.vgDiscovery={toggle:toggleFavorite,sync:syncHeader,render(products,promotions){items=(products||[]).filter(p=>p&&p.id);promoIds=new Set(promotions||[]);render()},open:null,heart};
})();
