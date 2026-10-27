const {chromium,webkit}=require('playwright'),fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict');
const root=path.resolve(__dirname,'..'),url='https://meapps-it.github.io/VG/catalogo-nuovo.html';
const products=Array.from({length:8},(_,i)=>({id:'boutique-'+i,name:i===0?'Alma Trunk BB':'Articolo della selezione '+i,sku:'SP-'+i,
 category:i<5?'Borse':'Accessori',brand:i<3?'Marca A':'Marca B',quality:i%2?'STANDARD':'ORIGINALE',price:180+i*12,
 image:'https://photos.test/'+i+'.jpg',images:['https://photos.test/'+i+'.jpg','https://photos.test/other.jpg'],description:'Dettagli dell’articolo.\nMisure e informazioni disponibili nel catalogo.',availability:'disponibile'}));
(async()=>{const browser=await(process.env.SHOP_NAV_BROWSER==='webkit'?webkit:chromium).launch({headless:true});try{
 for(const width of [360,412,1280]){
  const context=await browser.newContext({viewport:{width,height:844},isMobile:width<650,hasTouch:true}),page=await context.newPage(),errors=[];
  page.on('pageerror',e=>errors.push(e.message));
  await page.route('https://meapps-it.github.io/VG/**',r=>{
   const file=path.join(root,new URL(r.request().url()).pathname.replace('/VG/',''));
   if(r.request().url().includes('shop-access.js'))return r.fulfill({contentType:'text/javascript',body:`window.shopAccess={fetch:(...a)=>fetch(...a),viewer:()=>{},customer:()=>({phone:'393330000000',profile:{}}),logout:()=>{},start:load=>{document.body.classList.remove('shop-locked','shop-checking');load()}};`});
   return fs.existsSync(file)?r.fulfill({path:file}):r.abort();
  });
  await page.route('https://photos.test/**',r=>r.fulfill({contentType:'image/svg+xml',body:'<svg xmlns="http://www.w3.org/2000/svg" width="600" height="800"><rect width="600" height="800" fill="#b59279"/></svg>'}));
  await page.route('**/functions/v1/vg-shop-catalog**',r=>{
   assert.equal(r.request().method(),'GET','No production orders or account changes in design QA');
   return r.fulfill({contentType:'application/json',body:JSON.stringify(new URL(r.request().url()).searchParams.get('action')==='customer-orders'?{orders:[],hasMore:false}:{items:products,imagesExpiresAt:Date.now()+3600000})});
  });
  await page.goto(url);await page.locator('.item').first().waitFor();assert.equal(await page.locator('.collection-look').count(),3);
  await page.locator('#browseCollection').click();await page.locator('#catalogSection').scrollIntoViewIfNeeded();
  await page.locator('#catalog [data-detail="boutique-0"]').first().click();await page.locator('#productView').waitFor({state:'visible'});
  assert.equal(await page.locator('#productTitle').textContent(),'Alma Trunk BB');assert.equal(await page.locator('.related-item').count(),4);
  assert.equal(await page.locator('#relatedProducts [data-detail="boutique-0"]').count(),0);
  assert(await page.locator('#productPrice').isVisible());
  const title=await page.locator('#productTitle').boundingBox(),gallery=await page.locator('#productHero').boundingBox();
  if(width<650)assert(title.y<gallery.y,'title above gallery on phone');
  await page.locator('#relatedTitle').scrollIntoViewIfNeeded();assert(await page.locator('#productAdd').isVisible()||width>=650);
  const related=page.locator('#relatedProducts [data-detail]').first(),next=await related.getAttribute('data-detail');await related.click();
  assert.equal(await page.locator('#productTitle').textContent(),products.find(p=>p.id===next).name);
  await page.goBack();await page.waitForFunction(()=>document.querySelector('#productTitle').textContent==='Alma Trunk BB');
  await page.locator('#productAdd').click();await page.locator('#overlay').waitFor({state:'visible'});
  assert.match(await page.locator('#cartLines').textContent(),/Alma Trunk BB/);assert.equal(await page.locator('#overlay').evaluate(e=>e.scrollWidth<=innerWidth),true);
  await page.locator('#closeCart').click();await page.locator('#overlay').waitFor({state:'hidden'});
  await page.locator('#openCustomer').click();await page.locator('#customerView').waitFor({state:'visible'});await page.locator('#customerBack').click();await page.locator('#customerView').waitFor({state:'hidden'});
  assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),true);assert.deepEqual(errors,[]);
  console.log('PASS boutique '+width+'px: hero, recommendations, detail history, sticky purchase, cart, customer page and no overflow');await context.close();
 }
}finally{await browser.close()}})().catch(e=>{console.error(e);process.exitCode=1});
