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
<label>Foto da condividere (facoltativa, da riselezionare quando condividi)<input name="photo" type="file" accept="image/*"></label>
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
const fullText=p=>[p.title,p.body,p.link].filter(Boolean).join('\n\n');
const info=s=>{status.textContent=s};
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
add('✏️ Modifica','edit',()=>{el('id').value=p.id;el('category').value=p.category;el('title').value=p.title;el('body').value=p.body;el('link').value=p.link||'';el('date').value=p.date;el('time').value=p.time;el('photo').value='';form.scrollIntoView({behavior:'smooth',block:'start'});info('Modifica il post e premi Salva post.')});
add('📋 Copia','copy',async()=>{try{await navigator.clipboard.writeText(fullText(p));info('Testo copiato. Incollalo su Facebook.')}catch{info('Copia non disponibile: seleziona il testo dal post.')}});
add('📋 Copia e apri Facebook','share',async()=>{
 const text=fullText(p);
 // Facebook non garantisce l'inserimento di testi condivisi tramite Web Share API.
 // Copiamo esplicitamente il testo e apriamo Facebook per incollarlo nel compositore.
 let copied=false;
 try{await navigator.clipboard.writeText(text);copied=true}catch(_e){}
 if(!copied){
   const t=document.createElement('textarea');t.value=text;t.style.cssText='position:fixed;left:-9999px;top:0;opacity:0';document.body.append(t);t.select();
   try{copied=document.execCommand('copy')}catch(_e){}finally{t.remove()}
 }
 if(!copied){info('Copia automatica non disponibile: usa il pulsante 📋 Copia, poi incolla il testo su Facebook.');return}
 info('✅ Testo copiato. Su Facebook tocca «Scrivi qualcosa…», tieni premuto e scegli Incolla. La pubblicazione va confermata su Facebook.');
 window.open('https://www.facebook.com/','_blank','noopener,noreferrer');
});
add('📤 Condividi con altre app','other-share',async()=>{
 const content=fullText(p);
 const data={title:p.title,text:content};
 const photo=el('id').value===p.id?el('photo').files?.[0]:null;
 if(photo&&navigator.canShare?.({files:[photo]}))data.files=[photo];
 if(typeof navigator.share==='function'){
   try{await navigator.share(data);info('Condivisione avviata. Controlla nell’app scelta che testo e foto siano presenti.');return}
   catch(e){if(e.name==='AbortError')return;info('La condivisione nativa non è riuscita. Usa una delle opzioni qui sotto.')}
 }
 // The installed WebView may not expose Web Share. Use explicit app links instead
 // of showing a dead button or pretending Android's chooser has opened.
 let chooser=dialog.querySelector('#vgPostsShareFallback');
 if(chooser)chooser.remove();
 chooser=document.createElement('div');
 chooser.id='vgPostsShareFallback';
 chooser.style.cssText='margin:12px 0;padding:12px;background:#fff;border:1px solid #a5b7d1;border-radius:14px;display:flex;gap:8px;flex-wrap:wrap';
 const label=document.createElement('p');label.textContent='Scegli dove condividere. Per Facebook copia il testo e incollalo nel post. La foto va selezionata nell’app di destinazione.';
 label.style.cssText='width:100%;margin:0 0 5px';
 chooser.append(label);
 const link=(title,url)=>{
   const a=document.createElement('a');a.textContent=title;a.href=url;a.target='_blank';a.rel='noopener noreferrer';
   a.style.cssText='display:inline-block;padding:10px 12px;border-radius:10px;background:#244c97;color:white;text-decoration:none;font-weight:700';
   chooser.append(a);
 };
 const facebook=document.createElement('button');facebook.type='button';facebook.textContent='📘 Facebook';
 facebook.style.cssText='padding:10px 12px;border-radius:10px;background:#1877f2;color:#fff;border:0;font-weight:700;cursor:pointer';
 chooser.append(facebook);
 const fbTargets=document.createElement('div');fbTargets.hidden=true;
 fbTargets.style.cssText='width:100%;gap:8px;flex-wrap:wrap;padding:10px;background:#eff5ff;border:1px solid #c4d7f8;border-radius:10px';
 const fbInfo=document.createElement('p');fbInfo.style.cssText='width:100%;margin:0;font-size:14px';
 fbInfo.textContent='Scegli dove pubblicare. Copiamo il testo, poi su Facebook seleziona il gruppo o la pagina e incollalo nel nuovo post.';
 fbTargets.append(fbInfo);
 const fbChoice=(name,url)=>{
   const btn=document.createElement('button');btn.type='button';btn.textContent=name;
   btn.style.cssText='padding:10px 12px;border:0;border-radius:10px;background:#1877f2;color:#fff;font-weight:700;cursor:pointer';
   btn.onclick=async()=>{
     let copied=false;
     try{await navigator.clipboard.writeText(content);copied=true}catch(_e){
       const input=document.createElement('textarea');input.value=content;input.style.cssText='position:fixed;left:-9999px;top:0';
       document.body.append(input);input.select();try{copied=document.execCommand('copy')}catch(_error){}input.remove();
     }
     if(!copied){info('Impossibile copiare il testo. Premi prima il pulsante Copia del post.');return}
     // No app can choose Facebook posting identity on the user's behalf via a URL alone.
     // Let Facebook show the user's own managed groups/pages.
     info('Testo copiato! Scegli il gruppo o la pagina su Facebook, crea il post e tieni premuto per Incolla.');
     window.open(url,'_blank','noopener,noreferrer');
   };
   fbTargets.append(btn);
 };
 fbChoice('👥 Gruppo','https://www.facebook.com/groups/');
 fbChoice('📄 Pagina','https://www.facebook.com/pages/');
 facebook.onclick=()=>{
   fbTargets.hidden=!fbTargets.hidden;
   fbTargets.style.display=fbTargets.hidden?'none':'flex';
   if(!fbTargets.hidden)info('Seleziona Gruppo o Pagina, poi scegli la destinazione su Facebook.');
 };
 chooser.append(fbTargets);
 const txt=encodeURIComponent(content);
 link('WhatsApp','https://api.whatsapp.com/send?text='+txt);
 link('Telegram','https://t.me/share/url?url='+encodeURIComponent(p.link||'')+'&text='+encodeURIComponent([p.title,p.body].filter(Boolean).join('\\n\\n')));
 const copyBtn=document.createElement('button');copyBtn.type='button';copyBtn.textContent='📋 Copia per altre app';
 copyBtn.onclick=async()=>{
   try{await navigator.clipboard.writeText(content);info('Testo copiato. Apri l’app desiderata e incolla.')}
   catch(_e){
     const input=document.createElement('textarea');input.value=content;input.style.cssText='position:fixed;left:-9999px;top:0';
     document.body.append(input);input.select();
     let copied=false;try{copied=document.execCommand('copy')}catch(_err){}
     input.remove();info(copied?'Testo copiato. Apri l’app desiderata e incolla.':'Copia non riuscita. Usa il pulsante Copia sul post.');
   }
 };
 chooser.append(copyBtn);
 const dismiss=document.createElement('button');dismiss.type='button';dismiss.textContent='Chiudi opzioni';dismiss.onclick=()=>chooser.remove();chooser.append(dismiss);
 buttons.after(chooser);
 info('Sul tuo dispositivo manca la condivisione nativa: usa WhatsApp, Telegram o copia il testo per altre app.');
});
add(p.publishedAt?'↩️ Torna a bozza':'✅ Segna pubblicato','published',()=>{const arr=read(),item=arr.find(x=>x.id===p.id);if(!item)return;item.publishedAt=item.publishedAt?null:new Date().toISOString();write(arr);render();info(item.publishedAt?'Data e ora effettive registrate.':'Post riportato in bozza.')});
add('🗑️ Elimina','remove',()=>{if(!confirm('Eliminare questo post salvato?'))return;write(read().filter(x=>x.id!==p.id));render();info('Post eliminato.')});
card.append(h,meta,body,buttons);if(p.link){const a=document.createElement('a');a.href=p.link;a.target='_blank';a.rel='noopener noreferrer';a.textContent='Apri link';card.insertBefore(a,buttons)}list.append(card)
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
