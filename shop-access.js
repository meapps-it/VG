(function(){
 'use strict';
 const endpoint='https://qfjwtawsqfwmsmrrgqfi.supabase.co/functions/v1/vg-shop-catalog';
 const key='vg-shop-session-v1';let token='',reloadCatalog=null;
 const get=id=>document.getElementById(id);
 try{token=localStorage.getItem(key)||''}catch{}
 function saveToken(value){token=value;try{value?localStorage.setItem(key,value):localStorage.removeItem(key)}catch{}}
 function lock(message=''){document.body.classList.add('shop-locked');get('accessError').textContent=message;get('accessPassword').focus()}
 async function ownerSession(){
  try{const raw=JSON.parse(localStorage.getItem('sb-qfjwtawsqfwmsmrrgqfi-auth-token')||'null');const jwt=raw?.access_token||raw?.currentSession?.access_token;if(!jwt)return false;
   const r=await fetch(endpoint+'?action=admin-session',{method:'POST',headers:{Authorization:'Bearer '+jwt,'Content-Type':'application/json'},body:'{}'});if(!r.ok)return false;saveToken((await r.json()).token);return true;
  }catch{return false}
 }
 window.shopAccess={
  async fetch(url,options={}){const headers=new Headers(options.headers||{});if(token)headers.set('Authorization','Bearer '+token);const r=await fetch(url,{...options,headers});if(r.status===401){saveToken('');lock('Inserisci la password del catalogo.')}return r;},
  viewer(administrator){get('adminPreviewNotice').hidden=!administrator;},
  async start(load){
   reloadCatalog=load;
   const fragment=new URLSearchParams(location.hash.replace(/^#/,'')),adminToken=fragment.get('admin_token');
   if(adminToken&&/^[a-f0-9]{64}$/.test(adminToken)){if(token&&token!==adminToken)try{await fetch(endpoint+'?action=claim-admin',{method:'POST',headers:{Authorization:'Bearer '+adminToken,'Content-Type':'application/json'},body:JSON.stringify({previousToken:token})})}catch{}saveToken(adminToken);history.replaceState(null,'',location.pathname+location.search)}
   if(!token)await ownerSession();
   if(token){try{await load();document.body.classList.remove('shop-locked');return}catch{}}
   lock();
  }
 };
 get('accessForm').addEventListener('submit',async event=>{
  event.preventDefault();const button=get('accessSubmit');button.disabled=true;get('accessError').textContent='Verifica…';
  try{const response=await fetch(endpoint+'?action=login',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({password:get('accessPassword').value})});const data=await response.json();if(!response.ok)throw Error(data.error||'Accesso non riuscito');saveToken(data.token);await reloadCatalog();get('accessPassword').value='';get('accessError').textContent='';document.body.classList.remove('shop-locked');}
  catch(error){get('accessError').textContent=error.message||'Impossibile accedere. Riprova.'}finally{button.disabled=false}
 });
 get('shopLogout').onclick=()=>{saveToken('');try{localStorage.removeItem('vg-shop-shipping-v1')}catch{};get('orderForm').reset();window.dispatchEvent(new Event('vg-shop-logout'));lock('Accesso terminato su questo dispositivo.')};
})();
