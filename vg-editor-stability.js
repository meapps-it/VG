/* Preserve the user's text, selection and IME composition during automatic saves. */
(()=>{'use strict';
 if(window.VG_EDITOR_STABILITY)return;window.VG_EDITOR_STABILITY=true;
 const composing=new WeakSet(),cloudSaves=new Map();
 const editing=()=>{const el=document.activeElement;return el&&((el.matches('input:not([readonly]):not([type=checkbox]):not([type=radio]):not([type=button]):not([type=submit]),textarea:not([readonly])'))||el.isContentEditable)};
 document.addEventListener('compositionstart',e=>composing.add(e.target),true);
 document.addEventListener('compositionend',e=>{composing.delete(e.target);if(e.target.id==='vArtPostFb')window.queueViewFbPostSave?.()},true);
 window.fitTextarea=function(el){
  if(!el)return;const style=getComputedStyle(el),border=(parseFloat(style.borderTopWidth)||0)+(parseFloat(style.borderBottomWidth)||0),padding=(parseFloat(style.paddingTop)||0)+(parseFloat(style.paddingBottom)||0);
  const padded=Math.ceil(Math.max(String(el.value||el.placeholder||'').split('\n').length,4)*(parseFloat(style.lineHeight)||22)+padding+border+18);
  const focused=document.activeElement===el,current=el.getBoundingClientRect().height,scroll=el.scrollTop;
  // Shrinking an active textarea to "auto" makes Android scroll the caret twice.
  if(!focused)el.style.height='auto';
  const height=Math.max(el.scrollHeight+border,padded,140,focused?current:0);
  if(Math.abs((parseFloat(el.style.height)||0)-height)>1)el.style.height=height+'px';
  if(!focused)el.scrollTop=scroll;
 };
 document.addEventListener('focusout',e=>{if(e.target.matches?.('textarea'))setTimeout(()=>window.fitTextarea(e.target),0)},true);
 window.saveViewFbPostNow=function(opts={}){
  const el=document.getElementById('vArtPostFb'),artId=el?.dataset.articleId||currentArtId;if(!artId||!el||composing.has(el)&&!opts.force)return false;
  const db=loadDB(),i=(db.articoli||[]).findIndex(x=>x.id===artId);if(i<0)return false;
  const raw=String(el.value||''),focused=document.activeElement===el;
  const text=raw;
  // An autosave must never assign value on the focused editor: that resets the caret.
  if(!focused&&el.value!==text)el.value=text;
  window.fitTextarea(el);db.articoli[i]={...db.articoli[i],postFb:text,postFacebook:text,_ts:Date.now()};
  if(!saveDBLocal(db)){setViewFbSaveMeta('Salvataggio non riuscito sul dispositivo.');return false}
  setViewFbSaveMeta('Salvato sul dispositivo');
  if(opts.cloud&&cloudEnabled()){
   setViewFbSaveMeta('Sincronizzazione…');
   const previous=cloudSaves.get(artId)||Promise.resolve();
   const task=previous.catch(()=>{}).then(async()=>{
    const latest=loadDB(),article=latest.articoli?.find(a=>a.id===artId);
    if(!article||String(article.postFb||'')!==text)return;
    const saved=await cloudSaveOne('art',{...article},latest),fresh=loadDB(),j=fresh.articoli?.findIndex(a=>a.id===artId);
    if(j>=0&&String(fresh.articoli[j].postFb||'')===text){fresh.articoli[j]={...fresh.articoli[j],...saved,postFb:text,postFacebook:text,_ts:Date.now()};if(!saveDBLocal(fresh))throw Error('Salvataggio locale non riuscito')}
    if(currentArtId===artId&&el.value===text)setViewFbSaveMeta('Salvato e sincronizzato');return {ok:true,message:'Descrizione salvata e sincronizzata.'};
   }).catch(err=>{if(currentArtId===artId&&el.value===text)setViewFbSaveMeta('Salvato sul dispositivo; cloud non aggiornato.');console.warn('Sync descrizione non riuscita',err);return {ok:false,message:'Descrizione salvata sul dispositivo, ma cloud non aggiornato. Riprova.'}});
   cloudSaves.set(artId,task);task.finally(()=>{if(cloudSaves.get(artId)===task)cloudSaves.delete(artId)});
  }
  return true;
 };

 // Manual descriptions must reopen exactly as saved, without reinserting deleted lines.
 const generatedPost=window.getArticleFacebookPost;
 if(generatedPost)window.getArticleFacebookPost=function(article){
  const key=['postFb','postFacebook','post_fb'].find(k=>typeof article?.[k]==='string'&&article[k].length);
  return key?article[key]:generatedPost(article);
 };
 const editor=document.getElementById('vArtPostFb');let changed=false;
 const open=window.openArtView,hide=window.hide;
 function flush(){if(!changed||!editor)return true;return window.saveViewFbPostNow({cloud:true,force:true})}
 if(open)window.openArtView=function(id,...args){
  if(editor&&changed&&!flush())return;
  if(typeof __viewFbPostSaveTimer!=='undefined')clearTimeout(__viewFbPostSaveTimer);
  if(typeof __viewFbPostCloudTimer!=='undefined')clearTimeout(__viewFbPostCloudTimer);
  changed=false;const result=open.call(this,id,...args);if(editor)editor.dataset.articleId=String(id);return result;
 };
 if(hide)window.hide=function(id,...args){if(id==='mArtView'&&!flush()){window.toast?.('Descrizione non salvata: riprova prima di uscire.');return}return hide.call(this,id,...args)};
 if(editor){
  editor.addEventListener('input',()=>{changed=true;window.saveViewFbPostNow({cloud:false})},true);
  const button=document.createElement('button');button.type='button';button.className='btn primary';button.textContent='Salva descrizione';button.id='vgSaveDescription';editor.after(button);
  button.onclick=async()=>{
   const task=async()=>{if(!window.saveViewFbPostNow({cloud:true,force:true}))return {ok:false,message:'Descrizione non salvata. Riprova.'};const id=editor.dataset.articleId||currentArtId,result=await cloudSaves.get(id);return result||{ok:true,message:'Descrizione salvata sul dispositivo.'}};
   if(window.vgSaveFeedback)return window.vgSaveFeedback.run(button,task);
   button.disabled=true;try{const result=await task();setViewFbSaveMeta(result.message)}finally{button.disabled=false}
  };
 }
 window.addEventListener('popstate',flush,true);
 window.addEventListener('pagehide',()=>{if(changed)window.saveViewFbPostNow({cloud:true,force:true})},true);
 document.addEventListener('visibilitychange',()=>{if(document.hidden&&changed)window.saveViewFbPostNow({cloud:true,force:true})});

 const pull=window.autoCloudPullNow,render=window.renderAll;let deferredRender=false;
 if(pull)window.autoCloudPullNow=function(...args){if(editing()||cloudSaves.size)return;return pull.apply(this,args)};
 if(render)window.renderAll=function(...args){if(editing()){deferredRender=true;return}return render.apply(this,args)};
 document.addEventListener('focusout',()=>setTimeout(()=>{if(deferredRender&&!editing()){deferredRender=false;render?.()}},0),true);
})();
