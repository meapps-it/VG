/* Regression checks for typing work, save flushing and incremental font sizing. */
const fs=require('node:fs'),path=require('node:path');
const html=fs.readFileSync(path.join(__dirname,'../index.html'),'utf8');
const typingSource=html.slice(html.indexOf('let __vgArticleFieldsTimer=0;'),html.indexOf('/* ====== EVENTS ====== */',html.indexOf('let __vgArticleFieldsTimer=0;')));
const fontSource=fs.readFileSync(path.join(__dirname,'../vg-font-settings.js'),'utf8');
const vm=require('node:vm'),assert=require('node:assert/strict');
function harness(){
 let serial=0;const timers=new Map(),events={},fields={};
 const calls={render:0,validate:0,apply:[],suggest:0};
 function element(id){return fields[id]??=( {value:'',listeners:{},addEventListener(type,fn){(this.listeners[type]??=[]).push(fn)},fire(type,event={}){for(const fn of this.listeners[type]||[])fn(event)}})}
 const context={document:{getElementById:element,addEventListener(type,fn){events[type]=fn}},
 setTimeout(fn){timers.set(++serial,fn);return serial},clearTimeout(id){timers.delete(id)},isModalVisible:()=>true,
 renderArtAutoFields(){calls.render++},updateArticleCodeStatus(){calls.validate++},
 hideSearchSuggest(){},getSearchSuggestions(){calls.suggest++;return []},renderSearchSuggest(){}};
 vm.createContext(context);vm.runInContext(typingSource,context);
 function drain(){for(const [id,fn]of [...timers]){timers.delete(id);fn()}}
 return {context,calls,element,events,drain,timers};
}
{
 const h=harness();for(let i=0;i<30;i++)h.context.queueArticleAutoFields({});
 assert.equal(h.calls.render,0);assert.equal(h.timers.size,1);h.drain();assert.equal(h.calls.render,1);assert.equal(h.calls.validate,1);
 h.context.queueArticleAutoFields({});h.events.click({target:{closest:()=>true}});assert.equal(h.calls.render,2);assert.equal(h.timers.size,0);
 h.context.queueArticleAutoFields({isComposing:true});h.drain();assert.equal(h.calls.render,2);
}
{
 const h=harness(),input=h.element('search');h.context.bindSearchSuggest('search','suggest','art',v=>h.calls.apply.push(v));
 for(let i=0;i<30;i++){input.value='borsa'+i;input.fire('input')}
 assert.equal(h.calls.apply.length,0);h.drain();assert.deepEqual(h.calls.apply,['borsa29']);assert.equal(h.calls.suggest,1);
 input.fire('compositionstart');input.value='testo finale';input.fire('input',{isComposing:true});h.drain();assert.equal(h.calls.apply.length,1);
 input.fire('compositionend');h.drain();assert.equal(h.calls.apply.at(-1),'testo finale');
 input.value='invio';input.fire('input');input.fire('keydown',{key:'Enter'});assert.equal(h.calls.apply.at(-1),'invio');assert.equal(h.timers.size,0);
}
{
 let observer,frames=[],reads=0,writes=0,readingAfterWrite=false;
 function el(){return {nodeType:1,dataset:{},isConnected:true,closest:()=>null,matches:()=>false,children:[],querySelectorAll(){return this.children},style:{setProperty(){writes++}}}}
 const body=el();body.children=Array.from({length:1000},el);
 const ctx={localStorage:{getItem:()=>null},document:{body,readyState:'complete',head:{appendChild(){}},documentElement:{style:{setProperty(){}}},createElement:()=>({}),getElementById:()=>null,querySelectorAll:()=>[]},
 getComputedStyle(){reads++;if(writes)readingAfterWrite=true;return {fontSize:'14px'}},
 requestAnimationFrame:fn=>frames.push(fn),MutationObserver:class{constructor(fn){observer=fn}observe(){}}};
 vm.createContext(ctx);vm.runInContext(fontSource,ctx);
 assert.equal(reads,1001);assert.equal(readingAfterWrite,false);reads=0;writes=0;
 observer([{addedNodes:[{nodeType:3}]}]);assert.equal(frames.length,0);assert.equal(reads,0);
 const added=el();observer([{addedNodes:[added]}]);frames.shift()();assert.equal(reads,1);assert.equal(writes,1);
 reads=0;writes=0;observer([{addedNodes:[added]}]);frames.shift()();assert.equal(reads,0);assert.equal(writes,0);
}
console.log('PASS: 30 keystrokes coalesced into one recalculation/search; save flushes pending changes; IME preserved; 1,001-element page is not rescanned after clock/text updates; only new elements measured; reads precede writes.');