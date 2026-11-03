(()=>{'use strict';
 let cleanup=()=>{};
 function render(p,items=[]){
  cleanup();cleanup=()=>{};
  let root=document.getElementById('shopPromotions');if(!root){root=document.createElement('section');root.id='shopPromotions';root.className='shop-promotions';root.setAttribute('aria-labelledby','shopPromoTitle');document.querySelector('main.container')?.prepend(root)}
  root.replaceChildren();root.hidden=true;if(!p?.enabled)return;
  const copy=document.createElement('div'),tag=document.createElement('span'),title=document.createElement('h2');tag.className='section-kicker';tag.textContent='PROMO IN PRIMO PIANO';title.id='shopPromoTitle';title.textContent=p.title||'Promozioni';copy.append(tag,title);
  if(p.body){const text=document.createElement('p');text.textContent=p.body;copy.append(text)}root.append(copy);
  if(p.image_url){try{const u=new URL(p.image_url);if(u.protocol==='https:'){const img=document.createElement('img');img.className='shop-promo-banner';img.src=u.href;img.alt=p.title||'Promozioni';img.referrerPolicy='no-referrer';img.onerror=()=>img.remove();root.append(img)}}catch{}}
  const selected=new Set(p.product_ids||[]),products=items.filter(x=>selected.has(x.id)&&(x.promotionActive??x.promotion)===true).slice(0,12);
  if(products.length){
   const grid=document.createElement('div');grid.className='shop-promo-products';grid.id='shopPromoProducts';grid.setAttribute('aria-label','Articoli in promozione');
   for(const product of products){const b=document.createElement('button');b.type='button';b.dataset.detail=product.id;if(product.image){const img=document.createElement('img');img.src=product.image;img.alt='';img.loading='lazy';b.append(img)}const name=document.createElement('strong'),price=document.createElement('span');name.textContent=product.name;price.textContent=product.priceHidden?'Info in privato':Number(product.price).toLocaleString('it-IT',{style:'currency',currency:'EUR'});b.append(name,price);grid.append(b)}root.append(grid);
   if(products.length>1){
    const controls=document.createElement('div');controls.className='shop-promo-controls';let index=0,timer,paused=false,hover=false,visible=true;
    const reduced=window.matchMedia?.('(prefers-reduced-motion: reduce)');
    function move(step){index=(index+step+products.length)%products.length;const card=grid.children[index];grid.scrollTo?.({left:card.offsetLeft-grid.children[0].offsetLeft,behavior:reduced?.matches?'instant':'smooth'})}
    function sync(){clearInterval(timer);if(!paused&&!hover&&!document.hidden&&visible&&!root.contains(document.activeElement)&&!reduced?.matches)timer=setInterval(()=>move(1),4500)}
    for(const [label,step] of [['Precedente',-1],['Successiva',1]]){const b=document.createElement('button');b.type='button';b.textContent=label;b.setAttribute('aria-controls',grid.id);b.onclick=()=>{move(step);sync()};controls.append(b)}
    const toggle=document.createElement('button');toggle.type='button';toggle.textContent='Pausa scorrimento';toggle.onclick=()=>{paused=!paused;toggle.textContent=paused?'Riprendi scorrimento':'Pausa scorrimento';sync()};controls.append(toggle);root.append(controls);
    const enter=()=>{hover=true;sync()},leave=()=>{hover=false;sync()};root.addEventListener('pointerenter',enter);root.addEventListener('pointerleave',leave);root.addEventListener('focusin',sync);root.addEventListener('focusout',sync);document.addEventListener('visibilitychange',sync);reduced?.addEventListener?.('change',sync);
    let observer;if(window.IntersectionObserver){observer=new IntersectionObserver(entries=>{visible=entries[0].isIntersecting;sync()});observer.observe(root)}sync();
    cleanup=()=>{clearInterval(timer);observer?.disconnect();root.removeEventListener('pointerenter',enter);root.removeEventListener('pointerleave',leave);root.removeEventListener('focusin',sync);root.removeEventListener('focusout',sync);document.removeEventListener('visibilitychange',sync);reduced?.removeEventListener?.('change',sync)};
   }
  }
  root.hidden=false;
 }
 window.shopPromotions={render};window.addEventListener('vg-shop-logout',()=>render(null,[]));window.addEventListener('pagehide',()=>cleanup());
})();
