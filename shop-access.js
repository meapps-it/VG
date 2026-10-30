(function(){
 'use strict';
 const endpoint='https://qfjwtawsqfwmsmrrgqfi.supabase.co/functions/v1/vg-shop-catalog';
 const key='vg-shop-customer-v1',adminKey='vg-shop-session-v1',get=id=>document.getElementById(id);
 const valid=value=>/^[a-f0-9]{64}$/.test(value||'');
 const read=name=>{try{return localStorage.getItem(name)||''}catch{return ''}};
 let token=read(key),adminToken=read(adminKey),profile=null,reloadCatalog=null,register=false,googleClient;
 const store=(name,value)=>{try{value?localStorage.setItem(name,value):localStorage.removeItem(name)}catch{}};
 if(!valid(token))token='';if(!valid(adminToken))adminToken='';
 function busy(value){document.querySelectorAll('body>.brand-stage,body>main,body>.footer,body>.view,body>.overlay,body>.zoom-view').forEach(el=>el.inert=value);get('catalog').setAttribute('aria-busy',String(value))}
 function lock(message='') {document.body.classList.remove('shop-checking');document.body.classList.add('shop-locked');busy(true);get('accessError').textContent=message;get('accessCheck').hidden=!token;get('accessSignout').hidden=!token;get('accessForm').hidden=!!token;get('accessGoogle').hidden=!!token;get('accessPrivacyLabel').hidden=!!token||!register;get('accessTabs').hidden=!!token;get('accessGooglePrivacyLabel').hidden=!!token;}
 function checking(message='Verifica dell’accesso…'){get('accessPending').textContent=message;document.body.classList.remove('shop-locked');document.body.classList.add('shop-checking');busy(true)}
 function unlock(){document.body.classList.remove('shop-locked','shop-checking');busy(false)}
 const pendingMessage=()=>profile?.accessStatus==='blocked'?'Accesso sospeso dall’assistenza. Contattaci per informazioni.':'Account registrato. L’assistenza deve autorizzarti prima di mostrarti il catalogo.';
 async function rawAction(action,body,headers={}){
  const response=await fetch(endpoint+'?action='+action,{method:body===undefined?'GET':'POST',headers:{...(body===undefined?{}:{'Content-Type':'application/json'}),...(token?{'x-vg-customer-token':token}:{}),...headers},...(body===undefined?{}:{body:JSON.stringify(body)}),cache:'no-store'});
  const data=await response.json();if(!response.ok)throw Object.assign(Error(data.error||'Operazione non riuscita'),{status:response.status});return data;
 }
 async function ownerSession(){
  try{const raw=JSON.parse(read('sb-qfjwtawsqfwmsmrrgqfi-auth-token')||'null'),jwt=raw?.access_token||raw?.currentSession?.access_token;if(!jwt)return false;
   const data=await rawAction('admin-session',{}, {Authorization:'Bearer '+jwt});if(!valid(data.token))return false;adminToken=data.token;store(adminKey,adminToken);return true;
  }catch{return false}
 }
 async function restore(){
  if(token){try{profile=await rawAction('customer-me')}catch(error){if(error.status===403){token='';profile=null;store(key,'')}else throw error}}
  return profile;
 }
 async function open(verified=false){
  checking();try{
   if(!verified)await restore();
   if(profile&&profile.accessStatus!=='approved'){lock(pendingMessage());return}
   if(!token&&!adminToken&&!await ownerSession()){lock();return}
  }catch(error){lock(error.message||'Connessione non disponibile. Riprova.');return}
  // A verified session opens the catalogue; product loading is a separate state.
  unlock();window.dispatchEvent(new Event('vg-customer-ready'));
  try{await reloadCatalog()}catch(error){console.error('Caricamento catalogo:',error)}
 }
 async function accept(data){if(!valid(data.token))throw Error('Risposta di accesso non valida');token=data.token;profile=data;adminToken='';store(adminKey,'');store(key,token);get('accessPassword').value='';await open(true)}
 async function google(){
  if(!googleClient)googleClient=(async()=>{
   if(!window.supabase?.createClient)await new Promise((resolve,reject)=>{const script=document.createElement('script');script.src='https://cdn.jsdelivr.net/npm/@supabase/supabase-js@2.57.4/dist/umd/supabase.min.js';script.onload=resolve;script.onerror=()=>reject(Error('Impossibile caricare l’accesso Google. Riprova.'));document.head.append(script)});
   return window.supabase.createClient('https://qfjwtawsqfwmsmrrgqfi.supabase.co','sb_publishable_-EUVjwsk3txKk2Opqrc7Kw_xFNa9Hw1',{auth:{storageKey:'vg-shop-google-auth',flowType:'pkce',persistSession:true,autoRefreshToken:false,detectSessionInUrl:false}});
  })().catch(error=>{googleClient=null;throw error});return googleClient;
 }
 window.shopAccess={
  customer:()=>profile,
  async fetch(url,options={}){
   const headers=new Headers(options.headers||{});if(token){headers.set('x-vg-customer-token',token);headers.set('Authorization','Bearer '+token)}else if(adminToken)headers.set('Authorization','Bearer '+adminToken);
   const response=await fetch(url,{...options,headers});if(response.status===401||(response.status===403&&(await response.clone().json().catch(()=>({}))).code==='CUSTOMER_LOGIN_REQUIRED')){lock('Accedi con un account autorizzato dall’assistenza.');restore().then(()=>{if(!profile){adminToken='';store(adminKey,'')}lock(profile?pendingMessage():'Accedi con il tuo account personale.')}).catch(()=>{})}return response;
  },
  viewer(administrator){get('adminPreviewNotice').hidden=!administrator},
  async start(load){reloadCatalog=load;
   const fragment=new URLSearchParams(location.hash.replace(/^#/,'')),preview=fragment.get('admin_token'),params=new URLSearchParams(location.search);
   if(valid(preview)){adminToken=preview;token='';store(adminKey,preview);store(key,'');history.replaceState(null,'',location.pathname+location.search)}
   if(params.has('error')||fragment.has('error')){history.replaceState(null,'',location.pathname);lock('Accesso Google annullato o non riuscito. Riprova.');return}
   if(params.has('code')){checking('Accesso con Google in corso… Attendi, non serve premere di nuovo.');try{const code=params.get('code');history.replaceState(null,'',location.pathname);const client=await google(),result=await client.auth.exchangeCodeForSession(code);if(result.error)throw result.error;await accept(await rawAction('customer-google',{privacy:true},{Authorization:'Bearer '+result.data.session.access_token}));await client.auth.signOut({scope:'local'}).catch(()=>{});return}catch(error){lock('Accesso Google non riuscito. '+error.message);return}}
   await open();
  },
  async logout(){try{if(token)await rawAction('customer-logout',{})}catch{}token='';adminToken='';profile=null;store(key,'');store(adminKey,'');store('vg-shop-shipping-v1','');get('orderForm').reset();document.body.style.overflow='';document.querySelectorAll('.view.open,.overlay.open,.zoom-view.open').forEach(el=>{el.classList.remove('open');el.setAttribute('aria-hidden','true')});history.replaceState(null,'',location.pathname);window.dispatchEvent(new Event('vg-shop-logout'));lock('Accesso terminato su questo dispositivo.')}
 };
 function mode(value){register=value;get('accessNameField').hidden=!value;get('accessName').disabled=!value;get('accessName').required=value;get('accessFormTitle').textContent=value?'Crea il tuo account':'Accedi con telefono';get('accessSubmit').textContent=value?'Crea account ed entra':'Accedi';get('accessPassword').autocomplete=value?'new-password':'current-password';get('accessPrivacy').required=value;get('accessPrivacyLabel').hidden=!value;get('accessLoginTab').setAttribute('aria-pressed',String(!value));get('accessRegisterTab').setAttribute('aria-pressed',String(value));get('accessError').textContent=''}
 const resetButton=document.createElement('button');resetButton.type='button';resetButton.textContent='Password dimenticata?';resetButton.id='accessForgotPassword';get('accessForm').after(resetButton);
 const resetForm=document.createElement('form');resetForm.hidden=true;resetForm.id='accessResetForm';resetForm.innerHTML='<h2>Recupera la password</h2><p>Chiedi all’assistenza un codice reset. Inseriscilo qui per scegliere una nuova password personale.</p><label>Telefono<input name="phone" type="tel" autocomplete="tel" required maxlength="40"></label><label>Codice ricevuto dall’assistenza<input name="code" type="text" autocomplete="off" required maxlength="40"></label><label>Nuova password<input name="password" type="password" autocomplete="new-password" minlength="6" maxlength="128" required></label><label>Ripeti nuova password<input name="confirmPassword" type="password" autocomplete="new-password" minlength="6" maxlength="128" required></label><button type="submit">Salva nuova password</button><button type="button" data-cancel-reset>Torna all’accesso</button><p role="status" aria-live="polite"></p>';resetButton.after(resetForm);
 function showRecovery(open){resetForm.hidden=!open;resetButton.hidden=open||!!token;get('accessForm').hidden=open||!!token;get('accessGoogle').hidden=open||!!token;get('accessGooglePrivacyLabel').hidden=open||!!token;get('accessTabs').hidden=open||!!token;get('accessError').textContent='';if(open){resetForm.elements.phone.value=get('accessPhone').value;resetForm.elements.phone.focus()}else lock(profile?pendingMessage():'')}
 resetButton.onclick=()=>showRecovery(true);resetForm.querySelector('[data-cancel-reset]').onclick=()=>{resetForm.reset();showRecovery(false)};

 resetForm.onsubmit=async event=>{event.preventDefault();const f=resetForm.elements,status=resetForm.querySelector('[role=status]'),button=resetForm.querySelector('[type=submit]');if(f.password.value!==f.confirmPassword.value){status.textContent='Le due password non coincidono.';return}return window.vgSaveFeedback.run(button,async()=>{await rawAction('customer-reset-complete',{phone:f.phone.value,code:f.code.value,password:f.password.value});get('accessPhone').value=f.phone.value;resetForm.reset();showRecovery(false);mode(false);get('accessPassword').value='';get('accessError').textContent='Password salvata. Ora accedi con il numero e la nuova password.';get('accessPassword').focus();return {ok:true,message:'Password salvata correttamente.'}})};
 get('accessLoginTab').onclick=()=>mode(false);get('accessRegisterTab').onclick=()=>mode(true);
 get('accessForm').addEventListener('submit',async event=>{event.preventDefault();const button=get('accessSubmit');button.disabled=true;get('accessError').textContent='Verifica…';try{if(register&&!get('accessName').value.trim().match(/^.{2,100}$/u))throw Error('Inserisci il tuo nome (da 2 a 100 caratteri).');await accept(await rawAction(register?'customer-register':'customer-login',{...(register?{name:get('accessName').value.trim()}:{}),phone:get('accessPhone').value,password:get('accessPassword').value,privacy:get('accessPrivacy').checked}))}catch(error){get('accessError').textContent=error.message}finally{button.disabled=false}});
 function googleConsentError(invalid){get('accessGooglePrivacyLabel').classList.toggle('consent-invalid',invalid);get('accessGooglePrivacy').setAttribute('aria-invalid',String(invalid));get('accessGoogleConsentError').hidden=!invalid;}
 get('accessGooglePrivacy').addEventListener('change',()=>{if(get('accessGooglePrivacy').checked){googleConsentError(false);if(get('accessError').textContent==='Accetta il trattamento dei dati per continuare con Google.')get('accessError').textContent=''}});
 get('accessGoogle').onclick=async()=>{if(!get('accessGooglePrivacy').checked){googleConsentError(true);get('accessError').textContent='Accetta il trattamento dei dati per continuare con Google.';get('accessGooglePrivacy').focus();get('accessGooglePrivacyLabel').scrollIntoView({block:'nearest',behavior:'smooth'});return}googleConsentError(false);const button=get('accessGoogle');button.disabled=true;try{const client=await google(),result=await client.auth.signInWithOAuth({provider:'google',options:{redirectTo:location.origin+location.pathname,queryParams:{prompt:'select_account'}}});if(result.error)throw result.error}catch(error){get('accessError').textContent=error.message;button.disabled=false}};
 get('accessCheck').onclick=()=>open();get('accessSignout').onclick=()=>window.shopAccess.logout();get('shopLogout').onclick=()=>window.shopAccess.logout();mode(false);
})();
