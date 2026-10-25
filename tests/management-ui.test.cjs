// Uses the real management HTML/CSS and renderHome, with isolated account/API fixtures.
const {chromium}=require('playwright');
const fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict');
const root=path.resolve(__dirname,'..'),source=fs.readFileSync(root+'/index.html','utf8');
const renderHome=source.slice(source.indexOf('function renderHome(){'),source.indexOf('function normalizeCategoryName('));
const fixture=`
window.VG_SUPABASE_READY=Promise.resolve({auth:{getSession:async()=>({data:{session:{access_token:'isolated-owner-fixture'}}})}});
function setTopMenuOpen(open){document.getElementById('topMenuPanel').classList.toggle('open',open);document.getElementById('topMenuBtn').setAttribute('aria-expanded',String(open))}
document.getElementById('topMenuBtn').onclick=()=>setTopMenuOpen(!document.getElementById('topMenuPanel').classList.contains('open'));
const now=new Date();const db={clienti:[],articoli:[],ordini:[40,4,65,44,36,3].map((totale,i)=>({id:'fixture-'+i,totale,stato:'consegnato',data:new Date(now.getFullYear(),now.getMonth()-5+i,3).toLocaleDateString('en-CA')}))};
function loadDB(){return db}function orderPaidStatus(){return true}function orderMargin(){return 0}function orderIncassato(o){return o.totale}function money(x){return Number(x).toLocaleString('it-IT',{minimumFractionDigits:2})}function promoValid(){return false}function dashboardMood(){return {title:'Andamento vendite',sub:'Dati dimostrativi del test di visualizzazione'}}function renderDashboardUrgent(){}function renderDashboardAgenda(){}function renderShipAlerts(){}function esc(s){return String(s).replace(/[&<>"']/g,'')}
${renderHome}
renderHome();
`;
let html=source.replace(/<script\b[^>]*>[\s\S]*?<\/script>/gi,'');
html=html.replace(/<body[^>]*>[\s\S]*?(?=<div class="top">)/,'<body>');
html=html.replace('</body>','<script>'+fixture+'</script><script src="./vg-catalog-stats.js?v=test"></script></body>');
(async()=>{
 const browser=await chromium.launch({headless:true,args:['--no-sandbox']});
 try{
 for(const width of [360,412,1280])for(const hybrid of [false,true]){
  const context=await browser.newContext({viewport:{width,height:844},isMobile:width<650,hasTouch:width<650,userAgent:hybrid?'Mozilla/5.0 Android VgHybrid/0.8.1':undefined});
  const page=await context.newPage(),errors=[];page.on('pageerror',e=>errors.push(e.message));
  let forbidden=false,requests=0;
  await page.route('https://meapps-it.github.io/VG/**',route=>{
   const name=new URL(route.request().url()).pathname.replace('/VG/','');
   if(!name)return route.fulfill({contentType:'text/html',body:html});
   const file=path.join(root,name);return fs.existsSync(file)?route.fulfill({path:file}):route.abort();
  });
  await page.route('**/functions/v1/vg-shop-catalog**',route=>{
   assert.equal(route.request().method(),'GET');assert.equal(route.request().headers().authorization,'Bearer isolated-owner-fixture');requests++;
   if(forbidden)return route.fulfill({status:403,contentType:'application/json',body:'{"error":"forbidden"}'});
   const daily=Array.from({length:30},(_,i)=>({day:'2026-10-'+String(i+1).padStart(2,'0'),visits:i<23?0:[2,4,0,8,1,3,6][i-23]}));
   return route.fulfill({contentType:'application/json',body:JSON.stringify({today:6,week:24,month:24,devicesMonth:9,daily,definition:'Una visita per dispositivo ogni 30 minuti.'})});
  });
  await page.goto('https://meapps-it.github.io/VG/');
  assert.equal(await page.locator('#homeSpark .sparkBar').count(),6);
  assert(await page.locator('#homeSpark .sparkBar').evaluateAll(bars=>bars.every(b=>b.getBoundingClientRect().height>0)),'six-month sales columns remain visible');
  assert.equal(await page.locator('.vgNativeAction:not([hidden])').count(),hybrid?3:0);
  if(hybrid&&process.env.VG_UI_SCREENSHOTS){fs.mkdirSync(process.env.VG_UI_SCREENSHOTS,{recursive:true});await page.screenshot({path:path.join(process.env.VG_UI_SCREENSHOTS,'dashboard-'+width+'.png')});}
  await page.locator('#topMenuBtn').click();await page.locator('#vgCatalogStatsOpen').click();
  await page.locator('#vgStatsResults:not([hidden])').waitFor();assert.equal(await page.locator('#vgStatsBars li').count(),7);
  assert.equal(await page.locator('#vgStatsSummary strong').allTextContents().then(x=>x.join(',')),'6,24,24,9');
  assert.equal(await page.locator('#vgStatsBars li').nth(2).locator('.vgStatsBar').evaluate(b=>b.getBoundingClientRect().height),0,'zero visits have no false column');
  assert(await page.locator('#vgStatsBars .vgStatsBar').nth(3).evaluate(b=>b.getBoundingClientRect().height>100));
  assert(await page.locator('#vgStatsClose').isVisible());
  const fonts=await page.locator('body,#vgCatalogStatsOpen,#vgStatsClose').evaluateAll(nodes=>nodes.map(n=>getComputedStyle(n).fontFamily));assert(fonts.every(f=>f.includes('system-ui')));
  assert(await page.locator('#vgCatalogStats').evaluate(d=>{const r=d.getBoundingClientRect();return r.left>=0&&r.right<=innerWidth&&r.top>=0&&r.bottom<=innerHeight}));
  if(hybrid&&process.env.VG_UI_SCREENSHOTS)await page.screenshot({path:path.join(process.env.VG_UI_SCREENSHOTS,'statistics-'+width+'.png')});
  await page.getByRole('button',{name:'30 giorni',exact:true}).click();assert.equal(await page.locator('#vgStatsBars li').count(),30);
  assert(await page.locator('#vgStatsClose').isVisible());await page.locator('#vgStatsClose').click();
  forbidden=true;await page.locator('#topMenuBtn').click();await page.locator('#vgCatalogStatsOpen').click();
  await page.getByText('Statistiche riservate al titolare del catalogo.',{exact:true}).waitFor();assert(await page.locator('#vgStatsResults').isHidden());
  forbidden=false;await page.locator('#vgStatsRetry').click();await page.locator('#vgStatsResults:not([hidden])').waitFor();assert.equal(requests,3);
  await page.locator('#vgStatsClose').click();assert.deepEqual(errors,[]);
  console.log('PASS management '+width+'px '+(hybrid?'APK':'PWA')+': original dashboard, sales bars, shared system font, 7/30-day visit columns, zero visits, owner errors, retry and close');
  await context.close();
 }
 }finally{await browser.close()}
})().catch(error=>{console.error(error);process.exitCode=1});
