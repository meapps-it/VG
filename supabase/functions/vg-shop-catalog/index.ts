import {articleOptions,requestedVariant,articleMetadata} from "./shop-variants.ts";
import {customerAction,customerSession,shippingProfile} from "./shop-customers.ts";
import {accessAction,catalogSession,trackVisit} from "./shop-access.ts";
import { createClient } from "npm:@supabase/supabase-js@2.57.4";
const PROJECT = Deno.env.get("SUPABASE_URL")!;
const KEY = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;
const db = createClient(PROJECT, KEY, { auth:{persistSession:false} });
const OWNER = "a51e2715-3b5e-4ad3-906e-c664fcdd486d";
const SITE = "https://meapps-it.github.io";
const ALLOWED_ORIGINS=new Set([SITE,"https://simplexapp.it"]);
function valid(value:unknown,max:number){return typeof value==="string"?value.trim().slice(0,max):""}
const published=(q:any)=>q.eq("user_id",OWNER).eq("attivo",true).eq("disponibile",true).eq("stato_pubblicazione","pubblicato").eq("is_demo",false);
Deno.serve(async(req)=>{
 const origin=req.headers.get("origin");
 const cors={"Access-Control-Allow-Origin":origin&&ALLOWED_ORIGINS.has(origin)?origin:SITE,"Access-Control-Allow-Headers":"content-type,apikey,authorization,x-vg-customer-token","Access-Control-Allow-Methods":"GET,POST,OPTIONS","Vary":"Origin"};
 function out(data:unknown,status=200){return new Response(JSON.stringify(data),{status,headers:{...cors,"Content-Type":"application/json; charset=utf-8","Cache-Control":"no-store"}})}
 function fail(message:string,status=400){return out({error:message},status)}
 try{
  if(origin&&!ALLOWED_ORIGINS.has(origin))return fail("Origine non autorizzata",403);
  if(req.method==="OPTIONS")return new Response(null,{status:204,headers:cors});
  const url=new URL(req.url);
  const action=url.searchParams.get("action")||"";
  const handled=await accessAction(db,req,OWNER,action,out);if(handled)return handled;
  if(['customer-login','customer-register','customer-google','customer-me','customer-logout','customer-authorize'].includes(action)){const result=await customerAction(db,req,OWNER,action,out);if(result)return result;}
  const viewer=await catalogSession(db,req,OWNER);if(!viewer)return fail("Accedi con un account autorizzato dal negozio",401);
  const customerHandled=await customerAction(db,req,OWNER,action,out);if(customerHandled)return customerHandled;

  if(req.method==="GET"){
    if(url.searchParams.get("action")!=="catalog")return fail("Operazione non valida",404);
    const {data,error}=await published(db.from("prodotti").select("id,sku,nome,descrizione,descrizione_app,marca,qualita,fornitore,prezzo_vendita,in_promozione,prezzo_promozionale,categoria_id,giacenza,colore,taglia,tipo_misura,valore_misura,dimensioni").order("nome",{ascending:true}).limit(400));
    if(error)throw error;
    const ids=(data||[]).map((x:any)=>x.id);
    // Page explicitly: PostgREST's row cap must not silently truncate galleries.
    const photos:any[]=[];
    if(ids.length)for(let offset=0;;offset+=500){
      const page=await db.from("prodotti_foto").select("id,prodotto_id,path,ordine").eq("user_id",OWNER).in("prodotto_id",ids)
        .order("prodotto_id").order("ordine",{ascending:true,nullsFirst:false}).order("id").range(offset,offset+499);
      if(page.error)throw page.error;
      photos.push(...(page.data||[]));
      if((page.data||[]).length<500)break;
    }
    const {data:categories,error:categoryError}=await db.from("categorie").select("id,nome").eq("user_id",OWNER);
    if(categoryError)throw categoryError;
    const categoryMap=new Map((categories||[]).map((c:any)=>[c.id,c.nome]));
    const photoMap=new Map<string,string[]>();
    for(const p of photos){
      if(!p.path)continue;
      const paths=photoMap.get(p.prodotto_id)||[];
      if(!paths.includes(p.path))paths.push(p.path);
      photoMap.set(p.prodotto_id,paths);
    }
    // Some legacy articles only retain their original Storage paths in VGMETA.
    // Merge those associated references after the ordered photo rows; never guess files.
    for(const p of data||[]){
      const meta=articleMetadata(p);
      const legacy=Array.isArray(meta.foto)?meta.foto:Array.isArray(meta.fotoCloud)?meta.fotoCloud:[];
      const associated=legacy.filter((path:unknown)=>typeof path==="string"&&!path.includes("..")&&!path.includes("://")&&(path.startsWith(String(p.sku)+"/")||path.startsWith(OWNER+"/")));
      photoMap.set(p.id,[...new Set([...(photoMap.get(p.id)||[]),...associated])]);
    }
    const paths=[...new Set([...photoMap.values()].flat())];
    const signed=new Map<string,string>();
    const imagesExpiresAt=Date.now()+3600*1000;
    // Batch signing preserves private buckets and avoids one request per photograph.
    for(let i=0;i<paths.length;i+=100){
      const result=await db.storage.from("articoli").createSignedUrls(paths.slice(i,i+100),3600);
      if(result.error)throw result.error;
      for(const photo of result.data||[]){
        if(photo.path&&photo.signedUrl&&!photo.error)signed.set(photo.path,photo.signedUrl);
      }
    }
    const items=(data||[]).map((p:any)=>{
      const images=(photoMap.get(p.id)||[]).map(path=>signed.get(path)||"").filter(Boolean);
      const image=images[0]||"";
      const promo=Boolean(p.in_promozione)&&Number(p.prezzo_promozionale)>0&&Number(p.prezzo_promozionale)<Number(p.prezzo_vendita);
      const rawDescription=String(p.descrizione_app||p.descrizione||"");
      const meta=articleMetadata(p);
      const brand=valid(p.marca||meta?.brand||"",100);
      const supplier=String(p.fornitore||meta?.fornitore||"").trim().toLowerCase();
      const quality=supplier==="jayden"?"ORIGINALE":"STANDARD";
      const publicRaw=String(p.descrizione_app||((typeof meta.descrizione==='string'&&meta.descrizione)?meta.descrizione:rawDescription));
      const description=publicRaw.replace(/\[VGMETA\][\s\S]*?\[\/VGMETA\]/gi,' ').replace(/\[VGMETA\][\s\S]*$/gi,'').replace(/\\n/g,'\n').split(/\r?\n/).filter(line=>!/(?:costo|prezzo).*?(?:\busd\b|fornitor|jayden)/i.test(line)).join('\n').trim();
      return {id:p.id,sku:p.sku,name:p.nome,description,brand,quality,category:categoryMap.get(p.categoria_id)||valid(meta.categoria,100)||"",options:articleOptions(p),price:promo?Number(p.prezzo_promozionale):Number(p.prezzo_vendita),originalPrice:Number(p.prezzo_vendita),promotion:promo,image,images,availability:p.giacenza>0?"disponibile":"su richiesta"};
    });
    await trackVisit(db,OWNER,viewer);
    return out({items,imagesExpiresAt,administrator:viewer.is_admin});
  }
  if(req.method!=="POST")return fail("Metodo non consentito",405);
  const length=Number(req.headers.get("content-length")||0);
  if(length>12000)return fail("Richiesta troppo grande",413);
  let body:any;try{body=await req.json()}catch{return fail("Richiesta non valida")}
  if(body?.website)return out({ok:true,message:"Richiesta ricevuta"}); // honeypot
  const account=await customerSession(db,req,OWNER);
  if(req.headers.get('x-vg-customer-token')&&!account)return out({error:'Accedi di nuovo alla tua area clienti',code:'CUSTOMER_LOGIN_REQUIRED'},403);
  const customer=body?.customer||{};
  const name=valid(customer.name,100),phone=valid(customer.phone,40),address=valid(customer.address,200),city=valid(customer.city,100),cap=valid(customer.cap,12),province=valid(customer.province,100),notes=valid(customer.notes,500);
  if(body.privacy!==true)return fail("Il consenso privacy è obbligatorio");
  if(!province||!cap||cap.length<3)return fail("Inserisci provincia e CAP corretti");
  if(name.length<3||phone.replace(/\D/g,"").length<6||address.length<5||city.length<2)return fail("Inserisci nome, telefono, indirizzo e città corretti");
  const raw=Array.isArray(body?.items)?body.items:[];
  if(raw.length<1||raw.length>30)return fail("Carrello vuoto o troppo grande");
  const selected=new Map<string,number>();
  for(const entry of raw){const id=valid(entry?.id,60),qty=Number(entry?.qty);if(!/^[0-9a-f-]{36}$/i.test(id)||!Number.isInteger(qty)||qty<1||qty>20)return fail("Articolo o quantità non validi");selected.set(id,(selected.get(id)||0)+qty)}
  if([...selected.values()].some(n=>n>20))return fail("Quantità non valida");
  const ip=(req.headers.get("cf-connecting-ip")||req.headers.get("x-forwarded-for")||"unknown").split(",")[0].trim();
  const bytes=await crypto.subtle.digest("SHA-256",new TextEncoder().encode("vg-catalog-v1:"+ip));
  const hash=Array.from(new Uint8Array(bytes)).map(x=>x.toString(16).padStart(2,"0")).join("");
  const since=new Date(Date.now()-15*60*1000).toISOString();
  const {count, error:rateError}=await db.from("vg_catalog_order_rate").select("id",{count:"exact",head:true}).eq("source_hash",hash).gte("created_at",since);
  if(rateError)throw rateError;
  if((count||0)>=3)return fail("Troppe richieste. Riprova più tardi",429);
  const {error:attemptError}=await db.from("vg_catalog_order_rate").insert({source_hash:hash});
  if(attemptError)throw attemptError;
  const ids=[...selected.keys()];
  const {data:products,error:productError}=await published(db.from("prodotti").select("id,nome,sku,qualita,fornitore,descrizione,descrizione_app,colore,taglia,tipo_misura,valore_misura,dimensioni,prezzo_vendita,prezzo_promozionale,in_promozione,giacenza").in("id",ids));
  if(productError)throw productError;
  if(!products||products.length!==ids.length)return fail("Uno o più articoli non sono disponibili");
  const byId=new Map(products.map((p:any)=>[p.id,p]));
  let lines:any[];
  try{lines=raw.map((entry:any)=>{
    const p:any=byId.get(entry.id);const variant=requestedVariant(p,entry);
    const promo=p.in_promozione&&Number(p.prezzo_promozionale)>0&&Number(p.prezzo_promozionale)<Number(p.prezzo_vendita);
    const price=promo?Number(p.prezzo_promozionale):Number(p.prezzo_vendita);
    const meta=articleMetadata(p);
    const quality=String(p.fornitore||meta.fornitore||"").trim().toLowerCase()==="jayden"?"Originale":"Standard";
    return {id:p.id,qty:Number(entry.qty),price,name:p.nome,sku:p.sku,quality,...variant};
  })}catch(error){return fail(error instanceof Error?error.message:"Misura o colore non validi")}
  const total=Math.round(lines.reduce((v,x)=>v+x.qty*x.price,0)*100)/100;
  if(!(total>=0&&total<1000000))return fail("Totale ordine non valido");
  if(body.amendOrderId){
    if(!account)return fail('Accedi al tuo account per modificare una richiesta',403);
    const orderId=valid(body.amendOrderId,36);if(!/^[0-9a-f-]{36}$/i.test(orderId)||!body.expectedUpdatedAt)return fail('Riapri l’ordine prima di modificarlo',400);
    const timestamp=new Intl.DateTimeFormat('it-IT',{timeZone:'Europe/Rome',dateStyle:'short',timeStyle:'short'}).format(new Date());
    const note='Richiesta da catalogo Shop & Parallelo modificata dal cliente il '+timestamp+' (Europe/Rome). Consenso privacy ricevuto. Da confermare. '+notes+'\n'+lines.map(x=>x.qty+' × '+x.name+' ['+x.sku+'] · '+x.quality+(x.size?' · Misura: '+x.size:'')+(x.color?' · Colore: '+x.color:'')+' · EUR '+x.price.toFixed(2)).join('\n');
    const amended=await db.rpc('vg_shop_amend_order',{p_owner:OWNER,p_customer:account.customer_id,p_order:orderId,p_expected:body.expectedUpdatedAt,p_address:shippingProfile(customer),p_lines:lines,p_total:total,p_note:note});
    if(amended.error)return fail(amended.error.code==='P0001'?amended.error.message:'Modifica non salvata. Riprova.',409);
    const dispatch=Deno.env.get('FCM_SERVICE_ACCOUNT')?fetch(PROJECT+'/functions/v1/vg-shop-push',{method:'POST',headers:{Authorization:'Bearer '+KEY,'Content-Type':'application/json'},body:JSON.stringify({action:'dispatch',orderId})}).catch(()=>console.error('VG modified order notification pending')):Promise.resolve();
    const runtime=(globalThis as any).EdgeRuntime;if(runtime?.waitUntil)runtime.waitUntil(dispatch);else await dispatch;
    return out(amended.data);
  }
  let clientId:string|undefined,orderId:string|undefined;
  try{
    const {data:client,error:ce}=await db.from("clienti").insert({user_id:OWNER,nome:name,telefono:phone,indirizzo:address,citta:city,provincia:province,cap,note:"Richiesta da Shop & Parallelo"}).select("id").single();
    if(ce)throw ce;clientId=client.id;
    const orderPlacedAt=new Date();
    const orderTimestamp=new Intl.DateTimeFormat("it-IT",{timeZone:"Europe/Rome",day:"2-digit",month:"2-digit",year:"numeric",hour:"2-digit",minute:"2-digit",hour12:false}).format(orderPlacedAt);
    const number="WEB-"+orderPlacedAt.toISOString().replace(/[-:TZ.]/g,"").slice(0,14)+"-"+crypto.randomUUID().slice(0,6).toUpperCase();
    const {data:order,error:oe}=await db.from("ordini").insert({user_id:OWNER,numero_ordine:number,cliente_id:clientId,data_ordine:new Intl.DateTimeFormat("en-CA",{timeZone:"Europe/Rome",year:"numeric",month:"2-digit",day:"2-digit"}).format(orderPlacedAt),stato:"in_lavorazione",totale:total,pagato:false,stato_pagamento:"da_pagare",note:"Richiesta da catalogo Shop & Parallelo. Data e ora richiesta (Italia): "+orderTimestamp+". Fuso: Europe/Rome. Consenso privacy ricevuto. Da confermare. "+notes+"\n"+lines.map(x=>x.qty+" × "+x.name+" ["+x.sku+"] · "+x.quality+(x.size?" · Misura: "+x.size:"")+(x.color?" · Colore: "+(x.colorNumber?x.colorNumber+" - ":"")+x.color:"")+" · EUR "+x.price.toFixed(2)).join("\n")}).select("id,numero_ordine,created_at").single();
    if(oe)throw oe;orderId=order.id;
    const {error:re}=await db.from("righe_ordine").insert(lines.map(x=>({user_id:OWNER,ordine_id:orderId,prodotto_id:x.id,quantita:x.qty,prezzo_unitario:x.price,sconto:0,costo_unitario:0,guadagno_riga:0,...(x.size?{variante_misura:x.size}:{}),...(x.color?{variante_colore:x.color}:{}),...(x.colorNumber?{numero_colore:x.colorNumber}:{})})));
    if(re)throw re;
    if(account){
      const linked=await db.from('vg_shop_customer_orders').insert({order_id:order.id,owner_id:OWNER,customer_id:account.customer_id,items:lines});if(linked.error)throw linked.error;
      if(body.rememberShipping===true){const profileSaved=await db.from('vg_shop_customers').update({profile:shippingProfile(customer),updated_at:new Date().toISOString()}).eq('id',account.customer_id).eq('owner_id',OWNER);if(profileSaved.error)console.error('VG customer profile save unavailable')}
    }
    const queued=await db.from("vg_shop_notification_outbox").insert({order_id:order.id,owner_id:OWNER});
    if(queued.error)console.error("VG shop notification queue unavailable");
    else if(Deno.env.get("FCM_SERVICE_ACCOUNT")){
      const dispatch=fetch(PROJECT+"/functions/v1/vg-shop-push",{method:"POST",headers:{"Authorization":"Bearer "+KEY,"Content-Type":"application/json"},body:JSON.stringify({action:"dispatch",orderId:order.id})}).catch(()=>console.error("VG shop push delivery pending"));
      if(typeof EdgeRuntime!=="undefined")EdgeRuntime.waitUntil(dispatch);else await dispatch;
    }
    return out({ok:true,id:order.id,number:order.numero_ordine,requestedAt:order.created_at||orderPlacedAt.toISOString(),timeZone:"Europe/Rome"});
  }catch(e){
    if(orderId)await db.from("ordini").delete().eq("id",orderId).eq("user_id",OWNER);
    if(clientId)await db.from("clienti").delete().eq("id",clientId).eq("user_id",OWNER);
    console.error("VG order request failure",e);
    return fail("Invio non riuscito, riprova senza reinviare più volte",500)
  }
 }catch(e){console.error("VG catalog failure",e);return fail("Servizio temporaneamente non disponibile",500)}
});