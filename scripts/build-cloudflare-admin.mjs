import { mkdir, copyFile, writeFile, readdir, rm } from 'node:fs/promises';
import { join, extname } from 'node:path';

const root = process.cwd();
const out = join(root, 'dist-admin');
await rm(out, { recursive: true, force: true });
await mkdir(out, { recursive: true });

const holding = \`<!doctype html><html lang="it"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1"><meta name="robots" content="noindex,nofollow"><title>V&G | Amministrazione</title><style>body{font:16px system-ui;background:#f5f7fb;color:#18233d;min-height:100vh;display:grid;place-items:center;margin:0}main{padding:32px;max-width:460px;background:white;border:1px solid #e2e7f0;border-radius:18px;box-shadow:0 12px 35px #0001}h1{margin-top:0}</style></head><body><main><h1>Amministrazione V&G</h1><p>Area amministrativa in configurazione. Accesso non ancora disponibile.</p></main></body></html>\`;
if (process.env.VG_ADMIN_RELEASE !== 'enabled') {
  await writeFile(join(out, 'index.html'), holding);
  console.log('Safe holding page only. VG_ADMIN_RELEASE is not enabled.');
  process.exit(0);
}

// Only the admin frontend and known runtime assets; never publish repository contents wholesale.
// Cloudflare Access must protect both the custom domain and the pages.dev hostname
// before VG_ADMIN_RELEASE may be enabled.
const exact = new Set([
  'index.html', 'manifest.json', 'service-worker.js', 'reset.html',
  'supabaseClient.js',
  'favicon-v1-32.png','icon-v1-192.png','icon-v1-512.png',
  'icon-v1-maskable-512.png','apple-touch-icon-v1.png'
]);
const allowedScript = /^(?:vg-[a-z0-9-]+|customer-profile-ui|shop-[a-z0-9-]+)\.(?:js|css)$/;
const assets = /^(?:gestionale-sp-3d|shop-[a-z0-9-]+)\.(?:png|webp|svg)$/;
for (const filename of await readdir(root)) {
  if (!(exact.has(filename) || allowedScript.test(filename) || assets.test(filename))) continue;
  await copyFile(join(root, filename), join(out, filename));
}
console.log('Admin frontend copied to dist-admin; no SQL, JSON exports, docs or source folders.');
