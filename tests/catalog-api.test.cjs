const {test}=require('node:test');
const assert=require('node:assert/strict');
const vm=require('node:vm');
const fs=require('node:fs');
const {stripTypeScriptTypes}=require('node:module');
const source=stripTypeScriptTypes(fs.readFileSync(__dirname+'/../supabase/functions/vg-shop-catalog/index.ts','utf8').replace(/^import .*;\n/gm,''));
const galleryId="11111111-2222-3333-4444-555555555555";
async function serve({query="action=catalog",galleryProduct=false,legacyPhotos=[],photos=[],photoError=null,origin=null,method="GET",authorized=true,escapedMetadata=false,promotion=null,notice=null,promoEnd=''}={}){
 let handler;const signedBatches=[],ranges=[];let signing=0,peakSigning=0;
 const products=[{id:'one',sku:'B1',nome:'Borsa',descrizione:'Pelle vera\n[VGMETA]{"fornitore":"privato"}[/VGMETA]\nMisure 20 cm',prezzo_vendita:20,giacenza:1},{id:'empty',nome:'Vuoto',prezzo_vendita:5}];
 products[0].descrizione='Pelle vera\n[VGMETA]'+(escapedMetadata?JSON.stringify(JSON.stringify({fornitore:'Jayden',brand:'Louis Vuitton',misura:'35–40',descrizione:'Pelle vera\nCosto fornitore 200 USD',foto:legacyPhotos})).slice(1,-1):JSON.stringify({fornitore:'Jayden',foto:legacyPhotos,promoAttiva:true,prezzoPromo:15,scadenzaPromoPersonale:promoEnd}))+'[/VGMETA]\nMisure 20 cm';
 const db={auth:{getUser:async()=>({error:true,data:null})},from(table){const query={select(){return this},upsert(){return Promise.resolve({error:null})},gt(){return this},async maybeSingle(){if(table==='prodotti')return {data:galleryProduct?{...products[0],id:galleryId}:null};if(table==='vg_shop_promotion')return {data:promotion};if(table==='vg_shop_notice')return {data:notice};return {data:{token_hash:"test-session",is_admin:false,created_at:"2026-10-25T12:00:00Z"}}},async single(){return {data:{updated_at:"2026-10-24T00:00:00Z"}}},eq(){return this},in(){return this},order(){return this},limit(){return this},range(a,b){ranges.push([a,b]);return Promise.resolve({data:photos.slice(a,b+1),error:photoError})},then(resolve,reject){return Promise.resolve({data:table==='prodotti'?products:[]}).then(resolve,reject)}};return query},storage:{from(bucket){assert.equal(bucket,'articoli');return {async createSignedUrl(path,ttl){assert.equal(ttl,600);return {data:{signedUrl:'https://private.test/'+path+'?signed=temporary'}}},async createSignedUrls(paths,ttl){assert.equal(ttl,600);signedBatches.push(paths);peakSigning=Math.max(peakSigning,++signing);await Promise.resolve();signing--;return {data:paths.map(path=>path==='missing'?{path,error:'Not found'}:{path,signedUrl:'https://private.test/'+path+'?token=temporary'})}}}}}};
 vm.runInNewContext(source,{Deno:{env:{get:()=>''},serve:fn=>{handler=fn}},createClient:()=>db,...require('./catalog-server-helpers.cjs'),Response,URL,console,Date,crypto,accessAction:async()=>null,catalogSession:async()=>authorized?{token_hash:"test",is_admin:false}:null,trackVisit:async()=>{}});
 return {response:await handler(new Request('https://test/?'+query,{method,headers:origin?{origin}:{}})),signedBatches,ranges,peakSigning};
}
test('all ordered photos, compatible cover, missing objects, clean descriptions, private signing',async()=>{
 const {response,signedBatches}=await serve({photos:[{prodotto_id:'one',path:'a'},{prodotto_id:'one',path:'missing'},{prodotto_id:'one',path:'b'},{prodotto_id:'one',path:'a'}]});
 assert.equal(response.status,200);const body=await response.json();
 assert.deepEqual(body.items[0].images,['https://private.test/a?token=temporary','https://private.test/b?token=temporary']);
 assert.equal(body.items[0].image,body.items[0].images[0]);assert.deepEqual(body.items[1].images,[]);assert.equal(body.items[1].image,'');
 assert.match(body.items[0].description,/Pelle vera/);assert.match(body.items[0].description,/Misure 20 cm/);assert.doesNotMatch(body.items[0].description,/VGMETA|privato/);
 assert.ok(body.imagesExpiresAt>Date.now()+590000 && body.imagesExpiresAt<=Date.now()+600000);assert.equal(signedBatches.length,1);
});
test('paginates beyond PostgREST cap and batches every photo',async()=>{
 const photos=Array.from({length:1203},(_,i)=>({prodotto_id:'one',path:'photo-'+i}));
 const {response,ranges,signedBatches,peakSigning}=await serve({photos});const body=await response.json();
 assert.equal(body.items[0].images.length,1203);assert.equal(ranges.length,3);assert.equal(signedBatches.length,13);assert.equal(peakSigning,4);
});
test('photo query errors fail visibly instead of returning a partial catalog',async()=>{
 const {response}=await serve({photoError:{message:'unavailable'}});assert.equal(response.status,500);
});

test('CORS permits only VG and the existing SimplexApp domain, including preflight',async()=>{
 for(const origin of ['https://simplexapp.it','https://meapps-it.github.io','https://veglamour.cloud','https://www.veglamour.cloud']){
  const {response}=await serve({origin});assert.equal(response.status,200);assert.equal(response.headers.get('Access-Control-Allow-Origin'),origin);assert.equal(response.headers.get('Vary'),'Origin');
  const preflight=await serve({origin,method:'OPTIONS'});assert.equal(preflight.response.status,204);
 }
 const blocked=await serve({origin:'https://unrelated.example'});assert.equal(blocked.response.status,403);
});

test('catalog refuses unauthenticated requests before any photos are signed',async()=>{const {response,signedBatches}=await serve({authorized:false});assert.equal(response.status,401);assert.equal(signedBatches.length,0)});


test('legacy metadata photos are merged, deduplicated and limited to associated paths',async()=>{const {response}=await serve({photos:[{prodotto_id:'one',path:'B1/a'}],legacyPhotos:['B1/a','B1/b','other/private','../bad','https://external.test/image']});const body=await response.json();assert.deepEqual(body.items[0].images,['https://private.test/B1/a?token=temporary','https://private.test/B1/b?token=temporary']);assert.equal(body.items[0].quality,'ORIGINALE');assert.equal(body.items[1].quality,'STANDARD')});

test('escaped legacy metadata recovers real brand, sizes and associated photos without publishing supplier costs',async()=>{const {response}=await serve({escapedMetadata:true,legacyPhotos:['B1/old-photo.jpg','other/private.jpg']});assert.equal(response.status,200);const body=await response.json();assert.equal(body.items[0].brand,'Louis Vuitton');assert.equal(body.items[0].quality,'ORIGINALE');assert.deepEqual(body.items[0].options.sizes,['35','36','37','38','39','40']);assert.equal(body.items[0].images.length,1);assert.match(body.items[0].description,/Pelle vera/);assert.doesNotMatch(body.items[0].description,/USD|fornitore|Costo|VGMETA/)});

test('promotion links are restricted to published catalog IDs',async()=>{const {response}=await serve({promotion:{enabled:true,title:'Promo',product_ids:['one','hidden']}});const data=await response.json();assert.equal(data.promotion.title,'Promo');assert.deepEqual(data.promotion.product_ids,['one'])});

test('notice returns only enabled copy and a temporary signed image, without private storage paths',async()=>{const owner='a51e2715-3b5e-4ad3-906e-c664fcdd486d';const {response}=await serve({notice:{enabled:true,kind:'warning',title:'Consegne',body:'Dal lunedì',image_path:owner+'/catalog-notices/123.webp'}});const d=await response.json();assert.equal(d.notice.title,'Consegne');assert.match(d.notice.image_url,/signed=temporary/);assert.equal(d.notice.image_path,undefined);const hidden=await serve({notice:{enabled:false,title:'Bozza privata'}});assert.equal((await hidden.response.json()).notice,null)});
test('gallery promotion photos are signed temporarily and active promo eligibility is explicit',async()=>{
 const owner='a51e2715-3b5e-4ad3-906e-c664fcdd486d',path=owner+'/catalog-promotions/11111111-1111-4111-8111-111111111111.webp';
 const {response}=await serve({promotion:{enabled:true,title:'Natale',image_url:'https://qfjwtawsqfwmsmrrgqfi.supabase.co/storage/v1/object/public/articoli/'+path,product_ids:['one']}});const data=await response.json();assert.equal(data.promotion.image_url,'https://private.test/'+path+'?signed=temporary');assert.equal(data.items[0].promotionActive,true);
});

test('catalog exposes only the public promotion deadline',async()=>{const {response}=await serve({promoEnd:'2099-11-11'});const data=await response.json();assert.equal(data.items[0].promotionEndsAt,'2099-11-11');assert.equal(data.items[0].promotionActive,true);assert(!('costoUsdPromo' in data.items[0]));});

test('initial covers mode signs only one photo per product unless a cover is missing',async()=>{
 const photos=[{prodotto_id:'one',path:'missing'},{prodotto_id:'one',path:'fallback'},{prodotto_id:'empty',path:'cover'},{prodotto_id:'empty',path:'secondary'}];
 const {response,signedBatches}=await serve({photos,query:'action=catalog&photos=covers'});
 const body=await response.json();
 assert.deepEqual(Array.from(signedBatches.flat()),['missing','cover','fallback']);
 assert.deepEqual(body.items[0].images,['https://private.test/fallback?token=temporary']);
 assert.deepEqual(body.items[1].images,['https://private.test/cover?token=temporary']);
 assert.equal(body.items[1].photoCount,2);
});
test('gallery signs the requested published product photos after authentication',async()=>{
 const query='action=gallery&id='+galleryId;
 const photos=[{prodotto_id:galleryId,path:'one'},{prodotto_id:galleryId,path:'two'}];
 const {response}=await serve({query,photos,galleryProduct:true});
 assert.deepEqual((await response.json()).images,['https://private.test/one?token=temporary','https://private.test/two?token=temporary']);
 const unauthorized=await serve({query,photos,galleryProduct:true,authorized:false});
 assert.equal(unauthorized.response.status,401);assert.equal(unauthorized.signedBatches.length,0);
 const hidden=await serve({query,photos});
 assert.equal(hidden.response.status,404);assert.equal(hidden.signedBatches.length,0);
});
