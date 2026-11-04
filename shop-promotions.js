(()=>{'use strict';
 let cleanup=()=>{};
 function render(p,items=[]){
  cleanup();cleanup=()=>{};
  const hero=document.getElementById('catalogSection');
  hero?.classList.remove('has-promotion','has-promo-artwork');hero?.querySelector('.collection-promotion')?.remove();
  let dialog=document.getElementById('shopPromoDialog'),root=document.getElementById('shopPromotions');
  if(!dialog){dialog=document.createElement('dialog');dialog.id='shopPromoDialog';dialog.className='shop-promo-dialog';dialog.setAttribute('aria-labelledby','shopPromoTitle');const close=document.createElement('button');close.type='button';close.className='shop-promo-close';close.textContent='Chiudi offerte';close.onclick=()=>dialog.close();root=document.createElement('section');root.id='shopPromotions';root.className='shop-promotions';root.setAttribute('aria-labelledby','shopPromoTitle');root.addEventListener('click',e=>{if(e.target.closest('[data-detail]'))dialog.close()});dialog.append(close,root);document.querySelector('main.container')?.append(dialog)}
  if(dialog.open)dialog.close();root.replaceChildren();root.hidden=true;if(!p?.enabled)return;
  const copy=document.createElement('div'),tag=document.createElement('span'),title=document.createElement('h2');tag.className='section-kicker';tag.textContent='PROMO IN PRIMO PIANO';title.id='shopPromoTitle';title.textContent=p.title||'Promozioni';copy.append(tag,title);
  if(p.body){const text=document.createElement('p');text.textContent=p.body;copy.append(text)}root.append(copy);
  const selected=new Set(p.product_ids||[]),products=items.filter(x=>selected.has(x.id)&&(x.promotionActive??x.promotion)===true).slice(0,12);
  if(products.length){
   const grid=document.createElement('div');grid.className='shop-promo-products';grid.id='shopPromoProducts';grid.setAttribute('aria-label','Articoli in promozione');
   for(const product of products){const b=document.createElement('button');b.type='button';b.dataset.detail=product.id;if(product.image){const img=document.createElement('img');img.src=product.image;img.alt='';img.loading='lazy';b.append(img)}const name=document.createElement('strong'),price=document.createElement('span');name.textContent=product.name;price.textContent=product.priceHidden?'Info in privato':Number(product.price).toLocaleString('it-IT',{style:'currency',currency:'EUR'});b.append(name,price);grid.append(b)}root.append(grid);
   if(products.length>1){
    const controls=document.createElement('div');controls.className='shop-promo-controls';let index=0,timer,paused=false,hover=false,visible=true;
    const reduced=window.matchMedia?.('(prefers-reduced-motion: reduce)');
    function move(step){index=(index+step+products.length)%products.length;const card=grid.children[index];grid.scrollTo?.({left:card.offsetLeft-grid.children[0].offsetLeft,behavior:reduced?.matches?'instant':'smooth'})}
    function sync(){clearInterval(timer);if(dialog.open&&!paused&&!hover&&!document.hidden&&visible&&!root.contains(document.activeElement)&&!reduced?.matches)timer=setInterval(()=>move(1),4500)}
    const toggle=document.createElement('button');toggle.type='button';toggle.textContent='Pausa scorrimento';toggle.onclick=()=>{paused=!paused;toggle.textContent=paused?'Riprendi scorrimento':'Pausa scorrimento';sync()};controls.append(toggle);root.append(controls);
    const enter=()=>{hover=true;sync()},leave=()=>{hover=false;sync()};root.addEventListener('pointerenter',enter);root.addEventListener('pointerleave',leave);root.addEventListener('focusin',sync);root.addEventListener('focusout',sync);dialog.addEventListener('close',sync);dialog.addEventListener('toggle',sync);document.addEventListener('visibilitychange',sync);reduced?.addEventListener?.('change',sync);
    let observer;if(window.IntersectionObserver){observer=new IntersectionObserver(entries=>{visible=entries[0].isIntersecting;sync()});observer.observe(root)}sync();
    cleanup=()=>{clearInterval(timer);observer?.disconnect();root.removeEventListener('pointerenter',enter);root.removeEventListener('pointerleave',leave);root.removeEventListener('focusin',sync);root.removeEventListener('focusout',sync);dialog.removeEventListener('close',sync);dialog.removeEventListener('toggle',sync);document.removeEventListener('visibilitychange',sync);reduced?.removeEventListener?.('change',sync)};
   }
  }
  if(hero){
   hero.classList.add('has-promotion');const panel=document.createElement('div');panel.className='collection-promotion';const media=document.createElement('div');media.className='collection-promo-media';media.setAttribute('aria-label',p.title||'Promozioni');
   const productPhotos=products.filter(x=>x.image).map(x=>({image:x.image,name:x.name}));
   let artwork=false;try{artwork=new URL(p.image_url).protocol==='https:'}catch{}
   if(artwork)hero.classList.add('has-promo-artwork');
   const photos=(artwork?[{image:p.image_url,name:p.title}]:productPhotos).filter((x,i,all)=>{try{return new URL(x.image).protocol==='https:'&&all.findIndex(y=>y.image===x.image)===i}catch{return false}}).slice(0,5);
   const open=()=>{if(!dialog.open)dialog.showModal()};
   for(const photo of photos){const button=document.createElement('button');button.type='button';button.className='collection-promo-slide';button.setAttribute('aria-label','Scopri le promo · '+(photo.name||p.title||''));button.onclick=open;const img=document.createElement('img');img.src=photo.image;img.alt=photo.name||'Promozione';img.onerror=()=>{img.remove();button.textContent=p.title||'Promozioni'};button.append(img);media.append(button)}
   if(!photos.length){const button=document.createElement('button');button.type='button';button.className='collection-promo-slide';button.textContent=p.title||'Promozioni';button.onclick=open;media.append(button)}
   const cta=document.createElement('button');cta.type='button';cta.className='collection-promo-open';cta.textContent='Scopri le offerte ↗';cta.onclick=open;const bannerCopy=document.createElement('div');bannerCopy.className='collection-promo-copy';const kicker=document.createElement('span');kicker.className='section-kicker';kicker.textContent='LE OFFERTE DEL MOMENTO';const heading=document.createElement('h2');heading.textContent=p.title||'Promozioni';bannerCopy.append(kicker,heading);if(p.body){const summary=document.createElement('p');summary.textContent=p.body;bannerCopy.append(summary)}bannerCopy.append(cta);panel.append(bannerCopy,media);hero.append(panel);
   if(photos.length>1){let timer,index=0,paused=false,hover=false,visible=true;const reduced=window.matchMedia?.('(prefers-reduced-motion: reduce)');const toggle=document.createElement('button');toggle.type='button';toggle.className='collection-promo-pause';toggle.textContent='Ⅱ';toggle.setAttribute('aria-label','Pausa scorrimento promo');
    const sync=()=>{clearInterval(timer);if(!paused&&!hover&&visible&&!document.hidden&&!panel.contains(document.activeElement)&&!reduced?.matches)timer=setInterval(()=>{index=(index+1)%photos.length;media.scrollTo?.({left:index*media.clientWidth,behavior:'smooth'})},4500)};
    toggle.onclick=()=>{paused=!paused;toggle.textContent=paused?'▶':'Ⅱ';toggle.setAttribute('aria-label',paused?'Riprendi scorrimento promo':'Pausa scorrimento promo');sync()};panel.append(toggle);
    const enter=()=>{hover=true;sync()},leave=()=>{hover=false;sync()};panel.addEventListener('pointerenter',enter);panel.addEventListener('pointerleave',leave);panel.addEventListener('focusin',sync);panel.addEventListener('focusout',sync);document.addEventListener('visibilitychange',sync);reduced?.addEventListener?.('change',sync);
    let observer;if(window.IntersectionObserver){observer=new IntersectionObserver(entries=>{visible=entries[0].isIntersecting;sync()});observer.observe(panel)}sync();const previous=cleanup;cleanup=()=>{previous();clearInterval(timer);observer?.disconnect();document.removeEventListener('visibilitychange',sync);reduced?.removeEventListener?.('change',sync)};
   }
  }
  root.hidden=false;
 }
 window.shopPromotions={render};window.addEventListener('vg-shop-logout',()=>render(null,[]));window.addEventListener('pagehide',()=>cleanup());
})();
