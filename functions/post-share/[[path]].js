const ORIGIN = 'https://veglamour.cloud';
const OWNER = 'a51e2715-3b5e-4ad3-906e-c664fcdd486d';
const AUTH_URL = 'https://qfjwtawsqfwmsmrrgqfi.supabase.co/auth/v1/user';
const MAX_PHOTO = 5 * 1024 * 1024;
const PHOTO_TYPES = new Set(['image/jpeg', 'image/png', 'image/webp']);
const escapeHtml = value => value.replace(/[&<>"']/g, character => ({
  '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;',
})[character]);

export async function onRequestPost({ request, env, params }) {
  if (params.path?.length) return new Response(null, { status: 404 });
  if (!env.VG_POST_SHARES || !env.VG_POST_AUTH_KEY) return new Response(null, { status: 503 });
  const authorization = request.headers.get('Authorization');
  if (!authorization?.startsWith('Bearer ')) return new Response(null, { status: 401 });
  const auth = await fetch(AUTH_URL, {
    headers: { Authorization: authorization, apikey: env.VG_POST_AUTH_KEY },
  });
  if (!auth.ok || (await auth.json()).id !== OWNER) return new Response(null, { status: 403 });

  if (Number(request.headers.get('Content-Length')) > MAX_PHOTO + 65536) {
    return new Response(null, { status: 413 });
  }
  const data = await request.formData().catch(() => null);
  const title = data?.get('title');
  const body = data?.get('body');
  const photo = data?.get('photo');
  if (typeof title !== 'string' || !title.trim() || title.length > 120 ||
      typeof body !== 'string' || !body.trim() || body.length > 6000 ||
      (photo !== null && (!(photo instanceof File) || !PHOTO_TYPES.has(photo.type) || photo.size > MAX_PHOTO))) {
    return new Response(null, { status: 400 });
  }
  const id = crypto.randomUUID();
  const link = data.get('link');
  let safeLink = '';
  try {
    const url = new URL(link);
    if (['https:', 'http:'].includes(url.protocol) && url.href.length <= 2048) safeLink = url.href;
  } catch {}

  if (photo) await env.VG_POST_SHARES.put(id + '/photo', await photo.arrayBuffer(), { metadata: { type: photo.type } });
  await env.VG_POST_SHARES.put(id, JSON.stringify({ title, body, link: safeLink, hasPhoto: Boolean(photo) }));
  return Response.json({ url: ORIGIN + '/post-share/' + id }, { status: 201 });
}

export async function onRequestGet({ env, params }) {
  const [id, asset, extra] = params.path || [];
  if (!/^[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}$/.test(id || '') || extra || (asset && asset !== 'photo')) {
    return new Response(null, { status: 404 });
  }
  if (!env.VG_POST_SHARES) return new Response(null, { status: 503 });
  const post = await env.VG_POST_SHARES.get(id, 'json');
  if (!post) return new Response(null, { status: 404 });
  if (asset) {
    if (!post.hasPhoto) return new Response(null, { status: 404 });
    const { value, metadata } = await env.VG_POST_SHARES.getWithMetadata(id + '/photo', 'arrayBuffer');
    if (!value || !PHOTO_TYPES.has(metadata?.type)) return new Response(null, { status: 404 });
    return new Response(value, { headers: { 'Content-Type': metadata.type, 'X-Content-Type-Options': 'nosniff' } });
  }

  const url = ORIGIN + '/post-share/' + id;
  const image = post.hasPhoto ? url + '/photo' : ORIGIN + '/shop-vanity-glamour.webp';
  const title = escapeHtml(post.title);
  const description = escapeHtml([...post.body.replace(/\s+/g, ' ').trim()].slice(0, 200).join(''));
  const link = post.link ? '<p><a href="' + escapeHtml(post.link) + '">Apri il link del post</a></p>' : '';
  return new Response(`<!doctype html>
<html lang="it"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<meta name="robots" content="noindex">
<meta property="og:type" content="article">
<meta property="og:url" content="${url}">
<meta property="og:title" content="${title}">
<meta property="og:description" content="${description}">
<meta property="og:image" content="${image}">
<title>${title} | Vanity &amp; Glamour</title></head>
<body><main><h1>${title}</h1>${post.hasPhoto ? '<img alt="" width="320" src="' + image + '">' : ''}
<div style="white-space:pre-wrap">${escapeHtml(post.body)}</div>${link}</main></body></html>`, {
    headers: { 'Content-Type': 'text/html; charset=utf-8', 'X-Content-Type-Options': 'nosniff' },
  });
}
