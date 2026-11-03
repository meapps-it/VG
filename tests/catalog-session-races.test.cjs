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
test('profile-page replies from a closed session cannot create a form in the next account',async()=>{
 const d=new JSDOM(html,{url:'https://example.test',runScripts:'outside-only',pretendToBeVisual:true}),w=d.window,pending=[];
 w.crypto.randomUUID=()=> 'fixture-id';w.fetch=async()=>({ok:true,json:async()=>({provinces:{},municipalities:[]})});
 w.shopAccess={fetch:()=>new Promise(resolve=>pending.push(resolve))};
 try{
  w.eval(fs.readFileSync(__dirname+'/../customer-profile-ui.js','utf8'));
  const account=w.document.getElementById('customerAccount'),view=w.document.getElementById('customerView');
  account.hidden=false;view.classList.add('open');await tick();assert.equal(pending.length,1);
  account.hidden=true;view.classList.remove('open');w.dispatchEvent(new w.Event('vg-shop-logout'));
  pending[0]({ok:true,json:async()=>({profile:{name:'Old account'},addresses:[]})});await tick();await tick();
  assert.equal(account.querySelector('.profile-panel form'),null,'old reply must not create a profile form after logout');
  account.hidden=false;view.classList.add('open');await tick();assert.equal(pending.length,2);
  pending[1]({ok:true,json:async()=>({profile:{name:'New account'},addresses:[]})});await tick();await tick();
  assert.match(account.querySelector('.profile-summary').textContent,/New account/);assert.doesNotMatch(account.textContent,/Old account/);
 }finally{w.close()}
});
test('logout between the two profile saves prevents a write using the next account session',async()=>{
 const d=new JSDOM(html,{url:'https://example.test',runScripts:'outside-only',pretendToBeVisual:true}),w=d.window,calls=[];let release,lastTask;
 w.crypto.randomUUID=()=> 'fixture-id';w.fetch=async()=>({ok:true,json:async()=>({provinces:{},municipalities:[]})});
 w.vgSaveFeedback={run(button,task){lastTask=task();return lastTask.catch(()=>({ok:false}))}};
 w.shopAccess={fetch:async(url,options)=>{const action=new URL(url).searchParams.get('action');calls.push([action,options.method]);if(action==='profile-book'&&options.method==='POST')return new Promise(r=>release=r);return {ok:true,json:async()=>({profile:{name:'Old account'},addresses:[]})}}};
 try{
  w.eval(fs.readFileSync(__dirname+'/../customer-profile-ui.js','utf8'));w.document.getElementById('customerAccount').hidden=false;w.document.getElementById('customerView').classList.add('open');await tick();await tick();
  const form=w.document.querySelector('.profile-panel form');assert(form);form.dispatchEvent(new w.Event('submit',{cancelable:true}));await tick();assert(release);
  w.dispatchEvent(new w.Event('vg-shop-logout'));release({ok:true,json:async()=>({ok:true})});await assert.rejects(lastTask,/sessione è cambiata/);
  assert.equal(calls.filter(([action])=>action==='customer-profile').length,0,'old form must not send the second save after logout');
 }finally{w.close()}
});
test('unsynced shipping edits survive reopening and an old acknowledgement cannot clear newer edits',async()=>{
 const seed={name:'Nome locale',phone:'3330000000',address:'Via nuova 10',province:'CN',city:'Beinette',cap:'12081'};
 const d=new JSDOM(html,{url:'https://example.test',runScripts:'outside-only'}),w=d.window;try{
  w.localStorage.setItem('vg-shop-shipping-v1',JSON.stringify(seed));w.localStorage.setItem('vg-shop-shipping-pending-v1',JSON.stringify(seed));
  w.eval(fs.readFileSync(__dirname+'/../shop-shipping.js','utf8'));w.shopShipping.load=async()=>{};
  await w.shopShipping.applyProfile({...seed,address:'Via vecchia 1',city:'Cuneo'},{ifUntouched:true});
  assert.equal(w.shopShipping.current().address,'Via nuova 10');assert.equal(w.shopShipping.current().city,'Beinette');
  w.document.querySelector('#orderForm [name=address]').value='Via più recente 20';w.shopShipping.save();const latest=w.shopShipping.pending();
  w.shopShipping.acknowledge(seed);assert.equal(w.shopShipping.pending().address,'Via più recente 20');
  w.shopShipping.acknowledge(latest);assert.equal(w.shopShipping.pending(),null);
  w.shopShipping.save();w.shopShipping.clear();assert.equal(w.shopShipping.pending(),null,'logout removes the old account draft');
 }finally{w.close()}
});
test('shipping drafts remain bound to their customer even if an expired session is replaced without logout',()=>{
 const d=new JSDOM(html,{url:'https://example.test',runScripts:'outside-only'}),w=d.window;try{
  w.eval(fs.readFileSync(__dirname+'/../shop-shipping.js','utf8'));w.shopShipping.bindCustomer({id:'customer-one'});
  w.document.querySelector('#orderForm [name=address]').value='Private address';w.shopShipping.save();assert(w.shopShipping.pending());
  w.shopShipping.bindCustomer({id:'customer-two'});assert.equal(w.shopShipping.pending(),null);assert.equal(w.shopShipping.current().address,'');
  assert.equal(w.localStorage.getItem('vg-shop-shipping-owner-v1'),'customer-two');
 }finally{w.close()}
});
test('re-authenticating as a different customer clears an already opened profile page without requiring logout',async()=>{
 const d=new JSDOM(html,{url:'https://example.test',runScripts:'outside-only',pretendToBeVisual:true}),w=d.window;let source={id:'one'};
 w.crypto.randomUUID=()=> 'fixture-id';w.fetch=async()=>({ok:true,json:async()=>({provinces:{},municipalities:[]})});
 w.shopAccess={customer:()=>source,fetch:async()=>({ok:true,json:async()=>({profile:{name:source.id==='one'?'Old account':'New account'},addresses:[]})})};
 try{
  w.eval(fs.readFileSync(__dirname+'/../customer-profile-ui.js','utf8'));w.document.getElementById('customerAccount').hidden=false;w.document.getElementById('customerView').classList.add('open');await tick();await tick();
  assert.match(w.document.querySelector('.profile-summary').textContent,/Old account/);
  source={id:'two'};w.dispatchEvent(new w.Event('vg-customer-ready'));await tick();await tick();
  assert.match(w.document.querySelector('.profile-summary').textContent,/New account/);assert.doesNotMatch(w.document.getElementById('customerAccount').textContent,/Old account/);
 }finally{w.close()}
});
