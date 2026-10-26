import {digest,passwordHash,requireOwner} from './shop-access.ts';
const hex=(a:Uint8Array)=>Array.from(a).map(x=>x.toString(16).padStart(2,'0')).join('');
const text=(v:any,n:number)=>typeof v==='string'?v.trim().slice(0,n):'';
function equal(a:string,b:string){let d=a.length^b.length;for(let i=0;i<Math.max(a.length,b.length);i++)d|=(a.charCodeAt(i)||0)^(b.charCodeAt(i)||0);return d===0}
export function customerPhone(raw:any){let value=text(raw,40),n=value.replace(/\D/g,'');if(n.startsWith('00'))n=n.slice(2);else if(!value.startsWith('+')&&((n.length===10&&n.startsWith('3'))||(n.startsWith('0')&&n.length>=8&&n.length<=11)))n='39'+n;return /^\d{8,15}$/.test(n)?n:''}
export function shippingProfile(value:any){const out:any={};for(const [key,max] of Object.entries({name:100,phone:40,address:200,province:100,city:100,cap:12}))out[key]=text(value?.[key],max);return out}
export async function customerSession(db:any,req:Request,owner:string,allowPending=false){
 const token=req.headers.get('x-vg-customer-token')||'';if(!/^[a-f0-9]{64}$/.test(token))return null;
 const result=await db.from('vg_shop_customer_sessions').select('customer_id').eq('owner_id',owner).eq('token_hash',await digest(token)).gt('expires_at',new Date().toISOString()).maybeSingle();if(result.error)throw result.error;if(!result.data)return null;const account=await db.from('vg_shop_customers').select('access_status').eq('owner_id',owner).eq('id',result.data.customer_id).single();if(account.error)throw account.error;return allowPending||account.data?.access_status==='approved'?result.data:null;
}
async function issue(db:any,owner:string,account:any){
 const token=hex(crypto.getRandomValues(new Uint8Array(32))),expiresAt=new Date(Date.now()+365*86400000).toISOString();
 const result=await db.from('vg_shop_customer_sessions').insert({owner_id:owner,customer_id:account.id,token_hash:await digest(token),expires_at:expiresAt});if(result.error)throw result.error;
 const catalog=await db.from('vg_shop_sessions').insert({owner_id:owner,customer_id:account.id,token_hash:await digest(token),is_admin:false,expires_at:expiresAt});if(catalog.error)throw catalog.error;
 return {token,expiresAt,profile:shippingProfile(account.profile),phone:account.phone,email:account.email||'',accessStatus:account.access_status||'pending'};
}
export async function customerAction(db:any,req:Request,owner:string,action:string,out:any){
 if(!action.startsWith('customer-'))return null;
 if(action==='customer-authorize'&&req.method==='POST'){
  if(!await requireOwner(db,req,owner))return out({error:'Operazione riservata al titolare'},403);
  let body:any;try{body=await req.json()}catch{return out({error:'Dati non validi'},400)}
  if(!/^[a-f0-9-]{36}$/i.test(body.id||'')||!['approved','blocked','pending'].includes(body.status))return out({error:'Dati non validi'},400);
  const result=await db.from('vg_shop_customers').update({access_status:body.status,updated_at:new Date().toISOString()}).eq('owner_id',owner).eq('id',body.id).select('id').maybeSingle();if(result.error)throw result.error;
  return result.data?out({ok:true}):out({error:'Cliente non trovato'},404);
 }
 if(action==='customer-google'&&req.method==='POST'){
  const jwt=(req.headers.get('authorization')||'').replace(/^Bearer\s+/i,'');
  const verified=await db.auth.getUser(jwt),user=verified.data?.user;
  // Trust only the server-verified Google identity, never client metadata or a supplied email.
  const identity=user?.identities?.find((i:any)=>i.provider==='google');
  if(verified.error||!identity||!user.email||!user.email_confirmed_at)return out({error:'Accesso Google non valido'},401);
  let body:any;try{body=await req.json()}catch{return out({error:'Dati non validi'},400)}
  if(body.privacy!==true)return out({error:'Accetta il trattamento dei dati per continuare'},400);
  let account=await db.from('vg_shop_customers').select('id,phone,profile,email,access_status').eq('owner_id',owner).eq('auth_user_id',user.id).maybeSingle();if(account.error)throw account.error;
  if(!account.data){
   const salt=hex(crypto.getRandomValues(new Uint8Array(16)));
   account=await db.from('vg_shop_customers').insert({owner_id:owner,auth_user_id:user.id,email:user.email,phone:'',phone_hash:await digest(owner+':google:'+user.id),pin_salt:salt,pin_hash:await digest(hex(crypto.getRandomValues(new Uint8Array(32)))),profile:{},access_status:'pending',privacy_at:new Date().toISOString()}).select('id,phone,profile,email,access_status').single();
   if(account.error?.code==='23505')account=await db.from('vg_shop_customers').select('id,phone,profile,email,access_status').eq('owner_id',owner).eq('auth_user_id',user.id).single();if(account.error)throw account.error;
  }
  return out(await issue(db,owner,account.data));
 }
 if(['customer-register','customer-login'].includes(action)&&req.method==='POST'){
  const raw=await req.text();if(raw.length>4000)return out({error:'Richiesta troppo grande'},413);let body:any;try{body=JSON.parse(raw)}catch{return out({error:'Richiesta non valida'},400)}
  const phone=customerPhone(body.phone),pin=typeof body.password==='string'?body.password:(typeof body.pin==='string'?body.pin:'');if(!phone||pin.length<6||pin.length>128)return out({error:'Inserisci telefono e password personale da 6 a 128 caratteri'},400);
  if(action==='customer-register'&&body.privacy!==true)return out({error:'Accetta il trattamento dei dati per creare l’account'},400);
  const phoneHash=await digest(owner+':customer:'+phone),ip=(req.headers.get('cf-connecting-ip')||req.headers.get('x-forwarded-for')||'unknown').split(',')[0].trim(),ipHash=await digest('vg-customer:'+ip),since=new Date(Date.now()-15*60000).toISOString();
  const counts=await Promise.all(['ip_hash','phone_hash'].map((column,i)=>db.from('vg_shop_customer_attempts').select('id',{count:'exact',head:true}).eq(column,i?phoneHash:ipHash).gte('attempted_at',since)));
  if(counts.some(r=>r.error))throw counts.find(r=>r.error)!.error;if(counts.some(r=>(r.count||0)>=8))return out({error:'Troppi tentativi. Riprova tra 15 minuti.'},429);
  const attempt=await db.from('vg_shop_customer_attempts').insert({ip_hash:ipHash,phone_hash:phoneHash});if(attempt.error)throw attempt.error;
  if(action==='customer-register'){
   const salt=hex(crypto.getRandomValues(new Uint8Array(16)));
   const inserted=await db.from('vg_shop_customers').insert({owner_id:owner,phone,phone_hash:phoneHash,pin_salt:salt,pin_hash:await passwordHash(pin,salt),profile:{},access_status:'pending',privacy_at:new Date().toISOString()}).select('id,phone,profile,email,access_status').single();
   if(inserted.error?.code==='23505')return out({error:'Se hai già un account con questo numero, premi Accedi.'},409);if(inserted.error)throw inserted.error;return out(await issue(db,owner,inserted.data));
  }
  const account=await db.from('vg_shop_customers').select('id,phone,profile,email,access_status,pin_salt,pin_hash').eq('owner_id',owner).eq('phone_hash',phoneHash).maybeSingle();if(account.error)throw account.error;
  // Use the same derivation even for an unknown number.
  const actual=await passwordHash(pin,account.data?.pin_salt||'00'.repeat(16));if(!account.data||!equal(actual,account.data.pin_hash))return out({error:'Numero o password non corretti'},403);
  return out(await issue(db,owner,account.data));
 }
 const session=await customerSession(db,req,owner,['customer-me','customer-logout'].includes(action));if(!session)return out({error:'Accedi alla tua area clienti',code:'CUSTOMER_LOGIN_REQUIRED'},403);
 if(action==='customer-me'&&req.method==='GET'){
  const result=await db.from('vg_shop_customers').select('phone,profile,email,access_status').eq('owner_id',owner).eq('id',session.customer_id).single();if(result.error)throw result.error;return out({phone:result.data.phone,email:result.data.email||'',accessStatus:result.data.access_status,profile:shippingProfile(result.data.profile)});
 }
 if(action==='customer-profile'&&req.method==='POST'){
  const raw=await req.text();if(raw.length>5000)return out({error:'Dati troppo grandi'},413);let body:any;try{body=JSON.parse(raw)}catch{return out({error:'Dati non validi'},400)}
  const profile=shippingProfile(body.profile);const saved=await db.from('vg_shop_customers').update({profile,updated_at:new Date().toISOString()}).eq('id',session.customer_id).eq('owner_id',owner);if(saved.error)throw saved.error;return out({ok:true,profile});
 }
 if(action==='customer-orders'&&req.method==='GET'){
  const offset=Math.max(0,Math.min(10000,Number(new URL(req.url).searchParams.get('offset'))||0));if(!Number.isInteger(offset))return out({error:'Pagina non valida'},400);
  const result=await db.from('vg_shop_customer_orders').select('order_id,items,ordini!inner(numero_ordine,created_at,updated_at,note,cliente_id,stato,stato_pagamento,pagato,totale,tracking_code,tracking_url,corriere)').eq('owner_id',owner).eq('customer_id',session.customer_id).order('created_at',{ascending:false}).order('order_id').range(offset,offset+19);if(result.error)throw result.error;
  const ids=(result.data||[]).map((r:any)=>r.ordini.cliente_id).filter(Boolean);let addresses:any[]=[];if(ids.length){const a=await db.from('clienti').select('id,nome,telefono,indirizzo,provincia,citta,cap').eq('user_id',owner).in('id',ids);if(a.error)throw a.error;addresses=a.data||[]}
  return out({orders:(result.data||[]).map((row:any)=>({id:row.order_id,customer:(()=>{const a=addresses.find(x=>x.id===row.ordini.cliente_id);return a?{name:a.nome,phone:a.telefono,address:a.indirizzo,province:a.provincia,city:a.citta,cap:a.cap}:null})(),number:row.ordini.numero_ordine,requestedAt:row.ordini.created_at,updatedAt:row.ordini.updated_at||row.ordini.created_at,editable:row.ordini.stato==='in_lavorazione'&&!row.ordini.pagato&&!String(row.ordini.note||'').includes('[STATO:'),status:row.ordini.stato,paymentStatus:row.ordini.stato_pagamento,paid:row.ordini.pagato,total:row.ordini.totale,tracking:row.ordini.tracking_code,carrier:row.ordini.corriere,items:row.items})),hasMore:(result.data||[]).length===20});
 }
 if(action==='customer-logout'&&req.method==='POST'){
  const result=await db.from('vg_shop_customer_sessions').update({expires_at:new Date().toISOString()}).eq('owner_id',owner).eq('token_hash',await digest(req.headers.get('x-vg-customer-token')||''));if(result.error)throw result.error;const catalog=await db.from('vg_shop_sessions').update({expires_at:new Date().toISOString()}).eq('owner_id',owner).eq('token_hash',await digest(req.headers.get('x-vg-customer-token')||''));if(catalog.error)throw catalog.error;return out({ok:true});
 }
 return out({error:'Operazione non valida'},405);
}
