(()=>{'use strict';
const KEY='vg_facebook_posts_v1',OWNER='a51e2715-3b5e-4ad3-906e-c664fcdd486d';
const ready=()=>{const menu=document.querySelector('.topMenuGrid');if(!menu||document.getElementById('vgPostsOpen'))return;
const trigger=document.createElement('button');trigger.type='button';trigger.className='topMenuLink';trigger.id='vgPostsOpen';trigger.textContent='👜 Post Facebook · Storie e stile';menu.append(trigger);
const dialog=document.createElement('dialog');dialog.id='vgPostsPanel';dialog.className='vgStatsDialog vgCustomersPanel';dialog.setAttribute('aria-labelledby','vgPostsHeading');
dialog.innerHTML=`<header class="vgStatsHeader"><h2 id="vgPostsHeading">👜 Post Facebook</h2><button type="button" id="vgPostsClose">Chiudi</button></header><div class="vgStatsContent vgPostsContent">
<p>Prepara curiosità, storia, dettagli e consigli di stile sulle borse. I post restano salvati su questo dispositivo. La data programmata è un promemoria, non una pubblicazione automatica.</p>
<form id="vgPostsForm">
<input name="id" type="hidden">
<label>Argomento<select name="category"><option>Curiosità</option><option>Storia delle borse</option><option>Dettagli iconici</option><option>Consigli di stile</option><option>Materiali e lavorazioni</option></select></label>
<label>Titolo<input name="title" required maxlength="120" placeholder="Es. La storia della borsa a tracolla"></label>
<label>Testo del post<textarea name="body" required rows="7" maxlength="6000" placeholder="Racconta una curiosità, un dettaglio storico o un consiglio di stile…"></textarea></label>
<label>Link da condividere (facoltativo)<input name="link" type="url" placeholder="https://veglamour.cloud/"></label>
<div class="vgPostsTwo"><label>Data prevista<input name="date" type="date" required></label><label>Ora prevista<input name="time" type="time" required></label></div>
<div class="vgPostsActions"><button type="submit">💾 Salva post</button><button type="button" data-reset>Nuovo post</button></div>
</form><p id="vgPostsStatus" role="status" aria-live="polite"></p>
<h3>Post salvati</h3><div id="vgPostsList"></div></div>`;
document.body.append(dialog);
const css=document.createElement('style');css.textContent=`#vgPostsPanel{max-width:min(720px,96vw);width:100%;max-height:92vh;color:#172b4d}#vgPostsPanel .vgPostsContent{overflow:auto;max-height:calc(92vh - 90px);padding:18px}#vgPostsForm{display:grid;gap:14px}#vgPostsForm label{display:grid;gap:6px;font-weight:700}#vgPostsForm input:not([type=hidden]),#vgPostsForm textarea,#vgPostsForm select{box-sizing:border-box;width:100%;min-width:0;padding:12px;border:1px solid #b8c9da;border-radius:12px;background:#fff;color:#172b4d;font:inherit}#vgPostsForm textarea{resize:vertical}#vgPostsPanel .vgPostsTwo{display:grid;grid-template-columns:1fr 1fr;gap:12px}#vgPostsPanel .vgPostsActions,#vgPostsPanel .vgPostButtons{display:flex;flex-wrap:wrap;gap:8px}#vgPostsPanel button{cursor:pointer}#vgPostsPanel .vgPostsActions button,#vgPostsPanel .vgPostButtons button{padding:10px 12px;border:0;border-radius:10px;background:#244c97;color:#fff;font-weight:700}#vgPostsPanel button[data-reset],#vgPostsPanel button[data-edit]{background:#e6edf9;color:#19385e}#vgPostsPanel button[data-remove]{background:#ffe7e9;color:#9f2030}#vgPostsPanel .vgPostItem{border:1px solid #d4dfed;border-radius:16px;padding:14px;margin:12px 0;background:#fff}#vgPostsPanel .vgPostItem h4{margin:0 0 7px;font-size:17px}#vgPostsPanel .vgPostItem p{white-space:pre-wrap;overflow-wrap:anywhere;margin:8px 0}#vgPostsPanel .vgPostMeta{font-size:13px;color:#556579}#vgPostsPanel #vgPostsStatus{min-height:20px;white-space:pre-wrap}#vgPostsPanel [hidden]{display:none!important}`;document.head.append(css);
const form=dialog.querySelector('form'),list=dialog.querySelector('#vgPostsList'),status=dialog.querySelector('#vgPostsStatus');
const el=name=>form.elements.namedItem(name);
const pad=v=>String(v).padStart(2,'0');
const today=()=>{const d=new Date();return d.getFullYear()+'-'+pad(d.getMonth()+1)+'-'+pad(d.getDate())};
const now=()=>{const d=new Date();return pad(d.getHours())+':'+pad(d.getMinutes())};
const read=()=>{try{const x=JSON.parse(localStorage.getItem(KEY)||'[]');return Array.isArray(x)?x:[]}catch{return []}};
const write=posts=>localStorage.setItem(KEY,JSON.stringify(posts));
const printDate=(d,t)=>{if(!d)return 'Non indicata';const p=d.split('-');return p.length===3?p[2]+'/'+p[1]+'/'+p[0]+' alle '+(t||'00:00'):d};
const fullText=p=>[p.title,p.body].filter(Boolean).join('\n\n');
const info=s=>{status.textContent=s};
async function copyText(text){
  const previous=document.activeElement;
  const input=document.createElement('textarea');
  input.value=text;
  input.readOnly=true;
  input.style.cssText='position:fixed;left:-9999px;top:0';
  dialog.append(input);
  let copied=false;
  try{
    input.select();
    copied=document.execCommand('copy');
  }catch{}finally{
    input.remove();
    previous?.focus();
  }
  if(!copied)await navigator.clipboard.writeText(text);
}
async function sharePost(post,photo){
  if(typeof navigator.share!=='function'){
    info('Menu di sistema non disponibile. Usa WhatsApp, Telegram o Copia nelle opzioni.');
    return;
  }
  const data={text:fullText(post)};
  const includePhoto=photo&&navigator.canShare?.({...data,files:[photo]});
  if(photo&&!includePhoto){
    info('Questo browser non può allegare la foto. Usa «Condividi con altre app» per il solo testo, oppure allega la foto nell’app di destinazione.');
    return;
  }
  if(includePhoto)data.files=[photo];
  try{
    await navigator.share(data);
    info('Contenuto consegnato al sistema di condivisione.');
  }catch(error){
    if(error.name!=='AbortError')info('Condivisione non riuscita. Usa WhatsApp, Telegram o Copia nelle opzioni.');
  }
}
function reset(){form.reset();el('id').value='';el('date').value=today();el('time').value=now();info('Nuovo post pronto.')}
function render(){
list.replaceChildren();const posts=read().sort((a,b)=>((b.date||'')+' '+(b.time||'')).localeCompare((a.date||'')+' '+(a.time||'')));
if(!posts.length){const p=document.createElement('p');p.textContent='Ancora nessun post salvato.';list.append(p);return}
for(const p of posts){
const card=document.createElement('article');card.className='vgPostItem';
const h=document.createElement('h4');h.textContent=p.title;
const meta=document.createElement('div');meta.className='vgPostMeta';meta.textContent=p.category+' · Previsto: '+printDate(p.date,p.time)+(p.publishedAt?' · Pubblicato: '+new Date(p.publishedAt).toLocaleString('it-IT',{dateStyle:'short',timeStyle:'short'}):' · Bozza');
const body=document.createElement('p');body.textContent=p.body;
const buttons=document.createElement('div');buttons.className='vgPostButtons';
const add=(label,action,fn)=>{const b=document.createElement('button');b.type='button';b.setAttribute('data-'+action,'');b.textContent=label;b.onclick=fn;buttons.append(b)};
add('✏️ Modifica','edit',()=>{el('id').value=p.id;el('category').value=p.category;el('title').value=p.title;el('body').value=p.body;el('link').value=p.link||'';el('date').value=p.date;el('time').value=p.time;form.scrollIntoView({behavior:'smooth',block:'start'});info('Modifica il post e premi Salva post.')});
const content=fullText(p);
add('📋 Copia','copy',async()=>{
  try{await copyText(content);info('Testo copiato.')}
  catch{info('Copia non riuscita. Seleziona il testo del post.')}
});
const options=document.createElement('div');
options.className='vgPostButtons';
options.hidden=true;
options.style.marginTop='12px';
const photoLabel=document.createElement('label');
photoLabel.textContent='Foto da allegare (facoltativa) ';
const photo=document.createElement('input');
photo.type='file';
photo.accept='image/*';
photoLabel.append(photo);
options.append(photoLabel);
const sharePhoto=document.createElement('button');
sharePhoto.type='button';
sharePhoto.textContent='Condividi con foto';
sharePhoto.disabled=true;
photo.onchange=()=>{sharePhoto.disabled=!photo.files[0]};
sharePhoto.onclick=()=>sharePost(p,photo.files[0]);
options.append(sharePhoto);
function shareLink(label,url){
  const link=document.createElement('a');
  link.textContent=label;
  link.href=url;
  link.target='_blank';
  link.rel='noopener noreferrer';
  options.append(link);
}
shareLink('WhatsApp','https://api.whatsapp.com/send?text='+encodeURIComponent(content));
shareLink('Telegram','https://t.me/share/url?url='+encodeURIComponent('')+'&text='+encodeURIComponent(content));
add('📤 Condividi con altre app','other-share',()=>{
  options.hidden=false;
  return sharePost(p);
});
add('Opzioni e foto','share-options',()=>{options.hidden=!options.hidden});
add('📘 Copia testo e apri il gruppo','facebook',()=>{
  const url='https://m.facebook.com/groups/1902940953839880/';
  const copying=copyText(content);
  window.open(url,'_blank','noopener,noreferrer');
  copying.then(
    ()=>info('Testo copiato. Incollalo nel post del gruppo e aggiungi la foto con «Foto/video».'),
    ()=>info('Copia non riuscita. Torna al post e premi «Copia».')
  );
});
add(p.publishedAt?'↩️ Torna a bozza':'✅ Segna pubblicato','published',()=>{const arr=read(),item=arr.find(x=>x.id===p.id);if(!item)return;item.publishedAt=item.publishedAt?null:new Date().toISOString();write(arr);render();info(item.publishedAt?'Data e ora effettive registrate.':'Post riportato in bozza.')});
add('🗑️ Elimina','remove',()=>{if(!confirm('Eliminare questo post salvato?'))return;write(read().filter(x=>x.id!==p.id));render();info('Post eliminato.')});
card.append(h,meta,body,buttons,options);if(p.link){const a=document.createElement('a');a.href=p.link;a.target='_blank';a.rel='noopener noreferrer';a.textContent='Apri link';card.insertBefore(a,buttons)}list.append(card)
}}
async function checkOwner(){const db=await window.VG_SUPABASE_READY;if(!db)throw Error('Accedi al gestionale.');const {data,error}=await db.auth.getUser();if(error||data.user?.id!==OWNER)throw Error('Accesso riservato all’amministratore.')}
let opening=false;
trigger.onclick=async(event)=>{
 event.preventDefault();
 event.stopPropagation();
 if(opening||dialog.open)return;
 opening=true;
 trigger.disabled=true;
 const originalLabel=trigger.textContent;
 trigger.textContent='⏳ Apertura Post Facebook…';
 // Show a modal immediately so taps cannot pass through to another menu entry.
 // Keep saved posts hidden until the owner check has succeeded.
 const content=dialog.querySelector('.vgPostsContent');
 content.hidden=true;
 const waiting=document.createElement('p');
 waiting.id='vgPostsLoading';
 waiting.setAttribute('role','status');
 waiting.textContent='Verifica accesso in corso…';
 dialog.append(waiting);
 if(typeof window.setTopMenuOpen==='function')window.setTopMenuOpen(false);
 try{
   dialog.showModal();
   await checkOwner();
   reset();
   render();
   content.hidden=false;
 }catch(e){
   dialog.close();
   alert(e.message||'Impossibile aprire Post Facebook.');
 }finally{
   waiting.remove();
   trigger.disabled=false;
   trigger.textContent=originalLabel;
   opening=false;
 }
};
dialog.querySelector('#vgPostsClose').onclick=()=>dialog.close();
dialog.querySelector('[data-reset]').onclick=reset;
form.onsubmit=e=>{e.preventDefault();if(!form.reportValidity())return;const id=el('id').value||crypto.randomUUID(),posts=read(),existing=posts.find(x=>x.id===id),item={id,category:el('category').value,title:el('title').value.trim(),body:el('body').value.trim(),link:el('link').value.trim(),date:el('date').value,time:el('time').value,publishedAt:existing?.publishedAt||null,updatedAt:new Date().toISOString()};if(!item.title||!item.body){info('Inserisci titolo e testo.');return}try{if(existing)Object.assign(existing,item);else posts.push(item);write(posts);render();el('id').value=id;info('Post salvato su questo dispositivo. Data e ora previste: '+printDate(item.date,item.time)+'.')}catch{info('Salvataggio non riuscito. Verifica lo spazio disponibile nel browser.')}};
};
if(document.readyState==='loading')document.addEventListener('DOMContentLoaded',ready,{once:true});else ready();
})();
