// Run with jsdom 26.1.0 available in NODE_PATH.
const {JSDOM}=require('jsdom');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const html=fs.readFileSync(__dirname+'/../catalogo-nuovo.html','utf8');
const image=n=>'https://images.example/'+n;
const item=(id,images)=>({id,name:'Borsa '+id,images,image:images?.[0],price:25,category:'Borse',description:'Pelle vera\n[VGMETA]{"privato":true}[/VGMETA]\nMisure 20 cm'});
(async()=>{
 let requests=0;
 const dom=new JSDOM(html,{url:'https://meapps-it.github.io/VG/catalogo-nuovo.html',runScripts:'dangerously',pretendToBeVisual:true,beforeParse(w){
  w.HTMLElement.prototype.scrollIntoView=function(){};w.scrollTo=function(){};
  w.shopAccess={fetch:(...args)=>w.fetch(...args),viewer:()=>{},start:load=>{w.document.body.classList.remove('shop-locked');load().catch(()=>{})}};
  w.shopShipping={load:()=>{},save:()=>{},address:()=>({city:'Torino',province:'TO',cap:'12081'})};
  w.fetch=async(url,options)=>{assert.notEqual(options?.method,'POST','No orders should be sent by tests');requests++;return {ok:true,json:async()=>({items:[item('many',[image(1),image(2),image(3)]),item('single',[image(4)]),item('empty',[]),item('legacy',undefined)],imagesExpiresAt:Date.now()+(requests===1?1000:3600000)})}};
 }});
 const w=dom.window,doc=w.document,$=id=>doc.getElementById(id),tick=()=>new Promise(r=>setTimeout(r,0));
 const click=selector=>doc.querySelector(selector).click();
 const historyClick=async id=>{const popped=new Promise(resolve=>w.addEventListener('popstate',resolve,{once:true}));$(id).click();await popped;};
 try{
 await tick();assert.equal(doc.querySelectorAll('.item').length,4);
 click('[data-detail=many]');await tick();assert.equal(requests,2,'renew expired links');
 assert.equal($('galleryCount').textContent,'1 / 3');assert.equal($('galleryThumbs').children.length,3);
 $('galleryNext').click();assert.equal($('galleryCount').textContent,'2 / 3');
 $('galleryPrev').click();assert.equal($('galleryCount').textContent,'1 / 3');
 $('galleryThumbs').children[2].click();assert.equal($('galleryCount').textContent,'3 / 3');
 function touch(type,x,y){const e=new w.Event(type);Object.defineProperty(e,type==='touchstart'?'touches':'changedTouches',{value:[{clientX:x,clientY:y}]});$('productHero').dispatchEvent(e)}
 touch('touchstart',250,100);touch('touchend',100,105);assert.equal($('galleryCount').textContent,'1 / 3');
 touch('touchstart',100,100);touch('touchend',250,105);assert.equal($('galleryCount').textContent,'3 / 3');
 touch('touchstart',100,100);touch('touchend',105,250);assert.equal($('galleryCount').textContent,'3 / 3','vertical scroll does not change image');
 await new Promise(r=>setTimeout(r,410));$('productHero').click();assert.equal($('photoZoom').getAttribute('aria-hidden'),'false');
 $('zoomNext').click();assert.equal($('zoomCount').textContent,'1 / 3');
 $('zoomImage').dispatchEvent(new w.Event('error'));assert.equal($('zoomError').hidden,false);
 $('zoomNext').click();assert.equal($('zoomError').hidden,true);
 await historyClick('closeZoom');assert.equal($('photoZoom').getAttribute('aria-hidden'),'true');
 assert.match($('productDescription').textContent,/Pelle vera/);assert.match($('productDescription').textContent,/Misure/);assert.doesNotMatch($('productDescription').textContent,/VGMETA|privato/);
 $('productHero').querySelector('img').dispatchEvent(new w.Event('error'));assert.match($('productHero').textContent,/non disponibile/);
 $('galleryNext').click();assert.ok($('productHero').querySelector('img'));
 $('productQty').value='2';$('productAdd').click();assert.match($('total').textContent,/50,00/);
 for(const [name,value] of Object.entries({name:'Cliente prova',phone:'3330000000',address:'Via prova 1',city:'Torino'}))doc.querySelector('[name='+name+']').value=value;
 doc.querySelector('[name=privacy]').checked=true;doc.querySelector('[name=province]').value='manual';doc.querySelector('[name=city]').required=false;doc.querySelector('[name=cap]').value='12081';$('checkoutNext').click();assert.match($('checkoutReview').textContent,/2 × Borsa many/);assert.equal($('sendOrder').classList.contains('checkout-hide'),false);
 $('checkoutBack').click();await historyClick('closeCart');
 click('[data-detail=single]');assert.equal($('galleryCount').textContent,'1 / 1');assert.equal($('galleryNext').hidden,true);assert.equal($('galleryThumbs').children.length,0);await historyClick('closeProduct');
 click('[data-detail=empty]');assert.equal($('productHero').disabled,true);assert.match($('productHero').textContent,/Nessuna fotografia/);await historyClick('closeProduct');
 // Legacy API shape with only image remains supported.
 w.eval('products.find(p=>p.id==="legacy").image="https://images.example/legacy"');
 click('[data-detail=legacy]');assert.equal($('galleryCount').textContent,'1 / 1');
 assert.match($('productHero').querySelector('img').src,/legacy/);
 // Search/filters combine independent fields and tolerate accents and word order.
 await historyClick('closeProduct');
 w.eval(`products=[{id:'bag',name:'Borsa a tracolla',brand:'Hermès',sku:'ABC-123',category:'Borse/Zaini',description:'Pelle marrone',price:145,images:[]},{id:'shoe',name:'Sneaker Bianca',brand:'Gucci',sku:'S001',category:'Scarpe',description:'Pelle',price:90,images:[]},{id:'wallet',name:'Portafoglio',brand:'Hermès',sku:'W002',category:'Portafogli',description:'Pelle nera',price:60,images:[]}];render()`);
 $('search').value='hermes tracolla';$('search').dispatchEvent(new w.Event('input'));assert.equal(doc.querySelectorAll('.item').length,1);assert.match($('catalog').textContent,/tracolla/);
 $('search').value='ABC 123';$('search').dispatchEvent(new w.Event('input'));assert.equal(doc.querySelectorAll('.item').length,1);
 $('clearSearch').click();$('category').innerHTML='<option value="">Tutte le categorie</option><option>Scarpe</option>';$('categoryQuick').innerHTML='<button data-category-quick="Scarpe">Scarpe</button>';$('categoryQuick').querySelector('button').click();assert.equal(doc.querySelectorAll('.item').length,1);assert.match($('catalog').textContent,/Sneaker/);
 $('resetAll').click();$('minPrice').value='100';$('minPrice').dispatchEvent(new w.Event('input'));assert.equal(doc.querySelectorAll('.item').length,1);
 $('resetAll').click();$('sort').value='price-asc';$('sort').dispatchEvent(new w.Event('change'));assert.match(doc.querySelector('.item .name').textContent,/Portafoglio/);
 $('search').value='zzzzimpossibile';$('search').dispatchEvent(new w.Event('input'));assert.match($('catalog').textContent,/Nessun articolo/);doc.querySelector('[data-reset]').click();assert.equal(doc.querySelectorAll('.item').length,3);
 $('toggleFilters').click();assert.equal($('filterPanel').hidden,false);$('toggleFilters').click();assert.equal($('filterPanel').hidden,true);
 const beforeRefresh=requests;w.eval('imagesExpiresAt=Date.now()-1');await w.eval('refreshGallery()');assert.equal(requests,beforeRefresh+1,'expired catalogue photos renew even with no detail dialog open');
 console.log('PASS DOM: thumbnails, arrows, bidirectional swipe, vertical scroll, zoom/close, broken/empty/single/legacy images, signed URL renewal, description, cart and order review');
 }finally{w.close()}
})().catch(e=>{console.error(e);process.exitCode=1});