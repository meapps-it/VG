/* Dedicated service worker for public catalog Web Push. */
/* Notifiche push: visualizzazione e apertura del catalogo. */
self.addEventListener('push', event => {
  let data={};try{data=event.data?.json()||{}}catch{data={body:event.data?.text()||''}}
  const title=String(data.title||'Vanity & Glamour');
  const url=new URL(data.url||'/',self.location.origin);
  if(url.origin!==self.location.origin)url.href=self.location.origin+'/';
  event.waitUntil(self.registration.showNotification(title,{
    body:String(data.body||'Scopri le novità del catalogo'),
    icon:'/shop-vanity-glamour.webp',badge:'/shop-vanity-glamour.webp',
    tag:String(data.tag||'vg-news'),data:{url:url.href}
  }));
});
self.addEventListener('notificationclick', event => {
  event.notification.close();
  const url=event.notification.data?.url||self.location.origin+'/';
  event.waitUntil((async()=>{
    const windows=await clients.matchAll({type:'window',includeUncontrolled:true});
    for(const client of windows)if(client.url===url && 'focus' in client)return client.focus();
    return clients.openWindow(url);
  })());
});
