import {createClient} from 'npm:@supabase/supabase-js@2';
import {JWT} from 'npm:google-auth-library@9.15.1';
import {requireOwner} from './shop-access.ts';
const project=Deno.env.get('SUPABASE_URL')!,key=Deno.env.get('SUPABASE_SERVICE_ROLE_KEY')!;
const db=createClient(project,key,{auth:{persistSession:false}}),owner='a51e2715-3b5e-4ad3-906e-c664fcdd486d';
const origins=new Set(['https://meapps-it.github.io','https://simplexapp.it']);
function clientConfig(){try{const value=JSON.parse(Deno.env.get('FCM_CLIENT_CONFIG')||'null');if(!value)return null;const client=value.client?.find((c:any)=>c.client_info?.android_client_info?.package_name==='com.vg.gestionale.seconda');const clean={projectId:value.projectId||value.project_info?.project_id,applicationId:value.applicationId||client?.client_info?.mobilesdk_app_id,senderId:value.senderId||value.project_info?.project_number,apiKey:value.apiKey||client?.api_key?.[0]?.current_key};return Object.values(clean).every(v=>typeof v==='string'&&v.length>0)?clean:null}catch{return null}}
async function dispatch(orderId:string){
 const pending=await db.from('vg_shop_notification_outbox').select('order_id,sent_at').eq('owner_id',owner).eq('order_id',orderId).single();if(pending.error)throw pending.error;if(pending.data.sent_at)return {sent:true};
 const service=JSON.parse(Deno.env.get('FCM_SERVICE_ACCOUNT')||'null');if(!service)return {sent:false,configured:false};
 if(typeof service.client_email!=='string'||typeof service.private_key!=='string'||typeof service.project_id!=='string')throw Error('Invalid server push configuration');
 const order=await db.from('ordini').select('id,numero_ordine').eq('user_id',owner).eq('id',orderId).single();if(order.error)throw order.error;
 const devices=await db.from('vg_shop_push_tokens').select('token').eq('owner_id',owner);if(devices.error)throw devices.error;if(!devices.data?.length)return {sent:false,registeredDevices:0};
 const auth=new JWT({email:service.client_email,key:service.private_key,scopes:['https://www.googleapis.com/auth/firebase.messaging']});const access=await auth.getAccessToken();if(!access.token)throw Error('Push authorization unavailable');
 let sent=0;
 for(const device of devices.data){
  const response=await fetch('https://fcm.googleapis.com/v1/projects/'+encodeURIComponent(service.project_id)+'/messages:send',{method:'POST',headers:{Authorization:'Bearer '+access.token,'Content-Type':'application/json'},body:JSON.stringify({message:{token:device.token,notification:{title:'Nuovo ordine Shop & Parallelo',body:'Richiesta '+order.data.numero_ordine},data:{orderId:orderId},android:{priority:'high',collapse_key:orderId,notification:{channel_id:'vg_orders',tag:orderId}}}})});
  if(response.ok)sent++;else{const result=await response.json().catch(()=>({}));const invalid=result.error?.details?.some((d:any)=>d.errorCode==='UNREGISTERED');if(invalid)await db.from('vg_shop_push_tokens').delete().eq('owner_id',owner).eq('token',device.token);}
 }
 if(sent){const update=await db.from('vg_shop_notification_outbox').update({sent_at:new Date().toISOString(),last_error:null}).eq('owner_id',owner).eq('order_id',orderId);if(update.error)throw update.error;}
 return {sent:sent>0,devicesNotified:sent};
}
Deno.serve(async request=>{
 const origin=request.headers.get('origin');const headers={'Access-Control-Allow-Origin':origin&&origins.has(origin)?origin:'https://meapps-it.github.io','Access-Control-Allow-Headers':'authorization,content-type,apikey','Access-Control-Allow-Methods':'GET,POST,OPTIONS','Vary':'Origin','Content-Type':'application/json','Cache-Control':'no-store'};
 const out=(value:unknown,status=200)=>new Response(JSON.stringify(value),{status,headers});
 if(origin&&!origins.has(origin))return out({error:'Origine non autorizzata'},403);
 if(request.method==='OPTIONS')return new Response(null,{status:204,headers});
 const action=new URL(request.url).searchParams.get('action');
 try{
  if(request.method==='POST'&&request.headers.get('authorization')==='Bearer '+key){const body=await request.json();if(body.action!=='dispatch'||!/^[0-9a-f-]{36}$/i.test(body.orderId||''))return out({error:'Richiesta non valida'},400);return out(await dispatch(body.orderId));}
  if(!await requireOwner(db,request,owner))return out({error:'Accesso amministratore richiesto'},403);
  if(action==='config'&&request.method==='GET')return out({configured:Boolean(Deno.env.get('FCM_SERVICE_ACCOUNT')&&clientConfig()),client:clientConfig()});
  if(action==='register'&&request.method==='POST'){
   const body=await request.json();if(typeof body.token!=='string'||body.token.length<30||body.token.length>4096)return out({error:'Dispositivo non valido'},400);
   const result=await db.from('vg_shop_push_tokens').upsert({token:body.token,owner_id:owner,updated_at:new Date().toISOString()});if(result.error)throw result.error;
   if(Deno.env.get('FCM_SERVICE_ACCOUNT')){const pending=await db.from('vg_shop_notification_outbox').select('order_id').eq('owner_id',owner).is('sent_at',null).order('created_at').limit(20);if(pending.error)throw pending.error;for(const row of pending.data||[])await dispatch(row.order_id);}
   return out({ok:true,configured:Boolean(Deno.env.get('FCM_SERVICE_ACCOUNT'))});
  }
  return out({error:'Operazione non valida'},404);
 }catch(error){console.error('VG push operation failed');return out({error:'Servizio notifiche temporaneamente non disponibile'},500)}
});
