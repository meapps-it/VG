(()=>{'use strict';let box,invalidField;
function show(kind,message,host){invalidField=null;if(!box){box=document.createElement('aside');box.id='shopNotice';box.className='shop-notice';box.setAttribute('tabindex','-1');box.innerHTML='<div><strong></strong><p></p><button type="button" class="shop-notice-field" hidden>Vai al campo</button></div><button type="button" aria-label="Chiudi avviso">×</button>';box.querySelector('.shop-notice-field').onclick=()=>invalidField?.focus();box.querySelector('[aria-label="Chiudi avviso"]').onclick=clear;document.body.append(box)}const popup=kind==='success';const target=popup?document.body:(host||document.querySelector('#customerView.open .customer-content')||document.querySelector('#productView.open #productChoices')||document.querySelector('#overlay.open .drawer')||document.querySelector('.discovery')||document.body);if(popup)target.append(box);else target.prepend(box);box.classList.toggle('shop-notice-inline',!popup);box.hidden=false;box.querySelector('.shop-notice-field').hidden=true;box.dataset.kind=kind;box.setAttribute('role',kind==='error'?'alert':'status');box.setAttribute('aria-live',kind==='error'?'assertive':'polite');box.querySelector('strong').textContent=kind==='error'?'Controlla la richiesta':kind==='success'?'Operazione riuscita':'Attenzione';box.querySelector('p').textContent=message;return box}
function clear(){invalidField=null;if(box)box.hidden=true}
function invalid(control){
 const host=control.closest('#overlay.open .drawer,#productView.open .product-buy-panel,#customerView.open .customer-content,body.shop-locked:not(.shop-checking) #accessGate .access-card');
 if(!host)return false;
 control.classList.add('field-invalid');
 const first=control.form?.querySelector(':invalid');
 if(first&&first!==control)return true;
 const labels={name:'nome e cognome',phone:'telefono',address:'indirizzo',province:'provincia',city:'comune',cap:'CAP',privacy:'consenso privacy'};
 const notice=show('error',control.id==='productChoice-size'?'Scegli la misura dell’articolo.':control.id==='productChoice-color'?'Scegli il colore dell’articolo.':control.name==='privacy'?'Accetta il consenso privacy prima di inviare.':'Completa o correggi '+(labels[control.name]||control.labels?.[0]?.textContent?.trim().replace(/\s*\*\s*$/,'').slice(0,65)||'il campo evidenziato')+'.',host);
 if(host.closest('#accessGate'))notice.querySelector('strong').textContent='Controlla i dati di accesso';
 invalidField=control;
 notice.querySelector('.shop-notice-field').hidden=false;
 return true;
}
document.addEventListener('invalid',event=>{if(invalid(event.target))event.preventDefault()},true);
function edited(event){
 const control=event.target;
 if(!control.validity?.valid)return;
 control.classList.remove('field-invalid');
 if(control===invalidField)clear();
}
document.addEventListener('input',edited);
document.addEventListener('change',edited);
window.shopFeedback={show,clear,invalid};
})();
