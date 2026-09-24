# Gestionale Android (nativo)

Questa cartella contiene la nuova applicazione Android nativa. La PWA storica resta nella root del repository ed è mantenuta come versione legacy recuperabile.

## Stack

- Kotlin + Jetpack Compose
- chiamate HTTPS native verso Supabase Auth, PostgREST e Storage
- sessione Supabase persistita nello spazio privato dell'app e refresh automatico
- storage immagini privato con URL firmati
- `minSdk 26`, `targetSdk 36`

## Build

```bash
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
./gradlew :app:lintDebug
```

APK debug: `app/build/outputs/apk/debug/app-debug.apk`.

## Identità provvisoria

- nome: `Gestionale`
- package: `it.meapps.gestionale`
- versione: `0.1.0` (`versionCode 1`)

Il nome visualizzato e le risorse grafiche sono centralizzati nelle risorse Android e possono essere sostituiti senza modificare il modello dati.
