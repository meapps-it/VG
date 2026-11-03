let JSDOM;try{({JSDOM}=require('jsdom'))}catch{({JSDOM}=require('../../SimplexApp/node_modules/jsdom'))}
const fs=require('node:fs'),assert=require('node:assert/strict'),test=require('node:test');
const source=f=>fs.readFileSync(__dirname+'/../'+f,'utf8');
test('promotion is hidden when disabled, renders safe copy and only existing linked articles, clears on logout',()=>{
 const d=new JSDOM('<main class="container"><section id="home"></section></main>',{runScripts:'outside-only',url:'https://simplexapp.it/shop/'}),w=d.window;
 w.eval(source('shop-promotions.js'));const render=w.shopPromotions.render;
 render(null,[]);assert(w.document.querySelector('#shopPromotions').hidden);
 render({enabled:true,title:'<img onerror=alert(1)>',body:'Offerte',image_url:'javascript:alert(1)',product_ids:['a','private']},[{id:'a',name:'Borsa',price:99,promotion:true}]);
 const root=w.document.querySelector('#shopPromotions');assert(!root.hidden);assert.equal(root.querySelector('h2').textContent,'<img onerror=alert(1)>');assert.equal(root.querySelectorAll('button').length,1);assert.equal(root.querySelector('button').dataset.detail,'a');assert(!root.querySelector('.shop-promo-banner'));assert.equal(root.parentNode.id,'shopPromoDialog');
 render({enabled:false,title:'Hidden'},[]);assert(root.hidden);assert.equal(root.children.length,0);
 render({enabled:true,title:'Promo'},[]);w.dispatchEvent(new w.Event('vg-shop-logout'));assert(root.hidden);w.close();
});
test('administrator loads draft, saves enabled and disabled promotion with linked articles',async()=>{
 const d=new JSDOM('<nav class="topMenuGrid"></nav><section id="sec_home"><div class="heroSpot"></div></section>',{runScripts:'outside-only'}),w=d.window;let saved;
 w.HTMLDialogElement.prototype.showModal=function(){this.open=true};w.HTMLDialogElement.prototype.close=function(){this.open=false};
 const result=(data)=>({data,error:null});const chain={select(){return this},eq(){return this},order(){return this},limit(){return Promise.resolve(result([{id:'a',nome:'Borsa',sku:'B1',descrizione:JSON.stringify({promoAttiva:true})},{id:'b',nome:'Normale',sku:'B2',descrizione:'{}'}]))},maybeSingle(){return Promise.resolve(result({enabled:false,title:'Promo',product_ids:['a']}))},upsert(p){saved=p;return {select(){return this},single:()=>Promise.resolve(result({enabled:p.enabled}))}}};
 w.VG_SUPABASE_READY=Promise.resolve({auth:{getUser:async()=>result({user:{id:'a51e2715-3b5e-4ad3-906e-c664fcdd486d'}})},from:()=>Object.create(chain)});
 w.promoValid=m=>!!m.promoAttiva;w.unpackCloudArticleDescription=raw=>({meta:JSON.parse(raw||'{}')});w.eval(source('vg-save-feedback.js'));w.eval(source('vg-promotions.js'));await w.document.querySelector('.topMenuLink').onclick();const form=w.document.querySelector('form');assert.equal(form.elements.title.value,'Promo');assert(form.querySelector('[name=product_ids]').checked);assert.equal(form.querySelectorAll('[name=product_ids]').length,1);form.elements.enabled.checked=true;await form.onsubmit({preventDefault(){}});assert(saved.enabled);assert.deepEqual(Array.from(saved.product_ids),['a']);form.elements.enabled.checked=false;await form.onsubmit({preventDefault(){}});assert.equal(saved.enabled,false);w.close();
});
test('Promo eligibility includes supplier and personal promotions and excludes expired or disabled ones',()=>{
 const {articleHasActivePromo}=require('./catalog-server-helpers.cjs');const check=m=>articleHasActivePromo({descrizione:'[VGMETA]'+JSON.stringify(m)+'[/VGMETA]'},'2026-10-29');
 assert(check({promoAttiva:true,prezzoPromo:50}));assert(check({costoUsdPromo:40}));assert(check({costoUsdPromo:40,scadenzaPromoFornitore:'2026-10-29'}));assert(!check({costoUsdPromo:40,scadenzaPromoFornitore:'2026-10-28'}));assert(!check({costoUsdPromo:40,promoFornitoreAttiva:false}));assert(!check({promoAttiva:true,prezzoPromo:50,scadenzaPromoPersonale:'2026-10-28'}));assert(!check({}));
});
test('slider excludes inactive products, honors reduced motion and releases its timer on logout',()=>{
 const d=new JSDOM('<main class="container"></main>',{runScripts:'outside-only',pretendToBeVisual:true}),w=d.window;let active=0,cleared=0,reduced=false;
 w.setInterval=()=>{active++;return active};w.clearInterval=id=>{if(id)cleared++};w.matchMedia=()=>({matches:reduced,addEventListener(){},removeEventListener(){}});
 try{w.eval(source('shop-promotions.js'));const promo={enabled:true,title:'Natale',product_ids:['a','b','off']},items=[{id:'a',name:'Prima',promotionActive:true,price:10},{id:'b',name:'Seconda',promotionActive:true,priceHidden:true},{id:'off',name:'Scaduta',promotionActive:false,price:30}];
 w.shopPromotions.render(promo,items);assert.equal(w.document.querySelectorAll('[data-detail]').length,2);assert.equal(active,0,'closed offers do not run a carousel timer');assert.match(w.document.querySelector('#shopPromotions').textContent,/Info in privato/);assert.doesNotMatch(w.document.querySelector('#shopPromotions').textContent,/Scaduta/);
 w.dispatchEvent(new w.Event('vg-shop-logout'));reduced=true;const before=active;w.shopPromotions.render(promo,items);assert.equal(active,before);
 }finally{w.close()}
});
test('gallery prepares and uploads a photo before saving, preserves the stored pointer and offers removal',async()=>{
 const d=new JSDOM('<nav class="topMenuGrid"></nav>',{runScripts:'outside-only',pretendToBeVisual:true}),w=d.window;let saved,uploaded,removed=false;
 w.HTMLDialogElement.prototype.showModal=function(){this.open=true};w.HTMLDialogElement.prototype.close=function(){this.open=false};
 w.URL.createObjectURL=()=> 'blob:preview';w.URL.revokeObjectURL=()=>{};
 w.createImageBitmap=async()=>({width:2000,height:1000,close(){}});w.HTMLCanvasElement.prototype.getContext=()=>({drawImage(){}});w.HTMLCanvasElement.prototype.toBlob=fn=>fn(new w.Blob(['webp'],{type:'image/webp'}));
 const result=data=>({data,error:null}),chain={select(){return this},eq(){return this},order(){return this},limit:async()=>result([]),maybeSingle:async()=>result({title:'11.11',product_ids:[]}),upsert(p){saved=p;return {select(){return this},single:async()=>result({enabled:p.enabled})}}};
 const db={auth:{getUser:async()=>result({user:{id:'a51e2715-3b5e-4ad3-906e-c664fcdd486d'}})},from:()=>Object.create(chain),storage:{from:()=>({upload:async(path,blob)=>{uploaded=path;assert.equal(blob.type,'image/webp');return {error:null}},remove:async()=>{removed=true}})}};
 w.VG_SUPABASE_READY=Promise.resolve(db);w.eval(source('vg-save-feedback.js'));w.eval(source('vg-promotions.js'));
 try{await w.document.querySelector('.topMenuLink').onclick();const form=w.document.querySelector('form');assert.equal(form.elements.image_url.type,'hidden');Object.defineProperty(form.elements.image_file,'files',{value:[new w.File(['photo'],'natale.jpg',{type:'image/jpeg'})]});form.elements.image_file.onchange();await form.onsubmit({preventDefault(){}});assert(uploaded.includes('/catalog-promotions/'));assert(saved.image_url.endsWith(uploaded));assert(!removed);form.querySelector('[data-remove]').click();await form.onsubmit({preventDefault(){}});assert.equal(saved.image_url,'');}finally{w.close()}
});
test('hero replaces the selection only during active promotions and releases autoplay on logout',()=>{
 const d=new JSDOM('<main class="container"><section class="collection-heading"><div class="collection-copy">Il tuo stile</div><div id="collectionMosaic">Originali</div></section></main>',{runScripts:'outside-only',pretendToBeVisual:true}),w=d.window;let timers=0,cleared=0;
 w.HTMLDialogElement.prototype.showModal=function(){this.open=true};w.HTMLDialogElement.prototype.close=function(){this.open=false};w.setInterval=()=>++timers;w.clearInterval=id=>{if(id)cleared++};
 try{w.eval(source('shop-promotions.js'));w.shopPromotions.render({enabled:true,title:'11.11',image_url:'https://example.test/banner.webp',product_ids:['a']},[{id:'a',name:'Promo',image:'https://example.test/a.webp',promotionActive:true,price:20}]);assert(w.document.querySelector('.collection-heading').classList.contains('has-promotion'));assert.equal(w.document.querySelectorAll('.collection-promo-slide').length,2);assert.equal(timers,1);w.document.querySelector('.collection-promo-open').click();assert(w.document.getElementById('shopPromoDialog').open);w.dispatchEvent(new w.Event('vg-shop-logout'));assert(!w.document.getElementById('shopPromoDialog').open);assert(!w.document.querySelector('.collection-heading').classList.contains('has-promotion'));assert.equal(w.document.querySelector('.collection-promotion'),null);assert(cleared>0);assert.equal(w.document.getElementById('collectionMosaic').textContent,'Originali');}finally{w.close()}
});
