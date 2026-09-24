# Gestionale — PWA legacy + Android nativo

La nuova applicazione Android nativa è in [`android-native/`](android-native/). La PWA storica resta nella root per garantire recuperabilità e continuità durante la migrazione.

L'analisi tecnica della Fase 1 è in [`docs/PHASE1_ANALYSIS.md`](docs/PHASE1_ANALYSIS.md).

## PWA legacy

## Cosa fare
1. Crea un repository su GitHub.
2. Carica **tutti** i file di questa cartella nella root del repository.
3. Su GitHub vai in **Settings → Pages**.
4. In **Build and deployment** scegli:
   - **Source**: Deploy from a branch
   - **Branch**: `main`
   - **Folder**: `/ (root)`
5. Salva.
6. Aspetta 1-2 minuti e apri il link Pages che GitHub mostra.

## File importanti
- `index.html`
- `supabaseClient.js`
- `script.js`
- `manifest.json`
- `service-worker.js`
- icone e file accessori

## Nota cloud
Per usare Supabase devi fare login dall'app con l'utente creato nel progetto Supabase.
