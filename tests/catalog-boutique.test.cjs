const fs=require('node:fs'),vm=require('node:vm'),assert=require('node:assert/strict');
const scope={window:{}};vm.runInNewContext(fs.readFileSync(__dirname+'/../shop-boutique.js','utf8'),scope);
const {related}=scope.window.shopBoutique;
const current={id:'current',name:'Alma',category:'Borse',brand:'Marca A',quality:'ORIGINALE',price:200};
const values=[current,{id:'far',category:'Scarpe',brand:'Altra',price:10},
 {id:'brand',category:'Portafogli',brand:'Marca A',price:190},
 {id:'similar',category:'Borse',brand:'Marca A',quality:'ORIGINALE',price:210},
 {id:'category',category:'borse',brand:'Altra',price:210},
 {id:'hidden',category:'Borse',brand:'Marca A',price:200,published:false},
 {id:'similar',category:'Borse',price:200}];
const ranked=related(values,current);assert.deepEqual(Array.from(ranked,x=>x.product.id),['similar','category','brand','far']);
assert(!ranked.some(x=>['current','hidden'].includes(x.product.id)));assert.equal(related([current],current).length,0);
assert.equal(related(values,current,1).length,1);assert.equal(related(values,current,0).length,0);
assert.equal(new Set(ranked.map(x=>x.product.id)).size,ranked.length);
console.log('PASS recommendations: category, brand and price ranking; no duplicates, self or unpublished products; honest fallback and empty catalogue');
