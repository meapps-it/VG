(function(){
 'use strict';
 const get=id=>document.getElementById(id),remembered=new Map();
 const fields=['size','color','length'];
 function info(product){return product.options||{sizes:[],colors:[],fixedSize:'',fixedColor:''}}
 function configure(product,chosen){
  const data=info(product),root=get('productChoices'),previous=chosen||remembered.get(product.id)||{};root.replaceChildren();
  for(const [field,label,options,fixed] of [['size',data.lengths?.length ? 'H (largh.)' : data.sizeLabel||'Misura',data.sizes||[],data.fixedSize],['color','Colore',data.colors||[],data.fixedColor],['length','Lunghezza (cm)',data.lengths||[]]]){
   if(options.length){const wrap=document.createElement('label');wrap.textContent=label+' *';const select=document.createElement('select');select.id='productChoice-'+field;select.required=true;select.add(new Option('Scegli '+label.toLowerCase(),''));
    for(const option of options){const text=typeof option==='string'?option:(option.number?option.number+' · ':'')+option.label;select.add(new Option(text,typeof option==='string'?option:option.value))}if(options.some(o=>(typeof o==='string'?o:o.value)===previous[field]))select.value=previous[field];select.onchange=()=>{const v={};for(const f of fields)if(get('productChoice-'+f))v[f]=get('productChoice-'+f).value;remembered.set(product.id,v)};wrap.append(select);root.append(wrap);
   }else if(fixed){const p=document.createElement('p');p.className='variant-fixed';p.textContent=label+': '+fixed;root.append(p)}
  }
 }
 function selection(product){const data=info(product),selected={};for(const field of fields){const control=get('productChoice-'+field);if(control){if(!control.reportValidity())return null;selected[field]=control.value}}remembered.set(product.id,selected);return selected}
 function key(id,selected={}){
  if(!fields.some(field=>selected[field]))return id;
  const parts=[id,...fields.map(field=>encodeURIComponent(selected[field]||''))];
  if(!selected.length)parts.pop();
  return parts.join('::');
 }
 function parse(key){
  const [id,...parts]=String(key).split('::'),value={id};
  fields.forEach((field,index)=>{try{if(parts[index])value[field]=decodeURIComponent(parts[index])}catch{}});
  return value;
 }
 function label(key,product){const value=parse(key),data=info(product);const color=(data.colors||[]).find(c=>c.value===value.color);return [value.size?(data.lengths?.length?'H (largh.): ':'Misura: ')+value.size:'',color?'Colore: '+(color.number?color.number+' · ':'')+color.label:'',value.length?'Lunghezza: '+value.length+' cm':''].filter(Boolean).join(' · ')}
 function validate(key,product){const entry=parse(key),data=info(product);if(data.lengths?.length&&!data.lengths.includes(entry.length))return 'Scegli la lunghezza per '+product.name;if(data.sizes?.length&&!data.sizes.includes(entry.size))return 'Scegli la misura per '+product.name;if(data.colors?.length&&!data.colors.some(c=>c.value===entry.color))return 'Scegli il colore per '+product.name;return ''}
 window.shopVariants={configure,selection,key,parse,label,validate};
})();
