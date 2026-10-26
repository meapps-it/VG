// Real publication handler + cloud writer, with an isolated deferred server.
const {chromium}=require('playwright'),fs=require('node:fs'),assert=require('node:assert/strict');
const source=fs.readFileSync(__dirname+'/../index.html','utf8');
const writer=source.slice(source.indexOf('async function saveArticleCatalogPublication('),source.indexOf('async function cloudSaveOne('));
const start=source.indexOf('  if(!el.dataset.shopPublishBound){'),end=source.indexOf('  renderArtBreadcrumbs();',start);
const handler=source.slice(start,end);
(async()=>{const browser=await chromium.launch({headless:true,args:['--no-sandbox']});try{
 for(const width of [360,412]){
  const page=await browser.newPage({viewport:{width,height:844}}),errors=[];page.on('pageerror',e=>errors.push(e.message));
  const script=`let cloudBusy=false,catalogPublicationWrites=0,catalogPublicationRevision=0;const cloudSession={user:{id:'owner'}};window.requests=[];window.opened=0;window.messages=[];
  const initial={articoli:[{id:'article',_cloud:true,pubblicatoCatalogo:false,prezzoVendita:290,foto:['real-path.jpg']}]};
  function loadDB(){return JSON.parse(localStorage.getItem('fixture')||JSON.stringify(initial))}
  function saveDBLocal(db){localStorage.setItem('fixture',JSON.stringify(db));return true}
  function cloudUi(){}function cloudEnabled(){return true}function isUuid(){return true}function toast(s){messages.push(s)}
  function ensureCloud(){return {from(table){return {update(value){requests.push({table,value});return this},eq(){return this},select(){return this},single(){return new Promise(resolve=>window.finishWrite=fail=>resolve(fail?{error:Error('offline')}:{data:{id:'article',stato_pubblicazione:requests.at(-1).value.stato_pubblicazione}}))}}}}}
  function upsertCloudArticle(){throw Error('Must not resync photos')}
  ${writer}
  const el=document.getElementById('listArt');${handler}
  el.querySelector('input').checked=loadDB().articoli[0].pubblicatoCatalogo;
  document.addEventListener('click',e=>{if(e.target.closest('[data-open]'))opened++});`;
  const html='<meta name="viewport" content="width=device-width,initial-scale=1"><div id="listArt"><article data-open="art"><label class="shopPublishControl"><input type="checkbox" data-shop-publish="article"> Pubblicato nel catalogo clienti</label></article></div><script>'+script+'</script>';
  await page.route('https://meapps-it.github.io/VG/__publication-fixture',r=>r.fulfill({contentType:'text/html',body:html}));
  await page.goto('https://meapps-it.github.io/VG/__publication-fixture');
  let box=page.locator('input[data-shop-publish]');await box.check();
  await page.waitForFunction(()=>typeof finishWrite==='function');assert(await box.isChecked());assert(await box.isDisabled());assert.equal(await page.evaluate(()=>opened),0);assert.equal(await page.evaluate(()=>requests.length),1);
  await page.evaluate(()=>finishWrite(false));await page.waitForFunction(()=>!document.querySelector('input').disabled);assert(await box.isChecked());
  assert.deepEqual(await page.evaluate(()=>requests[0]),{table:'prodotti',value:{stato_pubblicazione:'pubblicato'}});
  await page.reload();box=page.locator('input[data-shop-publish]');assert(await box.isChecked(),'confirmed publication survives reload');
  await box.uncheck();await page.waitForFunction(()=>typeof finishWrite==='function');await page.evaluate(()=>finishWrite(true));await page.waitForFunction(()=>!document.querySelector('input').disabled);assert(await box.isChecked(),'server failure restores confirmed state');
  const article=await page.evaluate(()=>loadDB().articoli[0]);assert.equal(article.prezzoVendita,290);assert.deepEqual(article.foto,['real-path.jpg']);assert.deepEqual(errors,[]);
  console.log('PASS publication '+width+'px: pending toggle stays mounted, confirmed state survives reload, errors restore server state, one status-only request and zero photo syncs');await page.close();
 }
}finally{await browser.close()}})().catch(e=>{console.error(e);process.exitCode=1});
