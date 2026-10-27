/* Merchandising uses only the already-authorized catalogue, without customer profiling. */
(()=>{'use strict';
const esc=value=>String(value??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const norm=value=>String(value||'').trim().toLocaleLowerCase('it');
const money=value=>Number(value||0).toLocaleString('it-IT',{style:'currency',currency:'EUR'});
const image=p=>p.image||(Array.isArray(p.images)?p.images.find(Boolean):'')||'';
const publicItem=p=>p&&p.id&&p.published!==false&&p.visible!==false;
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
 const seen=new Set(),selected=items.filter(p=>publicItem(p)&&image(p)&&!seen.has(String(p.id))&&(seen.add(String(p.id)),true)).slice(0,3);
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
window.shopBoutique={related,home,product};
})();
