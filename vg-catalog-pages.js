(() => {
  const owner = 'a51e2715-3b5e-4ad3-906e-c664fcdd486d';
  const menu = document.querySelector('.topMenuGrid');
  if (!menu) return;

  const button = document.createElement('button');
  button.type = 'button';
  button.className = 'topMenuLink';
  button.textContent = 'Pagine del catalogo';
  menu.append(button);

  const dialog = document.createElement('dialog');
  dialog.className = 'vgStatsDialog vgCustomersPanel';
  dialog.setAttribute('aria-labelledby', 'vgCatalogPagesHeading');
  dialog.innerHTML = `
    <header class="vgStatsHeader">
      <h2 id="vgCatalogPagesHeading">Pagine del catalogo</h2>
      <button type="button" data-close>Chiudi</button>
    </header>
    <form class="vgStatsContent" id="vgCatalogPagesForm">
      <label>Pagina<select name="page"></select></label>
      <label>Tipo<select name="kind">
        <option value="information">Informazioni</option>
        <option value="special">Promo speciale</option>
        <option value="upcoming">Prossime offerte</option>
      </select></label>
      <label>Titolo<input name="title" maxlength="100" required></label>
      <label>Testo<textarea name="body" maxlength="5000" rows="6" required></textarea></label>
      <label class="vgPagePublish"><input type="checkbox" name="published"> Mostra nel catalogo</label>
      <button class="btn primary" type="submit" disabled>Salva pagina</button>
      <p data-status role="status"></p>
    </form>`;
  document.body.append(dialog);

  const form = dialog.querySelector('form');
  const fields = form.elements;
  const save = form.querySelector('[type=submit]');
  const status = form.querySelector('[data-status]');
  const close = dialog.querySelector('[data-close]');
  let pages = [];
  let busy = false;

  function setBusy(value) {
    busy = value;
    close.disabled = value;
    for (const field of fields) field.disabled = value;
  }

  function showPage() {
    const page = pages.find(item => item.id === fields.page.value);
    fields.kind.value = page?.kind || 'information';
    fields.title.value = page?.title || '';
    fields.body.value = page?.body || '';
    fields.published.checked = Boolean(page?.published);
  }

  function listPages(selected = '') {
    fields.page.replaceChildren(new Option('+ Nuova pagina', ''));
    for (const page of pages) {
      const state = page.published ? 'Visibile' : 'Bozza';
      fields.page.add(new Option(page.title + ' · ' + state, page.id));
    }
    fields.page.value = selected;
    showPage();
  }

  async function getClient() {
    const db = await window.VG_SUPABASE_READY;
    if (!db) throw Error('Accedi al gestionale.');
    const {data, error} = await db.auth.getUser();
    if (error || data.user?.id !== owner) throw Error('Funzione riservata all’amministratore.');
    return db;
  }

  close.onclick = () => dialog.close();
  dialog.addEventListener('cancel', event => {
    if (busy) event.preventDefault();
  });
  fields.page.onchange = () => {
    showPage();
    status.textContent = '';
  };
  button.onclick = async () => {
    if (typeof setTopMenuOpen === 'function') setTopMenuOpen(false);
    dialog.showModal();
    setBusy(true);
    status.textContent = 'Caricamento pagine…';
    let loaded = false;
    try {
      const db = await getClient();
      const {data, error} = await db.from('vg_catalog_pages')
        .select('id,kind,title,body,published').eq('owner_id', owner)
        .order('updated_at', {ascending: false}).order('id');
      if (error) throw error;
      pages = data || [];
      listPages();
      status.textContent = '';
      loaded = true;
    } catch (error) {
      status.textContent = error.message || 'Caricamento non riuscito.';
    } finally {
      setBusy(false);
      save.disabled = !loaded;
    }
  };
  form.onsubmit = async event => {
    event.preventDefault();
    if (busy || save.disabled) return;
    const id = fields.page.value;
    const draft = {
      owner_id: owner,
      kind: fields.kind.value,
      title: fields.title.value.trim(),
      body: fields.body.value.trim(),
      published: fields.published.checked,
      updated_at: new Date().toISOString()
    };
    if (!draft.title || !draft.body) {
      status.textContent = 'Inserisci titolo e testo.';
      return;
    }
    setBusy(true);
    status.textContent = 'Salvataggio…';
    try {
      const db = await getClient();
      const table = db.from('vg_catalog_pages');
      const write = id ? table.update(draft).eq('id', id).eq('owner_id', owner) : table.insert(draft);
      const {data, error} = await write.select('id,kind,title,body,published').single();
      if (error) throw error;
      pages = [data, ...pages.filter(page => page.id !== data.id)];
      listPages(data.id);
      status.textContent = data.published ? 'Pagina visibile nel catalogo.' : 'Bozza salvata.';
    } catch (error) {
      status.textContent = error.message || 'Salvataggio non riuscito.';
    } finally {
      setBusy(false);
    }
  };
})();
