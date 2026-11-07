/* A failed download or script must never leave an empty, locked management page. */
(()=>{'use strict';const gate=document.getElementById('authGate');if(!gate)return;
const retry=document.getElementById('vgStartupRetry'),status=document.getElementById('vgStartupStatus');
function pending(){return document.body.classList.contains('auth-locked')&&document.getElementById('authChecking')?.style.display!=='none'}
function explain(){if(!pending())return;gate.style.display='flex';status.hidden=false;status.textContent='L’apertura sta impiegando troppo tempo. Controlla la connessione e riprova. I dati e l’accesso salvato vengono conservati.';retry.hidden=false}
retry.onclick=()=>location.reload();setTimeout(explain,12000);
window.addEventListener('error',explain);window.addEventListener('unhandledrejection',explain);

const logo = document.createElement('img');
logo.src = './shop-vanity-glamour.webp';
logo.alt = 'Vanity & Glamour';
logo.width = 1000;
logo.height = 356;
logo.style.cssText = 'display:block;width:100%;max-width:260px;height:auto;margin:0 auto';
gate.querySelector('.authLogo')?.replaceChildren(logo);
const menu = document.getElementById('topMenuPanel');
menu?.prepend(logo.cloneNode());
const catalogLink = menu?.querySelector('a[href="./catalogo.html"]');
if (catalogLink) {
  catalogLink.href = 'https://veglamour.cloud/';
  catalogLink.querySelector('.topMenuTitle').textContent = 'Vanity & Glamour';
}
const favicon = document.querySelector('link[rel="icon"]');
if (favicon) favicon.href = './favicon-v1-32.png?v=vg-brand-1';
})();
