import { cp, mkdir, rm } from 'node:fs/promises';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const root = join(here, '..');
const www = join(root, 'www');

await rm(www, { recursive: true, force: true });
await mkdir(www, { recursive: true });

for (const file of ['index.html','app.css','app.js','supabaseClient.js','manifest.json','service-worker.js']) {
  await cp(join(root, file), join(www, file));
}

await cp(join(root, 'node_modules', '@supabase', 'supabase-js', 'dist', 'umd', 'supabase.js'), join(www, 'supabase.js'));

for (const icon of ['icon-v1-192.png','icon-v1-512.png','icon-v1-maskable-512.png']) {
  await cp(join(root, '..', icon), join(www, icon));
}

const manifestPath = join(www, 'manifest.json');
const manifest = JSON.parse(await (await import('node:fs/promises')).readFile(manifestPath, 'utf8'));
manifest.icons = manifest.icons.map(i => ({ ...i, src: i.src.replace('../','') }));
await (await import('node:fs/promises')).writeFile(manifestPath, JSON.stringify(manifest, null, 2) + '\n');
console.log('Web assets pronti in v2/www');
