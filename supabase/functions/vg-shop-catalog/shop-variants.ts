const clean=(value:any,max=100)=>typeof value==='string'?value.trim().slice(0,max):'';
export function decodeArticleMeta(raw:any){
 let value=typeof raw==='string'?raw:'';
 for(let i=0;i<4;i++){
  try{const parsed=JSON.parse(value);if(parsed&&typeof parsed==='object'&&!Array.isArray(parsed))return parsed;if(typeof parsed!=='string')return {};value=parsed;continue}catch{}
  if(!/^\s*\{\\"/.test(value))return {};
  try{value=JSON.parse('"'+value.replace(/\r/g,'\\r').replace(/\n/g,'\\n').replace(/\t/g,'\\t')+'"')}catch{return {}}
 }
 return {};
}
export function articleMetadata(p:any){let meta:any={};for(const raw of [p.descrizione,p.descrizione_app]){const body=String(raw||'').match(/\[VGMETA\]([\s\S]*?)\[\/VGMETA\]/i)?.[1]||'{}';meta={...meta,...decodeArticleMeta(body)}}return meta}
export function articleOptions(p:any){
 const meta=articleMetadata(p);
 const raw=meta.taglie||meta.misure||p.valore_misura||meta.misura||p.taglia||p.dimensioni||'';
 let sizes:string[]=[];const fixed=clean(raw,160);
 if(Array.isArray(raw))sizes=raw.map(x=>clean(String(x),40)).filter(Boolean);
 else if(fixed&&!/[×x]/i.test(fixed)){
  const range=fixed.match(/^(?:da\s+|dal\s+)?(\d{1,3}(?:[.,]5)?)\s*(?:[–—-]|a|al)\s*(\d{1,3}(?:[.,]5)?)(?:\s*cm)?$/i);
  if(range){const low=Number(range[1].replace(',','.')),high=Number(range[2].replace(',','.'));if(low>=1&&high>=low&&high<=300&&high-low<=50)sizes=Array.from({length:Math.floor(high-low)+1},(_,i)=>String(low+i))}
  else if(!/^\d+,[05]$/.test(fixed)&&/[\/;|\n,]/.test(fixed))sizes=fixed.split(/[\/;|\n,]+/).map(x=>clean(x,40)).filter(Boolean);
 }
 sizes=[...new Set(sizes)].slice(0,51);
 const labels=Array.isArray(meta.colorLabels)?meta.colorLabels:Array.isArray(meta.colorData?.labels)?meta.colorData.labels:[];
 const count=Math.min(50,Math.max(Number(meta.colorCount)||0,Number(meta.colorData?.count)||0,labels.length));
 const multiple=count>1||meta.colorMode==='multiple'||meta.colorType==='numbered';
 const colors=multiple?Array.from({length:Math.max(1,count)},(_,i)=>({value:String(i+1),number:i+1,label:clean(labels[i],80)||'Colore '+(i+1)})):[];
 return {sizes:sizes.length>1?sizes:[],colors,fixedSize:sizes.length===1?sizes[0]:sizes.length?'':fixed,fixedColor:colors.length?'':clean(p.colore||meta.colore,80),sizeLabel:/cm/i.test(fixed)?'Misura (cm)':'Numero / taglia'};
}
export function requestedVariant(p:any,entry:any){
 const options=articleOptions(p),size=clean(entry?.size,160),color=clean(entry?.color,80);
 if(options.sizes.length&&!options.sizes.includes(size))throw new Error('Scegli una misura valida per '+p.nome);
 if(!options.sizes.length&&size&&size!==options.fixedSize)throw new Error('Misura non prevista per '+p.nome);
 const chosen=options.colors.find(x=>x.value===color);
 if(options.colors.length&&!chosen)throw new Error('Scegli un colore valido per '+p.nome);
 if(!options.colors.length&&color&&color!==options.fixedColor)throw new Error('Colore non previsto per '+p.nome);
 return {size:options.sizes.length?size:options.fixedSize,color:chosen?.label||options.fixedColor,colorNumber:chosen?.number||null};
}

// Same active-promotion rules as the administration's Promo badge.
export function articleHasActivePromo(p:any,today=new Intl.DateTimeFormat('en-CA',{timeZone:'Europe/Rome',year:'numeric',month:'2-digit',day:'2-digit'}).format(new Date())){
 const m=articleMetadata(p),live=(end:any)=>!/^\d{4}-\d{2}-\d{2}$/.test(String(end||''))||String(end)>=today;
 const personal=!!m.promoAttiva&&Number(m.prezzoPromo)>0&&live(m.scadenzaPromoPersonale||m.scadenzaPromo);
 const values=[m.costoUsdPromo,m.costoUSDPromo,m.prezzoFornitorePromoUsd,m.promoCostoUsd];
 const supplierCost=values.find(v=>v!==undefined&&v!==null&&v!==''&&Number.isFinite(Number(v)));
 const supplier=Number(supplierCost)>0&&m.promoFornitoreAttiva!==false&&live(m.scadenzaPromoFornitore);
 return personal||supplier;
}
