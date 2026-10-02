# Gestionale Android nativo

Progetto esistente `it.meapps.gestionale`, Kotlin + Jetpack Compose / Material 3, Supabase Auth/PostgREST/Storage, Google Play Billing e Coil. Versione **0.8.0** (versionCode 16).

Questo percorso contiene esclusivamente l'app Android Gestionale. Il restyling preserva la struttura e la persistenza dell'app; descrizione completa, file, decisioni e copertura in [docs/REDESIGN.md](docs/REDESIGN.md).

## Build e test

Java 17, Android SDK 36 e rete per risolvere le dipendenze:

```sh
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
./gradlew :app:assembleRelease :app:bundleRelease
```

APK debug: `app/build/outputs/apk/debug/app-debug.apk`. La release è priva di configurazione di firma e deve essere firmata con la chiave di distribuzione del proprietario.

Per mantenere la compatibilità degli aggiornamenti debug, usare la chiave di sviluppo già fornita in `ci/debug.keystore.b64` nel percorso debug standard Android. Non disinstallare l'app per aggiornarla.

## Collaudo

Sono inclusi i test originali dei modelli e della navigazione, test del grafico/stati, e test Compose/Robolectric con grafica Android nativa. I test UI usano soltanto un backend in memoria: verificano CRUD e conservazione dei record/foto senza chiamare Supabase reale. Producono screenshot per 320/360/412 dp, font ingranditi, dark mode e landscape in `app/build/reports/ui-screenshots/`.

Supabase URL e chiave pubblicabile restano nella configurazione esistente; sessione, schema cloud, backup, OAuth e billing conservano l'implementazione originale. Le lingue dell'app rimangono incluse anche nel bundle AAB per supportare la scelta della lingua nelle Impostazioni.
