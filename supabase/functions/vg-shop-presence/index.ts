import {createClient} from 'npm:@supabase/supabase-js@2.57.4';
import {dispatchAccessNotification} from './access-notifications.ts';
const url=Deno.env.get('SUPABASE_URL')!;
const db=createClient(url,Deno.env.get('SUPABASE_SERVICE_ROLE_KEY')!,{auth:{persistSession:false}});
const owner='a51e2715-3b5e-4ad3-906e-c664fcdd486d';
const excludedCustomerIds=new Set(['d93e6255-a666-42e0-9c6b-4b59a6ff38b5']);
const origins=new Set(['https://veglamour.cloud','https://www.veglamour.cloud','https://meapps-it.github.io','https://simplexapp.it']);
const hash=async(s:string)=>Array.from(new Uint8Array(await crypto.subtle.digest('SHA-256',new TextEncoder().encode(s)))).map(x=>x.toString(16).padStart(2,'0')).join('');
function reply(origin:string|null,data:unknown,status=200){
 const headers={'Content-Type':'application/json','Cache-Control':'no-store','Vary':'Origin','Access-Control-Allow-Origin':origin&&origins.has(origin)?origin:'https://veglamour.cloud','Access-Control-Allow-Methods':'GET,POST,OPTIONS','Access-Control-Allow-Headers':'authorization,content-type,apikey,x-vg-customer-token'};
 return new Response(JSON.stringify(data),{status,headers});
}
Deno.serve(async(req)=>{
 const origin=req.headers.get('origin');
 if(origin&&!origins.has(origin))return reply(origin,{error:'Origine non autorizzata'},403);
 if(req.method==='OPTIONS')return reply(origin,{ok:true});
 const action=new URL(req.url).searchParams.get('action');
 try{
  if(req.method==='POST'&&action==='heartbeat'){
   const token=req.headers.get('x-vg-customer-token')||'';
   if(!/^[a-f0-9]{64}$/.test(token))return reply(origin,{error:'Sessione cliente richiesta'},401);
   const session=await db.from('vg_shop_customer_sessions').select('customer_id').eq('owner_id',owner).eq('token_hash',await hash(token)).gt('expires_at',new Date().toISOString()).maybeSingle();
   if(session.error)throw session.error;
   if(!session.data)return reply(origin,{error:'Sessione scaduta'},401);
   if(excludedCustomerIds.has(session.data.customer_id))return reply(origin,{ok:true,excluded:true});
   const customer=await db.from('vg_shop_customers').select('access_status,auth_user_id').eq('id',session.data.customer_id).eq('owner_id',owner).maybeSingle();
   if(customer.error)throw customer.error;
   if(customer.data?.auth_user_id===owner)return reply(origin,{ok:true,excluded:true});
   if(customer.data?.access_status!=='approved')return reply(origin,{error:'Cliente non autorizzato'},403);
   const result=await db.rpc('vg_record_presence_access',{p_owner:owner,p_customer:session.data.customer_id});
   if(result.error)throw result.error;
   if(result.data){
    const job=dispatchAccessNotification(db,owner,result.data,Deno.env.get('FCM_SERVICE_ACCOUNT')).catch(()=>console.error('Access push pending'));
    const runtime=(globalThis as any).EdgeRuntime;if(runtime?.waitUntil)runtime.waitUntil(job);else await job;
   }
   return reply(origin,{ok:true});
  }
  if(req.method==='GET'&&(action==='stats'||action==='details')){
   const jwt=(req.headers.get('authorization')||'').replace(/^Bearer\s+/i,'');
   if(!jwt)return reply(origin,{error:'Accesso amministratore richiesto'},401);
   const user=await db.auth.getUser(jwt);
   if(user.error||user.data.user?.id!==owner)return reply(origin,{error:'Accesso amministratore non autorizzato'},403);
   const since=new Date(Date.now()-5*60*1000).toISOString();
   const active=await db.from('vg_shop_customer_presence').select('customer_id,last_seen_at').eq('owner_id',owner).gte('last_seen_at',since).order('last_seen_at',{ascending:false});
   if(active.error)throw active.error;
   const online=(active.data||[]).filter(row=>!excludedCustomerIds.has(row.customer_id));
   if(action==='stats')return reply(origin,{online:online.length,windowMinutes:5});
   // Read access history in pages; merge repeated login/visit events within five minutes.
   const allEvents:{customer_id:string,event_type:string,occurred_at:string}[]=[];
   let truncated=false;
   for(let offset=0;offset<5000;offset+=1000){
     const batch=await db.from('vg_shop_customer_access_events').select('customer_id,event_type,occurred_at').eq('owner_id',owner).in('event_type',['visit','login']).order('occurred_at',{ascending:false}).range(offset,offset+999);
     if(batch.error)throw batch.error;
     allEvents.push(...(batch.data||[]));
     if((batch.data||[]).length<1000)break;
     if(offset===4000)truncated=true;
   }
   const customers=await db.from('vg_shop_customers').select('id,profile').eq('owner_id',owner).range(0,999);
   if(customers.error)throw customers.error;
   const names=new Map<string,string>();
   for(const c of customers.data||[])if(!excludedCustomerIds.has(c.id))names.set(c.id,String(c.profile?.name||'Cliente senza nome').slice(0,100));
   const sessions:{customerId:string,at:string}[]=[];
   const lastByCustomer=new Map<string,number>();
   for(const event of allEvents){
     if(excludedCustomerIds.has(event.customer_id))continue;
     const time=new Date(event.occurred_at).getTime();
     if(!Number.isFinite(time))continue;
     const last=lastByCustomer.get(event.customer_id);
     if(last!==undefined&&last-time<5*60*1000)continue;
     sessions.push({customerId:event.customer_id,at:event.occurred_at});
     lastByCustomer.set(event.customer_id,time);
   }
   const activity=new Map<string,{count:number,lastAt:string|null}>();
   for(const id of names.keys())activity.set(id,{count:0,lastAt:null});
   for(const session of sessions){
     const current=activity.get(session.customerId);
     if(!current)continue;
     current.count++;
     if(!current.lastAt)current.lastAt=session.at;
   }
   const ranking=[...activity.entries()].map(([id,v])=>({name:names.get(id)||'Cliente',visits:v.count,lastAt:v.lastAt})).sort((a,b)=>b.visits-a.visits||a.name.localeCompare(b.name,'it'));
   return reply(origin,{
     online:online.map(row=>({name:names.get(row.customer_id)||'Cliente',lastSeenAt:row.last_seen_at})),
     history:sessions.slice(0,100).map(row=>({name:names.get(row.customerId)||'Cliente',at:row.at})),
     ranking:ranking.filter(row=>row.visits>0),
     neverVisited:ranking.filter(row=>row.visits===0),
     windowMinutes:5,
     truncated
   });
  }
  return reply(origin,{error:'Richiesta non valida'},404);
 }catch(e){console.error('vg-shop-presence',e);return reply(origin,{error:'Servizio presenza temporaneamente non disponibile'},500)}
});
