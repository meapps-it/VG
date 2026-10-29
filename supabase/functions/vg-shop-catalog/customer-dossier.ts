import {requireOwner} from './shop-access.ts';
import {customerSession,shippingProfile} from './shop-customers.ts';
export async function dossierAction(db:any,req:Request,owner:string,action:string,out:any){
 if(!['profile-book','admin-dossier'].includes(action))return null;
 const admin=action==='admin-dossier';let id='';let body:any={};
 if(req.method==='POST'){const raw=await req.text();if(raw.length>16000)return out({error:'Dati troppo grandi'},413);try{body=JSON.parse(raw)}catch{return out({error:'Dati non validi'},400)}}else if(req.method!=='GET')return out({error:'Metodo non consentito'},405);
 if(admin){if(!await requireOwner(db,req,owner))return out({error:'Accesso riservato'},403);id=req.method==='POST'?body.id:new URL(req.url).searchParams.get('id')||'';if(!/^[a-f0-9-]{36}$/i.test(id))return out({error:'Cliente non valido'},400)}else{const session=await customerSession(db,req,owner);if(!session)return out({error:'Accedi al tuo account'},403);id=session.customer_id}
 const c=await db.from('vg_shop_customers').select('id,profile,phone,email,created_at,last_access_at').eq('id',id).eq('owner_id',owner).single();if(c.error||!c.data)return out({error:'Cliente non trovato'},404);
 if(req.method==='POST'){
  if(admin){if(body.rating!==null&&(!Number.isInteger(body.rating)||body.rating<1||body.rating>5)||typeof body.notes!=='string'||body.notes.length>4000)return out({error:'Valutazione o note non valide'},400);const init=await db.from('vg_shop_customer_details').upsert({customer_id:id,owner_id:owner},{onConflict:'customer_id',ignoreDuplicates:true});if(init.error)throw init.error;const r=await db.from('vg_shop_customer_details').update({rating:body.rating,notes:body.notes.trim(),updated_at:new Date().toISOString()}).eq('customer_id',id).eq('owner_id',owner).select('rating,notes').single();if(r.error)throw r.error;return out({ok:true,...r.data})}
  if(!Array.isArray(body.addresses)||body.addresses.length>5)return out({error:'Massimo cinque indirizzi'},400);
  const addresses=body.addresses.map((a:any)=>({...shippingProfile(a),label:typeof a.label==='string'?a.label.trim().slice(0,40):'Indirizzo'}));
  if(addresses.some((a:any)=>!a.name||!a.address||!a.city||!a.cap||!a.province))return out({error:'Completa nome, indirizzo, comune, provincia e CAP'},400);
  const init=await db.from('vg_shop_customer_details').upsert({customer_id:id,owner_id:owner},{onConflict:'customer_id',ignoreDuplicates:true});if(init.error)throw init.error;const r=await db.from('vg_shop_customer_details').update({addresses,updated_at:new Date().toISOString()}).eq('customer_id',id).eq('owner_id',owner).select('addresses').single();if(r.error)throw r.error;return out({ok:true,addresses:r.data.addresses});
 }
 const details=await db.from('vg_shop_customer_details').select(admin?'addresses,rating,notes':'addresses').eq('customer_id',id).eq('owner_id',owner).maybeSingle();if(details.error)throw details.error;
 if(!admin)return out({profile:shippingProfile(c.data.profile),phone:c.data.phone,email:c.data.email,addresses:details.data?.addresses||[]});
 const orders:any[]=[];for(let offset=0;;offset+=500){const r=await db.from('vg_shop_customer_orders').select('order_id,ordini!inner(numero_ordine,created_at,stato,totale,pagato)').eq('owner_id',owner).eq('customer_id',id).order('order_id').range(offset,offset+499);if(r.error)throw r.error;orders.push(...r.data);if(r.data.length<500)break}
 return out({customer:c.data,...details.data,orders:orders.map(x=>({id:x.order_id,...x.ordini})),total:orders.reduce((s,x)=>s+(x.ordini.stato==='annullato'?0:Number(x.ordini.totale)||0),0)});
}
