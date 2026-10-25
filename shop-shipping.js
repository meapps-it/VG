(function(){
 'use strict';
 const el=id=>document.getElementById(id),form=el('orderForm'),field=name=>form.elements.namedItem(name);let directory=null,pending=null;
 const normalize=s=>String(s||'').normalize('NFD').replace(/[\u0300-\u036f]/g,'').toLowerCase().trim();
 const manualCity=()=>field('province').value==='manual'||field('city').value==='manual';
 function syncManualCity(){
  const manual=manualCity(),input=el('shippingCityManual'),provinceManual=field('province').value==='manual',provinceInput=el('shippingProvinceManual');
  input.hidden=!manual;input.disabled=!manual;input.required=manual;field('city').required=!provinceManual;
  provinceInput.hidden=!provinceManual;provinceInput.disabled=!provinceManual;provinceInput.required=provinceManual;
 }
 function updateCities(clear=true){
  const province=field('province').value,list=field('city');list.replaceChildren(new Option(province?'Scegli comune':'Scegli prima la provincia',''));
  if(clear){el('shippingCityManual').value='';field('cap').value='';el('capOptions').replaceChildren()}
  if(directory&&province&&province!=='manual')directory.municipalities.filter(row=>row[1]===province).sort((a,b)=>a[0].localeCompare(b[0],'it')).forEach(row=>list.add(new Option(row[0],row[0])));
  if(province)list.add(new Option('Inserisci comune manualmente','manual'));
  if(province==='manual')list.value='manual';
  syncManualCity();el('shippingHint').textContent='Scegli il comune o usa l’inserimento manuale. Verifica il CAP della via.';
 }
 function updateCap(){
  syncManualCity();el('capOptions').replaceChildren();if(!directory||manualCity())return;
  const row=directory.municipalities.find(r=>r[1]===field('province').value&&normalize(r[0])===normalize(field('city').value));if(!row)return;
  row[2].forEach(cap=>el('capOptions').append(new Option(cap,cap)));
  if(row[2].length===1)field('cap').value=row[2][0];else if(!row[2].includes(field('cap').value))field('cap').value='';
  el('shippingHint').textContent=row[2].length===1?'CAP suggerito: '+row[2][0]+'. Puoi correggerlo se necessario.':'Questo comune ha più CAP: verifica quello corretto per la via.';
 }
 function address(){return {province:field('province').value==='manual'?el('shippingProvinceManual').value.trim():field('province').value,city:manualCity()?el('shippingCityManual').value.trim():field('city').value,cap:field('cap').value.trim()}}
 function restore(){try{
  const saved=JSON.parse(localStorage.getItem('vg-shop-shipping-v1')||'null');if(!saved)return;
  for(const name of ['name','phone','address'])if(saved[name]!==undefined)field(name).value=saved[name];
  const known=[...field('province').options].some(o=>o.value===saved.province);field('province').value=known?saved.province:'manual';el('shippingProvinceManual').value=known?'':saved.province||'';updateCities(false);
  if(saved.city){const knownCity=[...field('city').options].some(o=>o.value===saved.city);field('city').value=knownCity?saved.city:'manual';el('shippingCityManual').value=saved.city}syncManualCity();updateCap();if(saved.cap)field('cap').value=saved.cap;field('rememberShipping').checked=true;
 }catch{}}
 window.shopShipping={address,
  async load(){if(directory)return;if(pending)return pending;pending=(async()=>{try{
   const r=await fetch('./shop-data/italy.json',{cache:'no-store'});if(!r.ok)throw Error();directory=await r.json();const province=field('province');
   for(const [code,name] of Object.entries(directory.provinces).sort((a,b)=>a[1].localeCompare(b[1],'it')))province.add(new Option(name+' ('+code+')',code));restore();
  }catch{el('shippingHint').textContent='Elenco comuni non disponibile: usa l’inserimento manuale.'}finally{pending=null}})();return pending},
  save(){try{if(!field('rememberShipping').checked){localStorage.removeItem('vg-shop-shipping-v1');return}const saved={...address()};for(const name of ['name','phone','address'])saved[name]=field(name).value;localStorage.setItem('vg-shop-shipping-v1',JSON.stringify(saved))}catch{}}
 };
 field('province').addEventListener('change',()=>updateCities());field('city').addEventListener('change',updateCap);form.addEventListener('submit',()=>window.shopShipping.save());
})();