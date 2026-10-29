let JSDOM;try{({JSDOM}=require('jsdom'))}catch{({JSDOM}=require('../../SimplexApp/node_modules/jsdom'))}
const fs=require('node:fs'),assert=require('node:assert/strict'),test=require('node:test');
const source=f=>fs.readFileSync(__dirname+'/../'+f,'utf8');
test('promotion is hidden when disabled, renders safe copy and only existing linked articles, clears on logout',()=>{
 const d=new JSDOM('<main class="container"><section id="home"></section></main>',{runScripts:'outside-only',url:'https://simplexapp.it/shop/'}),w=d.window;
 w.eval(source('shop-promotions.js'));const render=w.shopPromotions.render;
 render(null,[]);assert(w.document.querySelector('#shopPromotions').hidden);
 render({enabled:true,title:'<img onerror=alert(1)>',body:'Offerte',image_url:'javascript:alert(1)',product_ids:['a','private']},[{id:'a',name:'Borsa',price:99}]);
 const root=w.document.querySelector('#shopPromotions');assert(!root.hidden);assert.equal(root.querySelector('h2').textContent,'<img onerror=alert(1)>');assert.equal(root.querySelectorAll('button').length,1);assert.equal(root.querySelector('button').dataset.detail,'a');assert(!root.querySelector('.shop-promo-banner'));assert.equal(root.parentNode.firstElementChild,root);
 render({enabled:false,title:'Hidden'},[]);assert(root.hidden);assert.equal(root.children.length,0);
 render({enabled:true,title:'Promo'},[]);w.dispatchEvent(new w.Event('vg-shop-logout'));assert(root.hidden);w.close();
});
test('administrator loads draft, saves enabled and disabled promotion with linked articles',async()=>{
 const d=new JSDOM('<section id="sec_home"><div class="heroSpot"></div></section>',{runScripts:'outside-only'}),w=d.window;let saved;
 w.HTMLDialogElement.prototype.showModal=function(){this.open=true};w.HTMLDialogElement.prototype.close=function(){this.open=false};
 const result=(data)=>({data,error:null});const chain={select(){return this},eq(){return this},order(){return this},limit(){return Promise.resolve(result([{id:'a',nome:'Borsa',sku:'B1',descrizione:JSON.stringify({promoAttiva:true})},{id:'b',nome:'Normale',sku:'B2',descrizione:'{}'}]))},maybeSingle(){return Promise.resolve(result({enabled:false,title:'Promo',product_ids:['a']}))},upsert(p){saved=p;return {select(){return this},single:()=>Promise.resolve(result({enabled:p.enabled}))}}};
 w.VG_SUPABASE_READY=Promise.resolve({auth:{getUser:async()=>result({user:{id:'a51e2715-3b5e-4ad3-906e-c664fcdd486d'}})},from:()=>Object.create(chain)});
 w.promoValid=m=>!!m.promoAttiva;w.unpackCloudArticleDescription=raw=>({meta:JSON.parse(raw||'{}')});w.eval(source('vg-save-feedback.js'));w.eval(source('vg-promotions.js'));await w.document.querySelector('.vgCustomersTile').onclick();const form=w.document.querySelector('form');assert.equal(form.elements.title.value,'Promo');assert(form.querySelector('[name=product_ids]').checked);assert.equal(form.querySelectorAll('[name=product_ids]').length,1);form.elements.enabled.checked=true;await form.onsubmit({preventDefault(){}});assert(saved.enabled);assert.deepEqual(Array.from(saved.product_ids),['a']);form.elements.enabled.checked=false;await form.onsubmit({preventDefault(){}});assert.equal(saved.enabled,false);w.close();
});
test('Promo eligibility includes supplier and personal promotions and excludes expired or disabled ones',()=>{
 const {articleHasActivePromo}=require('./catalog-server-helpers.cjs');const check=m=>articleHasActivePromo({descrizione:'[VGMETA]'+JSON.stringify(m)+'[/VGMETA]'},'2026-10-29');
 assert(check({promoAttiva:true,prezzoPromo:50}));assert(check({costoUsdPromo:40}));assert(check({costoUsdPromo:40,scadenzaPromoFornitore:'2026-10-29'}));assert(!check({costoUsdPromo:40,scadenzaPromoFornitore:'2026-10-28'}));assert(!check({costoUsdPromo:40,promoFornitoreAttiva:false}));assert(!check({promoAttiva:true,prezzoPromo:50,scadenzaPromoPersonale:'2026-10-28'}));assert(!check({}));
});
