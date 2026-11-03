const {test}=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),os=require('node:os'),path=require('node:path'),{execFileSync}=require('node:child_process'),{JSDOM}=require('jsdom');
test('public catalogue export includes every local HTML dependency and excludes management/backend files',()=>{
 const root=path.resolve(__dirname,'..'),out=fs.mkdtempSync(path.join(os.tmpdir(),'vg-public-export-'));
 let dom;try{
  execFileSync('python',[path.join(root,'scripts/export-shop.py'),'--destination',out]);
  dom=new JSDOM(fs.readFileSync(path.join(out,'index.html'),'utf8'),{url:'https://example.test/shop/index.html'});
  const refs=[...dom.window.document.querySelectorAll('script[src],link[href],img[src]')].map(n=>n.getAttribute(n.hasAttribute('src')?'src':'href')).filter(ref=>ref&&!/^(?:https?:|data:|#)/.test(ref));
  for(const ref of refs){const file=ref.split('?')[0].replace(/^\.\//,'');assert(fs.existsSync(path.join(out,file)),'missing public dependency: '+ref)}
  for(const file of ['shop-boutique.js','shop-boutique.css','shop-customer.js','shop-variants.js','customer-profile-ui.js','vg-save-feedback.js','shop-parallelo-3d.webp','shop-data/italy.json'])assert(fs.existsSync(path.join(out,file)),file);
  for(const file of ['supabaseClient.js','supabase','.git','service-worker.js'])assert(!fs.existsSync(path.join(out,file)),'must not export management/backend resources: '+file);
 }finally{dom?.window.close();fs.rmSync(out,{recursive:true,force:true})}
});
