# V&G ibrida

Build existing project variant: `./gradlew :app:assembleVgDebug :app:lintVgDebug :app:testVgDebugUnitTest`.
Package: `com.vg.gestionale.seconda`; version 0.8.0 (16). The existing native Compose experience is retained as the `native` flavor.

The `vg` launcher loads the existing remote VG PWA over HTTPS. Product data, web UI, catalog and checkout updates deploy online; Android framework, permissions and native integrations require a new APK. The wrapper supports persistent web storage, file chooser, external downloads and back navigation, without exposing a JavaScript-to-Java bridge.

Native menu:
- Statistics: server-authorized owner-only dashboard, 1 visit/device/30-minute window, today/7/30 days and daily graph.
- Admin catalog preview: signed administrator session, excluded from analytics. Opening it in the phone browser also marks a previous browser session as administrator, removing its earlier visits from the reported totals.
- Customer password: owner-only update; previous customer sessions are invalidated. Passwords are hashed server-side with salted PBKDF2; no shared password or server secret is bundled in the app.
- Order notifications: FCM client and server code are present, but delivery is NOT active until the Firebase project is configured.

## Firebase activation still required

No Firebase client or server configuration was present in the VG project at implementation time. Do not claim push delivery was tested or enabled without completing these steps:

1. Register Android package `com.vg.gestionale.seconda` in the intended Firebase project.
2. Set server-only Supabase Edge Function secret `FCM_SERVICE_ACCOUNT` to the service-account JSON. Never add that private key to Git, the APK, browser code, or chat.
3. Set `FCM_CLIENT_CONFIG` to the Android `google-services.json` content or a normalized object with `projectId`, `applicationId`, `senderId`, `apiKey`. These client settings are returned only to the authenticated owner; the APK loads them at runtime, so this setup does not require another APK.
4. Open APK, sign into VG, select Menu → Attiva notifiche ordini, grant Android notification permission, and verify a real controlled order on the physical device with the app in background.

The public catalog queues only real successful orders. A failed push cannot roll back or duplicate an order. Pending deliveries are retried when a device registers. No customer names or addresses appear in notification payloads.

## Shipping directory

The catalog bundles the municipality/CAP directory already used by the existing native app (RP92/comuni-italiani), with its license. Provinces filter municipality suggestions; single-CAP towns fill automatically; multi-CAP cities require choosing the street's CAP. Manual and foreign address entry remains possible. Saving shipping fields locally is opt-in.
