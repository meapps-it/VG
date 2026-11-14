/* Vanity & Glamour: preferenza notifiche ON, permesso browser sempre esplicito. */
(()=>{
  'use strict';
  const API='https://qfjwtawsqfwmsmrrgqfi.supabase.co/functions/v1/vg-push-subscriptions';
  const KEY='vg-push-preference-v1';
  const pref=()=>{try{return localStorage.getItem(KEY)!=='off'}catch{return true}};
  const save=value=>{try{localStorage.setItem(KEY,value?'on':'off')}catch{}};
  const supported='Notification' in window && 'serviceWorker' in navigator && 'PushManager' in window && window.isSecureContext;
  if(!supported)return;
  const base64ToBytes=s=>{const padded=s+'='.repeat((4-s.length%4)%4);const binary=atob(padded.replace(/-/g,'+').replace(/_/g,'/'));return Uint8Array.from(binary,c=>c.charCodeAt(0))};
  let config=null;
  async function loadConfig(){
    if(config)return config;
    const response=await fetch(API+'?action=config',{cache:'no-store'});
    if(!response.ok)throw new Error('Servizio notifiche non configurato');
    const json=await response.json();
    if(!json.publicKey || !json.enabled)throw new Error('Servizio notifiche non disponibile');
    config=json;return config;
  }
  async function sync(enabled){
    const registration=await navigator.serviceWorker.register('/vg-push-sw.js',{scope:'/'});
    const old=await registration.pushManager.getSubscription();
    if(!enabled){
      if(old){await fetch(API,{method:'POST',headers:{'content-type':'application/json'},body:JSON.stringify({action:'unsubscribe',endpoint:old.endpoint})});await old.unsubscribe()}
      return;
    }
    const cfg=await loadConfig();
    const subscription=old||await registration.pushManager.subscribe({userVisibleOnly:true,applicationServerKey:base64ToBytes(cfg.publicKey)});
    const response=await fetch(API,{method:'POST',headers:{'content-type':'application/json'},body:JSON.stringify({action:'subscribe',subscription:subscription.toJSON()})});
    if(!response.ok)throw new Error('Iscrizione alle notifiche non riuscita');
  }
  function show(){
    if(document.getElementById('vgPushPanel'))return;
    const container=document.createElement('div');container.id='vgPushPanel';
    container.style.cssText='position:fixed;bottom:18px;left:12px;right:12px;z-index:500;max-width:420px;margin:auto;background:#fff;color:#242424;border:1px solid #e6dee3;border-radius:16px;box-shadow:0 10px 36px #0003;padding:14px;font:14px system-ui,sans-serif';
    container.innerHTML='<strong>🔔 Novità Vanity & Glamour</strong><p style="margin:8px 0 12px">Ricevi avvisi per offerte e nuovi arrivi. Le notifiche sono selezionate per impostazione predefinita, ma Android richiede il tuo consenso.</p><div style="display:flex;justify-content:flex-end;gap:10px"><button type="button" id="vgPushNo" style="padding:9px;border:1px solid #ddd;border-radius:9px;background:white">Non ora</button><button type="button" id="vgPushYes" style="padding:9px;border:0;border-radius:9px;background:#a61557;color:white;font-weight:bold">Attiva notifiche</button></div>';
    document.body.appendChild(container);
    container.querySelector('#vgPushNo').onclick=()=>{save(false);container.remove()};
    container.querySelector('#vgPushYes').onclick=async()=>{const yes=container.querySelector('#vgPushYes');yes.disabled=true;yes.textContent='Attivazione…';try{const perm=await Notification.requestPermission();if(perm!=='granted'){save(false);container.remove();return}await sync(true);save(true);container.remove()}catch(e){yes.disabled=false;yes.textContent='Riprova';console.warn('[VG Push]',e);alert('Notifiche al momento non disponibili. Riprova più tardi.')}};
  }
  async function activateWhenReady(){
    if(!pref()||Notification.permission==='denied')return;
    try{await loadConfig();if(Notification.permission==='granted'){await sync(true)}else if(Notification.permission==='default')show()}catch(e){console.info('[VG Push] Servizio non ancora configurato:',e.message)}
  }
  const launch=()=>{if(document.body.classList.contains('shop-locked')||document.body.classList.contains('shop-checking'))return;activateWhenReady()};
  window.addEventListener('vg-catalog-ready',launch,{once:true});
  if(document.readyState!=='loading')launch();else document.addEventListener('DOMContentLoaded',launch,{once:true});
  async function addInvitation(){
    if(document.getElementById('vgPushInvitation')||Notification.permission==='denied')return;
    if(sessionStorage.getItem('vg-push-invite-dismissed')==='yes')return;
    if(pref() && Notification.permission==='granted'){
      try{const reg=await navigator.serviceWorker.getRegistration('/');if(reg && await reg.pushManager.getSubscription())return}catch(e){console.info('[VG Push] Stato iscrizione non verificabile:',e)}
    }
    const anchor=document.getElementById('catalogFilters');
    if(!anchor||!anchor.parentNode)return;
    const card=document.createElement('section');
    card.id='vgPushInvitation';
    card.setAttribute('aria-label','Novità e promozioni');
    card.style.cssText='margin:16px 0;padding:15px 16px;border:1px solid #efd5e1;border-radius:17px;background:#fff7fb;box-shadow:0 2px 12px #55223b0b;color:#38232f;font:14px system-ui,sans-serif';
    card.innerHTML='<div style="display:flex;align-items:start;gap:10px"><span style="font-size:26px" aria-hidden="true">🔔</span><div style="flex:1"><strong style="font-size:16px">Non perderti le prossime offerte!</strong><p style="margin:6px 0 12px;line-height:1.5">Ricevi un avviso quando arrivano nuovi articoli o iniziano le promozioni. Solo le novità che scegliamo di comunicarti.</p><div style="display:flex;align-items:center;gap:10px;flex-wrap:wrap"><button type="button" id="vgPushInviteEnable" style="border:0;border-radius:10px;padding:10px 14px;background:#a61557;color:#fff!important;-webkit-text-fill-color:#fff;font-weight:700;cursor:pointer">Attiva notifiche</button><button type="button" id="vgPushInviteDismiss" style="border:0;background:transparent;color:#75636c;padding:8px;cursor:pointer">Non ora</button></div></div></div>';
    anchor.insertAdjacentElement('afterend',card);
    card.querySelector('#vgPushInviteEnable').onclick=async()=>{try{await window.VGPush.enable();if(Notification.permission==='granted')card.remove()}catch(e){alert('Notifiche non disponibili: '+(e.message||'riprova più tardi'))}};
    card.querySelector('#vgPushInviteDismiss').onclick=()=>{sessionStorage.setItem('vg-push-invite-dismissed','yes');card.remove()};
  }
  window.addEventListener('vg-catalog-ready',addInvitation,{once:true});
  if(document.readyState==='loading')document.addEventListener('DOMContentLoaded',addInvitation,{once:true});else addInvitation();
  function addSettings(){
    if(document.getElementById('vgPushSettings')||!document.body)return;
    const element=document.createElement('button');element.id='vgPushSettings';element.type='button';element.textContent='🔔 Notifiche';
    element.style.cssText='display:block;margin:20px auto 12px;padding:7px 12px;border:1px solid #d9cbd1;background:#fff;border-radius:10px;color:#55444c;font:12px system-ui;cursor:pointer';
    const header=document.querySelector('.header-actions');
    if(!header)return;
    element.setAttribute('aria-label','Attiva o gestisci le notifiche');
    element.style.cssText='display:inline-flex;align-items:center;justify-content:center;flex-shrink:0;padding:0 8px;min-height:40px;border:1px solid #e5ddd9;background:#fff;border-radius:10px;color:#a61557;font:600 12px system-ui;cursor:pointer';
    element.textContent='🔔';
    element.style.background='#fff1f7';
    element.style.border='2px solid #b01760';
    element.style.fontSize='19px';
    element.title='Notifiche';
    header.insertBefore(element,header.firstChild);
    element.onclick=async()=>{
      try{
        if(!('Notification' in window)||!('serviceWorker' in navigator)||!('PushManager' in window))throw new Error('Questo browser non supporta le notifiche push');
        if(Notification.permission==='granted'){
          const registration=await navigator.serviceWorker.getRegistration('/');
          const subscription=registration?await registration.pushManager.getSubscription():null;
          if(subscription){
            if(!confirm('Vuoi disattivare le notifiche di Vanity & Glamour su questo dispositivo?'))return;
            await window.VGPush.disable();alert('Notifiche disattivate.');return;
          }
        }
        await window.VGPush.enable();
      }catch(e){alert('Impossibile attivare le notifiche: '+(e.message||'servizio non disponibile'))}
    };
  }
  window.addEventListener('vg-catalog-ready',addSettings,{once:true});
  if(document.readyState==='loading')document.addEventListener('DOMContentLoaded',addSettings,{once:true});else addSettings();
  window.VGPush={enable:async()=>{await loadConfig();save(true);if(Notification.permission==='granted')await sync(true);else show()},disable:async()=>{save(false);await sync(false)}};
})();
