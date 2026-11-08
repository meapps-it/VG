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
 vm.createContext(c);vm.runInContext('let catalogPublicationWrites=0,catalogPublicationRevision=0,clientMergeBusy=false,clientMergeRevision=0;'+slice('async function fetchClientMergeLinks(','async function mergeDuplicateClients(')+slice('async function pullCloudToLocal(','async function pushLocalToCloud('),c);
 const request=c.pullCloudToLocal({background:true});await new Promise(resolve=>setImmediate(resolve));assert.equal(queries,8);
 vm.runInContext('catalogPublicationRevision++',c);release();assert.equal(await request,false);
});

test('ordinary archive reads preserve creation date and never rewrite the whole local database',()=>{
 const original={createdAt:'2020-01-01',articoli:[{id:'article',pubblicatoCatalogo:true,descrizione:'Descrizione da conservare',postFb:'Testo Facebook',postFacebook:'Testo Facebook'}],clienti:[],ordini:[]};let writes=0,normalizations=0;
 const entries=new Map([['db',JSON.stringify(original)],['db_backup_latest',JSON.stringify(original)],['vg_draft_art_v2_clean',JSON.stringify({values:{descrizione:'Bozza da conservare',postFb:'Bozza Facebook'}})]]);
 const before=[...entries];
 const c={KEY:'db',LEGACY_KEYS:[],localStorage:{getItem:k=>entries.get(k)??null,setItem(k,v){writes++;entries.set(k,v)},removeItem(k){writes++;entries.delete(k)}},normalizeDBShape:db=>{normalizations++;return db},defDB(){throw Error('Existing archive must be read directly')}};
 vm.createContext(c);vm.runInContext(slice('function loadDB(){','function freeTechnicalLocalStorageSpace('),c);
 for(let i=0;i<30;i++){const db=c.loadDB();assert.equal(db.createdAt,'2020-01-01');db.articoli[0].pubblicatoCatalogo=false;}
 assert.equal(c.loadDB().articoli[0].pubblicatoCatalogo,true,'unsaved edits cannot leak into subsequent reads');assert.equal(writes,0);assert.equal(normalizations,31);assert.deepEqual([...entries],before,'reads must preserve descriptions, backups and drafts even without an old cleanup marker');
});

test('homonyms are flagged and a manual merge preserves orders and alternate contacts', async()=>{
 const keep={id:'local-1',nome:'Rosa',cognome:'De Luca',telefono:'111111111',note:'Nota principale'};
 const remove={id:'local-2',nome:'ROSA  DE LUCA',telefono:'222222222',email:'rosa@example.test',note:'Altra nota'};
 const extra={id:'local-4',nome:'Rosa De Luca'};
 let db={clienti:[keep,remove,extra,{id:'local-3',nome:'Rosa'}],ordini:[{id:'order',clienteId:remove.id,totale:85},{id:'other',clienteId:extra.id,totale:55}]};
 const context={
  Date,cloudBusy:false,isUuid:id=>id.startsWith('cloud-'),
  normalizeSpaceText:value=>String(value||'').trim().replace(/\s+/g,' '),
  loadDB:()=>structuredClone(db),saveDB:value=>{db=structuredClone(value);return true}
 };
 vm.createContext(context);
 vm.runInContext(slice('function normalizeTextKey(','\n')+ '\n'+slice('function normalizeClientDisplayName(','function splitClientNameForCloud(')+slice('function clientDuplicateGroups(','function renderCli('),context);
 assert.equal(context.clientDuplicateGroups(db.clienti).length,1);
 await context.mergeDuplicateClients(keep.id,[remove.id,extra.id]);
 assert.equal(context.clientDuplicateGroups(db.clienti).length,0);
 assert.deepEqual(db.ordini,[{id:'order',clienteId:keep.id,totale:85},{id:'other',clienteId:keep.id,totale:55}]);
 assert.equal(db.clienti[0].telefono,keep.telefono);
 assert.equal(db.clienti[0].email,remove.email);
 assert.match(db.clienti[0].note,/Nota principale[\s\S]*Altra nota[\s\S]*222222222/);
 assert.equal(db.clienti[1].id,'local-3');

 db.clienti=[{...keep,id:'cloud-1'},{...remove,id:'cloud-2'}];
 context.cloudEnabled=()=>true;
 context.ensureCloud=async()=>({rpc:async()=>({error:new Error('Offline')})});
 const before=structuredClone(db);
 await assert.rejects(context.mergeDuplicateClients('cloud-1',['cloud-2']),/Offline/);
 assert.deepEqual(db,before);
 context.ensureCloud=async()=>({rpc:async()=>({error:{code:'PGRST202'}})});
 await assert.rejects(context.mergeDuplicateClients('cloud-1',['cloud-2']),/attesa di attivazione/);
 assert.deepEqual(db,before);
 const query={select(){return this},order(){return this},async range(){return {error:{code:'PGRST205'}}}};
 assert.equal((await context.fetchClientMergeLinks({from:()=>query})).length,0);
 context.applyClientMergeLinks(db,[{removed_id:'cloud-2',kept_id:'cloud-1'}]);
 assert.deepEqual(db.clienti.map(client=>client.id),['cloud-1']);
});
