const {test}=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),vm=require('node:vm');
const html=fs.readFileSync(__dirname+'/../index.html','utf8');
const slice=(a,b)=>html.slice(html.indexOf(a),html.indexOf(b,html.indexOf(a)));
test('deleted clients stay hidden after reload, with owner isolation and retained order history',()=>{
 const storage=new Map();
 const c={cloudSession:{user:{id:'owner-a'}},localStorage:{getItem:k=>storage.get(k),setItem:(k,v)=>storage.set(k,v)},loadArticleDeleteTombstones:()=>[],loadOrderDeleteTombstones:()=>[],articleMatchesDeleteTombstone:()=>false,orderMatchesDeleteTombstone:()=>false};
 vm.createContext(c);vm.runInContext(slice('function clientDeleteStorageKey','function normalizeArticleDeleteKeys'),c);
 c.addClientDeleteTombstone({id:'deleted'});
 const db={clienti:[{id:'deleted'},{id:'keep'}],ordini:[{id:'historic',clienteId:'deleted'}],articoli:[]};
 c.filterDeletedCloudRecords(db);assert.deepEqual(db.clienti,[{id:'keep'}]);assert.equal(db.ordini.length,1);
 c.cloudSession.user.id='owner-b';assert.equal(c.loadClientDeleteTombstones().length,0);
 c.cloudSession.user.id='owner-a';assert.equal(c.loadClientDeleteTombstones()[0],'deleted');
});
test('cloud deletion invalidates overlapping downloads both before and after its request',async()=>{
 let release;const wait=new Promise(r=>release=r);
 const c={cloudEnabled:()=>true,cloudUi(){},deleteCloudClient:()=>wait};vm.createContext(c);
 vm.runInContext('let catalogPublicationWrites=0,catalogPublicationRevision=0,cloudBusy=false;'+slice('async function cloudDeleteOne(','const CLOUD_LOCAL_USER_KEY'),c);
 const p=c.cloudDeleteOne('cli',{id:'client'});assert.equal(vm.runInContext('catalogPublicationWrites',c),1);assert.equal(vm.runInContext('catalogPublicationRevision',c),1);
 release();await p;assert.equal(vm.runInContext('catalogPublicationWrites',c),0);assert.equal(vm.runInContext('catalogPublicationRevision',c),2);
});
test('failed cloud deletion releases the download guard',async()=>{
 const c={cloudEnabled:()=>true,cloudUi(){},deleteCloudOrder:async()=>{throw Error('offline')}};vm.createContext(c);
 vm.runInContext('let catalogPublicationWrites=0,catalogPublicationRevision=0,cloudBusy=false;'+slice('async function cloudDeleteOne(','const CLOUD_LOCAL_USER_KEY'),c);
 await assert.rejects(c.cloudDeleteOne('ord',{id:'order'}));assert.equal(vm.runInContext('catalogPublicationWrites',c),0);
});
