# V e G 0.8.9 — delivery checks

Existing repository: meapps-it/VG. Package: com.vg.gestionale.seconda. Version code: 25.

The customer storefront was rebuilt with one stylesheet, the existing logo, system fonts, ivory/cuoio colours, compact data-backed filters, product quality badges and galleries. Header accessory photos come only from published inventory; missing inventory photographs are never fabricated.

Orders require name, phone, address, province, city, postcode and privacy consent. Email is not collected. Native city selects support province filtering and manual fallback. Final submission stays above the summary; server validates consent and required address data, re-reads prices and records supplier quality snapshots. Timestamp is returned from database created_at and rendered in Europe/Rome. Existing FCM notification outbox and Android channel are preserved.

Supplier and quality columns now save together. Server-returned publication/quality values are checked after saving. Existing sale prices and historical tax data are preserved. Supplier metadata was backfilled for this owner's records without updating sale prices or photographs.

## Validation

- Shared pricing: every tier boundary, 25% floors, rounding, original-quality negotiation buffer, USD conversion and custom modes; 100 EUR + 40% = 140 EUR, without VAT.
- Article sync: published/hidden state, supplier-derived quality, preserved price/photos and failure if server does not confirm state.
- Supabase: real authenticated owner RLS update to hidden state returned successfully inside a rolled-back transaction. Inventory remains 213 records, 207 published/active/available.
- Browser suites: 360, 412 and 1280px, gallery arrows/thumbnails/swipe/zoom, missing photos, expiry refresh, combined filters, shipping, WhatsApp, validation and isolated order submission.
- Live browser: 207 real products, Originale filter returns 68 published records, Alma gallery has 8 pictures; quantity 2 totals EUR 580. Cuneo select contains 247 municipalities plus placeholder/manual options; Beinette suggests 12081. Invalid form cannot advance. No real customer order was submitted.
- Android Actions run 40242406333: unit tests, lint, assemble, fixed test-signing identity, article autofill, font/management/navigation/photo regressions, Android 35 instrumented tests, process-stop preferences and installation smoke check all passed.
- Production push history: one registered device; three outbox entries marked sent, zero recorded errors. Isolated FCM dispatch tests check high priority, channel, payload and duplicate prevention.
- Private delivery APK: signed with the existing private identity; binary package/certificate/version comparison against V-e-G-0.8.4.apk confirms compatible update, version code 20 -> 25.

## Remaining data/device limitations

48 of 207 published articles have no associated photograph objects available in Supabase. Stored legacy metadata references for these articles were checked; no matching objects exist. The UI shows a truthful missing-photo placeholder and preserves the articles. Recovering actual missing files requires an available original copy; no replacement product photos were generated.

Notification receipt on the owner's physical Samsung, especially with the app closed, is not demonstrated by the emulator or server delivery history. Existing credentials were reused. No new credentials or repository were created.

GitHub Pages is the deployment under this task. The legacy independent simplexapp.it/shop host is not deployed by this repository's Pages build; its duplicate deployment-version check was removed while all source-owned functional tests and CORS compatibility remain.