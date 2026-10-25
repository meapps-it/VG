/* Shared by the PWA and the APK asset: original V e G quality pricing rules. */
(function(root,factory){
 const api=factory();
 if(typeof module==='object'&&module.exports) module.exports=api;
 else root.VgQualityPricing=api;
})(typeof globalThis!=='undefined'?globalThis:this,function(){
 'use strict';
 const original=Object.freeze([[210,70],[230,75],[250,80],[300,85],[350,95]].map(Object.freeze));
 const standard=Object.freeze([[50,45],[80,55],[120,60],[160,70],[220,80]].map(Object.freeze));
 function profit(cost,quality){
  cost=Math.max(0,Number(cost)||0);
  if(!cost||!['ORIGINALE','STANDARD'].includes(quality)) return 0;
  const table=quality==='ORIGINALE'?original:standard;
  for(const [ceiling,amount] of table) if(cost<=ceiling) return amount;
  return Math.max(quality==='ORIGINALE'?110:90,cost*.25);
 }
 function calculate(cost,quality){
  cost=Math.max(0,Number(cost)||0);
  if(!cost) return {costo:0,sell:0,marg:0,theoretical:0,minPrice:0,buffer:0,highCost:false};
  if(!['ORIGINALE','STANDARD'].includes(quality)) return {costo:cost,sell:0,marg:0,theoretical:0,minPrice:0,buffer:0,highCost:false,needsQuality:true};
  const marg=profit(cost,quality),theoretical=Math.floor((cost+marg)/5)*5;
  const buffer=quality==='ORIGINALE'?10:0;
  return {costo:cost,sell:Math.ceil((theoretical+buffer)/5)*5,marg,theoretical,minPrice:theoretical,buffer,highCost:cost>(quality==='ORIGINALE'?350:220)};
 }
 return Object.freeze({version:'1',original,standard,profit,calculate});
});
