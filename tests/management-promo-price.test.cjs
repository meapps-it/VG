const {test}=require('node:test'),assert=require('node:assert/strict'),vm=require('node:vm'),fs=require('node:fs');
const source=fs.readFileSync(__dirname+'/../index.html','utf8');
function fixture(){
 const fields=new Proxy({}, {get:(o,k)=>o[k]||(o[k]={value:'',checked:false,readOnly:false,textContent:'',style:{},dataset:{}})});
 fields.a_cod.value='SKU';fields.a_usd.value='100';fields.a_usd_promo.value='50';
 const c={document:{getElementById:id=>fields[id],activeElement:null},currentArtChosenPrice:145,currentArtPriceManuallySet:true,currentArticleFormQuality:()=> 'STANDARD',loadPricingSettings:()=>({mode:'quality'}),acceptedArticleCostEstimate:()=>null,calcFromUsd:usd=>({costo:usd,sell:usd+45,marg:45,theoretical:usd+45}),normalizeArticlePromoState:a=>a,personalPromoValid:a=>a.promoAttiva&&a.prezzoPromo>0,supplierPromoValid:a=>a.promoFornitoreAttiva&&a.costoUsdPromo>0,promoTypeLabel:a=>a.promoFornitoreAttiva?'Fornitore':'Nessuna',money:n=>String(n),parseColorLabelsInput:()=>[],makeStableColorData:()=>({}),getArticleBrandValue:()=>'',roundDown5:n=>Math.floor(n/5)*5,qualityPricingKey:()=> 'STANDARD'};
 for(const name of ['updateArticleColorModeUI','syncPromoCheckboxesFromAmounts','updateSupplierPromoExpiryWarning','updateSupplierField','renderArticleCostEstimateBox','setArticleSocialPostsAuto','updateArticleCodeStatus','saveArticleDraft'])c[name]=()=>{};
 c.promoValid=a=>c.personalPromoValid(a)||c.supplierPromoValid(a);c.currentPrice=a=>c.personalPromoValid(a)?a.prezzoPromo:c.supplierPromoValid(a)?a.costoUsdPromo+45:a.prezzoVendita;c.currentCost=a=>c.supplierPromoValid(a)?a.costoUsdPromo:a.costoEur;
 vm.createContext(c);vm.runInContext(source.slice(source.indexOf('function parseFlexibleAmount('),source.indexOf('function parseOfficialListPrice('))+source.slice(source.indexOf('function articleBaseSaleInput(){'),source.indexOf('async function fileToDataURL(',source.indexOf('function renderArtAutoFields(){'))),c);return {c,fields};
}
test('supplier promo previews the discounted price and margin while keeping the normal price editable',()=>{const {c,fields}=fixture();c.renderArtAutoFields();assert.equal(fields.a_sell.value,'145');fields.a_supplier_promo_on.checked=true;c.renderArtAutoFields();assert.equal(fields.a_sell.value,'145');assert.match(fields.a_sell_hint.textContent,/95/);assert.equal(fields.a_final.value,'95');assert.equal(fields.a_margin.value,'45');assert.equal(c.currentArtChosenPrice,145);assert.equal(c.articleBaseSaleInput(),145,'draft and save keep the normal price');fields.a_usd_promo.value='40';c.renderArtAutoFields();assert.equal(fields.a_sell.value,'145');assert.equal(fields.a_final.value,'85');fields.a_supplier_promo_on.checked=false;c.renderArtAutoFields();assert.equal(fields.a_sell.value,'145');assert.equal(fields.a_margin.value,'45')});
test('personal promo retains precedence without replacing the normal price state',()=>{const {c,fields}=fixture();fields.a_promo_on.checked=true;fields.a_promo_price.value='80';fields.a_supplier_promo_on.checked=true;c.renderArtAutoFields();assert.equal(fields.a_sell.value,'145');assert.equal(fields.a_final.value,'80');assert.equal(fields.a_margin.value,'30');assert.equal(c.currentArtChosenPrice,145)});

test('clearing costs clears the saved value instead of reviving a stale normal price',()=>{const {c,fields}=fixture();fields.a_usd.value='';fields.a_usd_promo.value='';c.renderArtAutoFields();assert.equal(fields.a_sell.value,'');assert.equal(c.articleBaseSaleInput(),0)});

test('quality pricing keeps the normal price editable and accepts a decimal comma',()=>{
 const {c,fields}=fixture();
 c.renderArtAutoFields();
 assert.equal(fields.a_sell.readOnly,false);
 fields.a_sell.value='240,50';
 assert.equal(c.articleBaseSaleInput(),240.5);
});
