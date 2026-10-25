// Run: node tests/catalog-gallery.test.cjs (requires Playwright and Chromium).
const {chromium}=require('playwright');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const html=fs.readFileSync(__dirname+'/../catalogo-nuovo.html','utf8');
const base='https://meapps-it.github.io/VG/catalogo-nuovo.html';
const API='https://qfjwtawsqfwmsmrrgqfi.supabase.co/functions/v1/vg-shop-catalog';
const png=Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aJ1sAAAAASUVORK5CYII=','base64');
const photo=n=>'https://photos.test/'+n+'.png';
const item=(id,images)=>({id,name:'Borsa '+id,images,image:images[0]||'',price:25,category:'Borse',description:'Pelle vera\n[VGMETA]{"privato":true}[/VGMETA]\nMisure 20 cm',availability:'disponibile'});
(async()=>{
 const browser=await chromium.launch({headless:true,executablePath:process.env.CHROME_PATH||undefined,args:['--no-sandbox']});
 try{
 for(const width of [360,412,1280]){
  const context=await browser.newContext({viewport:{width,height:800},hasTouch:true,isMobile:width<650});
  const page=await context.newPage(),errors=[];page.on('pageerror',e=>errors.push(e.message));
  let requests=0;
  await page.route('https://meapps-it.github.io/VG/**',r=>{
   const path=decodeURIComponent(new URL(r.request().url()).pathname.replace('/VG/',''));
   if(path==='catalogo-nuovo.html')return r.fulfill({contentType:'text/html',body:html});
   if(path.startsWith('shop-')&&fs.existsSync(__dirname+'/../'+path))return r.fulfill({path:__dirname+'/../'+path});
   return r.abort();
  });
  await page.route('https://photos.test/**',r=>r.request().url().includes('broken')?r.fulfill({status:404}):r.fulfill({contentType:'image/png',body:png}));
  await page.route(API+'**',r=>{assert.equal(r.request().method(),'GET');requests++;return r.fulfill({contentType:'application/json',body:JSON.stringify({items:[item('many',[photo(1),photo(2),photo(3)]),item('single',[photo(4)]),item('none',[]),item('broken',[photo('broken'),photo(5)])],imagesExpiresAt:Date.now()+(requests===1?1000:3600000)})})});
  await page.addInitScript(()=>localStorage.setItem('vg-shop-session-v1','a'.repeat(64)));
  await page.goto(base);await page.locator('.item').first().waitFor();
  assert.ok(await page.locator('.intro h1,.item .name').evaluateAll(nodes=>nodes.every(n=>getComputedStyle(n).fontFamily.includes('system-ui'))),'catalog headings use the system font');
  await page.locator('[data-detail="many"]').first().click();
  await page.waitForFunction(()=>document.querySelector('#galleryCount').textContent==='1 / 3');
  await page.waitForFunction(()=>document.querySelector('#productHero img')?.naturalWidth>0);
  assert.ok(await page.locator('#productHero').evaluate(hero=>{const h=hero.getBoundingClientRect(),i=hero.querySelector('img').getBoundingClientRect();return i.left>=h.left&&i.top>=h.top&&i.right<=h.right+0.5&&i.bottom<=h.bottom+0.5}),'image must fit completely inside hero');
  assert.equal(await page.locator('#galleryThumbs button').count(),3);
  await page.locator('#galleryNext').click();assert.equal(await page.locator('#galleryCount').textContent(),'2 / 3');
  await page.locator('#galleryPrev').click();assert.equal(await page.locator('#galleryCount').textContent(),'1 / 3');
  await page.getByRole('button',{name:'Mostra fotografia 3',exact:true}).click();assert.equal(await page.locator('#galleryCount').textContent(),'3 / 3');
  await page.locator('#productHero').dispatchEvent('touchstart',{touches:[{identifier:1,clientX:250,clientY:200}]});
  await page.locator('#productHero').dispatchEvent('touchend',{changedTouches:[{identifier:1,clientX:100,clientY:205}]});
  assert.equal(await page.locator('#galleryCount').textContent(),'1 / 3');
  await page.locator('#productHero').dispatchEvent('touchstart',{touches:[{identifier:1,clientX:100,clientY:200}]});
  await page.locator('#productHero').dispatchEvent('touchend',{changedTouches:[{identifier:1,clientX:250,clientY:205}]});
  assert.equal(await page.locator('#galleryCount').textContent(),'3 / 3');
  await page.waitForTimeout(410);await page.locator('#productHero').click();
  assert.equal(await page.locator('#photoZoom').getAttribute('aria-hidden'),'false');
  const close=await page.locator('#closeZoom').boundingBox();console.log('ZOOM BOUNDS',width,close,await page.evaluate(()=>({innerWidth,innerHeight,scrollWidth:document.documentElement.scrollWidth,overflow:[...document.querySelectorAll('body *')].filter(e=>e.getBoundingClientRect().right>360&&getComputedStyle(e).position!=='fixed').slice(0,8).map(e=>({tag:e.tagName,id:e.id,cls:e.className,right:e.getBoundingClientRect().right}))})));assert.ok(close.x>=0&&close.y>=0&&close.x+close.width<=width&&close.y+close.height<800);
  await page.locator('#zoomNext').click();assert.equal(await page.locator('#zoomCount').textContent(),'1 / 3');
  await page.locator('#closeZoom').click();assert.equal(await page.locator('#photoZoom').getAttribute('aria-hidden'),'true');
  assert.ok(requests>=2,'expired signed URLs must refresh');
  const desc=await page.locator('#productDescription').textContent();assert.match(desc,/Pelle vera/);assert.match(desc,/Misure/);assert.doesNotMatch(desc,/VGMETA|privato/);
  assert.equal(await page.locator('#productHero img').evaluate(e=>getComputedStyle(e).objectFit),'contain');
  assert.ok(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),'no horizontal overflow');
  await page.locator('#productQty').fill('2');await page.locator('#productAdd').click();assert.match(await page.locator('#total').textContent(),/50,00/);
  await page.locator('input[name=name]').fill('Cliente di prova');await page.locator('input[name=phone]').fill('3330000000');await page.locator('input[name=address]').fill('Via di prova 1');await page.locator('input[name=privacy]').check();await page.locator('select[name=province]').selectOption('manual');await page.locator('#shippingProvinceManual').fill('Torino');await page.locator('#shippingCityManual').fill('Torino');await page.locator('input[name=cap]').fill('10100');
  
  await page.locator('#checkoutNext').click();assert.match(await page.locator('#checkoutReview').textContent(),/2 × Borsa many/);assert.ok(await page.locator('#sendOrder').isVisible());
  await page.locator('#checkoutBack').click();await page.locator('#closeCart').click();
  for(const [id,counter] of [['single','1 / 1'],['none','Nessuna fotografia'],['broken','1 / 2']]){
   await page.locator('[data-detail="'+id+'"]').first().click();assert.equal(await page.locator('#galleryCount').textContent(),counter);
   if(id==='single'){assert.equal(await page.locator('#galleryNext').isVisible(),false);assert.equal(await page.locator('#galleryThumbs button').count(),0)}
   if(id==='none')assert.equal(await page.locator('#productHero').isDisabled(),true);
   if(id==='broken'){await page.locator('#productHero .photo-error').waitFor();await page.locator('#galleryNext').click();await page.waitForFunction(()=>document.querySelector('#productHero img')?.naturalWidth>0)}
   await page.locator('#closeProduct').click();
  }
  assert.deepEqual(errors,[]);console.log('PASS gallery + swipe + zoom + missing photos + URL renewal + checkout at '+width+'px');
  await context.close();
 }
 }finally{await browser.close()}
})().catch(e=>{console.error(e);process.exitCode=1});
