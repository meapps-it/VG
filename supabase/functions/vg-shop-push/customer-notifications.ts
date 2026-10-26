import {JWT} from 'npm:google-auth-library@9.15.1';
export async function dispatchCustomerNotification(db:any,owner:string,id:string,raw:string|undefined){
 const row=await db.from('vg_shop_customer_notifications').select('id,customer_id,sent_at').eq('owner_id',owner).eq('id',id).single();if(row.error)throw row.error;if(row.data.sent_at)return {sent:true};
 const service=JSON.parse(raw||'null');
 if(!service){await db.from('vg_shop_customer_notifications').update({last_error:'FCM non configurato'}).eq('owner_id',owner).eq('id',id);return {sent:false,configured:false};}
 if(!service.client_email||!service.private_key||!service.project_id)throw Error('Invalid push configuration');
 const devices=await db.from('vg_shop_push_tokens').select('token').eq('owner_id',owner);if(devices.error)throw devices.error;
 if(!devices.data?.length){await db.from('vg_shop_customer_notifications').update({last_error:'Nessun telefono registrato'}).eq('owner_id',owner).eq('id',id);return {sent:false,registeredDevices:0};}
 const auth=new JWT({email:service.client_email,key:service.private_key,scopes:['https://www.googleapis.com/auth/firebase.messaging']}),access=await auth.getAccessToken();if(!access.token)throw Error('Push authorization unavailable');let sent=0;
 for(const device of devices.data){
  const response=await fetch('https://fcm.googleapis.com/v1/projects/'+encodeURIComponent(service.project_id)+'/messages:send',{method:'POST',headers:{Authorization:'Bearer '+access.token,'Content-Type':'application/json'},body:JSON.stringify({message:{token:device.token,notification:{title:'Nuovo cliente Shop & Parallelo',body:'Un cliente si è registrato. Apri Clienti catalogo per autorizzarlo.'},data:{type:'customer_registered',customerId:row.data.customer_id,notificationId:id},android:{priority:'high',collapse_key:id,notification:{channel_id:'vg_orders',tag:'customer-'+id}}}})});
  if(response.ok)sent++;else{const error=await response.json().catch(()=>({}));if(error.error?.details?.some((d:any)=>d.errorCode==='UNREGISTERED'))await db.from('vg_shop_push_tokens').delete().eq('owner_id',owner).eq('token',device.token);}
 }
 const result=await db.from('vg_shop_customer_notifications').update(sent?{sent_at:new Date().toISOString(),last_error:null}:{last_error:'Invio FCM non riuscito'}).eq('owner_id',owner).eq('id',id);if(result.error)throw result.error;
 return {sent:sent>0,devicesNotified:sent};
}
