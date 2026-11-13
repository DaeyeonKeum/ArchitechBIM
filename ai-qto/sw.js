/* AI 사진분석 물량 — 오프라인 실행용. 화면(index.html)은 새 버전을 먼저 받고, 나머지는 저장본을 먼저 쓴다 */
const V = "ai-qto-202611130921";
const CORE = ["./", "index.html", "ipad.js", "ipad.css", "ar-web.js", "manifest.webmanifest",
  "icons/apple-touch-icon.png", "icons/icon-192.png", "icons/icon-512.png", "brand/poscoenc-logo.png",
  "samples/site-barrier.jpg", "samples/site-cone.jpg", "samples/site-earth.jpg",
  "samples/thumb-site-barrier.jpg", "samples/thumb-site-cone.jpg", "samples/thumb-site-earth.jpg"];

self.addEventListener("install", e => {
  e.waitUntil(caches.open(V).then(c => c.addAll(CORE)).then(() => self.skipWaiting()));
});
self.addEventListener("activate", e => {
  e.waitUntil(caches.keys().then(ks => Promise.all(ks.filter(k => k.startsWith("ai-qto-") && k !== V).map(k => caches.delete(k))))
    .then(() => self.clients.claim()));
});
self.addEventListener("fetch", e => {
  const r = e.request;
  if (r.method !== "GET") return;
  const same = new URL(r.url).origin === self.location.origin;
  if (r.mode === "navigate") {
    e.respondWith(fetch(r).then(res => { const c = res.clone(); caches.open(V).then(k => k.put("index.html", c)); return res; })
      .catch(() => caches.match("index.html")));
    return;
  }
  e.respondWith(caches.match(r).then(hit => {
    const net = fetch(r).then(res => {
      if (res && (res.ok || res.type === "opaque") && (same || /fonts\.(googleapis|gstatic)\.com$/.test(new URL(r.url).hostname))) {
        const c = res.clone(); caches.open(V).then(k => k.put(r, c));
      }
      return res;
    }).catch(() => hit);
    return hit || net;
  }));
});
