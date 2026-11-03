(()=>{'use strict';
if(document.getElementById('vg-native-font-style'))return;
const STORAGE='vg-font-scale-percent';
const FONT='system-ui,-apple-system,BlinkMacSystemFont,"Segoe UI",Roboto,"Helvetica Neue",Arial,sans-serif';
const read=()=>{try{return Math.min(150,Math.max(75,Number(localStorage.getItem(STORAGE))||100));}catch{return 100;}};
let percent=read(),busy=false;
const style=document.createElement('style');style.id='vg-native-font-style';style.textContent=`
html,body,body :not(svg):not(svg *):not(.material-icons):not(.material-symbols-outlined):not(.material-symbols-rounded):not(.fa):not([class*="icon-"]){font-family:${FONT}!important;font-weight:700!important}
[data-vg-font-original]{font-size:calc(var(--vg-font-original) * var(--vg-font-scale, 1))!important}
#vg-font-control{font-family:${FONT}!important;display:flex;flex-direction:column;gap:10px;padding:16px;margin:14px 0;border:1px solid #dedede;border-radius:12px;background:var(--card,#fff);color:var(--ink,#222)}
#vg-font-control .vg-font-head{display:flex;justify-content:space-between;gap:10px;align-items:center;font-weight:600}
#vg-font-control input{width:100%;accent-color:#a85f2c}
#vg-font-control .vg-font-footer{display:flex;align-items:center;justify-content:space-between;font-size:13px}
#vg-font-control button{padding:5px 12px;border:1px solid #bbb;border-radius:8px;background:transparent;color:inherit}
`;
(document.head||document.documentElement).appendChild(style);
const ignored='svg,svg *,script,style,canvas,.material-icons,.material-symbols-outlined,.material-symbols-rounded,.fa,[class*="icon-"]';
function resize(roots=[document.body]){
 const nodes=new Set();
 roots.forEach(root=>{
  if(root?.nodeType!==1||root.closest('#vg-font-control')||root.matches(ignored))return;
  nodes.add(root);root.querySelectorAll('*').forEach(el=>nodes.add(el));
 });
 // Read first, write second: avoid a layout recalculation for every element.
 const sizes=[];
 nodes.forEach(el=>{
  if(el.dataset.vgFontOriginal||el.closest('#vg-font-control')||el.matches(ignored))return;
  const fs=parseFloat(getComputedStyle(el).fontSize);
  if(Number.isFinite(fs)&&fs>0)sizes.push([el,fs]);
 });
 sizes.forEach(([el,fs])=>{el.style.setProperty('--vg-font-original',fs+'px');el.dataset.vgFontOriginal='1';});
}
function update(val){percent=Number(val);try{localStorage.setItem(STORAGE,String(percent));}catch{};document.documentElement.style.setProperty('--vg-font-scale',String(percent/100));document.querySelectorAll('.vg-font-value').forEach(el=>el.textContent=percent+'%');}
function createControl(){
 const box=document.createElement('section');box.id='vg-font-control';
 box.innerHTML='<div class="vg-font-head"><label for="vg-font-range">Dimensione del testo</label><strong class="vg-font-value"></strong></div><input id="vg-font-range" type="range" min="75" max="150" step="5" aria-label="Dimensione del carattere in percentuale"><div class="vg-font-footer"><span>75% · 100% normale · 150%</span><button type="button">Ripristina</button></div>';
 box.querySelector('input').value=String(percent);
 box.querySelector('input').addEventListener('input',e=>update(e.target.value));
 box.querySelector('button').addEventListener('click',()=>{box.querySelector('input').value='100';update(100);});
 box.querySelector('.vg-font-value').textContent=percent+'%';return box;
}
function attach(){
 if(document.getElementById('vg-font-control'))return;
 const candidates=[...document.querySelectorAll('h1,h2,h3,h4,h5,h6,[role=heading],button,[role=tab],a')];
 const found=candidates.find(x=>/^impostazioni$|^settings$|^preferenze$/i.test((x.textContent||'').trim())&&x.getClientRects().length);
 if(!found)return;
 let target=found.closest('section,[role=dialog],main,.modal,.settings,.panel')||found.parentElement;
 if(!target)return;
 // Do not insert into generic root body or navigation: prefer local settings pane.
 if(target===document.body||target===document.documentElement)target=found.parentElement;
 if(target&&target.querySelectorAll('*').length<1500)target.appendChild(createControl());
}
const pendingRoots=new Set();
function schedule(){
 if(busy)return;busy=true;
 requestAnimationFrame(()=>{
  busy=false;
  const roots=[...pendingRoots].filter(root=>root.isConnected);
  pendingRoots.clear();
  if(!roots.length)return;
  resize(roots);
  if(!document.getElementById('vg-font-control'))attach();
 });
}
function start(){
 document.documentElement.style.setProperty('--vg-font-scale',String(percent/100));
 resize();attach();
 new MutationObserver(records=>{
  for(const record of records)for(const node of record.addedNodes){
   // Text updates (clock, validation hints) never require a page-wide scan.
   if(node.nodeType===1&&!node.closest('#vg-font-control')&&!node.matches(ignored))pendingRoots.add(node);
  }
  if(pendingRoots.size)schedule();
 }).observe(document.body,{subtree:true,childList:true});
}
if(document.readyState==='loading')document.addEventListener('DOMContentLoaded',start,{once:true});else start();
})();