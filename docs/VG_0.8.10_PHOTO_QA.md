# V e G 0.8.10 — recupero caricamento fotografie

## Modifiche

- Selettore immagini Android tramite Storage Access Framework, con lettura di `data` e `ClipData`, selezione multipla e deduplicazione degli URI.
- Le foto appena selezionate vengono lette e verificate prima di passarle alla WebView. La copia temporanea privata ha un'estensione e un MIME coerenti con il contenuto.
- Le nuove immagini HEIF/HEIC vengono convertite in JPEG su Android 9 o successivo, conservando l'orientamento. I file originali sul telefono non vengono modificati.
- Il gestionale riconosce le firme dei formati immagine quando un provider restituisce un MIME vuoto o generico. Un formato non decodificabile produce un messaggio esplicito.
- Salva attende la preparazione delle foto selezionate.
- La sincronizzazione conserva i collegamenti cloud quando l'archivio locale è assente e interrompe la sostituzione dei collegamenti se lettura o upload falliscono.
- Gli URL firmati falliti vengono rinnovati una volta; i percorsi mancanti hanno una cache negativa limitata. I problemi di prelettura non memorizzano una galleria permanentemente vuota.
- Anteprima amministratore dell'APK e messaggi WhatsApp aprono il catalogo corrente: https://meapps-it.github.io/VG/catalogo-nuovo.html.
- Versione 0.8.10, versionCode 26. Package e identità di firma di consegna invariati.

## Verifiche

- 27 test Node passati: fotografie, sincronizzazione articoli, API catalogo, accesso, ordini e payload FCM.
- Test prezzi passati: soglie, maggiorazioni, arrotondamenti, conversione USD e assenza IVA nel calcolo.
- Verifiche browser del gestionale passate a 360 e 412 px, inclusa una fotografia PNG presentata con MIME `application/octet-stream`, anteprime decodificate, annullamento e limite di 20 foto.
- Verifiche browser del catalogo, contatto WhatsApp e ordine isolato passate a 360, 412 e 1280 px.
- Build Android completa verde: https://github.com/meapps-it/VG/actions/runs/40279155799 (commit `a8e3868d6349a5da3bbc5e3a3bbf4d68b8275b4a`). Compilazione, unit test, lint, quattro test su Android 35 e preparazione/verifica della persistenza dopo force-stop passati.
- Firma privata verificata; aggiornamento da 0.8.9 compatibile: stesso pacchetto e certificato, versionCode 25 → 26. SHA256 APK di consegna: `03401748721d518988e3a77f448a24308a40d4bd12f4ae444f4429bd5b5de3f1`.
- I due test strumentali delle foto sono passati su Android 35: selezione dal provider con MIME generico, lettura nella WebView con immagine decodificata, ClipData, annullamento e rifiuto di un file non immagine. Il bersaglio del test degli appunti è fissato in alto, fuori dal pannello ClipboardOverlay di Android; le verifiche del gesto e dell'origine restano attive.
- Controllo reale della galleria Alma PM: otto fotografie; fotografia 8 decodificata a 1280 × 1280 px. Screenshot reale a 390 × 844 px.

## Limiti aperti

- 48 articoli pubblicati non hanno oggetti fotografici associati disponibili nel cloud. I 1098 collegamenti presenti in prodotti_foto corrispondono a file esistenti con il proprietario corretto. Nessuna foto è stata inventata o sostituita per coprire gli originali mancanti.
- Le verifiche Android vengono eseguite sull'emulatore Android 35 della pipeline. Il comportamento su un Samsung fisico richiede la prova dell'aggiornamento sul telefono interessato.
- La conversione HEIF è compilata e controllata dal lint; la prova del selettore usa immagini PNG reali, anche con MIME generico. Non è stata ancora certificata con una foto HEIF del telefono dell'utente.
