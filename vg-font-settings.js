(()=>{'use strict';
const STORAGE='vg-font-scale-percent';
const FONT='system-ui,-apple-system,BlinkMacSystemFont,"Segoe UI",Roboto,"Helvetica Neue",Arial,sans-serif';
const read=()=>{try{return Math.min(150,Math.max(75,Number(localStorage.getItem(STORAGE))||100));}catch{return 100;}};
let percent=read(),busy=false;
const style=document.createElement('style');style.id='vg-native-font-style';style.textContent=`
html,body,body :not(svg):not(svg *):not(.material-icons):not(.material-symbols-outlined):not(.material-symbols-rounded):not(.fa):not([class*="icon-"]){font-family:${FONT}!important}
[data-vg-font-original]{font-size:calc(var(--vg-font-original) * var(--vg-font-scale, 1))!important}
#vg-font-control{font-family:${FONT}!important;display:flex;flex-direction:column;gap:10px;padding:16px;margin:14px 0;border:1px solid #dedede;border-radius:12px;background:var(--card,#fff);color:var(--ink,#222)}
#vg-font-control .vg-font-head{display:flex;justify-content:space-between;gap:10px;align-items:center;font-weight:600}
#vg-font-control input{width:100%;accent-color:#a85f2c}
#vg-font-control .vg-font-footer{display:flex;align-items:center;justify-content:space-between;font-size:13px}
#vg-font-control button{padding:5px 12px;border:1px solid #bbb;border-radius:8px;background:transparent;color:inherit}
`;
(document.head||document.documentElement).appendChild(style);
function resize(){
 document.documentElement.style.setProperty('--vg-font-scale',String(percent/100));
 const nodes=document.body?.querySelectorAll('*')||[];
 nodes.forEach(el=>{
  if(el.closest('#vg-font-control')||el.matches('svg,svg *,script,style,canvas,.material-icons,.material-symbols-outlined,.material-symbols-rounded,.fa,[class*="icon-"]'))return;
  if(el.dataset.vgFontOriginal)return;
  const fs=parseFloat(getComputedStyle(el).fontSize);
  if(Number.isFinite(fs)&&fs>0) {el.style.setProperty('--vg-font-original',fs+'px');el.dataset.vgFontOriginal='1';}
 });
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
let next=0;
function schedule(){if(busy)return;busy=true;requestAnimationFrame(()=>{busy=false;resize();attach();});}
function start(){resize();attach();new MutationObserver(()=>{const now=Date.now();if(now-next>200){next=now;schedule();}}).observe(document.body,{subtree:true,childList:true});}
if(document.readyState==='loading')document.addEventListener('DOMContentLoaded',start,{once:true});else start();
})();