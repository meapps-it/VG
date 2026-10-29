const VERSION = 'gestionale-vg-1.0.166-notices';
const CACHE = `gestionale-runtime-${VERSION}`;
const REQUIRED_ASSETS = [
  './', './index.html', './manifest.json', './vg-quality-pricing.js', './vg-bold-theme.css?v=1.0.156', './vg-startup.js?v=1.0.156',
  './icon-v1-192.png', './icon-v1-512.png', './icon-v1-maskable-512.png',
  './apple-touch-icon-v1.png', './favicon-v1-32.png',
];

self.addEventListener('install', event => {
  self.skipWaiting();
  event.waitUntil((async()=>{
    const cache=await caches.open(CACHE);
    for(const asset of REQUIRED_ASSETS){
      try{ await cache.add(new Request(asset,{cache:'reload'})); }
      catch(err){ console.warn('[SW] Asset non memorizzato:',asset,err); }
    }
  })());
});

self.addEventListener('activate', event => {
  event.waitUntil((async()=>{
    const keys=await caches.keys();
    await Promise.all(keys.filter(k=>k!==CACHE).map(k=>caches.delete(k)));
    await self.clients.claim();
  })());
});

async function withFontSettings(response) {
  if (!response || !response.ok) return response;
  const contentType = response.headers.get('content-type') || '';
  if (!contentType.includes('text/html')) return response;
  const html = await response.text();
  if (html.includes('vg-font-settings.js')) return new Response(html, response);
  const tag = '<script src="./vg-font-settings.js" defer></script>';
  const transformed = html.includes('</head>') ? html.replace('</head>', tag + '</head>') : tag + html;
  const headers = new Headers(response.headers);
  headers.delete('content-length');
  headers.delete('content-encoding');
  return new Response(transformed, { status:response.status, statusText:response.statusText, headers });
}

self.addEventListener('fetch', event => {
  const req=event.request;
  if(req.method!=='GET') return;
  const url=new URL(req.url);

  if(req.mode==='navigate'){
    event.respondWith((async()=>{
      try{
        const fresh=await fetch(req,{cache:'no-store'});
        const cache=await caches.open(CACHE);
        cache.put('./index.html',fresh.clone());
        return withFontSettings(fresh);
      }catch(_e){
        return withFontSettings((await caches.match('./index.html')) || Response.error());
      }
    })());
    return;
  }

  if(url.origin===self.location.origin &&
     (url.pathname.endsWith('manifest.json') ||
      url.pathname.endsWith('supabaseClient.js') ||
      url.pathname.endsWith('vg-startup.js') ||
      url.pathname.endsWith('vg-bold-theme.css') ||
      url.pathname.endsWith('vg-catalog-stats.js') ||
      url.pathname.endsWith('vg-fullscreen-pages.js') ||
      url.pathname.endsWith('vg-fullscreen-pages.css') ||
      url.pathname.endsWith('vg-customer-panel.js') ||
      url.pathname.endsWith('vg-customer-panel.css') ||
      url.pathname.endsWith('vg-catalog-stats.css') ||
      url.pathname.endsWith('vg-font-settings.js') ||
      url.pathname.endsWith('vg-quality-pricing.js') ||
      /icon-|apple-touch|favicon/.test(url.pathname))){
    event.respondWith((async()=>{
      try{
        const fresh=await fetch(req,{cache:'no-store'});
        const cache=await caches.open(CACHE);
        cache.put(req,fresh.clone());
        return fresh;
      }catch(_e){
        return (await caches.match(req)) || Response.error();
      }
    })());
    return;
  }

  event.respondWith((async()=>{
    const cached=await caches.match(req);
    if(cached) return cached;
    try{
      const fresh=await fetch(req);
      if(fresh?.ok && url.origin===self.location.origin){
        const cache=await caches.open(CACHE);
        cache.put(req,fresh.clone());
      }
      return fresh;
    }catch(_e){
      return cached || Response.error();
    }
  })());
});
