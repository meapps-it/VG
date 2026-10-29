/* A shared save lifecycle: no success is inferred from a click or a timeout. */
(()=>{'use strict';
 if(window.vgSaveFeedback)return;
 const pending=new WeakSet();let activeAdmin=null,adminPending=false;
 const style=document.createElement('style');style.textContent='.vg-save-status{padding:12px 16px;margin:10px 16px;border:1px solid #b9cde8;border-radius:14px;background:#edf4ff;color:#173b66;font:inherit;overflow-wrap:anywhere}.vg-save-status[data-state=success]{background:#e7f6ed;border-color:#a1d5b4;color:#175631}.vg-save-status[data-state=error]{background:#fff0ef;border-color:#e5aaa5;color:#8a2922}.vg-save-status[hidden]{display:none}button[aria-busy=true]{cursor:progress;opacity:.75}';document.head.append(style);
 function statusFor(button,global){let node;if(global){node=document.getElementById('vgSaveStatus');if(!node){node=document.createElement('p');node.id='vgSaveStatus';const header=document.querySelector('body > header');if(header)header.after(node);else document.body.prepend(node)}}else{node=button.parentElement.querySelector(':scope > .vg-save-status');if(!node){node=document.createElement('p');button.after(node)}}node.className='vg-save-status';node.setAttribute('role','status');node.setAttribute('aria-live','polite');return node}
 async function run(button,task,{global=false,successTarget=null}={}){
  if(!button||pending.has(button))return;pending.add(button);const label=button.dataset.saveLabel||button.textContent;button.dataset.saveLabel=label;const disabled=button.disabled,node=statusFor(button,global);button.disabled=true;button.setAttribute('aria-busy','true');button.textContent='Salvataggio…';node.hidden=false;node.dataset.state='pending';node.textContent='Salvataggio in corso…';
  try{await new Promise(resolve=>setTimeout(resolve,0));const result=await task();if(!result?.ok){node.dataset.state='error';node.textContent=result?.message||'Salvataggio non completato. Controlla i dati e riprova.';return result}node.dataset.state='success';if(successTarget)successTarget.before(node);node.textContent=result.message||'Salvataggio completato.';button.textContent='Salvato';return result}
  catch(e){node.dataset.state='error';node.textContent=e?.message||'Salvataggio non riuscito. Riprova.';return {ok:false,message:node.textContent}}
  finally{pending.delete(button);button.disabled=disabled;button.removeAttribute('aria-busy');if(node.dataset.state!=='success')button.textContent=label;else{const reset=()=>{button.textContent=label;button.closest('form')?.removeEventListener('input',reset);button.closest('form')?.removeEventListener('change',reset)};button.closest('form')?.addEventListener('input',reset,{once:true});button.closest('form')?.addEventListener('change',reset,{once:true});button.addEventListener('click',reset,{once:true})}}
 }
 window.vgSaveFeedback={run};
 // Legacy administration saves report their validated result through toast().
 // Capture only these saves, preserving cloud warnings and validation failures.
 const actions={saveArt:'saveArt',saveCli:'saveCli',saveOrd:'saveOrd',savePricingSettings:'savePricingSettings',saveUiPrefs:'saveUiPrefs'};
 document.addEventListener('click',event=>{const button=event.target.closest?.('button[data-action]'),name=button&&actions[button.dataset.action];if(!name)return;event.preventDefault();event.stopImmediatePropagation();if(adminPending||button.disabled)return;adminPending=true;
  run(button,async()=>{let message='';const previous=window.toast;activeAdmin=name;window.toast=function(text,...args){message=String(text||'');if(/^(Articolo salvato|Cliente salvato|Ordine salvato|Regole prezzi salvate|Interfaccia aggiornata)/i.test(message)&&!/cloud non|non sincronizz|non caricat|errore|fallit/i.test(message))return;return previous?.call(this,text,...args)};
   try{if(name==='saveUiPrefs'){window.saveUiPrefsData(window.readUiEditor());window.applyUiPrefs();message='Interfaccia aggiornata'}else await window[name]();const ok=/^(Articolo salvato|Cliente salvato|Ordine salvato|Regole prezzi salvate|Interfaccia aggiornata)/i.test(message)&&!/cloud non|non sincronizz|non caricat|errore|fallit/i.test(message);return {ok,message:message||'Salvataggio non completato. Controlla i dati.'}}finally{window.toast=previous;activeAdmin=null}
  },{global:true}).finally(()=>{adminPending=false});
 },true);
})();
