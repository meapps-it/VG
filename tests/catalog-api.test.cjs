const {test}=require('node:test');
const assert=require('node:assert/strict');
const vm=require('node:vm');
const fs=require('node:fs');
const {stripTypeScriptTypes}=require('node:module');
const source=stripTypeScriptTypes(fs.readFileSync(__dirname+'/../supabase/functions/vg-shop-catalog/index.ts','utf8').replace(/^import .*;\n/,''));
async function serve({photos=[],photoError=null,origin=null,method="GET"}={}){
 let handler;const signedBatches=[],ranges=[];
 const products=[{id:'one',nome:'Borsa',descrizione:'Pelle vera\n[VGMETA]{"fornitore":"privato"}[/VGMETA]\nMisure 20 cm',prezzo_vendita:20,giacenza:1},{id:'empty',nome:'Vuoto',prezzo_vendita:5}];
 const db={from(table){const query={select(){return this},eq(){return this},in(){return this},order(){return this},limit(){return this},range(a,b){ranges.push([a,b]);return Promise.resolve({data:photos.slice(a,b+1),error:photoError})},then(resolve,reject){return Promise.resolve({data:table==='prodotti'?products:[]}).then(resolve,reject)}};return query},storage:{from(bucket){assert.equal(bucket,'articoli');return {async createSignedUrls(paths,ttl){assert.equal(ttl,3600);signedBatches.push(paths);return {data:paths.map(path=>path==='missing'?{path,error:'Not found'}:{path,signedUrl:'https://private.test/'+path+'?token=temporary'})}}}}}};
 vm.runInNewContext(source,{Deno:{env:{get:()=>''},serve:fn=>{handler=fn}},createClient:()=>db,Response,URL,console,Date});
 return {response:await handler(new Request('https://test/?action=catalog',{method,headers:origin?{origin}:{}})),signedBatches,ranges};
}
test('all ordered photos, compatible cover, missing objects, clean descriptions, private signing',async()=>{
 const {response,signedBatches}=await serve({photos:[{prodotto_id:'one',path:'a'},{prodotto_id:'one',path:'missing'},{prodotto_id:'one',path:'b'},{prodotto_id:'one',path:'a'}]});
 assert.equal(response.status,200);const body=await response.json();
 assert.deepEqual(body.items[0].images,['https://private.test/a?token=temporary','https://private.test/b?token=temporary']);
 assert.equal(body.items[0].image,body.items[0].images[0]);assert.deepEqual(body.items[1].images,[]);assert.equal(body.items[1].image,'');
 assert.match(body.items[0].description,/Pelle vera/);assert.match(body.items[0].description,/Misure 20 cm/);assert.doesNotMatch(body.items[0].description,/VGMETA|privato/);
 assert.ok(body.imagesExpiresAt>Date.now()+3500000);assert.equal(signedBatches.length,1);
});
test('paginates beyond PostgREST cap and batches every photo',async()=>{
 const photos=Array.from({length:1203},(_,i)=>({prodotto_id:'one',path:'photo-'+i}));
 const {response,ranges,signedBatches}=await serve({photos});const body=await response.json();
 assert.equal(body.items[0].images.length,1203);assert.equal(ranges.length,3);assert.equal(signedBatches.length,13);
});
test('photo query errors fail visibly instead of returning a partial catalog',async()=>{
 const {response}=await serve({photoError:{message:'unavailable'}});assert.equal(response.status,500);
});

test('CORS permits only VG and the existing SimplexApp domain, including preflight',async()=>{
 for(const origin of ['https://simplexapp.it','https://meapps-it.github.io']){
  const {response}=await serve({origin});assert.equal(response.status,200);assert.equal(response.headers.get('Access-Control-Allow-Origin'),origin);assert.equal(response.headers.get('Vary'),'Origin');
  const preflight=await serve({origin,method:'OPTIONS'});assert.equal(preflight.response.status,204);
 }
 const blocked=await serve({origin:'https://unrelated.example'});assert.equal(blocked.response.status,403);
});
