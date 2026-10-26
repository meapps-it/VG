(function(){
 'use strict';
 const get=id=>document.getElementById(id),remembered=new Map();
 function info(product){return product.options||{sizes:[],colors:[],fixedSize:'',fixedColor:''}}
 function configure(product,chosen){
  const data=info(product),root=get('productChoices'),previous=chosen||remembered.get(product.id)||{};root.replaceChildren();
  for(const [field,label,options,fixed] of [['size',data.sizeLabel||'Misura',data.sizes||[],data.fixedSize],['color','Colore',data.colors||[],data.fixedColor]]){
   if(options.length){const wrap=document.createElement('label');wrap.textContent=label+' *';const select=document.createElement('select');select.id='productChoice-'+field;select.required=true;select.add(new Option('Scegli '+label.toLowerCase(),''));
    for(const option of options){const text=typeof option==='string'?option:(option.number?option.number+' · ':'')+option.label;select.add(new Option(text,typeof option==='string'?option:option.value))}if(options.some(o=>(typeof o==='string'?o:o.value)===previous[field]))select.value=previous[field];select.onchange=()=>{const v={};for(const f of ['size','color'])if(get('productChoice-'+f))v[f]=get('productChoice-'+f).value;remembered.set(product.id,v)};wrap.append(select);root.append(wrap);
   }else if(fixed){const p=document.createElement('p');p.className='variant-fixed';p.textContent=label+': '+fixed;root.append(p)}
  }
 }
 function selection(product){const data=info(product),selected={};for(const field of ['size','color']){const control=get('productChoice-'+field);if(control){if(!control.reportValidity())return null;selected[field]=control.value}}remembered.set(product.id,selected);return selected}
 function key(id,selected={}){return selected.size||selected.color?id+'::'+encodeURIComponent(selected.size||'')+'::'+encodeURIComponent(selected.color||''):id}
 function parse(key){const parts=String(key).split('::');let size='',color='';try{size=decodeURIComponent(parts[1]||'');color=decodeURIComponent(parts[2]||'')}catch{}return {id:parts[0],...(size?{size}:{}),...(color?{color}:{})}}
 function label(key,product){const value=parse(key),data=info(product);const color=(data.colors||[]).find(c=>c.value===value.color);return [value.size?'Misura: '+value.size:'',color?'Colore: '+(color.number?color.number+' · ':'')+color.label:''].filter(Boolean).join(' · ')}
 function validate(key,product){const entry=parse(key),data=info(product);if(data.sizes?.length&&!data.sizes.includes(entry.size))return 'Scegli la misura per '+product.name;if(data.colors?.length&&!data.colors.some(c=>c.value===entry.color))return 'Scegli il colore per '+product.name;return ''}
 window.shopVariants={configure,selection,key,parse,label,validate};
})();
