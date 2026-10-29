const {test}=require('node:test'),assert=require('node:assert/strict'),{JSDOM}=require('jsdom'),fs=require('node:fs');
test('order alerts survive opening and reload, explicit dismissal preserves order, accounts remain isolated',()=>{
 const d=new JSDOM('<section id="vgDashboardAlerts"></section>',{url:'https://example.com',runScripts:'outside-only'}),w=d.window;
 const orders=[{id:'o1',numeroOrdine:'SP1',note:'Richiesta da catalogo Shop & Parallelo.'}];let opened;
 w.cloudSession={user:{id:'a'}};w.loadDB=()=>({ordini:orders});w.openOrdView=id=>opened=id;w.setInterval=()=>0;
 const source=fs.readFileSync(__dirname+'/../vg-dashboard-alerts.js','utf8');w.eval(source);
 assert.equal(w.document.querySelectorAll('.vgOrderAlert').length,1);w.document.querySelector('.vgOrderOpen').click();assert.equal(opened,'o1');assert.equal(w.document.querySelectorAll('.vgOrderAlert').length,1);
 w.document.querySelector('.vgOrderDismiss').click();assert.equal(w.document.querySelectorAll('.vgOrderAlert').length,0);assert.equal(orders.length,1);
 w.VG_DASHBOARD_ALERTS.refresh();assert.equal(w.document.querySelectorAll('.vgOrderAlert').length,0);
 w.cloudSession.user.id='b';w.VG_DASHBOARD_ALERTS.refresh();assert.equal(w.document.querySelectorAll('.vgOrderAlert').length,1);
 w.cloudSession=null;w.VG_DASHBOARD_ALERTS.refresh();assert.equal(w.document.querySelectorAll('.vgOrderAlert').length,0);d.window.close();
});
