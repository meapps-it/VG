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
html=html.replace(/<body[^>]*>[\s\S]*?(?=<div class="top">)/,'<body class="vg-workspace">');
html=html.replace('</body>','<script>'+fixture+'</script><script src="./vg-catalog-stats.js?v=test"></script></body>');
(async()=>{
 const browser=await chromium.launch({headless:true,args:['--no-sandbox']});
 try{
 for(const width of [360,412,1280])for(const hybrid of [false,true]){
  const context=await browser.newContext({viewport:{width,height:844},isMobile:width<650,hasTouch:width<650,userAgent:hybrid?'Mozilla/5.0 Android VgHybrid/0.8.1':undefined});
  if(hybrid)await context.addInitScript({path:path.join(root,'android-native/app/src/main/assets/vg-android-appearance.js')});
  const page=await context.newPage(),errors=[];page.on('pageerror',e=>errors.push(e.message));
  let forbidden=false,requests=0;
  await page.route('https://meapps-it.github.io/VG/**',route=>{
   const name=new URL(route.request().url()).pathname.replace('/VG/','');
   if(!name)return route.fulfill({contentType:'text/html',body:html});
   if(hybrid&&name==='vg-font-settings.js')return route.fulfill({contentType:'application/javascript',body:''});
   const file=path.join(root,name);return fs.existsSync(file)?route.fulfill({path:file}):route.abort();
  });
  await page.route('**/functions/v1/vg-shop-catalog**',route=>{
   const action=new URL(route.request().url()).searchParams.get('action');if(action==='customer-admin')return route.fulfill({contentType:'application/json',body:JSON.stringify({total:1,pending:1,unread:1,customers:[{id:'11111111-1111-4111-8111-111111111111',phone:'393330000000',status:'pending'}],notifications:[],hasMore:false,offset:0})});
   if(action==='customer-client-links')return route.fulfill({contentType:'application/json',body:'{"accounts":[],"clients":[]}'});
   if(route.request().method()==='POST'){assert.equal(new URL(route.request().url()).searchParams.get('action'),'customer-authorize');assert.equal(route.request().postDataJSON().status,'approved');return route.fulfill({contentType:'application/json',body:'{"ok":true}'});}
   assert.equal(route.request().method(),'GET');assert.equal(route.request().headers().authorization,'Bearer isolated-owner-fixture');requests++;
   if(forbidden)return route.fulfill({status:403,contentType:'application/json',body:'{"error":"forbidden"}'});
   const daily=Array.from({length:30},(_,i)=>({day:'2026-10-'+String(i+1).padStart(2,'0'),visits:i<23?0:[2,4,0,8,1,3,6][i-23]}));
   return route.fulfill({contentType:'application/json',body:JSON.stringify({today:6,week:24,month:24,devicesMonth:9,daily,customerStats:{summary:{registeredCustomers:2,catalogOrders:4,pendingOrders:1},customers:[{id:'c1',phone:'393330000000',profile:{name:'Cliente statistiche',address:'Via Roma 1',city:'Beinette',province:'CN',cap:'12081'},registeredAt:'2026-10-26T09:00:00Z',lastLoginAt:'2026-10-26T10:00:00Z',orders:1}],orders:[{number:'WEB-STATS',requestedAt:'2026-10-26T10:00:00Z',status:'in_lavorazione',total:140,customer:{nome:'Cliente statistiche',telefono:'3330000000'},items:[{name:'Scarpe',qty:1,price:140,size:'36',color:'Nero',quality:'Standard'}]}],hasMoreCustomers:false,hasMoreOrders:false},definition:'Una visita per dispositivo ogni 30 minuti.'})});
  });
  await page.goto('https://meapps-it.github.io/VG/');
  assert.equal(await page.locator('#homeSpark .sparkBar').count(),6);assert(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),'management must not overflow');const navStrip=page.locator('.navBottom .inner');
  const buttons=page.locator('.navBottom button');
  assert.equal(await buttons.count(),6);
  assert(await buttons.evaluateAll(nodes=>nodes.every(n=>n.offsetWidth>=44&&n.offsetHeight>=44)),'all navigation destinations have usable touch targets');
  const layout=await navStrip.evaluate(e=>({scroll:e.scrollWidth,width:e.clientWidth,columns:getComputedStyle(e).gridTemplateColumns.split(' ').length}));
  assert.equal(layout.columns,width>=1080?1:6);
  assert(layout.scroll<=layout.width,'navigation fits without horizontal scrolling');
  assert(await page.locator('.vgWorkspaceLogo').isVisible());
  assert(await page.locator('.vgQuickActions').isVisible());
  assert(await page.locator('.vgCatalogTools').isVisible());
  assert(await page.locator('#homeSpark .sparkBar').evaluateAll(nodes=>nodes.every(n=>n.getBoundingClientRect().width<n.parentElement.getBoundingClientRect().width*.8)),'chart columns are narrower than their touch targets');assert.equal(await page.locator('.stat').first().evaluate(e=>getComputedStyle(e).borderTopWidth),'1px');
  assert(await page.locator('#homeSpark .sparkBar').evaluateAll(bars=>bars.every(b=>b.getBoundingClientRect().height>0)),'six-month sales columns remain visible');
  assert.equal(await page.locator('.vgNativeAction:not([hidden])').count(),hybrid?4:1);
  if(hybrid&&process.env.VG_UI_SCREENSHOTS){fs.mkdirSync(process.env.VG_UI_SCREENSHOTS,{recursive:true});await page.screenshot({path:path.join(process.env.VG_UI_SCREENSHOTS,'dashboard-'+width+'.png')});}
  await page.locator('#topMenuBtn').click();await page.locator('#vgCatalogStatsOpen').click();
  await page.locator('#vgStatsResults:not([hidden])').waitFor();assert.equal(await page.locator('#vgStatsBars li').count(),7);
  assert.equal(await page.locator('#vgStatsSummary strong').allTextContents().then(x=>x.join(',')),'6,24,24,9');assert.equal(await page.locator('#vgCustomerStatsSummary strong').allTextContents().then(x=>x.join(',')),'2,4,1');await page.locator('#vgRegisteredCustomers summary').click();assert.match(await page.locator('#vgRegisteredCustomers').textContent(),/393330000000/);await page.locator('#vgCustomerOrders summary').click();assert.match(await page.locator('#vgCustomerOrders').textContent(),/Misura 36/);assert.match(await page.locator('#vgCustomerOrders').textContent(),/Nero/);
  assert.equal(await page.locator('#vgStatsBars li').nth(2).locator('.vgStatsBar').evaluate(b=>b.getBoundingClientRect().height),0,'zero visits have no false column');
  assert(await page.locator('#vgStatsBars .vgStatsBar').nth(3).evaluate(b=>b.getBoundingClientRect().height>100));
  assert(await page.locator('#vgStatsClose').isVisible());const fullscreen=await page.locator('#vgCatalogStats').boundingBox();assert.equal(fullscreen.x,0);assert.equal(fullscreen.y,0);assert.equal(Math.round(fullscreen.width),width);assert.equal(Math.round(fullscreen.height),844);
  assert(await page.locator('#vgStatsTitle').isVisible());
  console.log('STAT HEADER',width,hybrid,await page.locator('#vgStatsTitle').evaluate(e=>({text:e.textContent,color:getComputedStyle(e).color,rect:e.getBoundingClientRect().toJSON()})));
  const fonts=await page.locator('body,#vgCatalogStatsOpen,#vgStatsClose,.bulkPasteBox').evaluateAll(nodes=>nodes.map(n=>getComputedStyle(n).fontFamily));assert(fonts.every(f=>!f.includes('VG Admin Sans')),'management preserves the system font instead of the embedded face');
  if(hybrid){
   await page.evaluate(async()=>{await window.__vgApplyAppearance({percent:100,systemPercent:100,fontRevision:1,faces:[]});document.documentElement.style.setProperty('--vg-android-font','serif')});
   await page.waitForFunction(()=>document.getElementById('vg-android-appearance-style')&&!document.getElementById('vg-native-font-style'));
  }
  for(const selector of ['.heroSpot .desc','#vgStatsClose']){
   const typography=await page.locator(selector).evaluate(el=>({weight:getComputedStyle(el).fontWeight,family:getComputedStyle(el).fontFamily}));
   assert.equal(typography.weight,'700');assert(!typography.family.includes('VG Admin Sans'));assert(typography.family.startsWith(hybrid?'serif':'system-ui'),'bold styling must preserve the font supplied by Android');
  }
  if(hybrid){await page.evaluate(()=>document.documentElement.style.setProperty('--vg-android-font','monospace'));assert((await page.locator('#vgStatsClose').evaluate(el=>getComputedStyle(el).fontFamily)).startsWith('monospace'),'changing Android font keeps the native family');assert.equal(await page.locator('#vgStatsClose').evaluate(el=>getComputedStyle(el).fontWeight),'700');}
  if(hybrid){
   const encoded=JSON.parse(fs.readFileSync(root+'/tests/fixtures/native-regular-font.json','utf8')).base64;
   await page.evaluate(async encoded=>{
    await window.__vgApplyAppearance({percent:100,systemPercent:100,fontRevision:2,sources:[encoded],faces:[{source:0,weight:'400',style:'normal'},{source:0,weight:'700',style:'normal'}]});
   },encoded);
   await page.waitForFunction(()=>[...document.fonts].filter(f=>f.family.replace(/["']/g,'')==='VgAndroidSystem'&&f.weight==='700').length===0);
   assert(await page.evaluate(()=>{
    function raster(weight){const c=document.createElement('canvas');c.width=640;c.height=80;const x=c.getContext('2d');x.font=weight+' 48px "VgAndroidSystem"';x.fillText('AgMw 0123 Àèìòù',2,58);return x.getImageData(0,0,640,80).data}
    const normal=raster('400'),bold=raster('700');return normal.some((value,i)=>value!==bold[i]);
   }),'the same native font must produce visibly different pixels in regular and bold');
   assert((await page.locator('#vgStatsClose').evaluate(el=>getComputedStyle(el).fontFamily)).startsWith('VgAndroidSystem'));
   await page.evaluate(async encoded=>{
    const bytes=Uint8Array.from(atob(encoded),c=>c.charCodeAt(0));const distinct=new FontFace('VgAndroidSystem',bytes,{weight:'700',style:'normal',sizeAdjust:'120%'});await distinct.load();document.fonts.add(distinct);document.documentElement.style.setProperty('--vg-android-font','VgAndroidSystem, serif');
    await new Promise(resolve=>requestAnimationFrame(()=>requestAnimationFrame(resolve)));
   },encoded);
   assert(await page.evaluate(()=>[...document.fonts].some(f=>f.family.replace(/["']/g,'')==='VgAndroidSystem'&&f.weight==='700')),'a visually distinct bold face must be retained');
  }
  assert(await page.locator('#vgCatalogStats').evaluate(d=>{const r=d.getBoundingClientRect();return r.left>=0&&r.right<=innerWidth&&r.top>=0&&r.bottom<=innerHeight}));
  if(hybrid&&process.env.VG_UI_SCREENSHOTS)await page.screenshot({path:path.join(process.env.VG_UI_SCREENSHOTS,'statistics-'+width+'.png')});
  const requestsBeforeRange=requests;await page.getByRole('button',{name:'30 giorni',exact:true}).click();assert.equal(await page.locator('#vgStatsBars li').count(),30);assert.equal(requests,requestsBeforeRange,'switching chart range reuses already loaded statistics');
  assert(await page.locator('#vgStatsClose').isVisible());await page.locator('#vgStatsClose').click();
  forbidden=true;await page.locator('#topMenuBtn').click();await page.locator('#vgCatalogStatsOpen').click();
  await page.getByText('Statistiche riservate al titolare del catalogo.',{exact:true}).waitFor();assert(await page.locator('#vgStatsResults').isHidden());
  forbidden=false;await page.locator('#vgStatsRetry').click();await page.locator('#vgStatsResults:not([hidden])').waitFor();assert.equal(requests,3,'initial statistics load, rejected access and explicit retry each make one request');
  await page.locator('#vgStatsClose').click();
  await page.locator('#topMenuBtn').click();await page.locator('a[href="vgapp://password"]').click();
  await page.locator('#vgCustomersPanel').waitFor({state:'visible'});await page.locator('#vgCustomerList').getByRole('button',{name:'Autorizza',exact:true}).click();await page.locator('#vgCustomersClose').click();
  await page.locator('#mCliView').evaluate(e=>e.style.display='flex');await page.locator('#mCliView .vgPageHeader').waitFor();const clientPage=await page.locator('#mCliView .modal').boundingBox();assert.equal(clientPage.x,0);assert.equal(clientPage.y,0);assert.equal(Math.round(clientPage.width),width);assert.equal(Math.round(clientPage.height),844);await page.locator('#mCliView').evaluate(e=>e.style.display='none');
  assert.deepEqual(errors,[]);
  console.log('PASS management '+width+'px '+(hybrid?'APK':'PWA')+': original dashboard, sales bars, system bold font, 7/30-day visit columns, zero visits, owner errors, retry and close');
  await context.close();
 }
 }finally{await browser.close()}
})().catch(error=>{console.error(error);process.exitCode=1});
