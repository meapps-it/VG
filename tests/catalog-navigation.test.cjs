const {chromium,webkit}=require('playwright'),fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict');
const root=path.resolve(__dirname,'..'),url='https://meapps-it.github.io/VG/catalogo-nuovo.html';
(async()=>{const browser=await (process.env.SHOP_NAV_BROWSER==='webkit'?webkit:chromium).launch({headless:true,...(process.env.SHOP_NAV_BROWSER==='webkit'?{}:{args:['--no-sandbox']})});try{
 for(const width of [360,412,1280]){
  const context=await browser.newContext({viewport:{width,height:844},hasTouch:true,isMobile:width<650}),page=await context.newPage(),errors=[];
  page.on('pageerror',e=>errors.push(e.message));await context.addInitScript(()=>localStorage.setItem('vg-shop-session-v1','a'.repeat(64)));
  let release;const ready=new Promise(resolve=>release=resolve);
  await page.route('https://meapps-it.github.io/VG/**',r=>{const file=path.join(root,new URL(r.request().url()).pathname.replace('/VG/',''));return fs.existsSync(file)?r.fulfill({path:file}):r.abort()});
  await page.route('**/functions/v1/vg-shop-catalog**',async r=>{await ready;return r.fulfill({contentType:'application/json',body:JSON.stringify({items:Array.from({length:30},(_,i)=>({id:'item'+i,name:'Articolo '+i,sku:'SKU'+i,category:'Borse',quality:'STANDARD',price:140,images:['https://photos.test/a.jpg','https://photos.test/b.jpg']})),imagesExpiresAt:Date.now()+3600000})})});
  await page.route('https://photos.test/**',r=>r.fulfill({contentType:'image/svg+xml',body:'<svg xmlns="http://www.w3.org/2000/svg" width="600" height="800"><rect width="600" height="800" fill="tan"/></svg>'}));
  await page.goto(url,{waitUntil:'domcontentloaded'});assert(await page.locator('.catalog-loading').isVisible());assert.match(await page.locator('.catalog-loading').textContent(),/Per favore attendi/);assert.equal(await page.locator('.loading-ring').evaluate(e=>getComputedStyle(e).animationName),'catalog-spin');
  release();await page.locator('.item').first().waitFor();
  const opener=page.locator('[data-detail=item20]').first();await opener.scrollIntoViewIfNeeded();const scroll=await page.evaluate(()=>scrollY);assert(scroll>0);await opener.click();
  await page.locator('#productView').waitFor({state:'visible'});
  const size=await page.locator('#productView').boundingBox();assert.equal(size.x,0);assert.equal(size.y,0);assert.equal(size.width,width);assert.equal(size.height,844);assert.match(await page.locator('#closeProduct').textContent(),/Indietro/);
  const panel=await page.locator('.product-dialog').boundingBox();assert.equal(panel.x,0);assert.equal(panel.width,width);
  // Browser back is the same history navigation used by Android Back and iOS back/swipe.
  await page.goBack();await page.locator('#productView').waitFor({state:'hidden'});assert.equal(new URL(page.url()).pathname,'/VG/catalogo-nuovo.html');await page.waitForFunction(y=>Math.abs(scrollY-y)<3,scroll);
  await page.goForward();await page.locator('#productView').waitFor({state:'visible'});assert.equal(await page.locator('#productTitle').textContent(),'Articolo 20');
  await page.locator('#productHero').click();await page.locator('#photoZoom').waitFor({state:'visible'});await page.goBack();await page.locator('#photoZoom').waitFor({state:'hidden'});assert(await page.locator('#productView').isVisible());
  await page.locator('#closeProduct').click();await page.locator('#productView').waitFor({state:'hidden'});
  await page.locator('[data-detail=item20]').first().click();await page.locator('#productAdd').click();await page.locator('#overlay').waitFor({state:'visible'});await page.goBack();await page.locator('#overlay').waitFor({state:'hidden'});assert.equal(await page.locator('#productView').getAttribute('aria-hidden'),'true');
  assert.deepEqual(errors,[]);console.log('PASS detail '+width+'px: visible animated loader, full viewport page, Back/Forward, scroll restoration, zoom history and cart transition');await context.close();
 }
 const reduced=await browser.newContext({reducedMotion:'reduce'}),p=await reduced.newPage();await p.route('**/*',r=>r.fulfill({contentType:'text/html',body:'<style>'+fs.readFileSync(root+'/shop-catalog.css','utf8')+'</style><span class="loading-ring"></span>'}));await p.goto('https://fixture.test');assert.equal(await p.locator('.loading-ring').evaluate(e=>getComputedStyle(e).animationName),'none');await reduced.close();
}finally{await browser.close()}})().catch(e=>{console.error(e);process.exitCode=1});
