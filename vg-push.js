(()=>{
  'use strict';
  if(window.VGPush || !('Notification' in window) || !('serviceWorker' in navigator) ||
    !('PushManager' in window) || !window.isSecureContext) return;

  const project='https://qfjwtawsqfwmsmrrgqfi.supabase.co/functions/v1/';
  const api=project+'vg-push-subscriptions';
  const preference='vg-push-preference-v1';
  const dismissed='vg-push-invite-dismissed';
  let config, busy=false, active=false, button, invitation, status;
  const read=(storage,key)=>{try{return window[storage].getItem(key)}catch{return null}};
  const write=(storage,key,value)=>{try{window[storage].setItem(key,value)}catch{}};

  async function post(action,body){
    const response=await fetch(api,{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({action,...body})});
    if(!response.ok)throw Error('Il servizio notifiche non ha salvato la modifica. Riprova.');
  }

  async function register(){
    const current=await navigator.serviceWorker.getRegistration('/');
    const worker=current?.active||current?.waiting||current?.installing;
    if(worker && new URL(worker.scriptURL).origin===location.origin &&
      ['/service-worker.js','/vg-push-sw.js'].includes(new URL(worker.scriptURL).pathname)) return current;
    return navigator.serviceWorker.register('/vg-push-sw.js',{scope:'/',updateViaCache:'none'});
  }

  async function sync(){
    const registration=await register();
    let subscription=await registration.pushManager.getSubscription();
    if(!subscription){
      if(!config){
        const response=await fetch(api+'?action=config',{cache:'no-store'});
        if(!response.ok)throw Error('Servizio notifiche non disponibile.');
        config=await response.json();
      }
      if(!config.enabled||!config.publicKey)throw Error('Servizio notifiche non disponibile.');
      const key=config.publicKey.replace(/-/g,'+').replace(/_/g,'/');
      const bytes=Uint8Array.from(atob(key.padEnd(Math.ceil(key.length/4)*4,'=')),c=>c.charCodeAt(0));
      const ready=registration.active?registration:await navigator.serviceWorker.ready;
      subscription=await ready.pushManager.subscribe({userVisibleOnly:true,applicationServerKey:bytes});
    }
    await post('subscribe',{subscription:subscription.toJSON()});
    active=true;
    write('localStorage',preference,'on');
    if(window.shopAccess?.customer()){
      try{
        const response=await window.shopAccess.fetch(project+'vg-shop-catalog?action=catalog-notifications-enabled',{method:'POST'});
        if(!response.ok)throw Error('Avviso gestionale non registrato');
      }catch(error){console.warn('[VG Push]',error)}
    }
  }

  function render(){
    if(!button)return;
    button.disabled=busy;
    button.setAttribute('aria-pressed',String(active));
    const label=active?'Disattiva notifiche':'Attiva notifiche';
    button.title=label;
    button.setAttribute('aria-label',label);
    invitation.hidden=active||Notification.permission==='denied'||read('localStorage',preference)==='off'||read('sessionStorage',dismissed)==='yes';
    invitation.querySelector('button').disabled=busy;
  }

  async function change(enable,interactive=true){
    if(busy)return;
    busy=true;
    render();
    if(status)status.textContent='';
    try{
      if(enable){
        if(interactive && Notification.permission==='default')await Notification.requestPermission();
        if(Notification.permission!=='granted'){
          write('sessionStorage',dismissed,'yes');
          if(status)status.textContent=Notification.permission==='denied'?'Notifiche bloccate nelle impostazioni del browser.':'Attivazione annullata.';
          return;
        }
        await sync();
      }else{
        const registration=await navigator.serviceWorker.getRegistration('/');
        const subscription=await registration?.pushManager.getSubscription();
        if(subscription){
          await post('unsubscribe',{endpoint:subscription.endpoint});
          await subscription.unsubscribe();
        }
        active=false;
        write('localStorage',preference,'off');
      }
      if(interactive&&status)status.textContent=active?'Notifiche attivate.':'Notifiche disattivate.';
    }catch(error){
      if(interactive&&status)status.textContent=error.message;
      console.warn('[VG Push]',error);
    }finally{
      busy=false;
      render();
    }
  }

  function start(){
    if(button||document.body.classList.contains('shop-locked')||document.body.classList.contains('shop-checking'))return;
    const header=document.querySelector('.header-actions');
    const filters=document.getElementById('catalogFilters');
    if(!header||!filters)return;
    button=document.createElement('button');
    button.type='button';
    button.id='vgPushSettings';
    button.textContent='🔔';
    button.style.cssText='min-height:40px;padding:0 8px;border:2px solid #b01760;border-radius:10px;background:#fff1f7;font-size:19px';
    button.onclick=()=>change(!active);
    header.prepend(button);
    invitation=document.createElement('section');
    invitation.id='vgPushInvitation';
    invitation.style.cssText='padding:12px;margin:12px 0;border:1px solid #efd5e1;border-radius:12px;background:#fff7fb';
    invitation.innerHTML='<p>Ricevi un avviso per nuovi articoli e promozioni.</p><button type="button">Attiva notifiche</button> <button type="button">Non ora</button>';
    const [enable,skip]=invitation.querySelectorAll('button');
    enable.onclick=()=>change(true);
    skip.onclick=()=>{write('sessionStorage',dismissed,'yes');render()};
    status=document.createElement('div');
    status.setAttribute('role','status');
    status.style.cssText='font-size:14px;color:#58213b';
    filters.after(invitation,status);
    render();
    if(Notification.permission==='granted'&&read('localStorage',preference)!=='off')void change(true,false);
  }

  window.VGPush={enable:()=>change(true),disable:()=>change(false)};
  window.addEventListener('vg-catalog-ready',start);
  window.addEventListener('vg-customer-ready',()=>{if(active)void change(true,false)});
  if(document.readyState==='loading')document.addEventListener('DOMContentLoaded',start,{once:true});else start();
})();
