(() => {
  const kinds = {information: 'Informazioni', special: 'Promo speciale', upcoming: 'Prossime offerte'};
  const root = document.getElementById('shopPages');

  function render(pages = []) {
    root.replaceChildren();
    root.hidden = true;
    if (!Array.isArray(pages) || !pages.length) return;

    const heading = document.createElement('h2');
    heading.textContent = 'Informazioni e offerte';
    root.append(heading);
    for (const page of pages) {
      const details = document.createElement('details');
      const summary = document.createElement('summary');
      const kind = document.createElement('span');
      kind.textContent = kinds[page.kind] || kinds.information;
      const title = document.createElement('strong');
      title.textContent = page.title;
      const body = document.createElement('p');
      body.textContent = page.body;
      summary.append(kind, title);
      details.append(summary, body);
      root.append(details);
    }
    root.hidden = false;
  }

  window.vgShopPages = {render};
  window.addEventListener('vg-shop-logout', () => render());
})();
