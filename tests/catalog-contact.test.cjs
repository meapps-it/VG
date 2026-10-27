const {chromium}=require('playwright'),assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path');
const root=path.resolve(__dirname,'..'),html=fs.readFileSync(root+'/catalogo-nuovo.html','utf8'),id='11111111-1111-4111-8111-111111111111';
(async()=>{const browser=await chromium.launch({headless:true,args:['--no-sandbox']});try{
 for(const width of [360,412,1280]){
  const context=await browser.newContext({viewport:{width,height:844},isMobile:width<650,hasTouch:true}),page=await context.newPage(),errors=[];page.on('pageerror',e=>errors.push(e.message));let sent=[];
  await context.route('https://wa.me/**',r=>r.fulfill({contentType:'text/plain',body:'Isolated WhatsApp link verification. No message sent.'}));
  await page.route('https://meapps-it.github.io/VG/**',r=>{const name=decodeURIComponent(new URL(r.request().url()).pathname.replace('/VG/',''));if(name==='catalogo-nuovo.html')return r.fulfill({contentType:'text/html',body:html});const file=path.join(root,name);return fs.existsSync(file)&&fs.statSync(file).isFile()?r.fulfill({path:file}):r.abort()});
  await page.route('**/functions/v1/vg-shop-catalog**',async r=>{
   assert.equal(r.request().headers().authorization,'Bearer '+'a'.repeat(64));
   if(r.request().method()==='POST'){const body=r.request().postDataJSON();assert(body.customer);sent.push(body);return r.fulfill({contentType:'application/json',body:'{"ok":true,"number":"ISOLATED-ORDER-1"}'});}
   return r.fulfill({contentType:'application/json',body:JSON.stringify({items:[{id,name:'Borsa & Pelle',sku:'BORSA-123',images:[],image:'',price:49,category:'Borse',description:'Pelle vera',availability:'disponibile'}],imagesExpiresAt:Date.now()+3600000})});
  });
  await page.addInitScript(()=>localStorage.setItem('vg-shop-session-v1','a'.repeat(64)));
  await page.goto('https://meapps-it.github.io/VG/catalogo-nuovo.html');await page.locator('.item').waitFor();assert.equal(await page.locator('[data-whatsapp]').count(),5);
  for(const href of await page.locator('[data-whatsapp]').evaluateAll(links=>links.map(a=>a.href))){const u=new URL(href);assert.equal(u.pathname,'/393351604686');assert(!/https?:\/\/|www\./i.test(u.searchParams.get('text')||''))};
  await page.locator('[data-detail]').first().click();await page.evaluate(()=>history.replaceState(null,'','#admin_token=DO_NOT_LEAK'));
  const contact=page.locator('[data-whatsapp=product]');await contact.focus();const href=await contact.getAttribute('href'),url=new URL(href),text=url.searchParams.get('text');assert.match(text,/Borsa & Pelle/);assert.match(text,/BORSA-123/);assert(!/https?:\/\/|www\.|simplexapp\.it|github\.io/i.test(text),'WhatsApp message must contain no link');assert(!text.includes('simplexapp.it/shop')); assert(!text.includes('admin_token')&&!text.includes('DO_NOT_LEAK'));
  const popupPromise=context.waitForEvent('page');await contact.click();const popup=await popupPromise;await popup.waitForLoadState();assert.equal(new URL(popup.url()).pathname,'/393351604686');await popup.close();assert(await page.locator('#closeProduct').isVisible());
  await page.locator('#productQty').fill('2');await page.locator('#productAdd').click();
  for(const [field,value] of Object.entries({name:'Cliente isolato',phone:'3330000000',address:'Via del test 1'}))await page.locator('#orderForm [name='+field+']').fill(value);
  await page.locator('[name=province]').selectOption('manual');await page.locator('#shippingProvinceManual').fill('Cuneo');await page.locator('#shippingCityManual').fill('Beinette');await page.locator('[name=cap]').fill('12081');await page.locator('[name=privacy]').check();
  await page.locator('#checkoutNext').click();assert.match(await page.locator('#checkoutReview').textContent(),/2 × Borsa & Pelle/);assert.equal(sent.length,0);await page.locator('#sendOrder').click();await page.locator('#orderStatus').filter({hasText:'Richiesta inviata! Numero: ISOLATED-ORDER-1'}).waitFor();assert.match(await page.locator('#shopNotice[data-kind=success]').textContent(),/ISOLATED-ORDER-1/);
  assert.equal(sent.length,1);assert.deepEqual(sent[0].items,[{id,qty:2}]);assert.equal(sent[0].customer.cap,'12081');assert.equal(await page.locator('#cartCount').textContent(),'0');assert.deepEqual(errors,[]);
  console.log('PASS contacts + checkout '+width+'px: five WhatsApp links including account assistance, correct number, article/SKU message, no admin-token leak, chooser-only chat and one isolated order submission');await context.close();
 }
}finally{await browser.close()}})().catch(e=>{console.error(e);process.exitCode=1});

