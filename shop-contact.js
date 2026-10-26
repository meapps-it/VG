(function(){
 'use strict';
 const phone='393351604686';
 function message(kind){
  if(kind==='product'){
   const name=document.getElementById('productTitle')?.textContent?.trim()||'questo articolo';
   const sku=document.getElementById('productSku')?.textContent?.trim();
   return 'Ciao, vorrei informazioni su '+name+(sku?' ('+sku+')':'')+'.';
  }
  if(kind==='cart')return 'Ciao, avrei bisogno di aiuto con una richiesta dal catalogo Shop & Parallelo.';
  return 'Ciao, vorrei informazioni sul catalogo Shop & Parallelo.';
 }
 function update(link){link.href='https://wa.me/'+phone+'?text='+encodeURIComponent(message(link.dataset.whatsapp));}
 document.querySelectorAll('[data-whatsapp]').forEach(link=>{
  update(link);link.addEventListener('click',()=>update(link));link.addEventListener('focus',()=>update(link));
 });
})();

