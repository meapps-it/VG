/* Vanity & Glamour discovery: new arrivals, real promotions, device-local favourites. */
(()=>{'use strict';
const STORE='vg-catalog-favorites-v1';
const root=document.createElement('section');
root.id='vgDiscovery';
root.className='vg-discovery';
root.setAttribute('aria-label','Preferiti');
root.hidden=true;
const css=document.createElement('style');
css.textContent=`
.vg-discovery{margin:16px 0 22px;padding:17px 14px;background:#fff;border:1px solid #eee2d9;border-radius:19px}
.vg-discovery h2{margin:0 0 7px;font-size:20px}.vg-discovery p{font-size:13px;margin:0 0 12px;color:#675b56}
.vg-discovery-grid{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:10px}
.vg-discovery-item{position:relative;min-width:0;border:1px solid #ede3db;border-radius:12px;overflow:hidden;background:#fff}
.vg-discovery-open{border:0;background:none;text-align:left;font:inherit;padding:0;width:100%;cursor:pointer}
.vg-discovery-open img{width:100%;aspect-ratio:1;object-fit:cover;background:#f5f1ed}
.vg-discovery-name{display:block;padding:8px;font-size:12px;line-height:1.3;font-weight:700;min-height:45px}
.vg-fav-button{border:1px solid #e1d2c6;border-radius:50%;width:36px;height:36px;flex:none;display:grid;place-items:center;font-size:19px;background:#fff;color:#a32250!important;cursor:pointer}
.vg-discovery-model{display:flex;align-items:flex-start;gap:4px;padding-right:5px}
.vg-discovery-model .vg-discovery-open{flex:1;min-width:0;overflow-wrap:anywhere}
.vg-discovery-model .vg-fav-button{margin-top:3px}
.vg-fav-button[aria-pressed=true]{background:#ffe8ee;color:#a32250!important}
.vg-fav-button{touch-action:manipulation;pointer-events:auto!important;user-select:none;-webkit-tap-highlight-color:transparent}
.vg-header-favorites{width:40px;height:40px;min-width:40px;border:1px solid #e1d9d2;border-radius:11px;background:#fff;color:#9d2852;display:grid;place-items:center;font-size:23px;cursor:pointer;position:relative}
.vg-header-favorites:focus-visible{outline:2px solid #9d2852;outline-offset:2px}
.brand-stage #vgHeaderFavorites .vg-header-favorites-count{position:absolute;top:-5px;right:-5px;box-sizing:border-box;min-width:22px;padding:1px 4px;background:#801c46;color:#fff!important;border:2px solid #fff;border-radius:20px;font:800 13px/16px system-ui!important;font-variant-numeric:tabular-nums;text-align:center;pointer-events:none}

.catalog .item{position:relative}
.vg-discovery-item .vg-price{display:block;padding:0 8px 9px;font-size:13px;font-weight:800;color:#2e2521}
@media(max-width:600px){.vg-discovery-grid{grid-template-columns:repeat(2,minmax(0,1fr))}.vg-discovery{padding:12px 10px}}
`;
document.head.append(css);
document.getElementById('catalogSection')?.after(root);
let items=[];
function read(){try{const ids=JSON.parse(localStorage.getItem(STORE)||'[]');return new Set(Array.isArray(ids)?ids.filter(x=>typeof x==='string'):[])}catch{return new Set()}}
let fav=read();
const headerFav=document.getElementById('vgHeaderFavorites');
function sync(){
 document.querySelectorAll('button[data-vg-fav]').forEach(button=>{
  const selected=fav.has(button.dataset.vgFav);
  button.setAttribute('aria-pressed',String(selected));
  button.setAttribute('aria-label',selected?'Rimuovi dai preferiti':'Aggiungi ai preferiti');
  if(button.id!=='vgProductFavorite')button.textContent=selected?'♥︎':'♡';
 });
 if(headerFav){const count=headerFav.querySelector('[data-fav-count]');if(count){count.textContent=String(fav.size);count.hidden=false}headerFav.setAttribute('aria-label','Apri preferiti ('+fav.size+')')}
 const detail=document.getElementById('vgProductFavorite');
 if(detail?.dataset.vgFav){const selected=fav.has(detail.dataset.vgFav);detail.setAttribute('aria-pressed',String(selected));detail.setAttribute('aria-label',selected?'Rimuovi dai preferiti':'Aggiungi ai preferiti');detail.innerHTML=selected?'♥︎ <span>Salvato</span>':'♡ <span>Salva</span>'}
}
if(headerFav){headerFav.addEventListener('click',()=>{root.hidden=false;render();root.scrollIntoView({behavior:window.matchMedia('(prefers-reduced-motion: reduce)').matches?'auto':'smooth',block:'start'});});sync();}
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
function heart(id){const on=fav.has(String(id));return '<button type="button" class="vg-fav-button" data-vg-fav="'+id+'" aria-label="'+(on?'Rimuovi dai preferiti':'Aggiungi ai preferiti')+'" aria-pressed="'+on+'">'+(on?'♥︎':'♡')+'</button>'}
function render(){
 if(root.hidden)return;
 root.innerHTML='<h2>I tuoi preferiti</h2>';
 const selected=items.filter(p=>fav.has(String(p.id)));
 if(!selected.length){
  const note=document.createElement('p');
  note.textContent='Tocca il cuore accanto a un modello per ritrovarlo qui.';
  root.append(note);
 }
 const grid=document.createElement('div');grid.className='vg-discovery-grid';
 selected.forEach(p=>{
  const box=document.createElement('article');box.className='vg-discovery-item';
  const open=document.createElement('button');open.type='button';open.className='vg-discovery-open';open.dataset.vgOpen=p.id;
  if(p.image){const photo=open.cloneNode();const img=document.createElement('img');img.src=p.image;img.loading='lazy';img.alt=p.name||'Articolo';photo.append(img);box.append(photo)}
  const name=document.createElement('span');name.className='vg-discovery-name';name.textContent=p.name||'Articolo';open.append(name);
  const price=document.createElement('span');price.className='vg-price';price.textContent=fmt(p.price);
  const model=document.createElement('div');model.className='vg-discovery-model';
  const like=document.createElement('button');like.type='button';like.className='vg-fav-button';like.dataset.vgFav=p.id;like.setAttribute('aria-pressed',String(fav.has(String(p.id))));like.setAttribute('aria-label',fav.has(String(p.id))?'Rimuovi dai preferiti':'Aggiungi ai preferiti');like.textContent=fav.has(String(p.id))?'♥︎':'♡';like.addEventListener('click',e=>{e.stopPropagation();toggleFavorite(p.id)});model.append(open,like);box.append(model,price);grid.append(box);
 });root.append(grid);
}
root.addEventListener('click',e=>{
 const open=e.target.closest('[data-vg-open]');if(open){window.vgDiscovery?.open?.(open.dataset.vgOpen);return}
});
function toggleFavorite(id){
 id=String(id||'');
 if(!id)return;
 if(fav.has(id))fav.delete(id);else fav.add(id);
 try{localStorage.setItem(STORE,JSON.stringify([...fav]))}catch(error){console.warn('Preferiti non persistenti su questo browser',error)}
 sync();
 render();
 let status=document.getElementById('vgFavFeedback');
 if(!status){status=document.createElement('div');status.id='vgFavFeedback';status.setAttribute('role','status');status.style.cssText='position:fixed;bottom:72px;left:50%;transform:translateX(-50%);z-index:99999;background:#fff1f6;color:#711437!important;border:1px solid #d88aaa;padding:11px 18px;border-radius:22px;font:600 14px system-ui;box-shadow:0 4px 18px #0003;pointer-events:none';document.body.append(status)}
 status.textContent=fav.has(id)?'♥ Aggiunto ai preferiti':'♡ Rimosso dai preferiti';
 clearTimeout(toggleFavorite.feedbackTimer);
 toggleFavorite.feedbackTimer=setTimeout(()=>{status.textContent='';status.hidden=true},1800);
 status.hidden=false;
}
window.addEventListener('storage',e=>{if(e.key===STORE){fav=read();sync();render()}});
window.vgDiscovery={toggle:toggleFavorite,sync,render(products){items=(products||[]).filter(p=>p&&p.id);render()},open:null,heart};
})();
