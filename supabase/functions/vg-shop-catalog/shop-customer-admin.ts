import {requireOwner} from './shop-access.ts';
const uuid=(v:string)=>/^[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}$/i.test(v||'');
export async function customerAdminAction(db:any,req:Request,owner:string,action:string,out:any){
 if(!['customer-admin','customer-admin-read','customer-delete','customer-access-history'].includes(action))return null;
 if(!await requireOwner(db,req,owner))return out({error:'Operazione riservata al titolare del catalogo'},403);
 const url=new URL(req.url);
 if(action==='customer-admin'&&req.method==='GET'){
  const offset=Math.max(0,Math.min(100000,Number(url.searchParams.get('offset'))||0)),status=url.searchParams.get('status')||'';
  if(!Number.isInteger(offset)||status&&!['pending','approved','blocked'].includes(status))return out({error:'Filtro non valido'},400);
  const base=()=>db.from('vg_shop_customers').select('id',{count:'exact',head:true}).eq('owner_id',owner);
  let query=db.from('vg_shop_customers').select('id,phone,email,profile,access_status,created_at,last_login_at,last_access_at').eq('owner_id',owner);if(status)query=query.eq('access_status',status);
  const results=await Promise.all([base(),base().eq('access_status','pending'),db.from('vg_shop_customer_notifications').select('id',{count:'exact',head:true}).eq('owner_id',owner).is('read_at',null),query.order('created_at',{ascending:false}).order('id').range(offset,offset+49),db.from('vg_shop_customer_notifications').select('id,customer_id,created_at,read_at,vg_shop_customers(phone,email)').eq('owner_id',owner).order('created_at',{ascending:false}).order('id').limit(30)]);
  for(const r of results)if(r.error)throw r.error;
  return out({total:results[0].count||0,pending:results[1].count||0,unread:results[2].count||0,customers:(results[3].data||[]).map((c:any)=>({id:c.id,name:typeof c.profile?.name==='string'?c.profile.name:'',phone:c.phone,email:c.email,status:c.access_status,registeredAt:c.created_at,lastLoginAt:c.last_login_at,lastAccessAt:c.last_access_at})),notifications:results[4].data||[],hasMore:results[3].data?.length===50,offset});
 }
 if(action==='customer-access-history'&&req.method==='GET'){
  const id=url.searchParams.get('id')||'';if(!uuid(id))return out({error:'Cliente non valido'},400);
  const result=await db.from('vg_shop_customer_access_events').select('id,event_type,occurred_at').eq('owner_id',owner).eq('customer_id',id).order('occurred_at',{ascending:false}).order('id').limit(50);if(result.error)throw result.error;
  return out({events:result.data||[],definition:'Ultimi 50 eventi. Una visita al catalogo per sessione ogni 30 minuti; i nuovi login sono registrati separatamente.'});
 }
 if(req.method!=='POST')return out({error:'Metodo non consentito'},405);
 const raw=await req.text();if(raw.length>6000)return out({error:'Richiesta troppo grande'},413);let body:any;try{body=JSON.parse(raw)}catch{return out({error:'Richiesta non valida'},400)}
 if(action==='customer-admin-read'){
  if(!Array.isArray(body.ids)||body.ids.length>30||!body.ids.every(uuid))return out({error:'Avvisi non validi'},400);
  if(body.ids.length){const r=await db.from('vg_shop_customer_notifications').update({read_at:new Date().toISOString()}).eq('owner_id',owner).in('id',body.ids).is('read_at',null);if(r.error)throw r.error;}return out({ok:true});
 }
 if(action==='customer-delete'){
  if(!uuid(body.id)||body.confirm!==true)return out({error:'Conferma l’eliminazione dell’account'},400);
  const r=await db.rpc('vg_delete_catalog_customer',{p_owner:owner,p_customer:body.id});if(r.error)throw r.error;return r.data?out({ok:true,ordersPreserved:true}):out({error:'Cliente non trovato'},404);
 }
 return out({error:'Operazione non valida'},405);
}
export async function dispatchCustomerRegistration(db:any,owner:string,customerId:string){
 try{
  const r=await db.from('vg_shop_customer_notifications').select('id').eq('owner_id',owner).eq('customer_id',customerId).single();if(r.error)throw r.error;
  const job=fetch(Deno.env.get('SUPABASE_URL')+'/functions/v1/vg-shop-push',{method:'POST',headers:{Authorization:'Bearer '+Deno.env.get('SUPABASE_SERVICE_ROLE_KEY'),'Content-Type':'application/json'},body:JSON.stringify({action:'dispatch-customer',notificationId:r.data.id})}).catch(()=>console.error('Customer registration push pending'));
  const runtime=(globalThis as any).EdgeRuntime;if(runtime?.waitUntil)runtime.waitUntil(job);else await job;
 }catch{console.error('Customer registration notification queued for retry')}
}
