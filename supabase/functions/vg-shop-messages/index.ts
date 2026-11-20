import {createClient} from 'npm:@supabase/supabase-js@2.57.4';
import {JWT} from 'npm:google-auth-library@9.15.1';
import webpush from 'npm:web-push@3.6.7';
import {requireOwner,digest} from '../vg-shop-catalog/shop-access.ts';

const owner='a51e2715-3b5e-4ad3-906e-c664fcdd486d';
const db=createClient(Deno.env.get('SUPABASE_URL')!,Deno.env.get('SUPABASE_SERVICE_ROLE_KEY')!,{auth:{persistSession:false}});
const origins=new Set(['https://veglamour.cloud','https://www.veglamour.cloud','https://meapps-it.github.io','https://simplexapp.it']);
const uuid=/^[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}$/i;
const catalog='https://veglamour.cloud/catalogo-nuovo.html?messaggi=1';
const mailKey=Deno.env.get('RESEND_API_KEY');
const mailFrom=Deno.env.get('VG_MESSAGES_EMAIL_FROM');
const fields='id,customer_id,topic,subject,sender,body,created_at,read_at';

async function pushKeys(){
 const result=await db.rpc('vg_catalog_vapid_keys');
 const row=result.data?.[0];
 return row?.public_key&&row?.private_key?{publicKey:row.public_key,privateKey:row.private_key}:null;
}


async function customerId(req:Request){
 const token=req.headers.get('x-vg-customer-token')||'';
 if(!/^[a-f0-9]{64}$/.test(token))return null;
 const session=await db.from('vg_shop_customer_sessions').select('customer_id').eq('owner_id',owner).eq('token_hash',await digest(token)).gt('expires_at',new Date().toISOString()).maybeSingle();
 if(session.error)throw session.error;
 if(!session.data)return null;
 const account=await db.from('vg_shop_customers').select('id').eq('id',session.data.customer_id).eq('owner_id',owner).eq('access_status','approved').maybeSingle();
 if(account.error)throw account.error;
 return account.data?.id||null;
}

function validSubscription(value:any){
 if(typeof value?.endpoint!=='string'||value.endpoint.length>2048||!/^[-_a-zA-Z0-9]{87}$/.test(value.keys?.p256dh||'')||!/^[-_a-zA-Z0-9]{22}$/.test(value.keys?.auth||''))return false;
 try{
  const url=new URL(value.endpoint);
  return url.protocol==='https:'&&!url.port&&!url.username&&!url.password
   && ['fcm.googleapis.com','updates.push.services.mozilla.com','web.push.apple.com','wns.windows.com'].some(host=>url.hostname===host||url.hostname.endsWith('.'+host));
 }catch{return false}
}

async function notifyOwner(id:string){
 const raw=Deno.env.get('FCM_SERVICE_ACCOUNT');
 if(!raw)return false;
 const service=JSON.parse(raw);
 if(!service.client_email||!service.private_key||!service.project_id)return false;
 const devices=await db.from('vg_shop_push_tokens').select('token').eq('owner_id',owner);
 if(devices.error||!devices.data?.length)return false;
 const auth=new JWT({email:service.client_email,key:service.private_key,scopes:['https://www.googleapis.com/auth/firebase.messaging']});
 const access=await auth.getAccessToken();
 if(!access.token)return false;
 const endpoint='https://fcm.googleapis.com/v1/projects/'+encodeURIComponent(service.project_id)+'/messages:send';
 const sent=await Promise.allSettled(devices.data.map(async ({token})=>{
  const response=await fetch(endpoint,{method:'POST',headers:{Authorization:'Bearer '+access.token,'Content-Type':'application/json'},body:JSON.stringify({
   message:{token,notification:{title:'Nuovo messaggio dal catalogo',body:'Apri Messaggi catalogo per leggere e rispondere.'},
    data:{type:'catalog_message',messageId:id},android:{priority:'high',notification:{channel_id:'vg_orders',tag:'catalog-message-'+id}}}
  }),signal:AbortSignal.timeout(5000)});
  if(!response.ok)console.warn('VG admin message push failed',response.status);
  return response.ok;
 }));
 return sent.some(result=>result.status==='fulfilled'&&result.value);
}

async function notify(customer:string){
 const preference=await db.from('vg_catalog_message_alerts').select('email,subscription').eq('owner_id',owner).eq('customer_id',customer).maybeSingle();
 if(preference.error)throw preference.error;
 const alerts=preference.data;
 if(!alerts)return false;
 const tasks=[];
 const keys=alerts.subscription?await pushKeys():null;
 if(alerts.subscription&&keys){
  const {destination,...subscription}=alerts.subscription;
  tasks.push(webpush.sendNotification(subscription,JSON.stringify({title:'Vanity & Glamour',body:'Hai una nuova risposta nel catalogo.',url:destination||catalog,tag:'vg-messages'}),{
   vapidDetails:{subject:Deno.env.get('VG_VAPID_SUBJECT')||'mailto:info@simplexapp.it',...keys},TTL:86400,timeout:5000
  }).then(()=>true).catch(async error=>{
   if([404,410].includes(error.statusCode))await db.from('vg_catalog_message_alerts').update({subscription:null}).eq('owner_id',owner).eq('customer_id',customer).eq('subscription->>endpoint',alerts.subscription.endpoint);
   return false;
  }));
 }
 if(alerts.email&&mailKey&&mailFrom){
  tasks.push(fetch('https://api.resend.com/emails',{method:'POST',headers:{Authorization:'Bearer '+mailKey,'Content-Type':'application/json'},body:JSON.stringify({
   from:mailFrom,to:[alerts.email],subject:'Hai una risposta da Vanity & Glamour',text:'Accedi al catalogo per leggere la risposta: '+catalog
  }),signal:AbortSignal.timeout(5000)}).then(response=>response.ok).catch(()=>false));
 }
 return (await Promise.all(tasks)).some(Boolean);
}

Deno.serve(async req=>{
 const origin=req.headers.get('origin');
 const headers={'Access-Control-Allow-Origin':origin&&origins.has(origin)?origin:'https://veglamour.cloud','Access-Control-Allow-Methods':'GET,POST,OPTIONS','Access-Control-Allow-Headers':'authorization,content-type,x-vg-customer-token','Content-Type':'application/json','Cache-Control':'no-store','Vary':'Origin'};
 const out=(data:unknown,status=200)=>new Response(status===204?null:JSON.stringify(data),{status,headers});
 if(origin&&!origins.has(origin))return out({error:'Origine non autorizzata'},403);
 if(req.method==='OPTIONS')return out(null,204);
 try{
  const url=new URL(req.url), action=url.searchParams.get('action')||'';
  const admin=action.startsWith('admin-');
  const customer=admin?null:await customerId(req);
  if(admin?!await requireOwner(db,req,owner):!customer)return out({error:'Accedi con un account autorizzato.'},403);

  if(req.method==='GET'&&(action==='list'||action==='admin-list')){
   const page=Number(url.searchParams.get('page')||0);
   if(!Number.isSafeInteger(page)||page<0||page>10000)return out({error:'Pagina non valida'},400);
   let q=db.from('vg_catalog_messages').select(admin?fields+',vg_shop_customers(profile,phone,email)':fields).eq('owner_id',owner);
   if(customer)q=q.eq('customer_id',customer);
   const rows=await q.order('created_at',{ascending:false}).order('id',{ascending:false}).range(page*50,page*50+50);
   if(rows.error)throw rows.error;
   const preference=customer&&page===0
    ? await db.from('vg_catalog_message_alerts').select('email').eq('owner_id',owner).eq('customer_id',customer).maybeSingle()
    : null;
   if(preference?.error)throw preference.error;
   return out({items:rows.data.slice(0,50),more:rows.data.length>50,pushKey:customer?(await pushKeys())?.publicKey||null:null,emailEnabled:Boolean(customer&&mailKey&&mailFrom),email:preference?.data?.email||null});
  }
  if(req.method!=='POST')return out({error:'Operazione non valida'},405);
  let body:any;
  try{body=await req.json()}catch{return out({error:'Dati non validi'},400)}
  if(!body||typeof body!=='object')return out({error:'Dati non validi'},400);
  if(action==='alerts'){
   const update:Record<string,unknown>={owner_id:owner,customer_id:customer};
   if(Object.hasOwn(body,'email')){
    const email=typeof body.email==='string'?body.email.trim():'';
    if(email&&(!mailKey||!mailFrom||email.length>254||!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)))return out({error:'Email non valida o servizio non disponibile.'},400);
    update.email=email||null;
   }
   if(Object.hasOwn(body,'subscription')){
    if(!validSubscription(body.subscription)||!await pushKeys())return out({error:'Notifiche non disponibili su questo dispositivo.'},400);
    const {endpoint,keys:{p256dh,auth}}=body.subscription;
    const path=origin==='https://meapps-it.github.io'?'/VG/catalogo-nuovo.html':origin==='https://simplexapp.it'?'/shop':'/';
    const destination=origin?new URL(path+'?messaggi=1',origin).href:catalog;
    update.subscription={endpoint,keys:{p256dh,auth},destination};
   }
   const save=await db.from('vg_catalog_message_alerts').upsert(update,{onConflict:'owner_id,customer_id'});
   if(save.error?.code==='23505')return out({error:'Questo dispositivo riceve già notifiche per un altro account.'},409);
   if(save.error)throw save.error;
   return out({ok:true});
  }
  if(action==='logout-alerts'){
   const result=await db.from('vg_catalog_message_alerts').update({subscription:null}).eq('owner_id',owner).eq('customer_id',customer);
   if(result.error)throw result.error;
   return out({ok:true});
  }
  if(action==='read'||action==='admin-read'){
   const ids=Array.isArray(body.ids)?body.ids.filter((id:unknown)=>typeof id==='string'&&uuid.test(id)).slice(0,50):[];
   if(!ids.length)return out({ok:true});
   let q=db.from('vg_catalog_messages').update({read_at:new Date().toISOString()}).eq('owner_id',owner).eq('sender',admin?'customer':'shop').is('read_at',null).in('id',ids);
   if(customer)q=q.eq('customer_id',customer);
   const result=await q;if(result.error)throw result.error;
   return out({ok:true});
  }
  if(action!=='send'&&action!=='admin-send')return out({error:'Operazione non valida'},404);
  const message=typeof body.message==='string'?body.message.trim():'';
  if(!message||message.length>2000)return out({error:'Scrivi un messaggio da 1 a 2000 caratteri.'},400);
  let target=customer,topic='general',subject='Informazioni generali';
  if(body.replyTo){
   if(typeof body.replyTo!=='string'||!uuid.test(body.replyTo))return out({error:'Conversazione non valida'},400);
   let q=db.from('vg_catalog_messages').select('customer_id,topic,subject').eq('owner_id',owner).eq('id',body.replyTo);
   if(customer)q=q.eq('customer_id',customer);
   const prior=await q.maybeSingle();if(prior.error)throw prior.error;
   if(!prior.data)return out({error:'Conversazione non trovata'},404);
   ({customer_id:target,topic,subject}=prior.data);
  }else if(admin){
   return out({error:'Indica la conversazione'},400);
  }else if(body.productId){
   if(typeof body.productId!=='string'||!uuid.test(body.productId))return out({error:'Articolo non valido'},400);
   const product=await db.from('prodotti').select('id,nome,sku').eq('id',body.productId).eq('user_id',owner).eq('stato_pubblicazione','pubblicato').eq('attivo',true).eq('is_demo',false).maybeSingle();
   if(product.error)throw product.error;
   if(!product.data)return out({error:'Articolo non disponibile'},404);
   topic=product.data.id;subject=[product.data.nome,product.data.sku].filter(Boolean).join(' · ');
  }
  const id=typeof body.id==='string'&&uuid.test(body.id)?body.id:crypto.randomUUID();
  const sent=await db.from('vg_catalog_messages').insert({id,owner_id:owner,customer_id:target,topic,subject,sender:admin?'shop':'customer',body:message});
  if(sent.error?.code==='23505'){
   const prior=await db.from('vg_catalog_messages').select('id').eq('id',id).eq('owner_id',owner).eq('customer_id',target).eq('sender',admin?'shop':'customer').eq('topic',topic).eq('body',message).maybeSingle();
   if(prior.error)throw prior.error;
   if(prior.data)return out({ok:true});
  }
  if(sent.error)throw sent.error;
  const notified=await (admin?notify(target!):notifyOwner(id)).catch(error=>{
   console.warn('VG message alert failed',error instanceof Error?error.name:'unknown');
   return false;
  });
  return out({ok:true,notified});
 }catch(error){
  console.error('VG messages operation failed',error instanceof Error?error.name:'database');
  return out({error:'Messaggistica temporaneamente non disponibile. Riprova.'},500);
 }
});
