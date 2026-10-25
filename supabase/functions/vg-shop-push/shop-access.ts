const enc=new TextEncoder();
const hex=(bytes:ArrayBuffer|Uint8Array)=>Array.from(bytes instanceof Uint8Array?bytes:new Uint8Array(bytes)).map(x=>x.toString(16).padStart(2,'0')).join('');
export const digest=async(value:string)=>hex(await crypto.subtle.digest('SHA-256',enc.encode(value)));
export async function passwordHash(password:string,salt:string,iterations=200000){
 const bytes=Uint8Array.from(salt.match(/../g)||[],x=>parseInt(x,16));
 const key=await crypto.subtle.importKey('raw',enc.encode(password),'PBKDF2',false,['deriveBits']);
 return hex(await crypto.subtle.deriveBits({name:'PBKDF2',salt:bytes,iterations,hash:'SHA-256'},key,256));
}
function constantEqual(a:string,b:string){let difference=a.length^b.length;for(let i=0;i<Math.max(a.length,b.length);i++)difference|=(a.charCodeAt(i)||0)^(b.charCodeAt(i)||0);return difference===0}
export async function requireOwner(db:any,request:Request,owner:string){
 const token=(request.headers.get('authorization')||'').replace(/^Bearer\s+/i,'');
 if(!token)return false;
 const result=await db.auth.getUser(token);return !result.error&&result.data?.user?.id===owner;
}
async function createSession(db:any,owner:string,administrator=false){
 const token=hex(crypto.getRandomValues(new Uint8Array(32))),tokenHash=await digest(token);
 const expiresAt=new Date(Date.now()+365*86400000).toISOString();
 const {error}=await db.from('vg_shop_sessions').insert({token_hash:tokenHash,owner_id:owner,is_admin:administrator,expires_at:expiresAt});if(error)throw error;
 return {token,expiresAt,administrator};
}
export async function accessAction(db:any,req:Request,owner:string,action:string,out:any){
 if(action==='login'&&req.method==='POST'){
  const ip=(req.headers.get('cf-connecting-ip')||req.headers.get('x-forwarded-for')||'unknown').split(',')[0].trim();
  const ipHash=await digest('vg-shop-login:'+ip),since=new Date(Date.now()-15*60000).toISOString();
  const rate=await db.from('vg_shop_login_attempts').select('id',{count:'exact',head:true}).eq('ip_hash',ipHash).gte('attempted_at',since);if(rate.error)throw rate.error;
  if((rate.count||0)>=8)return out({error:'Troppi tentativi. Riprova tra 15 minuti.'},429);
  let body:any;try{body=await req.json()}catch{return out({error:'Richiesta non valida'},400)}
  const password=typeof body.password==='string'?body.password:'';
  if(!password||password.length>128)return out({error:'Password non valida'},401);
  const attempt=await db.from('vg_shop_login_attempts').insert({ip_hash:ipHash});if(attempt.error)throw attempt.error;
  const setting=await db.from('vg_shop_password').select('salt,password_hash,iterations').eq('owner_id',owner).single();if(setting.error)throw setting.error;
  const hash=await passwordHash(password,setting.data.salt,setting.data.iterations);
  if(!constantEqual(hash,setting.data.password_hash))return out({error:'Password non valida'},401);
  return out(await createSession(db,owner));
 }
 if(action==='claim-admin'&&req.method==='POST'){
  const admin=await catalogSession(db,req,owner);if(!admin?.is_admin)return out({error:'Anteprima amministratore richiesta'},403);
  let body:any;try{body=await req.json()}catch{return out({error:'Richiesta non valida'},400)}
  if(typeof body.previousToken!=='string'||!/^[a-f0-9]{64}$/.test(body.previousToken))return out({ok:true});
  const result=await db.from('vg_shop_sessions').update({is_admin:true}).eq('owner_id',owner).eq('token_hash',await digest(body.previousToken));if(result.error)throw result.error;
  return out({ok:true});
 }
 if(['admin-session','set-password','stats'].includes(action)){
  if(!await requireOwner(db,req,owner))return out({error:'Accesso amministratore richiesto'},403);
  if(action==='admin-session'&&req.method==='POST')return out(await createSession(db,owner,true));
  if(action==='set-password'&&req.method==='POST'){
   let body:any;try{body=await req.json()}catch{return out({error:'Richiesta non valida'},400)}
   const password=typeof body.password==='string'?body.password:'';
   if(password.length<10||password.length>128)return out({error:'Usa una password di almeno 10 caratteri'},400);
   const salt=hex(crypto.getRandomValues(new Uint8Array(16)));
   const result=await db.from('vg_shop_password').upsert({owner_id:owner,salt,password_hash:await passwordHash(password,salt),iterations:200000,updated_at:new Date().toISOString()});if(result.error)throw result.error;
   return out({ok:true});
  }
  if(action==='stats'&&req.method==='GET')return out(await statistics(db,owner));
  return out({error:'Metodo non consentito'},405);
 }
 return null;
}
export async function catalogSession(db:any,req:Request,owner:string){
 const token=(req.headers.get('authorization')||'').replace(/^Bearer\s+/i,'');
 if(!/^[a-f0-9]{64}$/.test(token))return null;
 const tokenHash=await digest(token);
 const session=await db.from('vg_shop_sessions').select('token_hash,is_admin,created_at,expires_at').eq('token_hash',tokenHash).eq('owner_id',owner).gt('expires_at',new Date().toISOString()).maybeSingle();if(session.error)throw session.error;
 if(!session.data)return null;
 if(!session.data.is_admin){const setting=await db.from('vg_shop_password').select('updated_at').eq('owner_id',owner).single();if(setting.error)throw setting.error;if(new Date(session.data.created_at).getTime()<new Date(setting.data.updated_at).getTime())return null;}
 return session.data;
}
export async function trackVisit(db:any,owner:string,session:any){
 if(session.is_admin)return;
 const windowStart=new Date(Math.floor(Date.now()/1800000)*1800000).toISOString();
 const {error}=await db.from('vg_shop_visits').upsert({owner_id:owner,session_hash:session.token_hash,window_start:windowStart},{onConflict:'session_hash,window_start',ignoreDuplicates:true});
 if(error)console.error('VG shop analytics unavailable');
}
async function statistics(db:any,owner:string){
 const day=(date:Date)=>new Intl.DateTimeFormat('en-CA',{timeZone:'Europe/Rome',year:'numeric',month:'2-digit',day:'2-digit'}).format(date);
 const today=day(new Date()),start=new Date(Date.now()-31*86400000).toISOString(),rows:any[]=[];
 for(let offset=0;;offset+=500){const result=await db.from('vg_shop_visits').select('session_hash,window_start,vg_shop_sessions!inner(is_admin)').eq('vg_shop_sessions.is_admin',false).eq('owner_id',owner).gte('window_start',start).order('window_start').order('id').range(offset,offset+499);if(result.error)throw result.error;rows.push(...result.data||[]);if((result.data||[]).length<500)break;}
 const midday=new Date(today+'T12:00:00Z').getTime();const days=Array.from({length:30},(_,i)=>day(new Date(midday-(29-i)*86400000)));
 const daily=days.map(date=>({day:date,visits:rows.filter(r=>day(new Date(r.window_start))===date).length}));
 const todayRows=rows.filter(r=>day(new Date(r.window_start))===today),week=new Set(days.slice(-7)),month=new Set(days);
 const devices=await db.from('vg_shop_push_tokens').select('token',{count:'exact',head:true}).eq('owner_id',owner);if(devices.error)throw devices.error;
 return {today:todayRows.length,week:rows.filter(r=>week.has(day(new Date(r.window_start)))).length,month:daily.reduce((n,d)=>n+d.visits,0),devicesMonth:new Set(rows.filter(r=>month.has(day(new Date(r.window_start)))).map(r=>r.session_hash)).size,daily,excludesAdministrator:true,registeredPushDevices:devices.count||0,pushConfigured:Boolean(Deno.env.get('FCM_SERVICE_ACCOUNT')),definition:'Una visita per dispositivo ogni 30 minuti. Anteprime amministratore escluse.'};
}
