(function(){
 'use strict';
 const endpoint='https://qfjwtawsqfwmsmrrgqfi.supabase.co/functions/v1/vg-shop-catalog';
 const key='vg-shop-session-v1',logoutKey='vg-shop-signed-out-v1';
 const valid=value=>/^[a-f0-9]{64}$/.test(value||'');
 const get=id=>document.getElementById(id);
 const stores=[];for(const name of ['localStorage','sessionStorage'])try{stores.push(window[name])}catch{}
 const read=name=>{for(const store of stores)try{const value=store.getItem(name);if(value)return value}catch{}return ''};
 let token=read(key),reloadCatalog=null,ownerRecovery=null,restored=false;
 if(!valid(token))token='';
 let database=null;
 function deviceToken(write,value=''){
  const operation=new Promise(resolve=>{
   try{
    if(!window.indexedDB){resolve('');return}
    if(!database)database=new Promise(done=>{
     const request=indexedDB.open('vg-shop-access',1);
     request.onupgradeneeded=()=>{if(!request.result.objectStoreNames.contains('session'))request.result.createObjectStore('session')};
     request.onsuccess=()=>done(request.result);request.onerror=()=>done(null);request.onblocked=()=>done(null);
    });
    database.then(db=>{
     if(!db){resolve('');return}
     try{
      const tx=db.transaction('session',write?'readwrite':'readonly'),store=tx.objectStore('session');
      const request=write?(value?store.put(value,key):store.delete(key)):store.get(key);
      let result='';request.onsuccess=()=>{result=write?value:(request.result||'')};
      tx.oncomplete=()=>resolve(result);tx.onerror=tx.onabort=()=>resolve('');
     }catch{resolve('')}
    });
   }catch{resolve('')}
  });
  return new Promise(resolve=>{const timer=setTimeout(()=>resolve(''),1000);operation.then(value=>{clearTimeout(timer);resolve(value)})});
 }
 function saveToken(value,explicitLogout=false){
  token=valid(value)?value:'';
  for(const store of stores)try{
   token?store.setItem(key,token):store.removeItem(key);
  }catch{}
  for(const store of stores)try{if(explicitLogout)store.setItem(logoutKey,'1');else if(token)store.removeItem(logoutKey)}catch{}
  return deviceToken(true,token);
 }
 function setShellBusy(busy){document.querySelectorAll('body>.brand-stage,body>main,body>.footer,body>.view,body>.overlay,body>.zoom-view').forEach(el=>{el.inert=busy});get('catalog').setAttribute('aria-busy',String(busy))}
 function showShell(){document.body.classList.remove('shop-locked');document.body.classList.add('shop-checking');setShellBusy(true)}
 function unlock(){document.body.classList.remove('shop-locked','shop-checking');setShellBusy(false)}
 function lock(message=''){
  document.body.classList.remove('shop-checking');document.body.classList.add('shop-locked');
  get('accessError').textContent=message;get('accessPassword').focus();
 }
 async function ownerSession(){
  if(ownerRecovery)return ownerRecovery;
  ownerRecovery=(async()=>{
   try{const raw=JSON.parse(read('sb-qfjwtawsqfwmsmrrgqfi-auth-token')||'null');const jwt=raw?.access_token||raw?.currentSession?.access_token;if(!jwt)return false;
    const r=await fetch(endpoint+'?action=admin-session',{method:'POST',headers:{Authorization:'Bearer '+jwt,'Content-Type':'application/json'},body:'{}'});if(!r.ok)return false;
    const value=(await r.json()).token;if(!valid(value))return false;saveToken(value);return true;
   }catch{return false}
  })();
  try{return await ownerRecovery}finally{ownerRecovery=null}
 }
 window.shopAccess={
  async fetch(url,options={}){
   const request=()=>{const headers=new Headers(options.headers||{});if(token)headers.set('Authorization','Bearer '+token);return fetch(url,{...options,headers})};
   let response=await request();
   if(response.status===401){
    if(await ownerSession())response=await request();
    if(response.status===401){saveToken('');lock('Inserisci la password del catalogo.')}
   }
   return response;
  },
  viewer(administrator){get('adminPreviewNotice').hidden=!administrator},
  async start(load){
   reloadCatalog=load;
   const fragment=new URLSearchParams(location.hash.replace(/^#/,'')),adminToken=fragment.get('admin_token');
   if(valid(adminToken)){
    const previousToken=token;saveToken(adminToken);history.replaceState(null,'',location.pathname+location.search);
    if(previousToken&&previousToken!==adminToken)fetch(endpoint+'?action=claim-admin',{method:'POST',headers:{Authorization:'Bearer '+adminToken,'Content-Type':'application/json'},body:JSON.stringify({previousToken})}).catch(()=>{});
   }
   if(!token&&!restored&&!read(logoutKey)){
    const recovered=await deviceToken(false);if(valid(recovered))saveToken(recovered);
   }
   restored=true;
   if(!token&&!read(logoutKey))await ownerSession();
   if(token){
    // Show the catalog shell immediately; protected products still require the server.
    saveToken(token);showShell();
    try{await load();unlock()}catch{if(!token)lock('Inserisci la password del catalogo.')}
    return;
   }
   lock();
  }
 };
 get('accessForm').addEventListener('submit',async event=>{
  event.preventDefault();const button=get('accessSubmit');button.disabled=true;get('accessError').textContent='Verifica…';
  try{
   const response=await fetch(endpoint+'?action=login',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({password:get('accessPassword').value})});
   const data=await response.json();if(!response.ok)throw Error(data.error||'Accesso non riuscito');if(!valid(data.token))throw Error('Risposta di accesso non valida');
   saveToken(data.token);get('accessPassword').value='';get('accessError').textContent='';showShell();await reloadCatalog();unlock();
  }catch(error){get('accessError').textContent=error.message||'Impossibile accedere. Riprova.'}finally{button.disabled=false}
 });
 get('shopLogout').onclick=async()=>{
  const clearing=saveToken('',true);try{localStorage.removeItem('vg-shop-shipping-v1')}catch{}
  get('orderForm').reset();window.dispatchEvent(new Event('vg-shop-logout'));lock('Accesso terminato su questo dispositivo.');await clearing;
 };
})();
