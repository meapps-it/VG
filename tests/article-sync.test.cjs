const {test}=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),vm=require('node:vm');
const source=fs.readFileSync(__dirname+'/../index.html','utf8'),code=source.slice(source.indexOf('async function upsertCloudArticle('),source.indexOf('async function deleteCloudArticle('));
async function save(article,mismatch=false,{pics=[],newArticle=false,uploadFailure=false,steps=[]}={}){let payload,update;const db={from:()=>({upsert(p){payload=p;steps.push(['save',p.stato_pubblicazione]);return this},select(){return this},async single(){return {data:{...payload,id:'server',stato_pubblicazione:mismatch?'WRONG':(update||payload).stato_pubblicazione},error:null}},update(p){update=p;steps.push(['update',p.stato_pubblicazione]);return this},eq(){return this}})};
 const ctx={cloudSession:{user:{id:'owner'}},ensureCloud:async()=>db,ensureCategoryId:async()=>null,packCloudArticleDescription:()=>'',normalizeExternalArticleUrl:()=>'',getEffectiveArticleSupplier:a=>a.fornitore,getEffectiveArticleQuality:a=>a.fornitore==='Jayden'?'ORIGINALE':'STANDARD',qualityPricingKey:s=>s,normalizeSpaceText:s=>s,isUuid:()=>!newArticle,getArticleCloudSyncSources:async()=>pics,syncArticlePhotosToCloud:async()=>{steps.push(['photos']);if(uploadFailure)throw Error('offline');return pics},normalizeCloudPhotoPathList:a=>a,console};vm.createContext(ctx);vm.runInContext(code,ctx);return {saved:await ctx.upsertCloudArticle(article),payload};}
test('publication and supplier quality persist with saved prices and photos unchanged',async()=>{for(const published of [false,true])for(const supplier of ['Jayden','Daniel','Altro']){const {saved,payload}=await save({id:'id',codice:'SKU',fornitore:supplier,pubblicatoCatalogo:published,prezzoVendita:154,foto:['original-path']});assert.equal(payload.stato_pubblicazione,published?'pubblicato':'bozza');assert.equal(payload.fornitore,supplier);assert.equal(payload.qualita,supplier==='Jayden'?'ORIGINALE':'STANDARD');assert.equal(payload.prezzo_vendita,154);assert.deepEqual(saved.foto,['original-path'])}});
test('server must confirm publication state',async()=>{await assert.rejects(save({pubblicatoCatalogo:false},true),/non confermato/)});
test('new article is published only after its photos are stored',async()=>{
 const steps=[];
 const {saved}=await save({codice:'SKU',pubblicatoCatalogo:true},false,{pics:['SKU/1.jpg'],newArticle:true,steps});
 assert.deepEqual(steps,[['save','bozza'],['photos'],['update','pubblicato']]);
 assert.deepEqual(saved.foto,['SKU/1.jpg']);
});
test('a failed photo upload never completes publication and missing local files are explicit',async()=>{
 const steps=[];
 await assert.rejects(save({codice:'SKU',pubblicatoCatalogo:true},false,{pics:['SKU/1.jpg'],newArticle:true,uploadFailure:true,steps}),/Foto non sincronizzate/);
 assert.deepEqual(steps,[['save','bozza'],['photos']]);
 await assert.rejects(save({codice:'SKU',photoIds:['missing']},false,{newArticle:true}),/seleziona di nuovo/);
});
