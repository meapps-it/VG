const {test}=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),{JSDOM}=require('jsdom');
const {articlePromotionDetails}=require('./catalog-server-helpers.cjs');
const article=m=>({descrizione:'[VGMETA]'+JSON.stringify(m)+'[/VGMETA]'});
test('public promo expiry follows the active price priority without supplier cost metadata',()=>{
 let p=article({promoAttiva:true,prezzoPromo:90,scadenzaPromoPersonale:'2026-11-11',costoUsdPromo:50,scadenzaPromoFornitore:'2026-11-20'});
 let info=articlePromotionDetails(p,'2026-11-04');assert.equal(info.active,true);assert.equal(info.endsAt,'2026-11-11');assert.deepEqual(Object.keys(info).sort(),['active','endsAt']);
 info=articlePromotionDetails(p,'2026-11-12');assert.equal(info.endsAt,'2026-11-20');assert.equal(articlePromotionDetails(p,'2026-11-21').active,false);
 info=articlePromotionDetails(article({promoAttiva:true,prezzoPromo:90,costoUsdPromo:50,scadenzaPromoFornitore:'2026-11-20'}),'2026-11-04');assert.equal(info.endsAt,'','an undated personal promo must not inherit a supplier deadline');
 assert.equal(articlePromotionDetails(article({costoUsdPromo:30,promoFornitoreAttiva:false}),'2026-11-04').active,false);
});
test('detail badge displays the inclusive deadline, hides expired/normal articles and resets stale dates',()=>{
 const d=new JSDOM('<div id="productPromoBadge" hidden><span id="productPromoEnd"></span></div>',{runScripts:'outside-only'}),w=d.window;const html=fs.readFileSync(__dirname+'/../catalogo-nuovo.html','utf8');w.el=id=>w.document.getElementById(id);w.eval(html.slice(html.indexOf('function renderProductPromo('),html.indexOf('function showProduct(')));
 try{w.renderProductPromo({promotionActive:true,promotionEndsAt:'2026-11-11'},'2026-11-11');assert(!w.el('productPromoBadge').hidden);assert.match(w.el('productPromoEnd').textContent,/11\/11\/2026 compreso/);w.renderProductPromo({promotionActive:true,promotionEndsAt:'2026-11-11'},'2026-11-12');assert(w.el('productPromoBadge').hidden);w.renderProductPromo({promotionActive:true},'2026-11-04');assert.equal(w.el('productPromoEnd').textContent,'Scadenza non comunicata');w.renderProductPromo({promotionActive:false,promotion:true},'2026-11-04');assert(w.el('productPromoBadge').hidden);assert.equal(w.el('productPromoEnd').textContent,'');}finally{w.close()}
});
