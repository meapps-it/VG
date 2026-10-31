/* Merchandising uses only the already-authorized catalogue, without customer profiling. */
(()=>{'use strict';
const esc=value=>String(value??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const norm=value=>String(value||'').trim().toLocaleLowerCase('it');
const money=value=>value===null?'Info in privato':Number(value||0).toLocaleString('it-IT',{style:'currency',currency:'EUR'});
const image=p=>p.image||(Array.isArray(p.images)?p.images.find(Boolean):'')||'';
const publicItem=p=>p&&p.id&&p.published!==false&&p.visible!==false;

const catalogueRanks=new Map();let featuredIds=null;
const rememberRead=key=>{try{const value=JSON.parse(localStorage.getItem(key)||'[]');return Array.isArray(value)?value.map(String):[]}catch{return []}};
const rememberWrite=(key,value)=>{try{localStorage.setItem(key,JSON.stringify(value))}catch{}};
function shuffled(items){
 const result=items.slice();
 for(let i=result.length-1;i>0;i--){const k=Math.floor(Math.random()*(i+1));[result[i],result[k]]=[result[k],result[i]]}
 return result;
}
function order(items){
 if(!catalogueRanks.size){
  const mixed=shuffled(items),previous=new Set(rememberRead('vg-shop-last-front-v1'));
  const fresh=mixed.findIndex(p=>!previous.has(String(p.id)));
  if(fresh>0)mixed.unshift(...mixed.splice(fresh,1));
  mixed.forEach((p,i)=>catalogueRanks.set(String(p.id),i));
  rememberWrite('vg-shop-last-front-v1',mixed.slice(0,6).map(p=>String(p.id)));
 }
 for(const p of items)if(!catalogueRanks.has(String(p.id)))catalogueRanks.set(String(p.id),catalogueRanks.size);
 return items.slice().sort((a,b)=>catalogueRanks.get(String(a.id))-catalogueRanks.get(String(b.id)));
}
function featured(items){
 const seen=new Set(),candidates=order(items).filter(p=>publicItem(p)&&image(p)&&!seen.has(String(p.id))&&(seen.add(String(p.id)),true));
 if(featuredIds===null){
  const previous=new Set(rememberRead('vg-shop-last-featured-v1'));
  const fresh=candidates.filter(p=>!previous.has(String(p.id)));
  const pool=[...fresh,...candidates.filter(p=>previous.has(String(p.id)))],chosen=[],categories=new Set();
  // Prefer different categories and products from the previous visit.
  for(const p of pool){const category=norm(p.category);if(categories.has(category))continue;chosen.push(p);categories.add(category);if(chosen.length===3)break}
  for(const p of pool){if(chosen.length===3)break;if(!chosen.includes(p))chosen.push(p)}
  featuredIds=chosen.map(p=>String(p.id));rememberWrite('vg-shop-last-featured-v1',featuredIds);
 }
 return featuredIds.map(id=>candidates.find(p=>String(p.id)===id)).filter(Boolean);
}

function related(items,current,limit=4){
 if(!current)return [];
 const seen=new Set([String(current.id)]),price=Number(current.price)||0;
 return items.filter(p=>{if(!publicItem(p)||seen.has(String(p.id)))return false;seen.add(String(p.id));return true})
 .map((p,index)=>{
  const category=!!norm(current.category)&&norm(current.category)===norm(p.category);
  const brand=!!norm(current.brand)&&norm(current.brand)===norm(p.brand);
  const quality=!!norm(current.quality)&&norm(current.quality)===norm(p.quality);
  const amount=Number(p.price)||0;
  const proximity=price>0&&amount>0?1-Math.min(1,Math.abs(amount-price)/Math.max(amount,price)):0;
  return {product:p,index,score:(category?50:0)+(brand?30:0)+(quality?8:0)+proximity*12,
   reason:category?'Stessa categoria':brand?'Della stessa marca':'Dalla selezione'};
 }).sort((a,b)=>b.score-a.score||a.index-b.index).slice(0,Math.max(0,Math.min(6,Number(limit)||0)));
}
function card(p,reason){
 const photo=image(p),id=esc(p.id),name=esc(p.name||'Articolo');
 return '<article class="related-item"><button type="button" class="related-photo" data-detail="'+id+'" aria-label="Scopri '+name+'">'+
  (photo?'<img loading="lazy" src="'+esc(photo)+'" alt="'+name+'">':'<span class="missing-photo">Foto non disponibile</span>')+
  '</button><div class="related-copy"><span class="recommendation-reason">'+esc(reason)+'</span><span class="category">'+esc(p.brand||p.category||'Shop & Parallelo')+
  '</span><h3><button type="button" data-detail="'+id+'">'+name+'</button></h3><div class="related-bottom"><span class="price">'+money(p.price)+
  '</span><button type="button" class="related-open" data-detail="'+id+'" aria-label="Apri '+name+'">↗</button></div></div></article>';
}
function home(items){
 const count=document.getElementById('collectionCount'),mosaic=document.getElementById('collectionMosaic');
 if(count)count.textContent=items.length+' '+(items.length===1?'articolo da scoprire':'articoli da scoprire');
 if(!mosaic)return;
 const selected=featured(items);
 const signature=selected.map(p=>String(p.id)+image(p)).join('|');
 if(mosaic.dataset.signature===signature)return;
 mosaic.dataset.signature=signature;mosaic.replaceChildren();
 for(const [index,p] of selected.entries()){
  const button=document.createElement('button');button.type='button';button.className='collection-look look-'+index;
  button.dataset.detail=p.id;button.setAttribute('aria-label','Scopri '+p.name);
  const img=document.createElement('img');img.src=image(p);img.alt=p.name||'Articolo della selezione';
  if(index>0)img.loading='lazy';
  const label=document.createElement('span');label.textContent=p.category||p.brand||'Da scoprire';button.append(img,label);mosaic.append(button);
 }
 mosaic.hidden=!selected.length;
}
function product(items,current){
 const trail=document.getElementById('productTrail');
 if(trail)trail.textContent=[current.category,current.brand].filter(Boolean).join(' / ')||'La selezione';
 const facts=document.getElementById('productFacts');
 if(facts){facts.replaceChildren();for(const [label,value] of [['Marca',current.brand],['Categoria',current.category],['Codice articolo',current.sku]]){
  if(!value)continue;const pair=document.createElement('div'),dt=document.createElement('dt'),dd=document.createElement('dd');
  dt.textContent=label;dd.textContent=value;pair.append(dt,dd);facts.append(pair);
 }}
 const section=document.getElementById('relatedSection'),grid=document.getElementById('relatedProducts');
 if(!section||!grid)return;
 const choices=related(items,current);section.hidden=!choices.length;
 grid.innerHTML=choices.map(x=>card(x.product,x.reason)).join('');
}
window.shopBoutique={related,home,product,order};
})();
