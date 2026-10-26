const {chromium}=require('playwright'),fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict');
const root=path.resolve(__dirname,'..'),token='b'.repeat(64),password='isolated-login-password';
const address='https://meapps-it.github.io/VG/catalogo-nuovo.html';
(async()=>{const browser=await chromium.launch({headless:true,args:['--no-sandbox']});try{
 for(const width of [360,412]){
  const context=await browser.newContext({viewport:{width,height:844},isMobile:true,hasTouch:true});
  let logins=0,catalogs=0,status=200,delay=0;
  await context.addInitScript(()=>{
   window.passwordFrames=0;
   const inspect=()=>{const f=document.getElementById('accessForm');if(f&&f.getBoundingClientRect().height>0&&getComputedStyle(f).display!=='none'&&getComputedStyle(document.getElementById('accessGate')).display!=='none')window.passwordFrames++;requestAnimationFrame(inspect)};requestAnimationFrame(inspect);
  });
  await context.route('https://meapps-it.github.io/VG/**',r=>{const name=new URL(r.request().url()).pathname.replace('/VG/','');const file=path.join(root,name);return fs.existsSync(file)&&fs.statSync(file).isFile()?r.fulfill({path:file}):r.abort()});
  await context.route('**/functions/v1/vg-shop-catalog**',async r=>{
   const action=new URL(r.request().url()).searchParams.get('action');
   if(action==='login'){
    logins++;assert.equal(r.request().postDataJSON().password,password);
    return r.fulfill({contentType:'application/json',body:JSON.stringify({token,expiresAt:new Date(Date.now()+365*86400000).toISOString()})});
   }
   assert.equal(action,'catalog');catalogs++;assert.equal(r.request().headers().authorization,'Bearer '+token);
   if(delay)await new Promise(resolve=>setTimeout(resolve,delay));
   return r.fulfill({status,contentType:'application/json',body:status===200?JSON.stringify({items:[{id:'article',name:'Borsa di prova',sku:'SKU',brand:'Gucci',category:'Borse',quality:'STANDARD',price:140,images:[]}],imagesExpiresAt:Date.now()+3600000}):JSON.stringify({error:status===401?'Accesso richiesto':'Temporaneamente non disponibile'})});
  });
  let page=await context.newPage();await page.goto(address);await page.locator('#accessPassword').waitFor({state:'visible'});
  await page.locator('#accessPassword').fill(password);await page.locator('#accessSubmit').click();await page.locator('.item').waitFor();assert.equal(logins,1);
  // Ensure the fallback database transaction has committed before closing the page.
  await page.waitForFunction(async()=>new Promise(resolve=>{const r=indexedDB.open('vg-shop-access',1);r.onsuccess=()=>{const db=r.result,q=db.transaction('session').objectStore('session').get('vg-shop-session-v1');q.onsuccess=()=>{db.close();resolve(Boolean(q.result))}}}));
  const stored=await page.evaluate(()=>JSON.stringify({...localStorage})+JSON.stringify({...sessionStorage}));assert(!stored.includes(password),'password must never be persisted');
  await page.close();delay=800;page=await context.newPage();await page.goto(address,{waitUntil:'domcontentloaded'});
  assert.equal(await page.locator('#accessForm').isVisible(),false,'no password screen during a slow catalog request');
  await page.locator('.item').waitFor();assert.equal(await page.evaluate(()=>passwordFrames),0);assert.equal(logins,1,'return visits never POST the password again');
  // Recover a remembered token from IndexedDB when the primary stores are unavailable/cleared.
  await page.evaluate(()=>{localStorage.removeItem('vg-shop-session-v1');sessionStorage.removeItem('vg-shop-session-v1')});
  await page.reload();await page.locator('.item').waitFor();assert.equal(logins,1);assert.equal(await page.evaluate(()=>passwordFrames),0);
  // A temporary server failure must not turn into a password request or delete the session.
  status=503;await page.reload();await page.getByText('Impossibile caricare il catalogo. Aggiorna la pagina e riprova.').waitFor();
  assert.equal(await page.locator('#accessForm').isVisible(),false);assert.equal(await page.evaluate(()=>localStorage.getItem('vg-shop-session-v1')),token);
  status=200;delay=0;await page.reload();await page.locator('.item').waitFor();
  await page.locator('#shopLogout').click();await page.locator('#accessPassword').waitFor({state:'visible'});await page.reload();await page.locator('#accessPassword').waitFor({state:'visible'});assert.equal(logins,1);
  // Revocation is still enforced by the real access controller, not hidden by the loading screen.
  await page.locator('#accessPassword').fill(password);await page.locator('#accessSubmit').click();await page.locator('.item').waitFor();status=401;await page.reload();await page.locator('#accessPassword').waitFor({state:'visible'});assert.equal(await page.evaluate(()=>localStorage.getItem('vg-shop-session-v1')),null);
  console.log('PASS automatic access '+width+'px: first login, reopened page with zero password frames, IndexedDB recovery, offline session retained, explicit logout and revoked session');
  await context.close();
 }
}finally{await browser.close()}})().catch(e=>{console.error(e);process.exitCode=1});
