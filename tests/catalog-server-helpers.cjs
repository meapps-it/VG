const fs=require('node:fs'),vm=require('node:vm'),{stripTypeScriptTypes}=require('node:module');
const context={};vm.runInNewContext(stripTypeScriptTypes(fs.readFileSync(__dirname+'/../supabase/functions/vg-shop-catalog/shop-variants.ts','utf8').replace(/^export /gm,'')),context);
module.exports={articleMetadata:context.articleMetadata,decodeArticleMeta:context.decodeArticleMeta,articleOptions:context.articleOptions,requestedVariant:context.requestedVariant,customerAction:async()=>null,customerSession:async()=>null,shippingProfile:v=>v};
