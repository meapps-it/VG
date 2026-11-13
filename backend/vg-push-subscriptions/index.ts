import {createClient} from 'npm:@supabase/supabase-js@2.57.4';
import webpush from 'npm:web-push@3.6.7';

const url=Deno.env.get('SUPABASE_URL')!;
const key=Deno.env.get('SUPABASE_SERVICE_ROLE_KEY')!;
const db=createClient(url,key,{auth:{persistSession:false}});
const owner='a51e2715-3b5e-4ad3-906e-c664fcdd486d';
const site='https://veglamour.cloud';
const origins=new Set([site,'https://www.veglamour.cloud']);
const vault=await db.rpc('vg_catalog_vapid_keys');
if(vault.error)console.error('VAPID Vault unavailable:',vault.error.message);
const pub=vault.data?.[0]?.public_key||'';
const priv=vault.data?.[0]?.private_key||'';
const subject=Deno.env.get('VG_VAPID_SUBJECT')||'mailto:info@simplexapp.it';
const configured=Boolean(pub&&priv);
if(configured)webpush.setVapidDetails(subject,pub,priv);
function reply(origin:string|null,data:unknown,status=200){
 const h={'Content-Type':'application/json','Access-Control-Allow-Origin':origin&&origins.has(origin)?origin:site,'Access-Control-Allow-Methods':'GET,POST,OPTIONS','Access-Control-Allow-Headers':'authorization,content-type,apikey','Vary':'Origin','Cache-Control':'no-store'};
 return new Response(JSON.stringify(data),{status,headers:h});
}
async function validAdmin(req:Request){
 const token=(req.headers.get('Authorization')||'').replace(/^Bearer\s+/i,'');
 if(!token)return false;
 const {data,error}=await db.auth.getUser(token);
 return !error&&data.user?.id===owner;
}
const digest=async s=>Array.from(new Uint8Array(await crypto.subtle.digest('SHA-256',new TextEncoder().encode(s)))).map(v=>v.toString(16).padStart(2,'0')).join('');
Deno.serve(async req=>{
 const origin=req.headers.get('origin');
 if(origin&&!origins.has(origin))return reply(origin,{error:'Origine non autorizzata'},403);
 if(req.method==='OPTIONS')return new Response(null,{status:204,headers:{'Access-Control-Allow-Origin':origin||site,'Access-Control-Allow-Methods':'GET,POST,OPTIONS','Access-Control-Allow-Headers':'authorization,content-type,apikey','Vary':'Origin'}});
 const action=new URL(req.url).searchParams.get('action');
 try {
  if(req.method==='GET'&&action==='config')return reply(origin,{enabled:configured,publicKey:configured?pub:null});
  if(req.method==='POST'){
   const body=await req.json().catch(()=>null);
   if(!body||typeof body!=='object')return reply(origin,{error:'Corpo non valido'},400);
   if(body.action==='subscribe'){
    if(!configured)return reply(origin,{error:'Notifiche non configurate'},503);
    const s=body.subscription;
    const endpoint=s?.endpoint, p256dh=s?.keys?.p256dh,auth=s?.keys?.auth;
    if(typeof endpoint!=='string'||endpoint.length>2048||!/^https:\/\//.test(endpoint)||typeof p256dh!=='string'||p256dh.length<40||p256dh.length>300||typeof auth!=='string'||auth.length<12||auth.length>150)return reply(origin,{error:'Iscrizione non valida'},400);
    const ip=(req.headers.get('cf-connecting-ip')||req.headers.get('x-forwarded-for')||'unknown').split(',')[0];
    const ipHash=await digest('vg-catalog-push-v1:'+ip);
    const existing=await db.from('vg_catalog_push_subscriptions').select('id').eq('endpoint',endpoint).maybeSingle();
    if(existing.error)throw existing.error;
    if(!existing.data){
     const since=new Date(Date.now()-86400000).toISOString();
     const count=await db.from('vg_catalog_push_subscriptions').select('id',{count:'exact',head:true}).eq('source_ip_hash',ipHash).gte('created_at',since);
     if(count.error)throw count.error;
     if((count.count||0)>=5)return reply(origin,{error:'Troppi dispositivi registrati. Riprova domani.'},429);
    }
    const record={endpoint,p256dh,auth_secret:auth,source_ip_hash:ipHash,enabled:true,updated_at:new Date().toISOString(),failure_count:0};
    const up=await db.from('vg_catalog_push_subscriptions').upsert(record,{onConflict:'endpoint'});
    if(up.error)throw up.error;
    return reply(origin,{ok:true});
   }
   if(body.action==='unsubscribe'){
    if(typeof body.endpoint!=='string'||body.endpoint.length>2048)return reply(origin,{error:'Endpoint non valido'},400);
    // The push endpoint is a secret bearer capability known only to its browser.
    const result=await db.from('vg_catalog_push_subscriptions').delete().eq('endpoint',body.endpoint);
    if(result.error)throw result.error;
    return reply(origin,{ok:true});
   }
   if(body.action==='send'){
    if(!await validAdmin(req))return reply(origin,{error:'Non autorizzato'},403);
    if(!configured)return reply(origin,{error:'Chiavi VAPID mancanti'},503);
    const title=String(body.title||'').trim(),message=String(body.message||'').trim();
    let dest:URL;
    try{dest=new URL(String(body.url||'/'),site)}catch{return reply(origin,{error:'Link non valido'},400)}
    if(![site,'https://www.veglamour.cloud'].includes(dest.origin)||title.length<3||title.length>90||message.length<3||message.length>220)return reply(origin,{error:'Titolo, testo o link non valido'},400);
    const sent=await db.from('vg_catalog_push_subscriptions').select('endpoint,p256dh,auth_secret').eq('enabled',true).limit(1000);
    if(sent.error)throw sent.error;
    let delivered=0,failed=0;
    for(const s of sent.data||[]){
     try{
      await webpush.sendNotification({endpoint:s.endpoint,keys:{p256dh:s.p256dh,auth:s.auth_secret}},JSON.stringify({title,body:message,url:dest.href}),{TTL:86400});
      delivered++;
      await db.from('vg_catalog_push_subscriptions').update({last_sent_at:new Date().toISOString(),failure_count:0}).eq('endpoint',s.endpoint);
     }catch(e){
      failed++;
      if(e?.statusCode===404||e?.statusCode===410)await db.from('vg_catalog_push_subscriptions').delete().eq('endpoint',s.endpoint);
      else await db.from('vg_catalog_push_subscriptions').update({failure_count:1}).eq('endpoint',s.endpoint);
     }
    }
    await db.from('vg_catalog_push_send_log').insert({sent_by:owner,title,body:message,url:dest.href,attempted:sent.data?.length||0,delivered,failed});
    return reply(origin,{ok:true,attempted:sent.data?.length||0,accepted:delivered,failed});
   }
   return reply(origin,{error:'Operazione non valida'},404);
  }
  if(req.method==='GET'&&action==='stats'){
   if(!await validAdmin(req))return reply(origin,{error:'Non autorizzato'},403);
   const count=await db.from('vg_catalog_push_subscriptions').select('id',{count:'exact',head:true}).eq('enabled',true);
   if(count.error)throw count.error;
   const history=await db.from('vg_catalog_push_send_log').select('created_at,title,attempted,delivered,failed').order('created_at',{ascending:false}).limit(10);
   if(history.error)throw history.error;
   return reply(origin,{enabled:configured,subscribers:count.count||0,history:history.data});
  }
  return reply(origin,{error:'Metodo non consentito'},405);
 }catch(e){console.error('vg-push-subscriptions',e?.message||'error');return reply(origin,{error:'Errore del servizio notifiche'},500)}
});
