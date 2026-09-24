# Analisi Fase 1 — 24 settembre 2026

## Repository storico

Il repository ufficiale è `meapps-it/VG`, branch `main`. La versione precedente è una PWA monolitica:

- `index.html`: interfaccia e gran parte della logica JavaScript inline;
- `supabaseClient.js`: inizializzazione Supabase JS e persistenza sessione;
- `manifest.json`, `service-worker.js`: installazione e cache PWA;
- `capacitor.config.json`, `codemagic.yaml`: precedente confezionamento Capacitor;
- numerose copie di lavoro e risorse del vecchio catalogo nella root.

La PWA non viene cancellata in Fase 1. È separata dalla nuova app in `android-native/`.

## Logiche specifiche individuate nella PWA

Il codice storico contiene ancora funzioni di riconoscimento del fornitore dal link, preset `Daniel`, `Jayden`, `Jessica`, qualità `Top Quality` / `Original Quality` e formule collegate. Queste logiche **non sono state riutilizzate** nell'app nativa. La nuova app usa esclusivamente le anagrafiche create dall'utente.

Le marche fisse non sono presenti nel codice Android. Le marche storiche nel database sono dati importati dell'account proprietario, non preset per gli account nuovi.

## Supabase verificato

Progetto: `qfjwtawsqfwmsmrrgqfi` (regione `eu-central-1`).

Consistenza rilevata prima della nuova app:

| Entità | Record |
|---|---:|
| Articoli (`prodotti`) | 215 |
| Categorie | 19 |
| Clienti | 335 |
| Ordini | 50 |
| Righe ordine | 46 |
| Foto articolo | 1.746 dopo allineamento Storage |
| Marche storiche importate | 20 |
| Fornitori anagrafici | 0 |

Tabelle di altri progetti presenti nello stesso Supabase (`lisa_*`, `me_apps*`) sono fuori ambito e non vengono modificate.

## Backup e migrazione

È presente lo schema privato `legacy_backup_20260924`, creato prima della migrazione del nucleo nativo. Il manifest riporta il backup `before_native_core_phase1` con 215 prodotti, 19 categorie, 335 clienti, 50 ordini, 46 righe ordine, 1 riga foto preesistente e 1.880 metadati Storage.

La migrazione del nucleo è additiva: mantiene colonne e record legacy, aggiunge `marche`, `fornitori`, relazioni proprietario-entità, campi neutri articolo, costi e margini generati, ordine categorie e metadati foto.

## Sicurezza

- RLS attiva su tutte le tabelle usate dall'app;
- policy CRUD limitate a `auth.uid() = user_id`;
- bucket `articoli` privato;
- policy Storage limitate all'`owner_id` autenticato;
- chiave nell'app: solo publishable key, mai `service_role`;
- URL immagini firmati e temporanei;
- record marca/categoria/fornitore collegabili solo se appartengono allo stesso utente.

Gli advisor segnalano problemi residuali in funzioni/tabelle condivise di altri progetti e funzioni amministrative storiche. Non sono stati corretti in questa fase per non alterare Lisa AI, ME Apps Admin o il gestionale legacy senza una verifica dedicata.

## Scelta Android

È stata scelta un'app Kotlin/Jetpack Compose nativa, non un WebView. Le funzioni Fase 1 sono articoli, marche, fornitori, categorie, immagini, autenticazione persistente, impostazioni font e navigazione con stack esplicito. L'astrazione `EntitlementProvider` prepara Billing/Premium senza applicare limiti nella prima versione.
