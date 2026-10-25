(function(){
 'use strict';
 const phone='393351604686',catalog='https://simplexapp.it/shop/';
 function message(kind){
  if(kind==='product'){
   const name=document.getElementById('productTitle')?.textContent?.trim()||'questo articolo';
   const sku=document.getElementById('productSku')?.textContent?.trim();
   return 'Ciao, vorrei informazioni su '+name+(sku?' ('+sku+')':'')+'.\n'+catalog;
  }
  if(kind==='cart')return 'Ciao, avrei bisogno di aiuto con una richiesta dal catalogo Shop & Parallelo.\n'+catalog;
  return 'Ciao, vorrei informazioni sul catalogo Shop & Parallelo.\n'+catalog;
 }
 function update(link){link.href='https://wa.me/'+phone+'?text='+encodeURIComponent(message(link.dataset.whatsapp));}
 document.querySelectorAll('[data-whatsapp]').forEach(link=>{
  update(link);link.addEventListener('click',()=>update(link));link.addEventListener('focus',()=>update(link));
 });
})();
