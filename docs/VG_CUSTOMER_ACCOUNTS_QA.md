# Customer accounts and article variants — 26 October 2026

Existing repository, catalogue and Supabase function retained. No Android package or signing changes.

## Behaviour

- Shipping fields save locally during editing, with an explicit opt-out. Privacy consent and notes are never remembered. Remembered customer profiles can also restore the address on another device after personal login. Background restore does not replace edited fields or override opt-out.
- Optional registration: telephone, chosen six-digit PIN and account privacy consent; no email or new SMS provider. This is a personal account username, not telephone ownership verification. Catalogue shared-password sessions remain separate.
- PINs use randomly salted PBKDF2-SHA256 (200,000 iterations). Random opaque sessions last 365 days; only their SHA256 hashes are stored server-side. Rate limits apply to phone and IP. Customer authentication failures use 403 so they cannot clear the shared catalogue session.
- Each authenticated order is explicitly linked to its account when submitted. Legacy orders are never claimed just because someone types the same telephone number. Older orders require a separately verified administrative association; this release does not automatically assign them.
- Customer history is paginated and shows server request time in Europe/Rome, order number, current status, total, tracking and submitted item snapshots. It never returns supplier costs, internal notes or another account's orders.
- Sizes and numbered colours derive from existing article metadata. Fixed dimensions are descriptive. Required choices are checked by both client and server, retained separately in cart/order rows and mirrored into the admin order note. Admin synchronization preserves the new row columns.
- Forgotten PIN links to the existing shop WhatsApp. There is no unverified phone-only reset.

## Validation

- Node: authentication, invalid PINs, consent, rate limits, expired tokens, own-account/owner isolation, profile whitelist, article variants, trusted prices, separate variants and explicit order ownership; existing API/photo/FCM tests.
- DOM: registration, remembered session, shipping autosave and opt-out, history, required variants; existing shipping and gallery tests.
- Chromium and Safari WebKit: 360px and 1280px registration, persistent login and shipping after reload, two variants of one article, checkout, history, browser Back and no page errors. Existing 360/412/1280px gallery and navigation checks retained.
- Real deployed Supabase v13: 207 catalogue items with options, temporary customer registration, profile write/readback, empty history without legacy takeover, logout with separate catalogue session preserved. No test orders or notifications created. Temporary account removed and QA catalogue session expired.
- Management responsive workflow passed for 1.0.153.

Original fiscal data, saved prices, photos, orders and push configuration were preserved. Historical customer orders are not automatically reassigned to new accounts.
