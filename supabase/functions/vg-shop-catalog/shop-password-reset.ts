import {digest,passwordHash,requireOwner} from './shop-access.ts';
import {customerPhone} from './shop-customers.ts';
const random=(n:number)=>Array.from(crypto.getRandomValues(new Uint8Array(n))).map(x=>x.toString(16).padStart(2,'0')).join('');
export async function passwordResetAction(db:any,req:Request,owner:string,action:string,out:any){
 if(!['customer-reset-password','customer-reset-complete'].includes(action))return null;
 if(action==='customer-reset-password'&&!await requireOwner(db,req,owner))return out({error:'Reset riservato al titolare del catalogo'},403);
 if(req.method!=='POST')return out({error:'Metodo non consentito'},405);
 const raw=await req.text();if(raw.length>4000)return out({error:'Richiesta troppo grande'},413);let body:any;try{body=JSON.parse(raw)}catch{return out({error:'Richiesta non valida'},400)}
 if(action==='customer-reset-password'){
  if(!/^[a-f0-9-]{36}$/i.test(body.id||'')||body.confirm!==true)return out({error:'Conferma il reset della password'},400);
  const code=random(10).toUpperCase(),salt=random(16),r=await db.rpc('vg_start_password_reset',{p_owner:owner,p_customer:body.id,p_code_hash:await digest(code),p_salt:salt,p_disabled_hash:await digest(random(32))});
  if(r.error)throw r.error;if(!r.data)return out({error:'Account non trovato o accesso solo Google. La password Google si recupera tramite Google.'},409);
  return out({ok:true,code:code.match(/.{1,5}/g)!.join('-'),expiresAt:new Date(Date.now()+3600000).toISOString(),singleUse:true});
 }
 const phone=customerPhone(body.phone),code=typeof body.code==='string'?body.code.replace(/[\s-]/g,'').toUpperCase():'',password=body.password;
 if(!phone||!/^[A-F0-9]{20}$/.test(code)||typeof password!=='string'||password.length<6||password.length>128)return out({error:'Inserisci telefono, codice reset e nuova password di almeno 6 caratteri'},400);
 const phoneHash=await digest(owner+':customer:'+phone),ip=(req.headers.get('cf-connecting-ip')||req.headers.get('x-forwarded-for')||'unknown').split(',')[0].trim(),ipHash=await digest('vg-customer-reset:'+ip),since=new Date(Date.now()-15*60000).toISOString();
 const counts=await Promise.all(['ip_hash','phone_hash'].map((column,i)=>db.from('vg_shop_customer_attempts').select('id',{count:'exact',head:true}).eq(column,i?phoneHash:ipHash).gte('attempted_at',since)));for(const r of counts)if(r.error)throw r.error;
 if(counts.some(r=>(r.count||0)>=8))return out({error:'Troppi tentativi. Riprova tra 15 minuti.'},429);
 const attempt=await db.from('vg_shop_customer_attempts').insert({ip_hash:ipHash,phone_hash:phoneHash});if(attempt.error)throw attempt.error;
 const salt=random(16),r=await db.rpc('vg_complete_password_reset',{p_owner:owner,p_phone_hash:phoneHash,p_code_hash:await digest(code),p_salt:salt,p_password_hash:await passwordHash(password,salt)});if(r.error)throw r.error;
 return r.data?out({ok:true}):out({error:'Codice reset non valido, scaduto o già utilizzato'},400);
}
