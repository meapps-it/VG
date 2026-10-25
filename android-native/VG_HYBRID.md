# V&G ibrida

Build existing project variant: `./gradlew :app:assembleVgDebug :app:lintVgDebug :app:testVgDebugUnitTest`.
Package: `com.vg.gestionale.seconda`; version 0.8.4 (20). The existing native Compose experience is retained as the `native` flavor.

## Signing and existing installations

The previously delivered 0.8.0/0.8.1/0.8.2 APKs used three different ephemeral Android Debug certificates. They cannot update each other. The original private keys were not retained. Do not promise that resigning a replacement APK will update one of those installations, and do not tell the owner to uninstall without a verified backup of local data and photographs.

CI now uses an explicit project-relative test keystore and verifies its certificate fingerprint before uploading. The public CI debug key is ONLY a reproducible test identity. **Never deliver the CI test-signed artifact as an app update.**

The persistent private delivery identity is held outside Git in the owner's private file `VG-Android-signing-private.json`. Resolve and materialize that exact file through the owner's private file storage before signing; never regenerate it if it already exists, never print its contents and never put it in this repository or the APK. The bundle contains the expected certificate fingerprint, keystore and credentials. Use `ci/sign-delivery.py --config <private-file> --input <CI-apk> --output <delivery-apk> --apksig-jar <pinned-Google-Maven-apksig-8.13.2.jar>`. The script refuses a wrong application package, library or certificate. Verify the final signer and increasing versionCode against the preceding DELIVERY artifact before sharing any new APK.

The existing installed hybrid apps can continue receiving web changes. In particular, Password clienti now uses the shared web form, accepting six characters even on the old APK, and never requires reinstalling it merely to change the catalog password.

The `vg` launcher loads the existing remote VG PWA over HTTPS. Product data, web UI, catalog and checkout updates deploy online; Android framework, permissions and native integrations require a new APK. The wrapper supports persistent web storage, file chooser, external downloads and back navigation, with an origin-restricted WebMessage bridge for Android appearance preferences only.

The APK uses the same PWA header, hamburger menu and dashboard. Android font transfer, accessibility scaling and fallback limits are documented in [VG_APPEARANCE.md](VG_APPEARANCE.md). It adds no native toolbar. The six-month sales chart remains in Dashboard; it is separate from catalog visit statistics.

Existing PWA hamburger menu:
- Statistics: shared web dialog with owner-only API authorization, 1 visit/device/30-minute window, today/7/30 days, devices and vertical 7/30-day bar charts. These statistics and their layout can update online without rebuilding Android.
- Admin catalog preview: signed administrator session, excluded from analytics. Opening it in the phone browser also marks a previous browser session as administrator, removing its earlier visits from the reported totals.
- Customer password: minimum 6 characters, owner-only update; previous customer sessions are invalidated. Passwords are hashed server-side with salted PBKDF2; no shared password or server secret is bundled in the app.
- Order notifications: FCM client and server code are present, but delivery is NOT active until the Firebase project is configured.

## Firebase activation still required

No Firebase client or server configuration was present in the VG project at implementation time. Do not claim push delivery was tested or enabled without completing these steps:

1. Register Android package `com.vg.gestionale.seconda` in the intended Firebase project.
2. Set server-only Supabase Edge Function secret `FCM_SERVICE_ACCOUNT` to the service-account JSON. Never add that private key to Git, the APK, browser code, or chat.
3. Set `FCM_CLIENT_CONFIG` to the Android `google-services.json` content or a normalized object with `projectId`, `applicationId`, `senderId`, `apiKey`. These client settings are returned only to the authenticated owner; the APK loads them at runtime, so this setup does not require another APK.
4. Open APK, sign into VG, select Menu → Notifiche ordini, grant Android notification permission, and verify a real controlled order on the physical device with the app in background.

The public catalog queues only real successful orders. A failed push cannot roll back or duplicate an order. Pending deliveries are retried when a device registers. No customer names or addresses appear in notification payloads.

## Shipping directory

The catalog bundles the municipality/CAP directory already used by the existing native app (RP92/comuni-italiani), with its license. Provinces filter municipality suggestions; single-CAP towns fill automatically; multi-CAP cities require choosing the street's CAP. Manual and foreign address entry remains possible. Saving shipping fields locally is opt-in.
