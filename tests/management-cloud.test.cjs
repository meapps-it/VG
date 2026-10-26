const {test}=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),vm=require('node:vm');
const html=fs.readFileSync(__dirname+'/../index.html','utf8');
const slice=(a,b)=>html.slice(html.indexOf(a),html.indexOf(b,html.indexOf(a)));
function setup({fail=false,mismatch=false}={}){
 let persisted={articoli:[{id:'article-id',_cloud:true,pubblicatoCatalogo:false,prezzoVendita:290,foto:['SKU/1.jpg'],photoIds:['local-1']}]};
 const changes=[];let gate;const wait=new Promise(resolve=>gate=resolve);
 const chain={update(payload){changes.push({update:payload});return this},eq(key,value){changes.push({filter:[key,value]});return this},select(){return this},async single(){await wait;return fail?{error:Error('offline')}:{data:{id:'article-id',stato_pubblicazione:mismatch?'bozza':'pubblicato'}}}};
 const c={console,Date,Math,cloudSession:{user:{id:'owner'}},cloudEnabled:()=>true,ensureCloud:async()=>({from(table){assert.equal(table,'prodotti');return chain}}),isUuid:()=>true,loadDB:()=>structuredClone(persisted),saveDBLocal:db=>{persisted=structuredClone(db);return true},cloudUi(){},upsertCloudArticle(){throw Error('Published cloud articles must never trigger full article/photo synchronization')}};
 vm.createContext(c);vm.runInContext('let catalogPublicationWrites=0,catalogPublicationRevision=0,cloudBusy=false;'+slice('async function saveArticleCatalogPublication(','async function cloudSaveOne('),c);
 return {c,changes,release:gate,get:()=>persisted};
}
test('publication sends only its status, waits for confirmation and preserves prices/photos',async()=>{
 const h=setup(),before=structuredClone(h.get());const request=h.c.saveArticleCatalogPublication(before.articoli[0],true);
 await new Promise(resolve=>setImmediate(resolve));assert.equal(h.get().articoli[0].pubblicatoCatalogo,false);assert.equal(vm.runInContext('catalogPublicationWrites',h.c),1);
 h.release();await request;assert.equal(h.get().articoli[0].pubblicatoCatalogo,true);assert.equal(h.get().articoli[0].prezzoVendita,290);assert.deepEqual(h.get().articoli[0].foto,before.articoli[0].foto);assert.deepEqual(h.get().articoli[0].photoIds,before.articoli[0].photoIds);
 assert.deepEqual(JSON.parse(JSON.stringify(h.changes[0])),{update:{stato_pubblicazione:'pubblicato'}});assert(h.changes.some(x=>x.filter?.[0]==='user_id'&&x.filter[1]==='owner'));assert.equal(vm.runInContext('catalogPublicationWrites',h.c),0);
});
test('publication failure or mismatched server confirmation leaves local state unchanged',async()=>{
 for(const options of [{fail:true},{mismatch:true}]){const h=setup(options);const before=structuredClone(h.get());const promise=h.c.saveArticleCatalogPublication(before.articoli[0],true);h.release();await assert.rejects(promise);assert.deepEqual(h.get(),before);assert.equal(vm.runInContext('cloudBusy',h.c),false)}
});
test('saving while viewing settings does not rebuild the hidden article grid or other pages',()=>{
 const calls=[];const c={renderActivePage:p=>calls.push(p),currentHistoryPage:()=> 'impostazioni',refreshArticleCategorySelects(){},refreshArticleBrandSelects(){},fillPricingSettings(){},renderHome(){throw Error('hidden page')},renderArt(){throw Error('hidden photos')},renderCli(){throw Error('hidden page')},renderOrd(){throw Error('hidden page')},renderFinanze(){throw Error('hidden page')}};
 vm.createContext(c);vm.runInContext(slice('function renderAll(){','/* ====== ART view/edit ====== */'),c);c.renderAll();assert.deepEqual(calls,['impostazioni']);
});
test('downloads overlapping a publication update are discarded before applying a stale snapshot',async()=>{
 let release,queries=0;const wait=new Promise(resolve=>release=resolve);
 const q={select(){return this},eq(){return this},order(){return this},limit(){return this},range(){return this},async then(resolve){queries++;await wait;return resolve({data:[],error:null})}};
 const c={console,ensureCloud:async()=>({from:()=>q}),cloudSession:{user:{id:'owner'}},cloudBusy:false,cloudUi(){},localStorage:{setItem(){},getItem(){return ''}},KEY:'fixture'};
 vm.createContext(c);vm.runInContext('let catalogPublicationWrites=0,catalogPublicationRevision=0;'+slice('async function pullCloudToLocal(','async function pushLocalToCloud('),c);
 const request=c.pullCloudToLocal({background:true});await new Promise(resolve=>setImmediate(resolve));assert.equal(queries,7);
 vm.runInContext('catalogPublicationRevision++',c);release();assert.equal(await request,false);
});

test('ordinary archive reads preserve creation date and never rewrite the whole local database',()=>{
 const original={createdAt:'2020-01-01',articoli:[{id:'article',pubblicatoCatalogo:true}],clienti:[],ordini:[]};let writes=0,normalizations=0;
 const c={KEY:'db',LEGACY_KEYS:[],localStorage:{getItem:k=>k==='db'?JSON.stringify(original):null,setItem(){writes++},removeItem(){writes++}},normalizeDBShape:db=>{normalizations++;return db},defDB(){throw Error('Existing archive must be read directly')}};
 vm.createContext(c);vm.runInContext(slice('function loadDB(){','function freeTechnicalLocalStorageSpace('),c);
 for(let i=0;i<30;i++){const db=c.loadDB();assert.equal(db.createdAt,'2020-01-01');db.articoli[0].pubblicatoCatalogo=false;}
 assert.equal(c.loadDB().articoli[0].pubblicatoCatalogo,true,'unsaved edits cannot leak into subsequent reads');assert.equal(writes,0);assert.equal(normalizations,31);
});
