// Runs the exact APK-bundled script against management HTML, no production writes.
const {chromium}=require('playwright');
const fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict');
const root=path.resolve(__dirname,'..');
const script=fs.readFileSync(root+'/android-native/app/src/main/assets/vg-android-appearance.js','utf8');
const source=fs.readFileSync(root+'/index.html','utf8').replace(/<script\b[^>]*>[\s\S]*?<\/script>/gi,'').replace(/<body[^>]*>[\s\S]*?(?=<div class="top">)/,'<body>');
(async()=>{
 const browser=await chromium.launch({headless:true,args:['--no-sandbox'],...(process.env.CHROMIUM_PATH?{executablePath:process.env.CHROMIUM_PATH}:{})});
 try {
  for(const width of [360,412,1280]) {
   const context=await browser.newContext({viewport:{width,height:844}});
   await context.route('**/*',route=>route.request().isNavigationRequest()?route.fulfill({contentType:'text/html',body:source}):route.abort());
   await context.addInitScript(()=>{
    window.savedAppearance=Number(localStorage.getItem('test-percent'))||100;
    window.VgAppearanceBridge={postMessage(raw){const data=JSON.parse(raw);if(data.action==='set'){window.savedAppearance=data.percent;localStorage.setItem('test-percent',String(data.percent));}window.__vgApplyAppearance({percent:window.savedAppearance,systemPercent:100});}};
   });
   await context.addInitScript(script);
   const page=await context.newPage();
   await page.goto('https://meapps-it.github.io/VG/');
   await page.evaluate(()=>{document.querySelectorAll('.section').forEach(x=>x.style.display='none');document.getElementById('sec_impostazioni').style.display='block';});
   await page.locator('#vg-font-range').waitFor();
   assert.equal(await page.locator('#vg-font-control').count(),1);
   assert.equal(await page.locator('#sec_impostazioni #vg-font-control').count(),1);
   assert.equal(await page.locator('#vg-font-value').textContent(),'100%');
   for(const percent of [75,150,100,125]) {
    await page.locator('#vg-font-range').evaluate((el,value)=>{el.value=String(value);el.dispatchEvent(new Event('input',{bubbles:true}));},percent);
    assert.equal(await page.locator('#vg-font-value').textContent(),percent+'%');
    assert.equal(await page.evaluate(()=>window.savedAppearance),percent);
   }
   await page.reload();
   assert.equal(await page.locator('#vg-font-value').textContent(),'125%');
   await page.evaluate(()=>document.getElementById('sec_impostazioni').style.display='block');
   await page.locator('#vg-font-reset').click();
   assert.equal(await page.evaluate(()=>localStorage.getItem('test-percent')),'100');
   // No per-node CSS scaling may survive from the previous implementation.
   await page.evaluate(()=>{
    const el=document.createElement('style');el.id='vg-native-font-style';el.textContent='[data-vg-font-original]{font-size:999px!important}';document.head.appendChild(el);
    document.getElementById('vg-font-preview').dataset.vgFontOriginal='1';
   });
   await page.waitForFunction(()=>!document.getElementById('vg-native-font-style'));
   assert.equal(await page.locator('[data-vg-font-original]').count(),0);
   // A denied native font must expose the fallback, rather than claiming success.
   await page.evaluate(()=>window.__vgApplyAppearance({fontRevision:1,faces:[],fontStatus:'Font Samsung non disponibile'}));
   assert.equal(await page.locator('#vg-font-status').textContent(),'Font Samsung non disponibile');
   // Text font changes preserve graphics and the icon font.
   await page.evaluate(()=>{const i=document.createElement('i');i.className='fa';i.id='icon-test';i.style.fontFamily='TestIcon';document.body.appendChild(i);});
   assert.equal(await page.locator('#icon-test').evaluate(el=>getComputedStyle(el).fontFamily),'TestIcon');
   // Simulate Android textZoom in desktop Chromium using browser font settings is not equivalent.
   // Layout is checked at increased CSS text sizes; actual textZoom is covered by Android tests.
   for(const factor of [.75,1.5,1.95]) {
    await page.evaluate(factor=>{
     document.querySelectorAll('#sec_impostazioni *').forEach(el=>{
      if(!el.dataset.baseSize)el.dataset.baseSize=getComputedStyle(el).fontSize;
      el.style.fontSize=parseFloat(el.dataset.baseSize)*factor+'px';
     });
     document.documentElement.classList.toggle('vg-large-text',factor>1.2);
    },factor);
    const box=await page.locator('#vg-font-control').boundingBox();
    assert(box.x>=0&&box.x+box.width<=width+1,'appearance panel fits viewport');
    assert(await page.locator('#vg-font-reset').evaluate(el=>el.scrollHeight<=el.clientHeight+2),'reset label is not clipped');
   }
   await context.close();
  }
  console.log('PASS APK appearance: settings location, range, reset, reload persistence, legacy cleanup, explicit fallback, icon preservation, responsive panel');
 } finally {await browser.close();}
})().catch(error=>{console.error(error);process.exit(1)});
