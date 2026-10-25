// Read-only smoke test of the deployed catalog and private signed images.
const {chromium}=require('playwright');
const assert=require('node:assert/strict');
(async()=>{
 const browser=await chromium.launch({headless:true,args:['--no-sandbox']});
 try{
 for(const width of [360,412,1280]){
  const context=await browser.newContext({viewport:{width,height:800},hasTouch:true,isMobile:width<650});
  const page=await context.newPage();page.setDefaultTimeout(45000);
  const errors=[];page.on('pageerror',e=>errors.push(e.message));
  await page.route('**/functions/v1/vg-shop-catalog**',r=>(r.request().method()==='GET'||(process.env.VG_SHOP_PASSWORD&&new URL(r.request().url()).searchParams.get('action')==='login'))?r.continue():r.abort());
  // GitHub Pages can take a few minutes to deploy the main branch.
  for(let attempt=0;attempt<18;attempt++){
   await page.goto((process.env.CATALOG_LIVE_URL||'https://meapps-it.github.io/VG/catalogo-nuovo.html')+'?gallery-check='+Date.now());
   if(await page.locator('.visual-categories').count())break;
   if(attempt===17)throw Error('Published gallery version did not appear');
   await page.waitForTimeout(10000);
  }
  // Password is supplied through a CI secret or an already-issued short-lived test session.
  if(process.env.VG_SHOP_TEST_SESSION)await page.evaluate(token=>localStorage.setItem('vg-shop-session-v1',token),process.env.VG_SHOP_TEST_SESSION);
  if(await page.locator('body.shop-locked').count()){
   if(!process.env.VG_SHOP_PASSWORD&&!process.env.VG_SHOP_TEST_SESSION){
    assert.ok(await page.locator('#accessGate').isVisible());
    console.log('LIVE PASS '+width+'px: password gate visible; authenticated read-only checks require a CI secret/session');await context.close();continue;
   }
   if(process.env.VG_SHOP_TEST_SESSION)await page.reload();
   else{await page.locator('#accessPassword').fill(process.env.VG_SHOP_PASSWORD);await page.locator('#accessSubmit').click()}
  }
  await page.locator('.item').first().waitFor();
  const inventory=await page.evaluate(()=>products.map(p=>({id:p.id,photos:p.images?.length||0,image:p.image,description:p.description})));
  assert.ok(inventory.length>100);assert.ok(inventory.every(p=>!p.description.includes('[VGMETA]')));
  const many=inventory.filter(p=>p.photos>1).sort((a,b)=>b.photos-a.photos)[0];
  const single=inventory.find(p=>p.photos===1);
  assert.ok(many&&single,'Real multi-photo and single-photo articles must exist');
  await page.locator('[data-detail="'+many.id+'"]').first().click();
  await page.waitForFunction(()=>document.querySelector('#productHero img')?.naturalWidth>0);
  assert.ok(await page.locator('#productHero').evaluate(hero=>{const h=hero.getBoundingClientRect(),i=hero.querySelector('img').getBoundingClientRect();return i.left>=h.left&&i.top>=h.top&&i.right<=h.right+0.5&&i.bottom<=h.bottom+0.5}),'image must fit completely inside hero');
  assert.equal(await page.locator('#galleryThumbs button').count(),many.photos);
  await page.locator('#galleryNext').click();assert.equal(await page.locator('#galleryCount').textContent(),'2 / '+many.photos);
  await page.waitForFunction(()=>document.querySelector('#productHero img')?.naturalWidth>0);
  await page.locator('#galleryThumbs button').last().click();assert.equal(await page.locator('#galleryCount').textContent(),many.photos+' / '+many.photos);
  await page.waitForFunction(()=>document.querySelector('#productHero img')?.naturalWidth>0);
  await page.locator('#productHero').dispatchEvent('touchstart',{touches:[{identifier:1,clientX:250,clientY:200}]});
  await page.locator('#productHero').dispatchEvent('touchend',{changedTouches:[{identifier:1,clientX:100,clientY:205}]});
  assert.equal(await page.locator('#galleryCount').textContent(),'1 / '+many.photos);
  await page.waitForTimeout(450);await page.locator('#productHero').click();
  const close=await page.locator('#closeZoom').boundingBox();assert.ok(close&&close.y>=0&&close.x+close.width<=width&&close.y+close.height<=800);
  await page.locator('#closeZoom').click();
  assert.ok(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth));
  await page.locator('#closeProduct').click();
  await page.locator('[data-detail="'+single.id+'"]').first().click();
  await page.waitForFunction(()=>document.querySelector('#productHero img')?.naturalWidth>0);
  assert.equal(await page.locator('#galleryCount').textContent(),'1 / 1');assert.equal(await page.locator('#galleryNext').isVisible(),false);
  assert.deepEqual(errors,[]);
  console.log('LIVE PASS '+width+'px: '+inventory.length+' products; '+inventory.reduce((s,p)=>s+p.photos,0)+' signed photos; gallery '+many.photos+' photos and single-photo product; thumbnails, arrows, swipe, zoom/close');
  await context.close();
 }
 }finally{await browser.close()}
})().catch(e=>{console.error(e);process.exitCode=1});
