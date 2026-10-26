const {test}=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),vm=require('node:vm');
const source=fs.readFileSync(__dirname+'/../index.html','utf8');
const slice=(start,end)=>source.slice(source.indexOf(start),source.indexOf(end,source.indexOf(start)));
const quiet={warn(){},error(){}};
function context(extra,code){const c={console:quiet,URL,Blob,File,Map,Set,setTimeout,location:{href:'https://meapps-it.github.io/VG/'},SUPABASE_BUCKET:'articoli',MAX_ARTICLE_PHOTOS:20,...extra};vm.createContext(c);vm.runInContext(code,c);return c}
const signedCode=slice('const FOTO_SIGNED_URL_CACHE=new Map();','// Nome mantenuto');
test('signed image requests deduplicate, expire and can explicitly renew failed image tokens',async()=>{
 let calls=0;const c=context({cloudSession:{},isTemporaryLocalPhotoRef:s=>/^(data:|blob:|file:)/.test(s),ensureCloud:async()=>({storage:{from:()=>({async createSignedUrl(path){calls++;return path==='missing/1.jpg'?{error:Error('missing')}:{data:{signedUrl:'https://private.test/'+path+'?token='+calls}}}})}})},signedCode);
 const urls=await Promise.all([c.getSignedFotoUrl('SKU/1.jpg'),c.getSignedFotoUrl('SKU/1.jpg')]);assert.equal(calls,1);assert.equal(urls[0],urls[1]);
 assert.equal(await c.getSignedFotoUrl('SKU/1.jpg'),urls[0]);assert.equal(calls,1);
 assert.notEqual(await c.getSignedFotoUrl('SKU/1.jpg',5400,true),urls[0]);assert.equal(calls,2);
 await c.getSignedFotoUrl('missing/1.jpg');await c.getSignedFotoUrl('missing/1.jpg');assert.equal(calls,3,'missing legacy objects have bounded negative caching');
 vm.runInContext("FOTO_SIGNED_URL_CACHE.get('SKU/1.jpg').expireAt=0",c);await c.getSignedFotoUrl('SKU/1.jpg');assert.equal(calls,4);
});
test('photo prefetch failures do not poison the cache as permanently empty',async()=>{
 let fail=true,calls=0;const c=context({ensureCloud:async()=>({from:()=>({select(){return this},in(){return this},order(){return this},then(resolve){calls++;return Promise.resolve(fail?{error:Error('offline')}:{data:[{prodotto_id:'p',path:'SKU/1.jpg',ordine:0}]}).then(resolve)}})})},"const ARTICLE_CLOUD_PHOTO_PATH_CACHE=new Map();let ARTICLE_CLOUD_PHOTO_PATH_INFLIGHT=null;\n"+slice('async function prefetchCloudPhotoPathsForArticles(','function canonicalPhotoSourceKey('));
 await c.prefetchCloudPhotoPathsForArticles([{id:'p'}]);assert.equal(vm.runInContext("ARTICLE_CLOUD_PHOTO_PATH_CACHE.has('p')",c),false);fail=false;await c.prefetchCloudPhotoPathsForArticles([{id:'p'}]);assert.equal(calls,2);assert.equal(vm.runInContext("ARTICLE_CLOUD_PHOTO_PATH_CACHE.get('p')[0]",c),'SKU/1.jpg');
});
test('incomplete device photo storage preserves existing cloud pictures and new local additions',async()=>{
 const c=context({getArticleLocalPhotoSources:async()=>['blob:new'],getArticleCloudSourcePaths:async()=>['SKU/1.jpg','SKU/2.jpg'],dedupePhotoSources:(v,n)=>[...new Set(v)].slice(0,n)},slice('async function getArticleCloudSyncSources(','async function getStoredPhotoSources('));
 assert.deepEqual(Array.from(await c.getArticleCloudSyncSources({photoIds:['old-1','old-2','new']})),['SKU/1.jpg','SKU/2.jpg','blob:new']);
});
function syncHarness({uploadFail=false,readFail=false,deleteFail=false}={}){
 const changes=[],existing=['SKU/1.jpg','SKU/2.jpg'];
 const db={from(){return {
  deleting:false,select(){return this},eq(){return this},order(){return this},
  delete(){changes.push('delete');this.deleting=true;return this},
  insert(rows){changes.push({insert:rows});return Promise.resolve({error:null})},
  then(resolve){return Promise.resolve(this.deleting?{error:deleteFail?Error('delete denied'):null}:{data:existing.map((path,ordine)=>({path,ordine})),error:readFail?Error('offline'):null}).then(resolve)}
 }}};

 const c=context({cloudSession:{user:{id:'owner'}},ensureCloud:async()=>db,isLikelyCloudPhotoPath:s=>!s.includes(':')&&s.includes('/'),normalizeCloudPhotoPathList:a=>a,fetch:async()=>({ok:true,blob:async()=>new Blob(['image'],{type:'image/jpeg'})}),uploadArticlePhotoToSupabase:async(file,code,slot)=>{if(uploadFail)throw Error('upload denied');return code+'/'+slot+'.jpg'}},"const ARTICLE_CLOUD_PHOTO_PATH_CACHE=new Map(),ARTICLE_CODE_PHOTO_PATH_CACHE=new Map();\n"+slice('async function syncArticlePhotosToCloud(','async function getCloudArticlePhotoUrls('));return {c,changes};
}
test('failed uploads and failed reads never remove or replace existing photo links',async()=>{
 for(const options of [{uploadFail:true},{readFail:true}]){const {c,changes}=syncHarness(options);await assert.rejects(c.syncArticlePhotosToCloud('p','SKU',['SKU/1.jpg','SKU/2.jpg','blob:new']));assert.deepEqual(changes,[])}
});
test('unchanged photographs avoid delete/insert churn; new upload appends after preserved paths',async()=>{
 const {c,changes}=syncHarness();await c.syncArticlePhotosToCloud('p','SKU',['SKU/1.jpg','SKU/2.jpg']);assert.deepEqual(changes,[]);
 const result=await c.syncArticlePhotosToCloud('p','SKU',['SKU/1.jpg','SKU/2.jpg','blob:new']);assert.deepEqual(Array.from(result),['SKU/1.jpg','SKU/2.jpg','SKU/3.jpg']);assert.equal(changes[0],'delete');assert.equal(changes[1].insert[2].ordine,2);
});

test('an unavailable local gallery never erases existing server links',async()=>{const {c,changes}=syncHarness();assert.deepEqual(Array.from(await c.syncArticlePhotosToCloud('p','SKU',[])),['SKU/1.jpg','SKU/2.jpg']);assert.deepEqual(changes,[])});

test('APK image load errors renew the signed URL once instead of reusing an expired token',async()=>{
 let signed=0;const image={style:{},complete:false,naturalWidth:0,src:'',getAttribute(){return this.src},removeAttribute(){this.src=''}},placeholder={style:{}};
 const c=context({articleThumbSelector:(id,attr)=>attr,document:{querySelector:selector=>selector.includes('placeholder')?placeholder:image,body:{contains:()=>true}},normalizeFotoStoragePath:()=> 'SKU/1.jpg',getSignedFotoUrl:async(path,seconds,force)=>{assert.equal(force,true);signed++;return 'https://private.test/SKU/1.jpg?token=fresh'}},slice('function setArticleThumbState(','async function hydrateOneArticleThumb('));
 c.setArticleThumbState('p','https://private.test/SKU/1.jpg?token=expired');await image.onerror();assert.equal(signed,1);assert.match(image.src,/token=fresh/);image.onload();assert.equal(image.style.visibility,'visible');image.onerror();assert.equal(signed,1);assert.equal(placeholder.style.display,'flex');
});