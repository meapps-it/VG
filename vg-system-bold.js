(()=>{'use strict';
 if(window.__vgSystemBoldInstalled||!document.fonts)return;
 window.__vgSystemBoldInstalled=true;
 let queued=false;
 function pixels(weight){
  const canvas=document.createElement('canvas');canvas.width=640;canvas.height=80;
  const context=canvas.getContext('2d',{willReadFrequently:true});if(!context)return null;
  context.font=weight+' 48px "VgAndroidSystem"';context.fillText('AgMw 0123 Àèìòù',2,58);
  return context.getImageData(0,0,canvas.width,canvas.height).data;
 }
 function check(){
  queued=false;
  const faces=[...document.fonts].filter(f=>f.family.replace(/["']/g,'')==='VgAndroidSystem'&&f.style==='normal'&&f.status==='loaded');
  if(!faces.some(f=>f.weight==='400'))return;
  const bold=faces.filter(f=>f.weight==='700');if(!bold.length)return;
  const regularPixels=pixels('400'),boldPixels=pixels('700');
  if(!regularPixels||!boldPixels||!regularPixels.some((value,i)=>i%4===3&&value))return;
  // Android can export the same unmodified file for NORMAL and synthetic BOLD.
  // Advertising that copy as a true 700 face prevents Chromium from synthesizing bold.
  if(regularPixels.every((value,i)=>value===boldPixels[i]))for(const face of bold)document.fonts.delete(face);
 }
 function schedule(){if(!queued){queued=true;requestAnimationFrame(check)}}
 document.fonts.addEventListener('loadingdone',schedule);
 new MutationObserver(schedule).observe(document.documentElement,{attributes:true,attributeFilter:['style']});
 schedule();
})();
