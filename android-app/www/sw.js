const CACHE_NAME = 'hesab-pwa-v4-fix';
const APP_SHELL = [
  './',
  './index.html',
  './manifest.json',
  './favicon-32.png',
  './icon-192.png',
  './icon-512.png',
  './icon-maskable-192.png',
  './icon-maskable-512.png',
  './apple-touch-icon.png'
];

self.addEventListener('install', event => {
  event.waitUntil(
    caches.open(CACHE_NAME)
      .then(cache => cache.addAll(APP_SHELL))
      .then(() => self.skipWaiting())
  );
});

self.addEventListener('activate', event => {
  event.waitUntil(
    caches.keys()
      .then(keys => Promise.all(keys.filter(key => key !== CACHE_NAME).map(key => caches.delete(key))))
      .then(() => self.clients.claim())
  );
});

async function networkFirst(request){
  const cache = await caches.open(CACHE_NAME);
  try{
    const response = await fetch(request);
    if(response && (response.ok || response.type === 'opaque')) cache.put(request, response.clone());
    return response;
  }catch(error){
    return (await cache.match(request)) || (await cache.match('./index.html'));
  }
}

async function cacheFirst(request){
  const cache = await caches.open(CACHE_NAME);
  const cached = await cache.match(request);
  if(cached) return cached;
  const response = await fetch(request);
  if(response && (response.ok || response.type === 'opaque')) cache.put(request, response.clone());
  return response;
}

self.addEventListener('fetch', event => {
  const request = event.request;
  if(request.method !== 'GET') return;

  if(request.mode === 'navigate'){
    event.respondWith(networkFirst(request));
    return;
  }

  const url = new URL(request.url);
  if(url.origin === self.location.origin){
    event.respondWith(cacheFirst(request));
    return;
  }

  event.respondWith(networkFirst(request));
});
