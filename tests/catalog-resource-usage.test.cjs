const {test}=require('node:test'),assert=require('node:assert/strict'),{JSDOM}=require('jsdom'),fs=require('node:fs');
const html=fs.readFileSync(__dirname+'/../catalogo-nuovo.html','utf8');
const wait=ms=>new Promise(r=>setTimeout(r,ms));
const items=Array.from({length:1000},(_,i)=>({id:'item-'+i,name:'Borsa '+i,brand:'Hermès',sku:'SKU-'+i,category:'Borse',description:'Pelle marrone',quality:'STANDARD',price:100+i,images:[]}));
function setup(){
 let requests=0,fail=false,release,hold=false;
 const dom=new JSDOM(html,{url:'https://example.test/catalogo-nuovo.html',runScripts:'dangerously',pretendToBeVisual:true,beforeParse(w){
  w.HTMLElement.prototype.scrollIntoView=function(){};w.scrollTo=()=>{};
  w.eval(fs.readFileSync(__dirname+'/../shop-boutique.js','utf8'));
  w.shopShipping={load(){},address:()=>({}),save(){}};
  w.shopAccess={viewer(){},start:load=>load(),fetch:async()=>{
   requests++;if(hold)await new Promise(r=>release=r);if(fail)throw Error('offline fixture');
   return {ok:true,json:async()=>({items,imagesExpiresAt:Date.now()+3600000})};
  }};
 }});
 return {dom,get requests(){return requests},hold(){hold=true},release(){hold=false;release?.()},fail(){fail=true}};
}
test('1,000-item search coalesces typing, reuses normalized text and preserves IME',async()=>{
 const h=setup(),w=h.dom.window;try{
  await wait(0);const input=w.document.getElementById('search');
  w.eval('window.renderCount=0;window.originalRender=render;render=function(){renderCount++;return originalRender()}');
  for(let i=0;i<30;i++){input.value='not-final-'+i;input.dispatchEvent(new w.Event('input'));}
  input.value='Hermes SKU 999';input.dispatchEvent(new w.Event('input'));
  assert.equal(w.renderCount,0);await wait(230);assert.equal(w.renderCount,1);
  assert.equal(w.document.querySelectorAll('.item').length,1);assert.match(w.document.getElementById('catalog').textContent,/Borsa 999/);
  let normalizations=0;const original=w.String.prototype.normalize;
  w.String.prototype.normalize=function(...args){normalizations++;return original.apply(this,args)};
  w.eval('filteredProducts()');assert(normalizations<=5,'same products must not be normalized again for every query token/comparison');
  input.dispatchEvent(new w.Event('compositionstart'));input.value='Borsa';input.dispatchEvent(new w.Event('input'));await wait(210);assert.equal(w.renderCount,1);
  input.dispatchEvent(new w.Event('compositionend'));await wait(220);assert.equal(w.renderCount,2);
  w.eval("products[0].name='Nome aggiornato';document.getElementById('search').value='Nome aggiornato';render()");
  assert.equal(w.document.querySelectorAll('.item').length,1,'search cache follows edits to a product');
 }finally{w.close()}
});
test('concurrent photo renewals share both request and gallery rebuild; network failure preserves catalogue',async()=>{
 const h=setup(),w=h.dom.window;try{
  await wait(0);w.eval("showProduct('item-0')");await wait(0);w.eval("window.galleryBuilds=0;window.originalSetup=setupGallery;setupGallery=function(p){galleryBuilds++;return originalSetup(p)};imagesExpiresAt=Date.now()-1");
  h.hold();const pending=w.eval('Promise.all([refreshGallery(),refreshGallery(),refreshGallery()])');await wait(0);assert.equal(h.requests,2);h.release();await pending;assert.equal(w.galleryBuilds,1);
  const card=w.document.querySelector('.item');h.fail();w.eval('imagesExpiresAt=Date.now()-1');await w.eval('refreshGallery()');
  assert.equal(w.document.querySelector('.item'),card,'already loaded cards remain mounted after an offline renewal');
 }finally{w.close()}
});
