const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');

const source = fs.readFileSync(process.env.VG_TEST_HTML || __dirname + '/../index.html', 'utf8');
const articles = [
  {id:'jayden', fornitore:' Jayden ', qualita:'STANDARD', promo:true},
  {id:'daniel', fornitore:'Daniel', qualita:'ORIGINALE', promo:true},
  {id:'jessica', fornitore:'Jessica', promo:false},
  {id:'legacy-link', fornitoreLink:'https://a2018031622450427991.szwego.com/', brand:'Chanel', promo:true},
  {id:'legacy-original', qualità:'Original', categoria:'Scarpe', promo:true},
  {id:'legacy-standard', qualita:'Top Quality'},
  {id:'unknown'},
  {id:'archive', fornitore:'Jayden', nonDisponibile:true},
].map(article => ({modello:'Demo', categoria:'Borse', brand:'Gucci', ...article}));

const fields = Object.fromEntries(['qArt','qArtCat','qArtBrand','qArtQuality'].map(id => [
  id, {value:'', addEventListener(event, fn) { this[event] = fn; }},
]));
const context = vm.createContext({
  URL, document:{getElementById:id => fields[id]}, loadDB:() => ({articoli:articles}),
  promoValid:a => !!a.promo, updateArtPromoFilterButton(){}, refreshArticlePreviewFilters(){},
  articlePhotoCountSync:() => 1, compareArticlesForPreview:() => 0,
  currentHistoryPage:() => null, result:[],
});
const names = [
  'normalizeSpaceText','normalizeSupplierValue','detectSupplierFromUrl','getDefaultQualityFromSupplier',
  'qualityPricingKey','getEffectiveArticleSupplier','getEffectiveArticleQuality','normalizeQualityValue',
  'articleQualityLabel','isArticleUnavailable','normalizeCategoryValue','displayCategoryValue',
  'normalizeBrandValue','displayBrandValue','articleSearchHay','setArtBrowse',
];
const declarations = names.flatMap(name => {
  const matches = [...source.matchAll(new RegExp('^function '+name+'\\([^\\n]*', 'gm'))];
  return matches.map(match => {
    if (match[0].trimEnd().endsWith('}')) return match[0];
    return source.slice(match.index, source.indexOf('\n}', match.index) + 2);
  });
});
const renderStart = source.indexOf('function renderArt(){');
const filter = source.slice(renderStart, source.indexOf("  const el=document.getElementById('listArt');", renderStart));
const listeners = source.split('\n').filter(line => /^document.getElementById\('qArt(Cat|Brand|Quality)'\)\?\.addEventListener/.test(line));
vm.runInContext([
  ...source.split('\n').filter(line => /^const (SUPPLIER_PRESETS|artBrowseState)=/.test(line)),
  ...declarations, filter + ' result=items.map(a=>a.id); }', ...listeners,
].join('\n'), context);

function expectQuality(value, ids) {
  fields.qArtQuality.value = value;
  fields.qArtQuality.change();
  assert.deepEqual(Array.from(context.result), ids);
}

expectQuality('', ['jayden','daniel','jessica','legacy-link','legacy-original','legacy-standard','unknown']);
expectQuality('Originale', ['jayden','legacy-link','legacy-original']);
expectQuality('Standard', ['daniel','jessica','legacy-standard']);

vm.runInContext("setArtBrowse('promo')", context);
fields.qArt.value = 'demo';
fields.qArtCat.value = 'Borse';
fields.qArtCat.change();
fields.qArtBrand.value = 'Gucci';
fields.qArtBrand.change();
expectQuality('Originale', ['jayden']);
expectQuality('Standard', ['daniel']);
fields.qArt.value = 'assente';
expectQuality('Standard', []);
