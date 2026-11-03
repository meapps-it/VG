const {test}=require('node:test'),assert=require('node:assert/strict'),{JSDOM}=require('jsdom'),fs=require('node:fs');
const html=fs.readFileSync(__dirname+'/../catalogo-nuovo.html','utf8');
const tick=()=>new Promise(r=>setTimeout(r,0));
test('profile restores share in-flight requests and discard replies from ended sessions',async()=>{
 const d=new JSDOM(html,{url:'https://example.test',runScripts:'outside-only'}),w=d.window;
 const original={phone:'1111111',profile:{name:'Old account'}};let source=original;const pending=[],applied=[];
 w.shopAccess={customer:()=>source,fetch:()=>new Promise(resolve=>pending.push(resolve))};
 w.shopShipping={applyProfile:async(data,options)=>{if(!options.isCurrent||options.isCurrent())applied.push(data)},clear(){},current:()=>({})};
 try{
  w.eval(fs.readFileSync(__dirname+'/../shop-customer.js','utf8'));
  const first=w.shopCustomer.restore(),second=w.shopCustomer.restore();assert.equal(pending.length,1);
  source=null;w.dispatchEvent(new w.Event('vg-shop-logout'));
  source={phone:'2222222',profile:{name:'New account'}};const third=w.shopCustomer.restore();assert.equal(pending.length,2);
  pending[1]({ok:true,json:async()=>({profile:{name:'New account',phone:'2222222'}})});await third;
  pending[0]({ok:true,json:async()=>({profile:{name:'Old account',phone:'1111111'}})});await Promise.all([first,second]);
  assert.deepEqual(applied.map(p=>p.name),['New account']);
  source=null;w.dispatchEvent(new w.Event('vg-shop-logout'));assert.equal(w.document.getElementById('customerAccount').hidden,true);
 }finally{w.close()}
});
test('shipping restoration checks session again after awaiting the address directory',async()=>{
 const d=new JSDOM(html,{url:'https://example.test',runScripts:'outside-only'}),w=d.window;try{
  w.eval(fs.readFileSync(__dirname+'/../shop-shipping.js','utf8'));let release,current=true;
  w.shopShipping.load=()=>new Promise(r=>release=r);
  const task=w.shopShipping.applyProfile({name:'Old account',phone:'1111111',address:'Old address'},{isCurrent:()=>current});
  current=false;release();await task;
  assert.equal(w.document.querySelector('#orderForm [name=name]').value,'');
  assert.equal(w.localStorage.getItem('vg-shop-shipping-v1'),null);
 }finally{w.close()}
});
