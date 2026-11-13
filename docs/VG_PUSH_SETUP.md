# Notifiche push per il catalogo Vanity & Glamour

## Stato
Prima fase, **non pronta per la pubblicazione**. Frontend e service worker sono predisposti. Le notifiche non saranno proposte finché l'endpoint di configurazione non risponde con `enabled: true` e una chiave VAPID pubblica valida.

## Consenso
La preferenza è inizialmente **attiva**, come richiesto, ma nessuna notifica può essere inviata senza un'azione esplicita del cliente che autorizza il browser. Se il cliente rifiuta, la preferenza viene disattivata. Non tentare di aggirare il prompt di sistema.

## Endpoint server necessario
Supabase Edge Function `vg-push-subscriptions`:
- `GET ?action=config` => `{enabled:true,publicKey:"..."}`
- `POST {action:"subscribe",subscription:{endpoint,keys:{p256dh,auth}}}`
- `POST {action:"unsubscribe",endpoint:"..."}`

**Sicurezza obbligatoria:** salvare e verificare le subscription lato server, rate limiting, CORS limitato al catalogo, VAPID private key solo come secret nel backend (mai nel repository), accesso all'invio limitato agli amministratori con autorizzazione verificata lato server. Predisporre rimozione endpoint scaduti 404/410 e disiscrizione. Servono poi API di invio, pannello amministratore e contatore iscritti prima di pubblicare.

Il service worker è quello già attivo nella root, modificato per supportare gli eventi `push` e `notificationclick`. Testare su Chrome Android con opt-in, rifiuto, refresh, disiscrizione e apertura del link.

Non dichiarare le notifiche operative finché non sono stati eseguiti test end-to-end su un telefono reale.
