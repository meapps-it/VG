const {chromium}=require('playwright'),assert=require('node:assert/strict'),fs=require('node:fs');
const source=fs.readFileSync(__dirname+'/../index.html','utf8');
const actions=[...new Set([...source.matchAll(/data-action="([A-Za-z][\w-]*)"/g)].map(m=>m[1]))];
const scripts=[...source.matchAll(/<script\b[^>]*>([\s\S]*?)<\/script>/gi)].map(m=>m[1]).join('\n');
for(const action of actions){const withoutMarkup=scripts.replaceAll('data-action="'+action+'"','');assert(new RegExp('[\'\"]'+action+'[\'\"]').test(withoutMarkup),'Unwired button: '+action)}
const start=source.indexOf("document.addEventListener('click',(ev)=>{"),end=source.indexOf('\n});',start)+4;
const dispatcher=source.slice(start,end),photos=source.slice(source.indexOf('function renderArtPhotoPrev('),source.indexOf('function fitTextarea('));
const state=`const MAX_ARTICLE_PHOTOS=20;let currentArtExistingPics=['https://fixtures.test/original.png'],currentArtPendingFiles=[],currentArtPendingPreviewUrls=[],currentArtPhotosCleared=false;let orderListMode='all';window.testCalls=[];window.testToasts=[];function toast(s){testToasts.push(s)}function saveArticleDraft(){testCalls.push('draft')}function hide(id){document.getElementById(id).style.display='none'}function go(page){testCalls.push('go:'+page)}function openArtEdit(){testCalls.push('newArt')}function openCliEdit(){testCalls.push('newCli')}function openOrdEdit(){testCalls.push('newOrd')}function saveArt(){testCalls.push('saveArt')}function saveCli(){testCalls.push('saveCli')}function saveOrd(){testCalls.push('saveOrd')}function saveClientDraft(){}function saveOrderDraft(){}\n`;
let html=source.replace(/<script\b[^>]*>[\s\S]*?<\/script>/gi,'');html=html.replace(/<body[^>]*>[\s\S]*?(?=<div class="top">)/,'<body>');
html=html.replace('</body>','<script>'+state+photos+dispatcher+"\ndocument.getElementById('a_photo').addEventListener('change',async()=>{await refreshArtPhotoPreviewFromInput();saveArticleDraft()});renderCurrentArtPhotoPreview();</script></body>");
const png=Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aJ1sAAAAASUVORK5CYII=','base64');
(async()=>{const browser=await chromium.launch({headless:true,args:['--no-sandbox']});try{
 for(const width of [360,412]){
  const context=await browser.newContext({viewport:{width,height:844},isMobile:true,hasTouch:true}),page=await context.newPage(),errors=[];page.on('pageerror',e=>errors.push(e.message));
  await page.route('**/*',r=>r.request().url().includes('/VG/')&&r.request().resourceType()==='document'?r.fulfill({contentType:'text/html',body:html}):r.abort());
  await page.goto('https://meapps-it.github.io/VG/');
  await page.evaluate(()=>document.getElementById('mArtEdit').style.display='flex');
  async function select(names,mimeType='image/png'){const pending=page.waitForEvent('filechooser');await page.locator('[data-action=pickMoreArtPhotos]').click();const chooser=await pending;assert(chooser.isMultiple());await chooser.setFiles(names.map(name=>({name,mimeType,buffer:png})));}
  await select(['first.png','second.png']);await page.waitForFunction(()=>currentArtPendingFiles.length===2&&currentArtPendingPreviewUrls.length===2);
  assert.equal(await page.locator('#a_photo_prev img').count(),3,'existing photograph stays in the gallery');
  await select(['third.png']);await page.waitForFunction(()=>currentArtPendingFiles.length===3);
  await select(['provider-photo.bin'],'application/octet-stream');await page.waitForFunction(()=>currentArtPendingFiles.length===4);
  assert.equal(await page.evaluate(()=>currentArtPendingFiles.at(-1).type),'image/png');
  await page.waitForFunction(()=>Array.from(document.querySelectorAll('#a_photo_prev img')).slice(1).every(img=>img.complete&&img.naturalWidth===1));
  await select([]);assert.equal(await page.evaluate(()=>currentArtPendingFiles.length),4,'cancel never clears earlier selections');
  await select(Array.from({length:25},(_,i)=>'extra-'+i+'.png'));await page.waitForFunction(()=>currentArtPendingFiles.length===19);
  assert.equal(await page.locator('#a_photo_prev img').count(),20);await page.locator('[data-action=pickMoreArtPhotos]').click();assert.match(await page.evaluate(()=>testToasts.at(-1)),/massimo di 20/);
  await page.locator('[data-action=clearPendingArtPhotos]').click();assert.equal(await page.evaluate(()=>currentArtPendingFiles.length),0);assert.equal(await page.locator('#a_photo_prev img').count(),1);assert.equal(await page.evaluate(()=>currentArtPhotosCleared),false);
  await page.locator('[data-action=saveArt]').click();assert((await page.evaluate(()=>testCalls)).includes('saveArt'));
  await page.evaluate(()=>{document.getElementById('mArtEdit').style.display='none';for(const action of ['newArt','newCli','newOrd','saveCli','saveOrd'])document.querySelector('[data-action="'+action+'"]').click();for(const name of ['home','articoli','clienti','ordini','finanze','impostazioni'])document.querySelector('[data-go="'+name+'"]').click()});
  const calls=await page.evaluate(()=>testCalls);for(const action of ['newArt','newCli','newOrd','saveCli','saveOrd',...['home','articoli','clienti','ordini','finanze','impostazioni'].map(s=>'go:'+s)])assert(calls.includes(action),action);
  assert.deepEqual(errors,[]);console.log('PASS actions '+width+'px: '+actions.length+' controls wired; real multiple-file chooser, append/cancel/20-photo limit, preserve existing pictures, form commands and all navigation destinations; no production writes');await context.close();
 }
}finally{await browser.close()}})().catch(e=>{console.error(e);process.exitCode=1});

