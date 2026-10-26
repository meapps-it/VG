/* A failed download or script must never leave an empty, locked management page. */
(()=>{'use strict';const gate=document.getElementById('authGate');if(!gate)return;
const retry=document.getElementById('vgStartupRetry'),status=document.getElementById('vgStartupStatus');
function pending(){return document.body.classList.contains('auth-locked')&&document.getElementById('authChecking')?.style.display!=='none'}
function explain(){if(!pending())return;gate.style.display='flex';status.hidden=false;status.textContent='L’apertura sta impiegando troppo tempo. Controlla la connessione e riprova. I dati e l’accesso salvato vengono conservati.';retry.hidden=false}
retry.onclick=()=>location.reload();setTimeout(explain,12000);
window.addEventListener('error',explain);window.addEventListener('unhandledrejection',explain);
})();
