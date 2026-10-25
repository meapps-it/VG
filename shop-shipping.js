(function(){
 'use strict';const el=id=>document.getElementById(id),form=el('orderForm'),field=name=>form.elements.namedItem(name);let directory=null,pending=null;
 const normalize=s=>String(s||'').normalize('NFD').replace(/[\u0300-\u036f]/g,'').toLowerCase().trim();
 function updateCities(clear=true){
  const province=field('province').value,list=el('cityOptions');list.replaceChildren();
  if(clear){field('city').value='';field('cap').value='';el('capOptions').replaceChildren()}
  if(directory&&province&&province!=='manual')directory.municipalities.filter(row=>row[1]===province).forEach(row=>{const o=document.createElement('option');o.value=row[0];list.append(o)});
  el('shippingHint').textContent=province==='manual'?'Inserisci comune e CAP manualmente.':'Scegli il comune: suggeriremo il CAP. Per città con più CAP, verifica quello della via.';
 }
 function updateCap(){
  if(!directory)return;const province=field('province').value,city=normalize(field('city').value);
  const row=directory.municipalities.find(r=>r[1]===province&&normalize(r[0])===city);el('capOptions').replaceChildren();if(!row)return;
  row[2].forEach(cap=>{const option=document.createElement('option');option.value=cap;el('capOptions').append(option)});
  if(row[2].length===1)field('cap').value=row[2][0];else if(!row[2].includes(field('cap').value))field('cap').value='';
  el('shippingHint').textContent=row[2].length===1?'CAP suggerito: '+row[2][0]+'. Puoi correggerlo se necessario.':'Questo comune ha più CAP: scegli quello corretto per la via.';
 }
 function restore(){try{const saved=JSON.parse(localStorage.getItem('vg-shop-shipping-v1')||'null');if(!saved)return;for(const name of ['name','phone','email','address','province','city','cap'])if(saved[name]!==undefined)field(name).value=saved[name];field('rememberShipping').checked=true;updateCities(false);updateCap()}catch{}}
 window.shopShipping={
  async load(){if(directory)return;if(pending)return pending;pending=(async()=>{try{const r=await fetch('./shop-data/italy.json');if(!r.ok)throw Error();directory=await r.json();const province=field('province');for(const [code,name] of Object.entries(directory.provinces).sort((a,b)=>a[1].localeCompare(b[1],'it'))){const o=document.createElement('option');o.value=code;o.textContent=name+' ('+code+')';province.append(o)}restore();}catch{el('shippingHint').textContent='Archivio non disponibile: scegli “Inserimento manuale” e compila comune e CAP.'}finally{pending=null}})();return pending;},
  save(){try{if(!field('rememberShipping').checked){localStorage.removeItem('vg-shop-shipping-v1');return}const saved={};for(const name of ['name','phone','email','address','province','city','cap'])saved[name]=field(name).value;localStorage.setItem('vg-shop-shipping-v1',JSON.stringify(saved))}catch{}}
 };
 field('province').addEventListener('change',()=>updateCities());field('city').addEventListener('input',updateCap);
 form.addEventListener('submit',()=>window.shopShipping.save());
})();
