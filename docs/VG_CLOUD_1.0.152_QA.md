# V e G — cloud e pubblicazione, web 1.0.152

## Errore corretto sul database

`prodotti` e `ordini` avevano indici UNIQUE parziali (`WHERE sku IS NOT NULL` e `WHERE numero_ordine IS NOT NULL`). I target PostgREST `on_conflict=user_id,sku` e `on_conflict=user_id,numero_ordine`, senza predicato, non riuscivano a inferirli: PostgreSQL restituiva 42P10. I log reali confermavano lo stesso errore dello screenshot.

Gli indici sono stati sostituiti con indici UNIQUE completi sulle medesime coppie di colonne. La semantica dei valori NULL resta invariata: possono essere multipli. Non sono state eliminate o modificate righe. Lo SQL ripetibile è in `supabase/fixes/vg-upsert-constraints.sql`.

Verifica reale con ruolo `authenticated` e identità del proprietario: UPSERT di un articolo esistente, cambio pubblicazione e rilettura confermata. La transazione di prova è stata annullata per ripristinare integralmente lo stato originale. EXPLAIN dell'UPSERT ordini conferma `vg_ordini_user_numero_upsert` come conflict arbiter. RLS rimane attiva su entrambe le tabelle.

## Pubblicazione e reattività

- Per un articolo già cloud la spunta invia soltanto `stato_pubblicazione`, con filtri per ID e proprietario. Prezzo, foto e metadati non vengono riscritti.
- Il controllo resta disabilitato e presente nella pagina durante la richiesta. Lo stato locale cambia dopo la conferma del server. Gli errori sono mostrati e mantengono l'ultimo stato confermato.
- I download che si sovrappongono a una modifica di pubblicazione vengono scartati prima di applicare uno snapshot vecchio.
- Le normali letture locali non ricompongono né riscrivono più l'archivio completo. La migrazione di eventuali chiavi legacy rimane disponibile.
- I salvataggi aggiornano la pagina attiva. Le altre pagine sono renderizzate quando vengono aperte; le griglie e le fotografie nascoste non vengono ricostruite a ogni operazione.
- Il rinnovo del token non ricostruisce l'interfaccia completa.

## Verifiche

- 16 test Node su pubblicazione, concorrenza, letture locali, salvataggio articoli e fotografie: passati.
- 16 test Node su API catalogo, accesso, ordini e payload push: passati.
- Test prezzi completo: passato.
- Browser a 360/412 px: spunta durante richiesta differita, conferma mantenuta dopo reload, ripristino corretto su errore, un aggiornamento del solo stato e nessuna sincronizzazione foto.
- Verifiche esistenti del gestionale e delle foto: passate.
- Pipeline verde: https://github.com/meapps-it/VG/actions/runs/40282507610

## Consegna

Versione web visibile nelle impostazioni: **1.0.152**. Si applica anche all'APK ibrida 0.8.10 già firmata, senza modifiche al contenitore Android. Nessun nuovo package, chiave o account richiesto.

Il miglioramento dei percorsi di rendering è verificato nei test; la fluidità sul telefono fisico resta da confermare dopo il caricamento della nuova versione. I file fotografici mancanti già segnalati non vengono ricreati da questa correzione.
