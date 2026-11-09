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
test('legacy list cover fetches one photo reference and never caches a partial gallery',async()=>{
 let fail=true,calls=0;
 const c=context({
  isUuid:()=>true,
  ensureCloud:async()=>({from:table=>{
   assert.equal(table,'prodotti_foto');
   return {select(fields){assert.equal(fields,'path');return this},eq(){return this},order(){return this},
    async limit(n){assert.equal(n,1);calls++;return fail?{error:Error('offline')}:{data:[{path:'SKU/1.jpg'}]}}};
  }})
 },'const ARTICLE_CLOUD_PHOTO_PATH_CACHE=new Map();\n'+slice('async function getArticleCloudCover(','async function getArticleThumbForList('));
 await assert.rejects(c.getArticleCloudCover({id:'p'}),/offline/);
 fail=false;
 assert.equal(await c.getArticleCloudCover({id:'p'}),'SKU/1.jpg');
 assert.equal(calls,2);
 assert.equal(vm.runInContext("ARTICLE_CLOUD_PHOTO_PATH_CACHE.has('p')",c),false);
});
test('list thumbnails download only the first source and reuse a cached cover',async()=>{
 const downloaded=[],resolved=[];
 let cached=null;
 const c=context({
  getArticleThumbCandidates:()=>['cover','gallery'].map(key=>({key})),
  thumbGet:async()=>cached,
  resolveArticleThumbCandidateUrl:async({key})=>{resolved.push(key);return key},
  cacheArticleThumbFromCandidate:async({key})=>{downloaded.push(key);cached={dataUrl:'miniature'};return cached.dataUrl},
  getArticleCloudCover:async()=>{throw Error('Unexpected gallery lookup')}
 },slice('async function getArticleThumbForList(','async function clearArticleThumbCache('));
 assert.equal(await c.getArticleThumbForList({id:'p'}),'miniature');
 assert.equal(await c.getArticleThumbForList({id:'p'}),'miniature');
 assert.deepEqual(resolved,['cover']);
 assert.deepEqual(downloaded,['cover']);
});
test('incomplete device photo storage preserves existing cloud pictures and new local additions',async()=>{
 const c=context({getArticleLocalPhotoSources:async()=>['blob:new'],getArticleCloudSourcePaths:async()=>['SKU/1.jpg','SKU/2.jpg'],dedupePhotoSources:(v,n)=>[...new Set(v)].slice(0,n)},slice('async function getArticleCloudSyncSources(','async function getStoredPhotoSources('));
 assert.deepEqual(Array.from(await c.getArticleCloudSyncSources({photoIds:['old-1','old-2','new']})),['SKU/1.jpg','SKU/2.jpg','blob:new']);
});
function syncHarness({uploadFail=false,readFail=false,transientReads=0,transientWrites=0,writeFail=false}={}){
 const changes=[],existing=['SKU/1.jpg','SKU/2.jpg'],delays=[];let reads=0;
 const db={from(){return {
  select(){return this},eq(){return this},order(){return this},
  then(resolve){reads++;return Promise.resolve({data:existing.map((path,ordine)=>({path,ordine})),error:readFail?Error('offline'):reads<=transientReads?Error('Failed to fetch'):null}).then(resolve)}
 }},async rpc(name,args){
  assert.equal(name,'vg_sync_article_photo_links');changes.push(JSON.parse(JSON.stringify(args)));
  return transientWrites-->0?{error:Error('Failed to fetch')}:{data:!writeFail,error:writeFail?Error('link denied'):null};
 }};
 const c=context({setTimeout(fn,delay){delays.push(delay);queueMicrotask(fn)},cloudSession:{user:{id:'owner'}},ensureCloud:async()=>db,isLikelyCloudPhotoPath:s=>!s.includes(':')&&s.includes('/'),normalizeCloudPhotoPathList:a=>a,blobFromPhotoSrc:async()=>new Blob(['image'],{type:'image/jpeg'}),uploadArticlePhotoToSupabase:async(file,code,slot)=>{if(uploadFail)throw Error('upload denied');return code+'/'+slot+'.jpg'}},"const ARTICLE_CLOUD_PHOTO_PATH_CACHE=new Map(),ARTICLE_CODE_PHOTO_PATH_CACHE=new Map();\n"+slice('function existingStoragePhotoPath(','async function getCloudArticlePhotoUrls('));return {c,changes,delays,get reads(){return reads}};
}
test('failed uploads and failed reads never remove or replace existing photo links',async()=>{
 for(const options of [{uploadFail:true},{readFail:true}]){const {c,changes}=syncHarness(options);await assert.rejects(c.syncArticlePhotosToCloud('p','SKU',['SKU/1.jpg','SKU/2.jpg','blob:new']),options.readFail?/offline/:/Foto non sincronizzate/);assert.deepEqual(changes,[])}
});
test('unchanged photographs avoid writes; new upload links once with the previous gallery snapshot',async()=>{
 const {c,changes}=syncHarness();await c.syncArticlePhotosToCloud('p','SKU',['SKU/1.jpg','SKU/2.jpg']);assert.deepEqual(changes,[]);
 const result=await c.syncArticlePhotosToCloud('p','SKU',['SKU/1.jpg','SKU/2.jpg','blob:new']);assert.deepEqual(Array.from(result),['SKU/1.jpg','SKU/2.jpg','SKU/3.jpg']);assert.deepEqual(changes,[{p_product_id:'p',p_paths:['SKU/1.jpg','SKU/2.jpg','SKU/3.jpg'],p_previous_paths:['SKU/1.jpg','SKU/2.jpg']}]);
});

test('an unavailable local gallery never erases existing server links',async()=>{const {c,changes}=syncHarness();assert.deepEqual(Array.from(await c.syncArticlePhotosToCloud('p','SKU',[])),['SKU/1.jpg','SKU/2.jpg']);assert.deepEqual(changes,[])});

test('APK image load errors renew the signed URL once instead of reusing an expired token',async()=>{
 let signed=0;const image={style:{},complete:false,naturalWidth:0,src:'',getAttribute(){return this.src},removeAttribute(){this.src=''}},placeholder={style:{}};
 const c=context({articleThumbSelector:(id,attr)=>attr,document:{querySelector:selector=>selector.includes('placeholder')?placeholder:image,body:{contains:()=>true}},normalizeFotoStoragePath:()=> 'SKU/1.jpg',getSignedFotoUrl:async(path,seconds,force)=>{assert.equal(force,true);signed++;return 'https://private.test/SKU/1.jpg?token=fresh'}},slice('function setArticleThumbState(','async function hydrateOneArticleThumb('));
 c.setArticleThumbState('p','https://private.test/SKU/1.jpg?token=expired');await image.onerror();assert.equal(signed,1);assert.match(image.src,/token=fresh/);image.onload();assert.equal(image.style.visibility,'visible');image.onerror();assert.equal(signed,1);assert.equal(placeholder.style.display,'flex');
});

test('Android photos with missing or generic MIME types are identified from bytes before preview and save',async()=>{
 const released=[];
 class DecodableImage { set src(url){this.naturalWidth=24;queueMicrotask(()=>this.onload())} }
 const c=context({Uint8Array,Image:DecodableImage,URL:{createObjectURL:()=> 'blob:preview',revokeObjectURL:u=>released.push(u)}},slice('async function normalizeArticlePhotoFile(','async function filesToObjectUrls('));
 for(const type of ['', 'application/octet-stream']){
  const file=new File([new Uint8Array([255,216,255,224,0,16])],'photo',{type});
  const normalized=await c.normalizeArticlePhotoFile(file);assert.equal(normalized.type,'image/jpeg');assert.equal(normalized.size,file.size);
 }
 assert.equal(released.length,2);
 await assert.rejects(c.normalizeArticlePhotoFile(new File(['text'],'fake.jpg')),/non è una foto/);
 await assert.rejects(c.normalizeArticlePhotoFile(new File([],'empty.jpg',{type:'image/jpeg'})),/vuota/);
});
test('unreadable photo formats produce an explicit error and release preview resources',async()=>{
 let revoked=0;
 class InvalidImage { set src(url){queueMicrotask(()=>this.onerror())} }
 const c=context({Image:InvalidImage,URL:{createObjectURL:()=> 'blob:bad',revokeObjectURL:()=>revoked++}},slice('async function normalizeArticlePhotoFile(','async function filesToObjectUrls('));
 await assert.rejects(c.normalizeArticlePhotoFile(new File(['bad'],'bad.heic',{type:'image/heic'})),/JPG o PNG/);assert.equal(revoked,1);
});

test('transient photo reads retry with bounded delays before any write',async()=>{
 const h=syncHarness({transientReads:2});
 const paths=await h.c.syncArticlePhotosToCloud('p','SKU',['SKU/1.jpg','SKU/2.jpg']);
 assert.deepEqual(Array.from(paths),['SKU/1.jpg','SKU/2.jpg']);
 assert.equal(h.reads,3);assert.deepEqual(h.delays,[300,900]);assert.deepEqual(h.changes,[]);
 const exhausted=syncHarness({transientReads:3});
 await assert.rejects(exhausted.c.syncArticlePhotosToCloud('p','SKU',['blob:new']),/Failed to fetch/);
 assert.equal(exhausted.reads,3);assert.deepEqual(exhausted.changes,[]);
});
test('existing signed photo URLs reuse stored paths without replacing photos',async()=>{
 const h=syncHarness();
 const paths=await h.c.syncArticlePhotosToCloud('p','SKU',[
  'https://qfjwtawsqfwmsmrrgqfi.supabase.co/storage/v1/object/sign/articoli/SKU/1.jpg?token=old',
  'https://qfjwtawsqfwmsmrrgqfi.supabase.co/storage/v1/object/sign/articoli/SKU/2.jpg?token=old'
 ]);
 assert.deepEqual(Array.from(paths),['SKU/1.jpg','SKU/2.jpg']);assert.deepEqual(h.changes,[]);
});
test('photo linking retries a transient failure without changing the payload',async()=>{
 const h=syncHarness({transientWrites:1});
 await h.c.syncArticlePhotosToCloud('p','SKU',['SKU/1.jpg','SKU/2.jpg','blob:new']);
 assert.equal(h.changes.length,2);
 assert.deepEqual(h.changes[0],h.changes[1]);
 assert.deepEqual(h.delays,[300]);
});
test('a permanent photo link rejection fails without a separate delete or restore',async()=>{
 const h=syncHarness({writeFail:true});
 await assert.rejects(h.c.syncArticlePhotosToCloud('p','SKU',['SKU/1.jpg','SKU/2.jpg','blob:new']),/link denied/);
 assert.equal(h.changes.length,1);
 assert.deepEqual(h.delays,[]);
});

test('local photo blobs go to cloud directly without fetching object URLs',async()=>{
 const {c}=syncHarness();
 c.blobFromPhotoSrc=async()=>{throw Error('Blob URL unavailable')};
 const image=new Blob(['image'],{type:'image/jpeg'});
 const paths=await c.syncArticlePhotosToCloud('p','SKU',['SKU/1.jpg','SKU/2.jpg',image]);
 assert.deepEqual(Array.from(paths),['SKU/1.jpg','SKU/2.jpg','SKU/3.jpg']);
});
