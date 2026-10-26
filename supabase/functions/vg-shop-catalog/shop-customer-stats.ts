// Only called after requireOwner. Never expose this data to catalogue/customer tokens.
export async function customerStatistics(db:any,owner:string,customerOffset=0,orderOffset=0){
 const page=(value:number)=>Number.isInteger(value)&&value>=0&&value<=100000?value:0;
 customerOffset=page(customerOffset);orderOffset=page(orderOffset);
 const catalogOrders=()=>db.from('ordini').select('id',{count:'exact',head:true}).eq('user_id',owner).like('numero_ordine','WEB-%').eq('is_demo',false);
 const results=await Promise.all([
  db.from('vg_shop_customers').select('id',{count:'exact',head:true}).eq('owner_id',owner),
  catalogOrders(),catalogOrders().not('stato','in','(consegnato,annullato)'),
  db.from('vg_shop_customers').select('id,phone,profile,created_at,email,access_status').eq('owner_id',owner).order('created_at',{ascending:false}).order('id').range(customerOffset,customerOffset+49),
  db.from('ordini').select('id,numero_ordine,created_at,stato,pagato,totale,tracking_code,clienti(nome,telefono,indirizzo,provincia,citta,cap),righe_ordine(quantita,prezzo_unitario,variante_misura,variante_colore,numero_colore,prodotti(nome,sku,qualita,fornitore))').eq('user_id',owner).eq('is_demo',false).like('numero_ordine','WEB-%').order('created_at',{ascending:false}).order('id').range(orderOffset,orderOffset+49)
 ]);for(const r of results)if(r.error)throw r.error;
 const customers=results[3].data||[],ids=customers.map((c:any)=>c.id),sessions:any[]=[],links:any[]=[];
 if(ids.length)for(const [table,columns,target] of [['vg_shop_customer_sessions','customer_id,created_at',sessions],['vg_shop_customer_orders','customer_id,order_id',links]] as any[]){
  for(let offset=0;;offset+=500){const r=await db.from(table).select(columns).eq('owner_id',owner).in('customer_id',ids).order(table==='vg_shop_customer_sessions'?'created_at':'order_id').range(offset,offset+499);if(r.error)throw r.error;target.push(...r.data||[]);if((r.data||[]).length<500)break}
 }
 return {summary:{registeredCustomers:results[0].count||0,catalogOrders:results[1].count||0,pendingOrders:results[2].count||0},customers:customers.map((c:any)=>({id:c.id,phone:c.phone,email:c.email,accessStatus:c.access_status,profile:Object.fromEntries(['name','phone','address','province','city','cap'].map(key=>[key,typeof c.profile?.[key]==='string'?c.profile[key]:''])),registeredAt:c.created_at,lastLoginAt:sessions.filter(s=>s.customer_id===c.id).reduce((latest,s)=>s.created_at>latest?s.created_at:latest,''),orders:links.filter(l=>l.customer_id===c.id).length})),orders:(results[4].data||[]).map((o:any)=>({id:o.id,number:o.numero_ordine,requestedAt:o.created_at,status:o.stato,paid:o.pagato,total:o.totale,tracking:o.tracking_code,customer:o.clienti,items:(o.righe_ordine||[]).map((r:any)=>({name:r.prodotti?.nome||r.prodotti?.sku||'Articolo',qty:r.quantita,price:r.prezzo_unitario,size:r.variante_misura,color:r.variante_colore,colorNumber:r.numero_colore,quality:String(r.prodotti?.fornitore||'').toLowerCase()==='jayden'?'Originale':'Standard'}))})),hasMoreCustomers:customers.length===50,hasMoreOrders:(results[4].data||[]).length===50,customerOffset,orderOffset};
}
