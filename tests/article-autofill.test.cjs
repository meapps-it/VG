const {chromium}=require('playwright'),fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict');
const root=path.resolve(__dirname,'..'),source=fs.readFileSync(root+'/index.html','utf8');
const supplierCode=source.slice(source.indexOf('const SUPPLIER_DEFAULT_QUALITY='),source.indexOf('function sortedQuantile('));
const pasteCode=source.slice(source.indexOf('function normalizeBulkArticleLabel('),source.indexOf('async function openArtEdit('));
const brandCode=source.slice(source.indexOf('function syncBrandField('),source.indexOf('function handleBrandSelectionChange('));
let html=source.replace(/<script\b[^>]*>[\s\S]*?<\/script>/gi,'').replace(/<body[^>]*>[\s\S]*?(?=<div class="top">)/,'<body>');
const fixtures=`
function normalizeSpaceText(s){return String(s||'').trim().replace(/\\s+/g,' ')}
let currentArticleCostEstimate=null;window.draftCount=0;window.messages=[];
function toast(s){messages.push(s)}function show(id){document.getElementById(id).style.display='flex'}function hide(id){document.getElementById(id).style.display='none'}
function getBrandPresetList(){return ['Prada','Gucci']}function renderArtAutoFields(){}function saveArticleDraft(){draftCount++}
function refreshArticleCategorySelects(value){const el=document.getElementById('a_cat');if(![...el.options].some(o=>o.value===value))el.add(new Option(value,value));el.value=value}
function parseOfficialListPrice(value){return parseBulkArticleNumber(value)}
function updateArticleColorModeUI(){}function updateArticleMeasureState(){}function updateArticleCodeStatus(){}function renderArticleCostEstimateBox(){}
function parseColorLabelsInput(raw){return String(raw||'').split(/\\n/).filter(Boolean)}function formatColorLabelsInput(values){return values.join('\\n')}
${supplierCode}
${brandCode}
${pasteCode}
document.getElementById('a_brand').innerHTML='<option value=""></option><option>Prada</option><option>Gucci</option><option value="__ALTRO__">Altro</option>';
document.addEventListener('click',event=>{const action=event.target.closest('[data-action]')?.dataset.action;if(action==='openBulkPasteArticle')openBulkPasteArticle();if(action==='applyBulkPasteArticle')applyBulkPasteArticle();if(action==='closeBulkPasteArticle')hide('mArtBulkPaste')});
`;
html=html.replace('</body>','<script>'+fixtures+'</script></body>');
(async()=>{const browser=await chromium.launch({headless:true,args:['--no-sandbox']});try{
for(const width of [360,412]){
const context=await browser.newContext({viewport:{width,height:844},isMobile:true,hasTouch:true});const page=await context.newPage();const errors=[];page.on('pageerror',e=>errors.push(e.message));
await page.route('**/*',route=>route.request().resourceType()==='document'?route.fulfill({contentType:'text/html',body:html}):route.abort());
await page.goto('https://meapps-it.github.io/VG/');await page.evaluate(()=>{document.getElementById('mArtEdit').style.display='flex';document.getElementById('a_desc').value='Descrizione privata da mantenere';document.getElementById('a_note').value='Nota riservata';});
assert(await page.locator('#a_qual').isVisible(),'quality field visible in article form');
await page.locator('[data-action=openBulkPasteArticle]').click();assert(await page.locator('#mArtBulkPaste').isVisible(),'paste tool opens inside the article form');
await page.locator('#a_bulk_paste').fill('CODICE: AUTO-001\nBRAND: Prada\nMODELLO: Borsa prova\nCATEGORIA: Borse\nFORNITORE: Jayden\nPREZZO FORNITORE USD: 100,50\nMATERIALE: Pelle\nMISURE: 20 x 10\nCOLORE: Nero\nDESCRIZIONE: non importare\nNOTE: non importare\nUn commento senza etichetta non deve sporcare i campi');
await page.locator('[data-action=applyBulkPasteArticle]').click();
for(const [id,value] of Object.entries({a_cod:'AUTO-001',a_brand:'Prada',a_mod:'Borsa prova',a_cat:'Borse',a_forn:'Jayden',a_qual:'ORIGINALE',a_usd:'100.50',a_taglia:'Pelle',a_mis:'20 x 10',a_colore:'Nero',a_desc:'Descrizione privata da mantenere',a_note:'Nota riservata'}))assert.equal(await page.locator('#'+id).inputValue(),value,id);
assert.equal(await page.locator('#a_qual').evaluate(e=>e.disabled),true);assert(!(await page.locator('#mArtBulkPaste').isVisible()));assert.equal(await page.evaluate(()=>draftCount),1);
await page.locator('[data-action=openBulkPasteArticle]').click();await page.locator('#a_bulk_paste').fill('CODICE: AUTO-002\nLINK: https://szwego.com/static/index.html#a201803280815415730062008\nPREZZO FORNITORE USD: 80');
await page.evaluate(()=>{currentSupplierManualOverride=false;document.getElementById('a_forn').value='';});
await page.locator('[data-action=applyBulkPasteArticle]').click();assert.equal(await page.locator('#a_forn').inputValue(),'Daniel');assert.equal(await page.locator('#a_qual').inputValue(),'STANDARD');
await page.locator('[data-action=openBulkPasteArticle]').click();await page.locator('#a_bulk_paste').fill('Riga senza dati riconoscibili');await page.locator('[data-action=applyBulkPasteArticle]').click();assert.equal(await page.locator('#a_cod').inputValue(),'AUTO-002');
assert.deepEqual(errors,[]);await context.close();console.log('PASS article autofill '+width+'px: tool visible, structured paste populates actual fields, supplier/quality recognition, decimal cost, notes/description preserved, draft only');
}
}finally{await browser.close()}})().catch(e=>{console.error(e);process.exitCode=1});
