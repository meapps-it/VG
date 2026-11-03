const {test}=require('node:test'),assert=require('node:assert/strict'),vm=require('node:vm'),fs=require('node:fs');
const source=fs.readFileSync(__dirname+'/../service-worker.js','utf8');
function harness(){
 const events={},writes=[],network=[];
 const context={URL,Response,Request,Headers,console,self:{location:new URL('https://example.test/VG/service-worker.js?v=1'),addEventListener(type,fn){events[type]=fn}},
 caches:{open:async()=>({put:(key)=>writes.push(key)}),match:async()=>undefined},fetch:async(req,options)=>{network.push({req,options});return new Response('<html><head></head><body>fixture</body></html>',{headers:{'content-type':'text/html'}})}};
 vm.runInNewContext(source,context);
 async function navigate(path){let result;events.fetch({request:{url:'https://example.test/VG/'+path,method:'GET',mode:'navigate'},respondWith(p){result=p}});return (await result).text()}
 return {context,events,writes,network,navigate};
}
test('only the management entry gets font injection and may replace the offline admin HTML',async()=>{
 const h=harness();const catalog=await h.navigate('catalogo-nuovo.html');assert(!catalog.includes('vg-font-settings'));assert.equal(h.writes.length,0);
 const admin=await h.navigate('');assert(admin.includes('vg-font-settings.js'));assert.deepEqual(h.writes,['./index.html']);
 await h.navigate('catalogo.html');assert.equal(h.writes.length,1);
});
test('shared profile and shop assets fetch fresh copies instead of staying cache-first',async()=>{
 for(const path of ['customer-profile-ui.js?v=6','shop-customer.js?v=old','shop-boutique.css?v=old']){
  const h=harness();let response;h.events.fetch({request:{url:'https://example.test/VG/'+path,method:'GET',mode:'cors'},respondWith(p){response=p}});await response;
  assert.equal(h.network[0].options.cache,'no-store');
 }
});
test('activating an admin update preserves caches belonging to other applications',async()=>{
 const h=harness(),deleted=[];h.context.self.clients={claim:async()=>{}};
 const current=vm.runInNewContext("'gestionale-runtime-gestionale-vg-resource-audit-20261103'");
 h.context.caches.keys=async()=>['another-app-cache','gestionale-runtime-old',current];h.context.caches.delete=async name=>deleted.push(name);
 let task;h.events.activate({waitUntil(p){task=p}});await task;assert.deepEqual(deleted,['gestionale-runtime-old']);
});
