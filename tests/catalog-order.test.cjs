const {test}=require('node:test'),assert=require('node:assert/strict'),vm=require('node:vm'),fs=require('node:fs');
const source=require('node:module').stripTypeScriptTypes(fs.readFileSync(__dirname+'/../supabase/functions/vg-shop-catalog/index.ts','utf8').replace(/^import .*;\n/gm,''));
const id='11111111-1111-4111-8111-111111111111';
async function order(overrides={}){
 let handler;const writes=[],products=[{id,nome:'Borsa',sku:'B-1',fornitore:'Jayden',qualita:'ORIGINALE',prezzo_vendita:140,giacenza:0}];
 const db={from(table){let inserted;return {select(){return this},eq(){return this},gte(){return this},in(){return this},insert(data){inserted=data;writes.push({table,data});return this},async single(){return {data:table==='clienti'?{id:'client'}:{id:'order',numero_ordine:'WEB-SERVER',created_at:'2026-10-25T23:30:00Z'},error:null}},then(resolve,reject){return Promise.resolve({data:table==='prodotti'?products:null,count:0,error:null}).then(resolve,reject)}}}};
 vm.runInNewContext(source,{Deno:{env:{get:()=>''},serve:f=>handler=f},createClient:()=>db,...require('./catalog-server-helpers.cjs'),Response,Request,URL,Date,Intl,crypto,TextEncoder,console,accessAction:async()=>null,catalogSession:async()=>({is_admin:true}),trackVisit:async()=>{}});
 const body={privacy:true,customer:{name:'Cliente prova',phone:'3330000000',address:'Via prova 1',province:'CN',city:'Beinette',cap:'12081'},items:[{id,qty:2,quality:'STANDARD',price:1}],...overrides};
 const response=await handler(new Request('https://test/',{method:'POST',body:JSON.stringify(body)}));return {response,writes,body:await response.json()};
}
test('server prices, supplier quality snapshot, database timestamp, address and push outbox',async()=>{
 const {response,writes,body}=await order();assert.equal(response.status,200);assert.equal(body.requestedAt,'2026-10-25T23:30:00Z');assert.equal(body.timeZone,'Europe/Rome');
 const client=writes.find(w=>w.table==='clienti').data;assert.equal(client.provincia,'CN');assert.equal(client.email,undefined);
 const header=writes.find(w=>w.table==='ordini').data;assert.equal(header.totale,280);assert.match(header.note,/Originale.*EUR 140.00/);assert.match(header.note,/Europe\/Rome/);
 assert.equal(writes.find(w=>w.table==='righe_ordine').data[0].prezzo_unitario,140);assert(writes.some(w=>w.table==='vg_shop_notification_outbox'));
});
test('missing consent, province, CAP, invalid quantity never create orders',async()=>{
 for(const override of [{privacy:false},{customer:{name:'Cliente',phone:'3330000000',address:'Via prova 1',city:'Beinette',cap:'12081'}},{items:[{id,qty:0}]}]){const r=await order(override);assert.equal(r.response.status,400);assert.equal(r.writes.length,0)}
});
