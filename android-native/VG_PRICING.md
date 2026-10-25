# V e G 0.8.5 — ricarichi e compilazione articoli

Le fasce Original e Top/Standard recuperate dal vecchio gestionale sono attive
come modalità predefinita. Il modulo `vg-quality-pricing.js` è condiviso con la
PWA e incluso identico negli asset dell'APK; la WebView lo serve dall'APK per
l'URL del gestionale. Le impostazioni precedenti restano nel proprio archivio;
il nuovo profilo `vg_pricing_settings_v3_quality` attiva le fasce conservando
importi personalizzati e IVA per quando si sceglie un'altra modalità.

Original: costo fino a 210 → +70; fino a 230 → +75; fino a 250 → +80;
fino a 300 → +85; fino a 350 → +95; oltre → max(110, 25% del costo).
Top/Standard: fino a 50 → +45; fino a 80 → +55; fino a 120 → +60;
fino a 160 → +70; fino a 220 → +80; oltre → max(90, 25% del costo).
I limiti sono inclusivi. Prezzo teorico = costo + ricarico, arrotondato per
difetto a un multiplo di 5; Original aggiunge poi 10 euro di trattativa.
Questa modalità riproduce il vecchio calcolo senza aggiungervi l'IVA configurata.
Percentuale/importo fisso mantengono le impostazioni IVA già presenti.
La conversione USD del progetto resta USD × 0,90 + 5 euro.

Jayden determina Original; Daniel e Jessica determinano Top/Standard.
Con altri fornitori la qualità va scelta esplicitamente: non viene indovinata.
La scheda segnala l'assenza della qualità e non salva un prezzo automatico
privo della qualità necessaria. I prezzi standard già salvati non sono
riscritti in massa; le promozioni automatiche continuano a usare la modalità
attiva, come già previsto dalla gestione delle promozioni del progetto.

Il comando recuperato `Incolla dati articolo` apre il parser esistente dei
blocchi preparati da ChatGPT. Codice, brand, modello, categoria, fornitore,
qualità, materiale, misure, colori e costi vengono trasferiti ai campi della
bozza. Descrizione e note vengono ignorate; il salvataggio dell'articolo
richiede ancora il pulsante Salva. Non è un'analisi automatica delle foto e
non scarica contenuti dai siti dei fornitori.
Nell'APK il pulsante Incolla dagli appunti usa ClipboardManager solo tramite
il collegamento nativo protetto dai controlli di origine, pagina principale
e gesto utente; il browser/PWA mantiene la propria funzione appunti e il
fallback di incolla manuale. Il contenuto non viene inviato a servizi esterni.

Verifiche predisposte: tutte le soglie, arrotondamenti e buffer, profilo
precedente e nuove impostazioni, conversione USD, modalità alternative;
parser e compilazione della scheda reale a 360/412 px; modulo incluso nella
WebView Android, font e scala già presenti, appunti con tocco reale e rifiuto
della navigazione senza gesto; persistenza dopo chiusura del processo.
Consultare l'esito dell'esecuzione CI: questo elenco non ne certifica l'esito.
