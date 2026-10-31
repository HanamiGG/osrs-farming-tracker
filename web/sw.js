/* Farmwatch service worker.
 * Caches the static shell so the dashboard still renders when offline. The
 * plugin's /state endpoint (and anything from 127.0.0.1 / localhost) is
 * network-only — we never want to serve a stale live snapshot. The cache
 * key is tied to a version string that bumps on each deploy.
 */
const CACHE = 'farmwatch-shell-v2';
const SHELL = [
  './',
  './index.html',
  './app.js',
  './style.css',
  './patch-decoder.json',
  './samples/sample-state.json',
  './icon.png',
];

self.addEventListener('install', e => {
  e.waitUntil(caches.open(CACHE).then(c => c.addAll(SHELL)).then(() => self.skipWaiting()));
});

self.addEventListener('activate', e => {
  e.waitUntil(
    caches.keys().then(keys => Promise.all(keys.filter(k => k !== CACHE).map(k => caches.delete(k))))
      .then(() => self.clients.claim())
  );
});

self.addEventListener('fetch', e => {
  const url = new URL(e.request.url);
  // Never cache or intercept plugin traffic — always go to the network.
  if (url.hostname === '127.0.0.1' || url.hostname === 'localhost') return;
  // Only handle same-origin GETs; cross-origin (wiki icons) go straight through.
  if (url.origin !== self.location.origin) return;
  if (e.request.method !== 'GET') return;

  e.respondWith(
    caches.match(e.request).then(cached => {
      const network = fetch(e.request).then(r => {
        if (r && r.ok) {
          const copy = r.clone();
          caches.open(CACHE).then(c => c.put(e.request, copy));
        }
        return r;
      }).catch(() => cached);
      return cached || network;
    })
  );
});
