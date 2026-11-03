const {test}=require('node:test'),assert=require('node:assert/strict'),{JSDOM}=require('jsdom'),fs=require('node:fs');
const tick=()=>new Promise(r=>setTimeout(r,0));
test('clock/text mutations do not rescan admin pages or customer cards; new dialogs still work',async()=>{
 const d=new JSDOM('<div id="clock"></div><div class="modalwrap"><div class="modal"><h2>Cliente</h2><button data-action="closeCli">Annulla</button></div></div><div id="vgCustomerList"><article data-customer="one"></article></div>',{url:'https://example.test',runScripts:'outside-only',pretendToBeVisual:true}),w=d.window;
 try{
  w.eval(fs.readFileSync(__dirname+'/../vg-fullscreen-pages.js','utf8'));
  w.eval(fs.readFileSync(__dirname+'/../customer-profile-ui.js','utf8'));await tick();
  let scans=0;const query=w.document.querySelectorAll.bind(w.document);
  w.document.querySelectorAll=selector=>{if(selector.includes('.modalwrap')||selector==='dialog.vgStatsDialog'||selector.includes('#vgCustomerList'))scans++;return query(selector)};
  for(let i=0;i<100;i++)w.document.getElementById('clock').textContent=String(i);await tick();
  assert.equal(scans,0,'text-only updates must not rescan page structures');
  const dialog=w.document.createElement('div');dialog.className='modalwrap';dialog.innerHTML='<div class="modal"><h2>Nuova pagina</h2><button data-action="closeNew">Annulla</button></div>';w.document.body.append(dialog);await tick();
  assert(dialog.querySelector('.vgPageHeader h2'));assert(scans>0);
  const article=w.document.createElement('article');article.dataset.customer='two';w.document.getElementById('vgCustomerList').append(article);await tick();assert(article.querySelector('button'),'dossier actions are attached to newly added customer cards');
 }finally{w.dispatchEvent(new w.Event('pagehide'));w.close()}
});
test('customer badge polling does not download hidden integration dossiers or reset an open form',async()=>{
 const d=new JSDOM('<div id="sec_home"><div class="heroSpot"></div><section id="vgDashboardAlerts"></section><div id="catalogCustomersStat"><b id="c_catalog_customers"></b><span id="c_catalog_last_access"></span></div></div><button id="vgCatalogStatsOpen"></button>',{url:'https://example.test',runScripts:'outside-only',pretendToBeVisual:true}),w=d.window,calls=[];
 w.setInterval=()=>0;w.HTMLDialogElement.prototype.showModal=function(){this.open=true};w.HTMLDialogElement.prototype.close=function(){this.open=false};
 w.VG_SUPABASE_READY=Promise.resolve({auth:{getSession:async()=>({data:{session:{access_token:'fixture'}}})}});
 w.fetch=async url=>{const action=new URL(url).searchParams.get('action');calls.push(action);return {ok:true,json:async()=>action==='customer-client-links'?{accounts:[],clients:[]}:{total:1,pending:0,unread:0,customers:[],notifications:[],hasMore:false,offset:0}}};
 try{
  w.eval(fs.readFileSync(__dirname+'/../vg-customer-panel.js','utf8'));await tick();await tick();
  assert.equal(calls.filter(a=>a==='customer-client-links').length,0);
  await w.VG_CUSTOMERS.refresh();assert.equal(calls.filter(a=>a==='customer-client-links').length,0);
  w.VG_CUSTOMERS.open();await tick();await tick();assert.equal(calls.filter(a=>a==='customer-client-links').length,1);
  const root=w.document.getElementById('vgClientIntegrationList'),node=root.firstChild;
  await w.VG_CUSTOMERS.refresh();assert.equal(root.firstChild,node);assert.equal(calls.filter(a=>a==='customer-client-links').length,1);
 }finally{w.close()}
});
test('unchanged order alerts reuse the archive, pause while typing and isolate dismissal actions',()=>{
 const d=new JSDOM('<section id="vgDashboardAlerts"></section><input id="editing">',{url:'https://example.test',runScripts:'outside-only',pretendToBeVisual:true}),w=d.window;
 let reads=0,interval;w.KEY='fixture';w.cloudSession={user:{id:'one'}};
 const orders=[{id:'order',numeroOrdine:'SP1',note:'Richiesta da catalogo'}];w.localStorage.setItem('fixture',JSON.stringify({ordini:orders}));
 w.loadDB=()=>{reads++;return {ordini:orders}};w.setInterval=fn=>{interval=fn};
 try{
  w.eval(fs.readFileSync(__dirname+'/../vg-dashboard-alerts.js','utf8'));assert.equal(reads,1);
  for(let i=0;i<10;i++)interval();assert.equal(reads,1,'unchanged archive is not parsed repeatedly');
  w.document.getElementById('editing').focus();w.localStorage.setItem('fixture','changed');interval();assert.equal(reads,1,'typing is not interrupted by archive parsing');
  w.document.getElementById('editing').blur();interval();assert.equal(reads,2);
  w.cloudSession.user.id='two';w.VG_DASHBOARD_ALERTS.refresh();w.document.querySelector('.vgOrderDismiss').click();
  assert.equal(w.localStorage.getItem('vg_dismissed_order_alerts_v1:one'),null);assert.deepEqual(JSON.parse(w.localStorage.getItem('vg_dismissed_order_alerts_v1:two')),['order']);
 }finally{w.close()}
});
