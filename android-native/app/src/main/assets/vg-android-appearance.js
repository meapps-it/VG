(() => {
  'use strict';
  if (window !== window.top || location.origin !== 'https://meapps-it.github.io' ||
      !(location.pathname === '/VG' || location.pathname.startsWith('/VG/'))) return;
  if (window.__vgAppearanceInstalled) return;
  window.__vgAppearanceInstalled = true;
  let state = {percent:100, systemPercent:100}, fontStatus = 'Carattere Android in verifica…';
  let loadedFonts = [], fontRevision = null, loadGeneration = 0;
  const style = document.createElement('style');
  style.id = 'vg-android-appearance-style';
  style.textContent = `
    html { -webkit-text-size-adjust:100%!important; text-size-adjust:100%!important }
    html,body {font-family:var(--vg-android-font,system-ui),sans-serif!important}
    body :not(svg):not(svg *):not(canvas):not(.material-icons):not(.material-symbols-outlined):not(.material-symbols-rounded):not(.fa):not(.fas):not(.far):not(.fab):not([class*="icon-"]):not([data-vg-preserve-font]):not([data-vg-preserve-font] *) {font-family:var(--vg-android-font,system-ui),sans-serif!important}
    #vg-font-control {display:flex;flex-direction:column;gap:12px;min-width:0}
    #vg-font-control label {font-weight:700}
    #vg-font-control input[type=range] {width:100%;min-height:48px;padding:0}
    #vg-font-control output {font-weight:700;font-variant-numeric:tabular-nums}
    #vg-font-control p {margin:0;line-height:1.5;overflow-wrap:anywhere}
    #vg-font-control .actions {display:flex;flex-wrap:wrap}
    body button,body .btn,body .btnBig,body .topMenuLink {height:auto;min-height:44px;white-space:normal;overflow-wrap:anywhere}
    body input:not([type=checkbox]):not([type=radio]):not([type=range]),body select {height:auto;min-height:44px;box-sizing:border-box}
    body textarea {height:auto;min-height:80px}
    body .topMenuText,body .card,body .grid2>*,body .grid3>* {min-width:0}
    body .topMenuTitle,body .topMenuSub,body .k,body .small {white-space:normal;overflow-wrap:anywhere}
    body .modal,body [role=dialog] {max-height:90dvh;overflow-y:auto}
    @media(max-width:480px) {html.vg-large-text .grid2,html.vg-large-text .grid3 {grid-template-columns:minmax(0,1fr)}}
  `;
  function post(data) { if (window.VgAppearanceBridge) window.VgAppearanceBridge.postMessage(JSON.stringify(data)); }
  function render() {
    const section = document.getElementById('sec_impostazioni');
    if (!section) return;
    let control = document.getElementById('vg-font-control');
    if (!control || control.dataset.android !== 'true') {
      control?.remove();
      control = document.createElement('section');
      control.id = 'vg-font-control'; control.className = 'card'; control.dataset.android = 'true';
      control.innerHTML = '<div class="h2">Aspetto e caratteri</div><label for="vg-font-range">Dimensione del testo: <output id="vg-font-value" for="vg-font-range" aria-live="polite"></output></label><input id="vg-font-range" type="range" min="75" max="150" step="5" aria-label="Dimensione del testo in percentuale"><p id="vg-font-preview">Anteprima: V e G · Articoli, clienti e ordini. Àèìòù 0123456789</p><div class="actions"><button class="btn" type="button" id="vg-font-reset">Ripristina 100%</button></div><p class="small" id="vg-font-system"></p><p class="small" id="vg-font-status"></p>';
      section.prepend(control);
      control.querySelector('input').addEventListener('input', event => {
        state.percent = Number(event.target.value); sync();
        post({action:'set', percent:state.percent});
      });
      control.querySelector('button').addEventListener('click', () => {
        state.percent = 100; sync(); post({action:'set', percent:100});
      });
    }
    sync();
  }
  function sync() {
    document.documentElement.classList.toggle('vg-large-text', state.percent * state.systemPercent > 12000);
    const slider = document.getElementById('vg-font-range');
    if (!slider) return;
    slider.value = String(state.percent);
    document.getElementById('vg-font-value').textContent = state.percent + '%';
    document.getElementById('vg-font-system').textContent = 'Dimensione Android: ' + state.systemPercent + '%. La percentuale scelta si applica una sola volta alla dimensione del telefono.';
    document.getElementById('vg-font-status').textContent = fontStatus;
    slider.disabled = !window.VgAppearanceBridge;
    document.getElementById('vg-font-reset').disabled = !window.VgAppearanceBridge;
    if (!window.VgAppearanceBridge) document.getElementById('vg-font-status').textContent = 'Aggiorna Android System WebView per usare questo controllo.';
  }
  window.__vgApplyAppearance = async data => {
    state = {...state, ...data};
    if (data.fontStatus && data.fontRevision !== fontRevision) fontStatus = data.fontStatus;
    render();
    if (!data.faces || data.fontRevision === fontRevision) return;
    fontRevision = data.fontRevision;
    const generation = ++loadGeneration;
    loadedFonts.forEach(font => document.fonts.delete(font)); loadedFonts = [];
    document.documentElement.style.removeProperty('--vg-android-font');
    if (!data.faces.length) {sync();return;}
    try {
      const faces = await Promise.all(data.faces.map(face => {
        const descriptors = {weight:face.weight, style:face.style};
        if (face.variationSettings) descriptors.variationSettings = face.variationSettings;
        return new FontFace('VgAndroidSystem', 'url(data:font/ttf;base64,' + face.data + ')', descriptors).load();
      }));
      if (generation !== loadGeneration) return;
      faces.forEach(font => document.fonts.add(font)); loadedFonts = faces;
      document.documentElement.style.setProperty('--vg-android-font', 'VgAndroidSystem');
      fontStatus = 'Carattere del testo nativo Android caricato nella WebView. Se differisce da quello scelto in Samsung, il dispositivo non lo espone correttamente.';
    } catch (error) {console.warn('VgAppearance font load:',error.name,error.message);fontStatus = 'La WebView non accetta il font fornito da Android: viene usato il carattere di sistema della WebView. Il font Samsung personalizzato non è garantito.';}
    sync();
  };
  let pending = false;
  function maintain() {
    if (pending) return; pending = true;
    requestAnimationFrame(() => {
      pending = false;
      if (!style.isConnected) (document.head || document.documentElement).appendChild(style);
      // Remove only the obsolete double-scaling implementation, including cached copies.
      document.querySelectorAll('#vg-native-font-style').forEach(el => el.remove());
      document.documentElement.style.removeProperty('--vg-font-scale');
      document.querySelectorAll('[data-vg-font-original]').forEach(el => {
        el.removeAttribute('data-vg-font-original');el.style.removeProperty('--vg-font-original');
      });
      render();
    });
  }
  function start() {
    maintain();
    new MutationObserver(records => {
      if (records.some(record => !record.target.closest?.('#vg-font-control'))) maintain();
    }).observe(document.documentElement, {childList:true, subtree:true});
    post({action:'get'});
  }
  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', start, {once:true}); else start();
})();
