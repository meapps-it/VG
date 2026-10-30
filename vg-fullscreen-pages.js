(()=>{'use strict';
 if(window.VG_FULLSCREEN_PAGES)return;window.VG_FULLSCREEN_PAGES=true;
 function preparePages(){
  document.querySelectorAll('.modalwrap:not(#mConfirm)>.modal').forEach(page=>{
   if(page.querySelector(':scope>.vgPageHeader'))return;
   const title=page.querySelector(':scope>.h2,:scope>h2');if(!title)return;
   const header=document.createElement('header');header.className='vgPageHeader';
   const back=document.createElement('button');back.type='button';back.className='btn vgPageBack';back.innerHTML='<svg viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="m14 6-6 6 6 6M8 12h12"/></svg>';back.setAttribute('aria-label','Indietro');back.title='Indietro';
   back.onclick=event=>{event.stopPropagation();const cancel=page.querySelector('button[data-action^="close"]');if(cancel)cancel.click();else if(typeof window.hide==='function')window.hide(page.parentElement.id)};
   header.append(back);title.before(header);header.append(title);
  });
 }
 const openDialogs=new Set();
 function dialogs(){
  document.querySelectorAll('dialog.vgStatsDialog').forEach(dialog=>{
   if(dialog.open&&!openDialogs.has(dialog.id)){
    openDialogs.add(dialog.id);history.pushState({...history.state,vgFullPageDialog:dialog.id},'',location.href);
   }else if(!dialog.open&&openDialogs.has(dialog.id)){
    openDialogs.delete(dialog.id);if(history.state?.vgFullPageDialog===dialog.id)history.back();
   }
  });
 }
 const observer=new MutationObserver(()=>{preparePages();dialogs()});observer.observe(document.body,{childList:true,subtree:true,attributes:true,attributeFilter:['open']});
 window.addEventListener('popstate',()=>{for(const id of [...openDialogs]){const dialog=document.getElementById(id);if(dialog?.open&&history.state?.vgFullPageDialog!==id)dialog.close()}});
 window.addEventListener('pagehide',()=>observer.disconnect());
 window.addEventListener('pageshow',()=>{observer.observe(document.body,{childList:true,subtree:true,attributes:true,attributeFilter:['open']});preparePages();dialogs()});
 preparePages();dialogs();
})();
