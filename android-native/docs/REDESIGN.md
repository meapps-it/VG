# Gestionale Android — restyling 0.8.0

Il lavoro interessa esclusivamente `android-native/`, package `it.meapps.gestionale`. Base: `cfe5e502274d8a97eba4e2977aebf2cf43147ba8` (0.7.4, codice 15). Nuova versione: 0.8.0, codice 16.

## Analisi dell'app esistente

App nativa Kotlin/Jetpack Compose, Material 3, Activity con AndroidViewModel e stato Compose. Gradle 8.13, AGP 8.13.2, Kotlin 2.2.21, Java 17; API minima 26, target/compile 36. Coil gestisce le immagini; scanner ZXing, BillingClient Google Play, OkHttp e coroutines restano quelli del progetto.

Schermate: accesso/registrazione/recupero password/OAuth Google, Dashboard, Articoli griglia/elenco, Clienti, Ordini, Anagrafiche (marche/fornitori/categorie), Impostazioni, tre dettagli e quattro editor. Restano tutorial, conferme eliminazione, demo volontarie, export backup, privacy dei valori economici, lingua, tema, dimensione testo e premium.

La persistenza di articoli, clienti, ordini, marche, categorie e fornitori avviene tramite l'API Supabase esistente (PostgREST). Auth e sessione usano `SessionStore` e SharedPreferences `session`; preferenze locali in `preferences`. Le foto sono nel bucket privato `articoli`, con URL firmati e galleria Coil. Non è stato aggiunto un database locale né modificato lo schema cloud. I clienti di esempio non sono stati trattati come dati hardcoded dell'interfaccia.

Navigazione originale: tab, cronologia indietro, editor e dettagli gestiti dal ViewModel. Questa struttura resta in uso. Sono state aggiunte soltanto azioni di apertura ordine e filtro ordini per cliente. `SupabaseApi.kt`, `Models.kt`, `BillingManager.kt`, manifest e regole backup restano invariati.

La build originale e i suoi sei test sono stati eseguiti prima del restyling. È stato verificato anche il percorso Dashboard → Articoli → Clienti → Ordini → dettagli, con schermate acquisite dal renderer Android nativo di Robolectric e dati isolati.

## Decisioni grafiche

- Identità navy `#102C48`, blu interattivo `#245FCC`, verde economico `#176A52`, arancio caldo `#88521B`, fondo `#F3F4F2`. Palette e variante scura centralizzate nel tema.
- Font Android di sistema: titoli semibold, testi con interlinea, etichette distinte e importi con dimensioni contenute. L'ingrandimento Android viene moltiplicato per la preferenza interna dell'app.
- Testata compatta: nome, data e hamburger allineato, senza aggiornamento dell'orologio ogni secondo. Navigazione Material 3 con un unico colore attivo e insets per le gesture.
- Dashboard: riepilogo mensile navy, due KPI neutri, grafico con scala reale da zero, selezione mese al tocco, valori leggibili, animazione breve e collegamento agli ordini; guadagno complessivo e stato ordini separati.
- Catalogo: ricerca leggibile, filtri espandibili/azzerabili, promo, griglia adattiva e vista elenco. Foto quando presente, placeholder compatto quando assente, prezzo effettivo promo, costo/margine subordinati, disponibilità neutra e fornitore. Galleria con scatto, miniature touch e stati di caricamento/errore.
- Clienti: iniziali, contatti/località, conteggio e valore ricavati dai veri ordini; nuovo ordine precompilato, dettaglio e chiamata con ACTION_DIAL.
- Ordini: cliente, riferimento/data, badge di consegna e pagamento distinti, articoli, totale ordine, guadagno e importo incassato separati. Uno stato sconosciuto non viene reinterpretato come lavorazione.
- Dettagli: immagine e prezzo in evidenza; dati, note e azioni ordinate. Il dettaglio cliente apre esclusivamente i suoi ordini.
- Editor: componenti condivisi, sezioni, tastiere e selettori esistenti, campi accoppiati che si impilano sugli schermi stretti/testo grande. Salva riconoscibile nella testata e messaggi di errore persistenti. Scanner, fotocamera, galleria e calendario conservati.
- Menu a gruppi: attività, organizzazione, app e dati. Transizioni di 150 ms, animazione grafico di 350 ms. Niente gradienti pesanti, contorni sistematici o ombre pronunciate.

## Organizzazione del codice

| File | Responsabilità |
| --- | --- |
| `MainActivity.kt` | Activity, accesso, root e dialog esistenti |
| `DesignSystem.kt` | Palette, tema scuro, typography e dimensioni |
| `UiComponents.kt` | Pannelli, KPI, badge, navigazione, pulsanti, editor responsive |
| `MainNavigation.kt` | Scaffold, menu, bottom navigation, transizioni |
| `DashboardScreen.kt` | KPI, grafico e aggregazione dei sei mesi |
| `CatalogScreen.kt` | Ricerca/filtri e schede griglia/elenco |
| `CrmScreens.kt` | Clienti, ordini e presentazione stati |
| `ProductImages.kt` | Galleria privata, miniature, placeholder, caricamento/errore |
| `DetailScreens.kt` | Dettagli articolo, cliente e ordine |
| `EditorScreens.kt` | Editor originali estratti, scanner/foto/calendario/comuni |
| `FormComponents.kt` | Campi/selezioni/stati vuoti e condivisione |
| `SettingsScreen.kt` | Anagrafiche e impostazioni originali estratte |
| `AppViewModel.kt` | Azioni ordine per cliente; nessuna modifica alla persistenza |
| `app/build.gradle.kts` | Versione 16/0.8.0, dipendenze solo di test e lingue AAB |
| `values/themes.xml`, `values-v31/themes.xml` | Aspetto iniziale e barre di sistema |
| `DashboardPresentationTest.kt` | Sei mesi, zeri, stati sconosciuti, pagamenti |
| `RedesignUiTest.kt`, `src/test/assets/catalog-photo.jpg` | UI Android isolata, CRUD, foto, responsive e screenshot |

## Verifica riproducibile

Con Java 17 e Android SDK 36 configurati:

```sh
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug :app:assembleRelease :app:bundleRelease
```

I test UI usano Robolectric API 35 e grafica Android nativa, con interceptor OkHttp che risponde in memoria a tutte le richieste del fixture. Nessun dato test viene scritto sul Supabase reale; nessuna nuova demo permanente viene aggiunta al prodotto. La fotografia test è un file già presente nel repository.

Copertura: navigazione e tre dettagli, griglia/elenco, validazione cliente, creazione/modifica cliente, modifica articolo con mantenimento foto, creazione/modifica ordine con prezzo promozionale, ricaricamento e mantenimento record. Larghezze 320, 360 e 412 dp, 320 dp con testo 1.3×, tema scuro e landscape. Sono prodotti screenshot reali in `app/build/reports/ui-screenshots/<versione>/`.

Gli artefatti standard si trovano in:

- `app/build/outputs/apk/debug/app-debug.apk` — debug firmato con la chiave di sviluppo già prevista in `ci/debug.keystore.b64`.
- `app/build/outputs/apk/release/app-release-unsigned.apk` — release non firmata.
- `app/build/outputs/bundle/release/app-release.aab` — bundle senza firma di distribuzione.

La firma release non è configurata nel progetto: gli artefatti release richiedono la chiave del proprietario prima della distribuzione. Non sono state create chiavi sostitutive. La firma debug va confrontata con la build precedente prima di aggiornare un dispositivo; non disinstallare l'app per l'aggiornamento.

Limiti: i controlli UI/CRUD sono effettuati sul runtime Android simulato con backend isolato, non costituiscono un collaudo su telefono fisico o sul Supabase di produzione. Accesso Google interattivo, acquisto Play, autorizzazioni hardware, upload remoto, connettività e ripristino backup reale richiedono un collaudo sul dispositivo/account del proprietario. Nessuna di queste integrazioni è stata modificata.

## Risultato del collaudo — 2 ottobre 2026

- Build originale: debug, 6 test esistenti e lint riusciti; test UI originale di navigazione/dettagli riuscito.
- Build finale: `assembleDebug`, `testDebugUnitTest`, `lintDebug`, `assembleRelease`, `bundleRelease`: BUILD SUCCESSFUL (2m 33s nell'ambiente preparato).
- Test finali: 15 eseguiti, 0 errori/fallimenti (6 originali, 4 presentazione dashboard/stati, 5 Android UI/CRUD/responsive).
- Lint: 0 errori, 67 avvisi. Si conservano risorse delle traduzioni precedenti ora non usate; restano anche indicazioni KTX, icona monocromatica, pluralizzazione e API pregresse. Nessun errore è stato silenziato mediante baseline.
- Verifica visiva delle schermate acquisite: foto reale visibile, placeholder compatto, KPI distinti, grafico proporzionato, badge e moduli coerenti in tema chiaro/scuro. Nessun dato del fixture viene distribuito nell'APK.
- Codice di modello/API/billing e regole di backup identici alla base; nessuna migrazione o eliminazione dei dati.

L'ambiente temporaneo precedente era stato ripulito prima della pubblicazione. Il restyling è stato riapplicato alla base originale e ricompilato/testato nuovamente; i risultati sopra si riferiscono a questa copia effettivamente salvata.
