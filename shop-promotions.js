(()=>{'use strict';
 function render(p,items){
  let root=document.getElementById('shopPromotions');if(!root){root=document.createElement('section');root.id='shopPromotions';root.className='shop-promotions';root.setAttribute('aria-labelledby','shopPromoTitle');document.querySelector('main.container')?.prepend(root)}
  root.replaceChildren();root.hidden=true;if(!p?.enabled)return;
  const copy=document.createElement('div'),tag=document.createElement('span'),title=document.createElement('h2');tag.className='section-kicker';tag.textContent='IN EVIDENZA';title.id='shopPromoTitle';title.textContent=p.title||'Promozioni';copy.append(tag,title);
  if(p.body){const text=document.createElement('p');text.textContent=p.body;copy.append(text)}root.append(copy);
  if(p.image_url){try{const u=new URL(p.image_url);if(u.protocol==='https:'){const img=document.createElement('img');img.className='shop-promo-banner';img.src=u.href;img.alt=p.title||'Promozioni';img.referrerPolicy='no-referrer';img.onerror=()=>img.remove();root.append(img)}}catch{}}
  const selected=new Set(p.product_ids||[]),products=items.filter(x=>selected.has(x.id)).slice(0,12);
  if(products.length){const grid=document.createElement('div');grid.className='shop-promo-products';for(const p of products){const b=document.createElement('button');b.type='button';b.dataset.detail=p.id;if(p.image){const img=document.createElement('img');img.src=p.image;img.alt='';img.loading='lazy';b.append(img)}const name=document.createElement('strong'),price=document.createElement('span');name.textContent=p.name;price.textContent=p.priceHidden?'Info in privato':Number(p.price).toLocaleString('it-IT',{style:'currency',currency:'EUR'});b.append(name,price);grid.append(b)}root.append(grid)}
  root.hidden=false;
 }
 window.shopPromotions={render};window.addEventListener('vg-shop-logout',()=>render(null,[]));
})();
