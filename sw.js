const CACHE = 'claude-chat-v9';
const FILES = ['index.html', 'manifest.json', 'app-icon.png', 'logo.png'];

self.addEventListener('install', e => {
  e.waitUntil(caches.open(CACHE).then(c => c.addAll(FILES)));
});

self.addEventListener('fetch', e => {
  if (e.request.method !== 'GET' || e.request.url.includes('api.anthropic.com')) return;
  e.respondWith(caches.match(e.request).then(r => r || fetch(e.request)));
});
