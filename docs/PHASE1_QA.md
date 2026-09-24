# Phase 1 QA report

Date: 2026-09-24

## Automated verification

- GitHub Actions run: `36058612982`
- Source commit: `8457acf6934ee78599c42928c2075a8ef9d4ce02`
- Kotlin/JVM unit tests: passed
- Android Lint: passed
- Debug APK assembly: passed
- GitHub artifact upload: passed

Unit coverage includes cost/margin calculations, zero-sale-price handling, navigation history and core validation.

## APK verification

- Application ID: `it.meapps.gestionale`
- Version code/name: `1` / `0.1.0`
- Minimum SDK: 26
- Target/compile SDK: 36
- APK Signature Scheme v2: valid
- Debug signer: Android Debug (development installation only)
- APK SHA-256: `4afb97b92485146ff75553abda6deffe7c39452af5b7b61728341b9b300bbf51`

## Supabase verification

- A logical pre-migration backup exists in schema `legacy_backup_20260924`.
- Native CRUD was exercised transactionally for brands, suppliers, categories and products, then rolled back.
- Generated costs and margins were verified during the transaction.
- A second authenticated user could not read the primary user's product or temporary brand records.
- The `articoli` storage bucket is private and its policies restrict objects by authenticated owner UUID.

## Manual device test still required before Play release

The APK is compiled and cryptographically installable, but a physical-device acceptance pass remains necessary for camera/gallery UI, keyboard/IME behaviour, rotation and Android Back gestures across vendor-specific Android builds. A Play Store release also requires a private release keystore, signed AAB, privacy-policy assets and Play Console declarations; the debug key in this APK must not be used for publishing.
