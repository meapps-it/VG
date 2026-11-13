# Vanity & Glamour — notifiche push web

## Componenti realizzati

- `vg-push.js`: preferenza inizialmente attiva, richiesta esplicita del consenso del browser dopo il caricamento catalogo, iscrizione, rinnovo e disiscrizione.
- `service-worker.js`: gestione eventi `push` e `notificationclick` per aprire il catalogo.
- `notifiche-admin.html`: pannello riservato all'amministratore Supabase, statistiche e invio messaggi.
- `backend/vg-push-subscriptions/index.ts`: Edge Function Supabase già distribuita con slug `vg-push-subscriptions`. Richiede Auth Supabase con ID proprietario per statistiche e invio.
- Database produzione: `public.vg_catalog_push_subscriptions` e `public.vg_catalog_push_send_log`, RLS attiva, accesso negato a `anon` e `authenticated`.
- Due chiavi VAPID memorizzate cifrate in Supabase Vault. Una RPC `public.vg_catalog_vapid_keys()` accessibile soltanto a `service_role` le fornisce al backend, senza inserire il segreto nel codice.

## Sicurezza e comportamento

Il consenso browser è **obbligatorio** anche se la preferenza è inizialmente attivata. L'utente può rifiutare o disiscriversi. L'endpoint `subscribe` accetta solo URL HTTPS e limita a 5 nuovi dispositivi per hash IP in 24 ore. L'invio è limitato agli utenti autenticati come proprietario; URL destinazioni vincolati a `veglamour.cloud`.

## Endpoint

- `GET .../vg-push-subscriptions?action=config` (pubblico) restituisce `{ enabled, publicKey }`.
- `POST` con `action=subscribe` o `action=unsubscribe` (pubblico).
- `GET ...?action=stats` con Bearer JWT proprietario.
- `POST` con `action=send`, titolo, messaggio, URL e Bearer JWT proprietario.

## Test ancora necessari

1. Controllare la risposta HTTPS a `config` da Chrome Android.
2. Aprire il catalogo su smartphone, dare consenso, verificare l'iscrizione in database e il contatore.
3. Provare invio con telefono bloccato e apertura del link.
4. Disattivare le notifiche, verificare la rimozione dal database.
5. Verificare la navigazione del menu amministratore e l'accesso Google.
6. Verificare compilazione ed esecuzione dell'import `npm:web-push` in runtime Supabase.
7. Solo dopo, unire la PR e distribuire il frontend tramite il flusso Cloudflare/GitHub corrente.

**Nota:** la funzione backend è attiva nel progetto Supabase; il frontend resta in PR bozza. Nessun test Android end-to-end è stato completato.
