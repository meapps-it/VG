# Gestionale Vendite V2

Nuova versione mobile-first del gestionale, mantenuta separata dalla versione esistente nella root del repository.

## Funzioni
- Dashboard con vendite, guadagni, ordini, clienti, articoli e spedizioni.
- Catalogo con filtri, promozioni, pubblicazione, foto e condivisione.
- Anagrafica clienti con storico commerciale.
- Ordini multi-articolo con costo e guadagno automatici.
- Spedizioni con tracking, stati e collegamento all'ordine.
- Statistiche 6/12 mesi, articoli più venduti e clienti principali.
- Login Supabase, sincronizzazione e backup JSON.
- PWA e build Android tramite Capacitor.

## Database
Usa il progetto Supabase già collegato al gestionale esistente. Lo schema V2 è stato aggiunto in modo compatibile senza cancellare i dati esistenti.

## Sviluppo locale
1. `npm install`
2. `npm run web:sync`
3. `npx cap add android` la prima volta
4. `npm run android:debug`

## GitHub Actions
Il workflow **Build Gestionale Vendite Android** genera automaticamente un APK debug e lo pubblica come artifact del workflow.
