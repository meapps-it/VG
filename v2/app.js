(() => {
  'use strict';

  const $ = (s, root=document) => root.querySelector(s);
  const $$ = (s, root=document) => [...root.querySelectorAll(s)];
  const euro = n => new Intl.NumberFormat('it-IT',{style:'currency',currency:'EUR'}).format(Number(n||0));
  const num = n => Number(n||0);
  const fmtDate = d => d ? new Intl.DateTimeFormat('it-IT').format(new Date(`${d}T12:00:00`)) : '—';
  const esc = v => String(v ?? '').replace(/[&<>'"]/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;',"'":'&#39;','"':'&quot;'}[c]));
  const uid = () => (crypto.randomUUID ? crypto.randomUUID() : `${Date.now()}-${Math.random().toString(16).slice(2)}`);
  const today = () => new Date().toISOString().slice(0,10);
  const monthKey = d => String(d||'').slice(0,7);

  const state = {
    supabase:null,user:null,current:'dashboard',
    categories:[],products:[],photos:[],clients:[],orders:[],lines:[],shipments:[],
    signedPhotos:new Map(),
    filters:{
      products:{q:'',category:'',brand:'',quality:'',supplier:'',mode:'all'},
      clients:{q:''},orders:{q:'',status:''},shipments:{q:'',status:''}
    }
  };

  async function init(){
    try{
      state.supabase = await window.VG_SUPABASE_READY;
      if(!state.supabase) throw new Error('Configurazione Supabase non disponibile.');
      bindShell();
      const {data:{session}} = await state.supabase.auth.getSession();
      if(session?.user) await enter(session.user); else showAuth();
      state.supabase.auth.onAuthStateChange((_event,session)=>{
        if(session?.user && (!state.user || session.user.id!==state.user.id)) enter(session.user);
        if(!session?.user && state.user) leave();
      });
      if('serviceWorker' in navigator) navigator.serviceWorker.register('service-worker.js').catch(()=>{});
    }catch(err){
      showAuth();
      $('#authMessage').textContent = err.message || 'Errore di avvio.';
    }
  }

  function bindShell(){
    $('#loginForm').addEventListener('submit',login);
    $('#registerBtn').addEventListener('click',register);
    $('#resetBtn').addEventListener('click',resetPassword);
    $('#menuBtn').addEventListener('click',openDrawer);
    $('#drawerClose').addEventListener('click',closeDrawer);
    $('#drawerBackdrop').addEventListener('click',closeDrawer);
    $('#logoutBtn').addEventListener('click',()=>state.supabase.auth.signOut());
    $('#refreshBtn').addEventListener('click',async()=>{closeDrawer();await loadAll(true);toast('Dati sincronizzati');});
    $('#backupBtn').addEventListener('click',exportBackup);
    $$('.nav-btn').forEach(b=>b.addEventListener('click',()=>go(b.dataset.tab)));
    $$('[data-secondary]').forEach(b=>b.addEventListener('click',()=>{closeDrawer();go(b.dataset.secondary);}));
    setInterval(updateClock,1000); updateClock();
  }

  function updateClock(){
    const el=$('#liveClock'); if(!el)return;
    const d=new Date();
    const date=new Intl.DateTimeFormat('it-IT',{weekday:'long',day:'2-digit',month:'2-digit',year:'numeric'}).format(d);
    const time=new Intl.DateTimeFormat('it-IT',{hour:'2-digit',minute:'2-digit',second:'2-digit'}).format(d);
    el.textContent=`${date} • ${time}`;
  }

  function showAuth(){ $('#authScreen').hidden=false; $('#appShell').hidden=true; }
  function leave(){ state.user=null; $('#appShell').hidden=true; $('#authScreen').hidden=false; closeDrawer(); }

  async function login(e){
    e.preventDefault();
    const email=$('#loginEmail').value.trim(), password=$('#loginPassword').value;
    $('#authMessage').textContent='Accesso…';
    const {error}=await state.supabase.auth.signInWithPassword({email,password});
    $('#authMessage').textContent=error ? error.message : '';
  }

  async function register(){
    const email=$('#loginEmail').value.trim(), password=$('#loginPassword').value;
    if(!email || password.length<6){ $('#authMessage').textContent='Inserisci email e password di almeno 6 caratteri.'; return; }
    const {error}=await state.supabase.auth.signUp({email,password});
    $('#authMessage').textContent=error ? error.message : 'Registrazione effettuata. Controlla la mail se è richiesta la conferma.';
  }

  async function resetPassword(){
    const email=$('#loginEmail').value.trim();
    if(!email){ $('#authMessage').textContent='Inserisci prima la tua email.'; return; }
    const {error}=await state.supabase.auth.resetPasswordForEmail(email,{redirectTo:location.href.split('#')[0]});
    $('#authMessage').textContent=error ? error.message : 'Email di recupero inviata.';
  }

  async function enter(user){
    state.user=user;
    $('#authScreen').hidden=true; $('#appShell').hidden=false;
    $('#drawerUser').textContent=user.email||'';
    await loadAll();
    const initial=(location.hash||'#dashboard').slice(1);
    go(['dashboard','prodotti','clienti','ordini','spedizioni','statistiche'].includes(initial)?initial:'dashboard',false);
  }

  async function loadAll(showErrors=false){
    const queries=[
      state.supabase.from('categorie').select('*').order('nome'),
      state.supabase.from('prodotti').select('*').order('created_at',{ascending:false}),
      state.supabase.from('prodotti_foto').select('*').order('ordine'),
      state.supabase.from('clienti').select('*').order('nome'),
      state.supabase.from('ordini').select('*').order('data_ordine',{ascending:false}),
      state.supabase.from('righe_ordine').select('*'),
      state.supabase.from('spedizioni').select('*').order('created_at',{ascending:false})
    ];
    const [cats,prods,photos,clients,orders,lines,ships]=await Promise.all(queries);
    const errors=[cats,prods,photos,clients,orders,lines,ships].map(x=>x.error).filter(Boolean);
    if(errors.length && showErrors) toast(errors[0].message);
    state.categories=cats.data||[]; state.products=prods.data||[]; state.photos=photos.data||[];
    state.clients=clients.data||[]; state.orders=orders.data||[]; state.lines=lines.data||[]; state.shipments=ships.data||[];
    state.signedPhotos.clear();
    await hydratePhotos();
    render();
  }

  async function hydratePhotos(){
    const first=new Map();
    for(const p of state.photos){ if(!first.has(p.prodotto_id)) first.set(p.prodotto_id,p); }
    await Promise.all([...first.values()].slice(0,120).map(async p=>{
      const {data}=await state.supabase.storage.from('articoli').createSignedUrl(p.path,3600);
      if(data?.signedUrl) state.signedPhotos.set(p.prodotto_id,data.signedUrl);
    }));
  }

  function go(tab,push=true){
    state.current=tab;
    if(push) location.hash=tab;
    $$('.nav-btn').forEach(b=>b.classList.toggle('active',b.dataset.tab===tab));
    render();
  }

  addEventListener('hashchange',()=>{
    const t=location.hash.slice(1);
    if(['dashboard','prodotti','clienti','ordini','spedizioni','statistiche'].includes(t)){state.current=t;render();}
  });

  function render(){
    if(!state.user)return;
    const v=$('#view');
    if(state.current==='dashboard') return renderDashboard(v);
    if(state.current==='prodotti') return renderProducts(v);
    if(state.current==='clienti') return renderClients(v);
    if(state.current==='ordini') return renderOrders(v);
    if(state.current==='spedizioni') return renderShipments(v);
    if(state.current==='statistiche') return renderStats(v);
    if(state.current==='ricerca') return openGlobalSearch();
  }

  function activeOrders(){ return state.orders.filter(o=>!['consegnato','annullato'].includes(o.stato)); }

  function orderProfit(o){
    if(num(o.guadagno)!==0) return num(o.guadagno);
    return state.lines.filter(l=>l.ordine_id===o.id).reduce((s,l)=>s+num(l.guadagno_riga),0)-num(o.spese);
  }

  function monthlyOrders(key){return state.orders.filter(o=>monthKey(o.data_ordine)===key && o.stato!=='annullato');}

  function monthSeries(count=6){
    const out=[], now=new Date();
    for(let i=count-1;i>=0;i--){
      const d=new Date(now.getFullYear(),now.getMonth()-i,1);
      const key=`${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}`;
      const os=monthlyOrders(key);
      out.push({
        key,
        label:new Intl.DateTimeFormat('it-IT',{month:'short'}).format(d).replace('.',''),
        sales:os.reduce((s,o)=>s+num(o.totale),0),
        profit:os.reduce((s,o)=>s+orderProfit(o),0),
        count:os.length
      });
    }
    return out;
  }

  function chartHTML(series,field='sales'){
    const max=Math.max(1,...series.map(x=>num(x[field])));
    return `<div class="chart">${series.map((x,i)=>`<div class="bar-wrap"><div><div class="bar-value">${num(x[field])?esc(euro(x[field]).replace(',00 €','')):''}</div><div class="bar ${i===series.length-1?'current':''}" style="height:${Math.max(8,Math.round(num(x[field])/max*105))}px"></div></div><div class="bar-label">${esc(x.label)}</div></div>`).join('')}</div>`;
  }

  function renderDashboard(v){
    const nowKey=monthKey(today()), mo=monthlyOrders(nowKey);
    const sales=mo.reduce((s,o)=>s+num(o.totale),0), profit=mo.reduce((s,o)=>s+orderProfit(o),0);
    const totalProfit=state.orders.filter(o=>o.stato!=='annullato').reduce((s,o)=>s+orderProfit(o),0);
    const valid=state.orders.filter(o=>o.stato!=='annullato');
    const avg=valid.length?valid.reduce((s,o)=>s+num(o.totale),0)/valid.length:0;
    const problems=state.shipments.filter(s=>s.stato==='problema_spedizione').length;
    const series=monthSeries(6), avgMonthly=series.slice(0,-1).reduce((s,x)=>s+x.count,0)/Math.max(1,series.length-1);
    const headline=problems?`${problems} spedizion${problems===1?'e':'i'} da controllare`:mo.length<Math.max(1,avgMonthly*.65)?'Vendite sotto media':'Situazione sotto controllo';
    const sub=problems?'Ci sono spedizioni segnate con un problema.':mo.length<Math.max(1,avgMonthly*.65)?'Il mese corrente è sotto il ritmo medio degli ultimi mesi.':'Ordini e spedizioni non mostrano criticità evidenti.';
    v.innerHTML=`
      <section class="card hero-card"><div class="hero-kicker">Novità in primo piano</div><h2>${esc(headline)}</h2><p>${esc(sub)}</p><div class="list-head"><strong>ANDAMENTO ULTIMI 6 MESI</strong><span>INCASSATO</span></div>${chartHTML(series)}</section>
      <div class="grid-2" style="margin-top:14px">
        ${metric('Ordini in corso',activeOrders().length,'non ancora consegnati')}
        ${metric('Vendite mese',euro(sales),'incassato del mese','blue')}
        ${metric('Guadagno mese',euro(profit),'margine del mese corrente','green')}
        ${metric('Guadagno totale',euro(totalProfit),'margine complessivo','green')}
        ${metric('Media ordini',euro(avg),'valore medio per ordine')}
        ${metric('Clienti',state.clients.length,'anagrafiche salvate')}
        ${metric('Articoli',state.products.length,'prodotti a catalogo')}
        ${metric('Spedizioni da controllare',problems,problems?'richiedono attenzione':'nessun problema',problems?'red':'green')}
      </div>
      <section class="card quick-card"><h2>Azioni rapide</h2><div class="quick-actions">
        <button class="btn btn-primary" data-quick="product">Nuovo articolo</button>
        <button class="btn btn-soft" data-quick="order">Nuovo ordine</button>
        <button class="btn btn-soft" data-quick="client">Nuovo cliente</button>
        <button class="btn btn-info" data-quick="shipment">Nuova spedizione</button>
      </div></section>`;
    $$('[data-quick]',v).forEach(b=>b.addEventListener('click',()=>({product:openProductForm,order:openOrderForm,client:openClientForm,shipment:openShipmentForm}[b.dataset.quick])()));
  }

  function metric(title,value,note,color=''){
    return `<section class="card"><div class="metric-title">${esc(title)}</div><div class="metric-value ${color}">${esc(value)}</div><div class="metric-note">${esc(note)}</div></section>`;
  }

  function categoryName(id){return state.categories.find(x=>x.id===id)?.nome||'';}
  function productPrice(p){return p.in_promozione && num(p.prezzo_promozionale)>0?num(p.prezzo_promozionale):num(p.prezzo_vendita);}
  function productProfit(p){return productPrice(p)-num(p.prezzo_acquisto);}
  function unique(field){return [...new Set(state.products.map(p=>p[field]).filter(Boolean))].sort((a,b)=>String(a).localeCompare(String(b),'it'));}

  function renderProducts(v){
    const f=state.filters.products;
    const items=state.products.filter(p=>{
      const q=f.q.toLowerCase(), hay=`${p.nome||''} ${p.sku||''} ${p.marca||''} ${p.qualita||''} ${p.fornitore||''}`.toLowerCase();
      if(q&&!hay.includes(q))return false;
      if(f.category&&p.categoria_id!==f.category)return false;
      if(f.brand&&p.marca!==f.brand)return false;
      if(f.quality&&p.qualita!==f.quality)return false;
      if(f.supplier&&p.fornitore!==f.supplier)return false;
      if(f.mode==='promo'&&!p.in_promozione)return false;
      if(f.mode==='hidden'&&p.stato_pubblicazione!=='nascosto')return false;
      if(f.mode==='published'&&p.stato_pubblicazione!=='pubblicato')return false;
      if(f.mode==='nophoto'&&state.photos.some(x=>x.prodotto_id===p.id))return false;
      return true;
    });

    v.innerHTML=`<div class="list-head"><div><h2 class="section-title">Articoli</h2><p class="section-subtitle">${items.length} visualizzati su ${state.products.length}</p></div><button id="newProduct" class="btn btn-primary">+ Nuovo</button></div>
      <section class="card filters">
        <input class="wide" id="prodQ" placeholder="Cerca codice, nome, marca…" value="${esc(f.q)}">
        ${selectFilter('prodCat','Tutte le categorie',state.categories.map(x=>[x.id,x.nome]),f.category)}
        ${selectFilter('prodBrand','Tutte le marche',unique('marca').map(x=>[x,x]),f.brand)}
        ${selectFilter('prodQuality','Tutte le qualità',unique('qualita').map(x=>[x,x]),f.quality)}
        ${selectFilter('prodSupplier','Tutti i fornitori',unique('fornitore').map(x=>[x,x]),f.supplier)}
        <select id="prodMode" class="wide"><option value="all">Tutti gli articoli</option><option value="promo">Solo promozioni</option><option value="published">Pubblicati</option><option value="hidden">Nascosti</option><option value="nophoto">Senza foto</option></select>
      </section>
      ${items.length?`<div class="products-grid">${items.map(productCard).join('')}</div>`:'<div class="empty">Nessun articolo con questi filtri.</div>'}`;
    $('#prodMode').value=f.mode;
    const refresh=()=>{
      f.q=$('#prodQ').value;f.category=$('#prodCat').value;f.brand=$('#prodBrand').value;
      f.quality=$('#prodQuality').value;f.supplier=$('#prodSupplier').value;f.mode=$('#prodMode').value;renderProducts(v);
    };
    $('#prodQ').addEventListener('input',debounce(refresh,180));
    ['prodCat','prodBrand','prodQuality','prodSupplier','prodMode'].forEach(id=>$('#'+id).addEventListener('change',refresh));
    $('#newProduct').addEventListener('click',()=>openProductForm());
    $$('[data-edit-product]',v).forEach(b=>b.addEventListener('click',()=>openProductForm(b.dataset.editProduct)));
    $$('[data-share]',v).forEach(b=>b.addEventListener('click',()=>shareProduct(b.dataset.product,b.dataset.share)));
  }

  function selectFilter(id,label,options,value){
    return `<select id="${id}"><option value="">${esc(label)}</option>${options.map(([v,l])=>`<option value="${esc(v)}" ${String(v)===String(value)?'selected':''}>${esc(l)}</option>`).join('')}</select>`;
  }

  function productCard(p){
    const photo=state.signedPhotos.get(p.id), promo=p.in_promozione, margin=productProfit(p);
    const noPhoto=!state.photos.some(x=>x.prodotto_id===p.id);
    return `<article class="product-card ${promo?'promo':''}">${promo?'<span class="chip green promo-badge">🔥 IN PROMOZIONE</span>':''}
      ${photo?`<img class="product-image" src="${esc(photo)}" alt="${esc(p.nome)}" loading="lazy">`:`<div class="product-image product-placeholder">🛍️</div>`}
      <div class="product-name">${esc(p.nome)}</div>
      <div class="product-meta">Cod. ${esc(p.sku||'—')} • ${esc(p.qualita||'Standard')} • 📷 ${state.photos.filter(x=>x.prodotto_id===p.id).length} foto${noPhoto?' • senza foto':''}</div>
      <div class="product-price ${promo?'green':''}">${euro(productPrice(p))}</div>
      ${margin<20?`<div class="chip red">⚠ SOTTO MARGINE DI 20 €</div>`:''}
      <div class="card-actions"><button class="btn btn-outline" data-edit-product="${p.id}">Modifica</button><button class="btn btn-primary" data-share="fb" data-product="${p.id}">FB</button><button class="btn btn-info" data-share="tg" data-product="${p.id}">TG</button></div>
    </article>`;
  }

  function renderClients(v){
    const f=state.filters.clients,q=f.q.toLowerCase();
    const items=state.clients.filter(c=>`${c.nome||''} ${c.cognome||''} ${c.telefono||''} ${c.email||''} ${c.citta||''}`.toLowerCase().includes(q));
    v.innerHTML=`<div class="list-head"><div><h2 class="section-title">Clienti</h2><p class="section-subtitle">${state.clients.length} clienti</p></div><button id="newClient" class="btn btn-primary">+ Nuovo</button></div>
      <section class="card filters"><input class="wide" id="clientQ" placeholder="Cerca nome, telefono, città…" value="${esc(f.q)}"></section>
      <div class="list">${items.map(c=>`<article class="list-card" data-client="${c.id}"><div class="list-head"><div><h3>${esc([c.nome,c.cognome].filter(Boolean).join(' '))}</h3><p>${c.telefono?'Tel. '+esc(c.telefono):'Nessun telefono'}</p><p>${esc([c.citta,c.provincia].filter(Boolean).join(' • '))}</p></div><span class="chip">${esc(c.paese||'Italia')}</span></div></article>`).join('')||'<div class="empty">Nessun cliente trovato.</div>'}</div>`;
    $('#newClient').addEventListener('click',()=>openClientForm());
    $('#clientQ').addEventListener('input',debounce(()=>{f.q=$('#clientQ').value;renderClients(v)},180));
    $$('[data-client]',v).forEach(x=>x.addEventListener('click',()=>openClientDetail(x.dataset.client)));
  }

  function renderOrders(v){
    const f=state.filters.orders,q=f.q.toLowerCase();
    const items=state.orders.filter(o=>{
      const c=state.clients.find(x=>x.id===o.cliente_id);
      const hay=`${o.numero_ordine||''} ${c?.nome||''} ${c?.cognome||''} ${o.tracking_code||''}`.toLowerCase();
      return (!q||hay.includes(q))&&(!f.status||o.stato===f.status);
    });
    v.innerHTML=`<div class="list-head"><div><h2 class="section-title">Ordini</h2><p class="section-subtitle">${state.orders.length} ordini</p></div><button id="newOrder" class="btn btn-primary">+ Nuovo</button></div>
      <section class="card filters"><input id="orderQ" placeholder="Cerca cliente, ordine, tracking…" value="${esc(f.q)}">${selectFilter('orderStatus','Tutti gli stati',orderStates(),f.status)}</section>
      <div class="list">${items.map(orderCard).join('')||'<div class="empty">Nessun ordine trovato.</div>'}</div>`;
    $('#newOrder').addEventListener('click',()=>openOrderForm());
    const rr=()=>{f.q=$('#orderQ').value;f.status=$('#orderStatus').value;renderOrders(v)};
    $('#orderQ').addEventListener('input',debounce(rr,180));$('#orderStatus').addEventListener('change',rr);
    $$('[data-order]',v).forEach(x=>x.addEventListener('click',()=>openOrderDetail(x.dataset.order)));
  }

  function orderStates(){return [['nuovo','Nuovo'],['in_preparazione','In preparazione'],['ordinato_fornitore','Ordinato al fornitore'],['ricevuto','Ricevuto'],['pronto','Pronto'],['spedito','Spedito'],['consegnato','Consegnato'],['annullato','Annullato']];}
  function orderLabel(s){return orderStates().find(x=>x[0]===s)?.[1]||String(s||'').replaceAll('_',' ');}

  function orderCard(o){
    const c=state.clients.find(x=>x.id===o.cliente_id);
    const lines=state.lines.filter(l=>l.ordine_id===o.id);
    const names=lines.map(l=>state.products.find(p=>p.id===l.prodotto_id)?.nome).filter(Boolean).join(', ');
    return `<article class="list-card" data-order="${o.id}"><div class="list-head"><div><h3>${esc([c?.nome,c?.cognome].filter(Boolean).join(' ')||o.numero_ordine)}</h3><p>${fmtDate(o.data_ordine)} • ${esc(o.numero_ordine)}</p></div><span class="chip ${o.stato==='consegnato'?'green':o.stato==='annullato'?'red':'blue'}">${esc(orderLabel(o.stato))}</span></div><p><strong>Articoli:</strong> ${esc(names||'Nessun articolo')}</p><p>Totale pagato: <span class="list-value">${euro(o.totale_pagato||o.totale)}</span> • Guadagno <span class="list-value">${euro(orderProfit(o))}</span></p>${o.tracking_code?`<p>Tracking: <span class="tracking">${esc(o.tracking_code)}</span></p>`:''}</article>`;
  }

  function renderShipments(v){
    const f=state.filters.shipments,q=f.q.toLowerCase();
    const items=state.shipments.filter(s=>{
      const c=state.clients.find(x=>x.id===s.cliente_id),o=state.orders.find(x=>x.id===s.ordine_id);
      const hay=`${c?.nome||''} ${c?.cognome||''} ${s.corriere||''} ${s.tracking_code||''} ${o?.numero_ordine||''}`.toLowerCase();
      return (!q||hay.includes(q))&&(!f.status||s.stato===f.status);
    });
    v.innerHTML=`<div class="list-head"><div><h2 class="section-title">Spedizioni</h2><p class="section-subtitle">${state.shipments.length} spedizioni</p></div><button id="newShipment" class="btn btn-primary">+ Nuova</button></div>
      <section class="card filters"><input id="shipQ" placeholder="Cerca cliente, corriere, tracking…" value="${esc(f.q)}">${selectFilter('shipStatus','Tutti gli stati',shipmentStates(),f.status)}</section>
      <div class="list">${items.map(shipmentCard).join('')||'<div class="empty">Nessuna spedizione.</div>'}</div>`;
    $('#newShipment').addEventListener('click',()=>openShipmentForm());
    const rr=()=>{f.q=$('#shipQ').value;f.status=$('#shipStatus').value;renderShipments(v)};
    $('#shipQ').addEventListener('input',debounce(rr,180));$('#shipStatus').addEventListener('change',rr);
    $$('[data-shipment]',v).forEach(x=>x.addEventListener('click',e=>{if(e.target.closest('[data-copy],[data-track]'))return;openShipmentForm(x.dataset.shipment)}));
    $$('[data-copy]',v).forEach(b=>b.addEventListener('click',()=>copyText(b.dataset.copy)));
    $$('[data-track]',v).forEach(b=>b.addEventListener('click',()=>openTracking(b.dataset.track)));
  }

  function shipmentStates(){return [['da_spedire','Da spedire'],['preparata','Preparata'],['spedita','Spedita'],['in_transito','In transito'],['in_consegna','In consegna'],['consegnata','Consegnata'],['problema_spedizione','Problema spedizione']];}
  function shipLabel(s){return shipmentStates().find(x=>x[0]===s)?.[1]||String(s||'').replaceAll('_',' ');}

  function shipmentCard(s){
    const c=state.clients.find(x=>x.id===s.cliente_id),o=state.orders.find(x=>x.id===s.ordine_id),problem=s.stato==='problema_spedizione';
    return `<article class="list-card" data-shipment="${s.id}"><div class="list-head"><div><h3>${esc([c?.nome,c?.cognome].filter(Boolean).join(' ')||'Cliente')}</h3><p>${esc(o?.numero_ordine||'Ordine')} • ${esc(s.corriere||'Corriere non indicato')}</p></div><span class="chip ${problem?'red':s.stato==='consegnata'?'green':'blue'}">${esc(shipLabel(s.stato))}</span></div><p>Spedizione: ${fmtDate(s.data_spedizione)} • Prevista: ${fmtDate(s.data_prevista_consegna)}</p>${s.tracking_code?`<p class="tracking">${esc(s.tracking_code)}</p><div class="btn-row"><button class="btn btn-outline" data-copy="${esc(s.tracking_code)}">Copia tracking</button><button class="btn btn-info" data-track="${s.id}">Apri tracking</button></div>`:''}</article>`;
  }

  function renderStats(v){
    const six=monthSeries(6), twelve=monthSeries(12), sold=new Map(), clientTotals=new Map();
    for(const l of state.lines){
      sold.set(l.prodotto_id,(sold.get(l.prodotto_id)||0)+num(l.quantita));
      const o=state.orders.find(x=>x.id===l.ordine_id);
      if(o&&o.stato!=='annullato')clientTotals.set(o.cliente_id,(clientTotals.get(o.cliente_id)||0)+num(l.totale_riga||l.prezzo_unitario*l.quantita));
    }
    const topP=[...sold.entries()].sort((a,b)=>b[1]-a[1]).slice(0,8),topC=[...clientTotals.entries()].sort((a,b)=>b[1]-a[1]).slice(0,8);
    const all=state.orders.filter(o=>o.stato!=='annullato'), total=all.reduce((s,o)=>s+num(o.totale),0),profit=all.reduce((s,o)=>s+orderProfit(o),0);
    v.innerHTML=`<h2 class="section-title">Statistiche</h2><p class="section-subtitle">Vendite, guadagni e andamento reale.</p>
      <div class="grid-2">${metric('Vendite totali',euro(total),'ordini non annullati','blue')}${metric('Guadagno totale',euro(profit),'margine complessivo','green')}${metric('Numero ordini',all.length,'ordini validi')}${metric('Valore medio',euro(all.length?total/all.length:0),'media per ordine')}</div>
      <section class="card" style="margin-top:14px"><h3>Vendite ultimi 6 mesi</h3>${chartHTML(six)}</section>
      <section class="card" style="margin-top:14px"><h3>Guadagni ultimi 12 mesi</h3>${chartHTML(twelve,'profit')}</section>
      <section class="card" style="margin-top:14px"><h3>Articoli più venduti</h3><table class="stat-table"><tbody>${topP.map(([id,n])=>`<tr><td>${esc(state.products.find(p=>p.id===id)?.nome||'Articolo')}</td><td>${n}</td></tr>`).join('')||'<tr><td>Nessun dato</td><td></td></tr>'}</tbody></table></section>
      <section class="card" style="margin-top:14px"><h3>Clienti migliori</h3><table class="stat-table"><tbody>${topC.map(([id,n])=>{const c=state.clients.find(x=>x.id===id);return `<tr><td>${esc([c?.nome,c?.cognome].filter(Boolean).join(' ')||'Cliente')}</td><td>${euro(n)}</td></tr>`}).join('')||'<tr><td>Nessun dato</td><td></td></tr>'}</tbody></table></section>`;
  }

  function openDrawer(){ $('#drawerBackdrop').hidden=false; $('#drawer').classList.add('open'); $('#drawer').setAttribute('aria-hidden','false'); }
  function closeDrawer(){ $('#drawerBackdrop').hidden=true; $('#drawer').classList.remove('open'); $('#drawer').setAttribute('aria-hidden','true'); }

  function modal(title,body,{large=false}={}){
    $('#modalRoot').innerHTML=`<div class="modal-backdrop"><section class="modal ${large?'large':''}"><div class="modal-head"><h2>${esc(title)}</h2><button class="icon-btn" data-close>×</button></div>${body}</section></div>`;
    const root=$('#modalRoot');
    root.querySelector('[data-close]').addEventListener('click',closeModal);
    root.querySelector('.modal-backdrop').addEventListener('click',e=>{if(e.target===e.currentTarget)closeModal();});
    return root;
  }

  function closeModal(){ $('#modalRoot').innerHTML=''; }
  function formVal(form,name){return form.elements[name]?.value?.trim?.()??form.elements[name]?.value;}

  function field(name,label,value='',required=false,type='text',step=''){
    return `<label class="field">${esc(label)}<input name="${name}" type="${type}" value="${esc(value)}" ${required?'required':''} ${step?`step="${step}"`:''}></label>`;
  }

  function openProductForm(id){
    const p=state.products.find(x=>x.id===id)||{};
    const root=modal(id?'Modifica articolo':'Nuovo articolo',`<form id="productForm"><div class="form-grid">
      ${field('sku','Codice',p.sku||'')}${field('nome','Nome',p.nome||'',true)}
      <label class="field">Categoria<select name="categoria_id"><option value="">Nessuna</option>${state.categories.map(c=>`<option value="${c.id}" ${c.id===p.categoria_id?'selected':''}>${esc(c.nome)}</option>`).join('')}</select></label>
      ${field('marca','Marca',p.marca||'')}${field('qualita','Qualità',p.qualita||'')}${field('colore','Colore',p.colore||'')}
      ${field('materiale','Materiale',p.materiale||'')}${field('dimensioni','Dimensioni',p.dimensioni||p.taglia||'')}${field('fornitore','Fornitore',p.fornitore||'')}
      ${field('prezzo_acquisto','Prezzo acquisto',num(p.prezzo_acquisto),false,'number','0.01')}${field('prezzo_vendita','Prezzo vendita',num(p.prezzo_vendita),false,'number','0.01')}
      ${field('giacenza','Disponibilità / quantità',num(p.giacenza),false,'number','1')}
      <label class="field">Pubblicazione<select name="stato_pubblicazione"><option value="pubblicato" ${p.stato_pubblicazione!=='nascosto'?'selected':''}>Pubblicato</option><option value="nascosto" ${p.stato_pubblicazione==='nascosto'?'selected':''}>Nascosto</option></select></label>
      <label class="field"><span>Promozione</span><select name="in_promozione"><option value="false" ${!p.in_promozione?'selected':''}>No</option><option value="true" ${p.in_promozione?'selected':''}>Sì</option></select></label>
      ${field('prezzo_promozionale','Prezzo promozionale',num(p.prezzo_promozionale),false,'number','0.01')}
      <label class="field full">Descrizione<textarea name="descrizione">${esc(p.descrizione||'')}</textarea></label>
      <label class="field full">Note interne<textarea name="note_interne">${esc(p.note_interne||'')}</textarea></label>
      <label class="field full">Fotografie<input name="photos" type="file" accept="image/*" multiple></label>
      </div><div class="modal-footer">${id?'<button type="button" id="duplicateProduct" class="btn btn-soft">Duplica</button><button type="button" id="deleteProduct" class="btn btn-danger">Elimina</button>':''}<button class="btn btn-primary" type="submit">Salva</button></div></form>`,{large:true});
    const form=$('#productForm',root);
    form.addEventListener('submit',e=>saveProduct(e,id));
    if(id){
      $('#duplicateProduct',root).addEventListener('click',()=>duplicateProduct(id));
      $('#deleteProduct',root).addEventListener('click',()=>deleteProduct(id));
    }
  }

  async function saveProduct(e,id){
    e.preventDefault();
    const form=e.currentTarget;
    const payload={
      sku:formVal(form,'sku')||null,nome:formVal(form,'nome'),categoria_id:formVal(form,'categoria_id')||null,
      marca:formVal(form,'marca')||null,qualita:formVal(form,'qualita')||null,colore:formVal(form,'colore')||null,
      materiale:formVal(form,'materiale')||null,dimensioni:formVal(form,'dimensioni')||null,fornitore:formVal(form,'fornitore')||null,
      prezzo_acquisto:num(formVal(form,'prezzo_acquisto')),prezzo_vendita:num(formVal(form,'prezzo_vendita')),
      giacenza:Math.max(0,parseInt(formVal(form,'giacenza')||0)),
      stato_pubblicazione:formVal(form,'stato_pubblicazione'),attivo:formVal(form,'stato_pubblicazione')==='pubblicato',
      in_promozione:formVal(form,'in_promozione')==='true',prezzo_promozionale:num(formVal(form,'prezzo_promozionale')),
      descrizione:formVal(form,'descrizione')||null,note_interne:formVal(form,'note_interne')||null,user_id:state.user.id
    };
    let productId=id,error;
    if(id){({error}=await state.supabase.from('prodotti').update(payload).eq('id',id));}
    else{
      const r=await state.supabase.from('prodotti').insert(payload).select('id').single();
      error=r.error; productId=r.data?.id;
    }
    if(error)return toast(error.message);
    const files=[...form.elements.photos.files];
    if(files.length&&productId) await uploadProductPhotos(productId,files);
    closeModal();await loadAll();go('prodotti');toast('Articolo salvato');
  }

  async function uploadProductPhotos(productId,files){
    let order=state.photos.filter(x=>x.prodotto_id===productId).length;
    for(const file of files){
      const ext=(file.name.split('.').pop()||'jpg').toLowerCase();
      const path=`${state.user.id}/${productId}/${uid()}.${ext}`;
      const up=await state.supabase.storage.from('articoli').upload(path,file,{upsert:false,contentType:file.type||'image/jpeg'});
      if(up.error){toast(up.error.message);continue;}
      await state.supabase.from('prodotti_foto').insert({prodotto_id:productId,path,ordine:order++,user_id:state.user.id});
    }
  }

  async function duplicateProduct(id){
    const p=state.products.find(x=>x.id===id);if(!p)return;
    const clone={...p};delete clone.id;delete clone.created_at;delete clone.updated_at;
    clone.nome=`${p.nome} - copia`;clone.sku=p.sku?`${p.sku}-C`:null;
    const {error}=await state.supabase.from('prodotti').insert(clone);
    if(error)return toast(error.message);
    closeModal();await loadAll();toast('Articolo duplicato');
  }

  async function deleteProduct(id){
    if(!confirm('Eliminare questo articolo?'))return;
    const {error}=await state.supabase.from('prodotti').delete().eq('id',id);
    if(error)return toast(error.message);
    closeModal();await loadAll();toast('Articolo eliminato');
  }

  function openClientForm(id){
    const c=state.clients.find(x=>x.id===id)||{};
    const root=modal(id?'Modifica cliente':'Nuovo cliente',`<form id="clientForm"><div class="form-grid">
      ${field('nome','Nome',c.nome||'',true)}${field('cognome','Cognome',c.cognome||'')}
      ${field('telefono','Telefono',c.telefono||'')}${field('email','Email',c.email||'',false,'email')}
      ${field('indirizzo','Indirizzo',c.indirizzo||'')}${field('citta','Città',c.citta||'')}
      ${field('provincia','Provincia',c.provincia||'')}${field('cap','CAP',c.cap||'')}${field('paese','Nazione',c.paese||'Italia')}
      <label class="field full">Note<textarea name="note">${esc(c.note||'')}</textarea></label></div>
      <div class="modal-footer">${id?'<button type="button" id="deleteClient" class="btn btn-danger">Elimina</button>':''}<button class="btn btn-primary">Salva</button></div></form>`,{large:true});
    $('#clientForm',root).addEventListener('submit',e=>saveClient(e,id));
    if(id)$('#deleteClient',root).addEventListener('click',()=>deleteClient(id));
  }

  async function saveClient(e,id){
    e.preventDefault();const f=e.currentTarget;
    const p={nome:formVal(f,'nome'),cognome:formVal(f,'cognome')||null,telefono:formVal(f,'telefono')||null,email:formVal(f,'email')||null,indirizzo:formVal(f,'indirizzo')||null,citta:formVal(f,'citta')||null,provincia:formVal(f,'provincia')||null,cap:formVal(f,'cap')||null,paese:formVal(f,'paese')||'Italia',note:formVal(f,'note')||null,user_id:state.user.id};
    const r=id?await state.supabase.from('clienti').update(p).eq('id',id):await state.supabase.from('clienti').insert(p);
    if(r.error)return toast(r.error.message);
    closeModal();await loadAll();go('clienti');toast('Cliente salvato');
  }

  async function deleteClient(id){
    if(!confirm('Eliminare il cliente? Gli ordini collegati possono impedirlo.'))return;
    const {error}=await state.supabase.from('clienti').delete().eq('id',id);
    if(error)return toast(error.message);
    closeModal();await loadAll();toast('Cliente eliminato');
  }

  function openClientDetail(id){
    const c=state.clients.find(x=>x.id===id);if(!c)return;
    const os=state.orders.filter(o=>o.cliente_id===id&&o.stato!=='annullato'), ss=state.shipments.filter(s=>s.cliente_id===id);
    const total=os.reduce((s,o)=>s+num(o.totale),0);
    const last=[...os].sort((a,b)=>String(b.data_ordine).localeCompare(String(a.data_ordine)))[0];
    const root=modal([c.nome,c.cognome].filter(Boolean).join(' '),`<div class="grid-2">
      ${metric('Totale acquistato',euro(total),'complessivo','green')}${metric('Numero ordini',os.length,'ordini effettuati')}
      ${metric('Ultimo ordine',last?fmtDate(last.data_ordine):'—','data ultimo acquisto')}${metric('Spedizioni',ss.length,'collegate')}
      </div><section class="card" style="margin-top:14px"><p>${esc(c.telefono||'')}</p><p>${esc(c.email||'')}</p>
      <p>${esc([c.indirizzo,c.citta,c.provincia,c.cap,c.paese].filter(Boolean).join(' • '))}</p>
      ${c.note?`<p><strong>Note:</strong> ${esc(c.note)}</p>`:''}</section>
      <div class="modal-footer"><button id="editClient" class="btn btn-primary">Modifica</button></div>`,{large:true});
    $('#editClient',root).addEventListener('click',()=>openClientForm(id));
  }

  function openOrderForm(){
    if(!state.clients.length)return toast('Prima crea almeno un cliente.');
    if(!state.products.length)return toast('Prima crea almeno un articolo.');
    const root=modal('Nuovo ordine',`<form id="orderForm"><div class="form-grid">
      <label class="field full">Cliente<select name="cliente_id" required><option value="">Seleziona cliente</option>${state.clients.map(c=>`<option value="${c.id}">${esc([c.nome,c.cognome].filter(Boolean).join(' '))}</option>`).join('')}</select></label>
      ${field('data_ordine','Data ordine',today(),true,'date')}
      <label class="field">Stato ordine<select name="stato">${orderStates().map(([v,l])=>`<option value="${v}">${l}</option>`).join('')}</select></label>
      <label class="field">Pagamento<select name="stato_pagamento"><option value="da_pagare">Da pagare</option><option value="parziale">Parziale</option><option value="pagato">Pagato</option></select></label>
      ${field('metodo_pagamento','Metodo pagamento','')}${field('spese','Spese extra',0,false,'number','0.01')}${field('totale_pagato','Totale pagato',0,false,'number','0.01')}
      <label class="field full">Note<textarea name="note"></textarea></label></div>
      <h3>Articoli</h3><div id="orderLines"></div><button id="addLine" type="button" class="btn btn-soft">+ Aggiungi articolo</button>
      <div class="card" style="margin-top:12px"><div class="list-head"><strong>Totale</strong><strong id="orderTotal">€ 0,00</strong></div><div class="list-head"><span>Guadagno stimato</span><span id="orderProfit" class="green">€ 0,00</span></div></div>
      <div class="modal-footer"><button class="btn btn-primary">Salva ordine</button></div></form>`,{large:true});
    const lines=$('#orderLines',root);
    const add=()=>{
      const key=uid();lines.insertAdjacentHTML('beforeend',lineEditor(key));
      const row=$(`[data-line="${key}"]`,root);
      $$('select,input',row).forEach(x=>x.addEventListener('input',calcOrderForm));
      $('[data-remove]',row).addEventListener('click',()=>{row.remove();calcOrderForm();});
      calcOrderForm();
    };
    $('#addLine',root).addEventListener('click',add);add();
    $('#orderForm',root).addEventListener('submit',saveOrder);
  }

  function lineEditor(key){
    return `<div class="line-editor" data-line="${key}"><div class="line-grid"><label class="field">Articolo<select name="product" required><option value="">Seleziona</option>${state.products.filter(p=>p.attivo!==false).map(p=>`<option value="${p.id}">${esc(p.nome)} • ${euro(productPrice(p))}</option>`).join('')}</select></label><label class="field">Qtà<input name="qty" type="number" min="1" value="1" required></label><label class="field">Prezzo<input name="price" type="number" min="0" step="0.01" placeholder="auto"></label></div><button type="button" class="btn btn-text" data-remove>Rimuovi</button></div>`;
  }

  function collectOrderLines(root=document){
    return $$('[data-line]',root).map(row=>{
      const pid=$('[name="product"]',row).value,p=state.products.find(x=>x.id===pid);
      const qty=Math.max(1,parseInt($('[name="qty"]',row).value||1));
      const manual=$('[name="price"]',row).value,price=manual===''?productPrice(p||{}):num(manual),cost=num(p?.prezzo_acquisto);
      return {prodotto_id:pid,quantita:qty,prezzo_unitario:price,costo_unitario:cost,totale_riga:price*qty,guadagno_riga:(price-cost)*qty};
    }).filter(x=>x.prodotto_id);
  }

  function calcOrderForm(){
    const ls=collectOrderLines($('#modalRoot'));
    $('#orderTotal').textContent=euro(ls.reduce((s,l)=>s+l.totale_riga,0));
    $('#orderProfit').textContent=euro(ls.reduce((s,l)=>s+l.guadagno_riga,0));
  }

  async function saveOrder(e){
    e.preventDefault();const f=e.currentTarget,ls=collectOrderLines($('#modalRoot'));
    if(!ls.length)return toast('Inserisci almeno un articolo.');
    const total=ls.reduce((s,l)=>s+l.totale_riga,0),expenses=num(formVal(f,'spese')),profit=ls.reduce((s,l)=>s+l.guadagno_riga,0)-expenses,paid=num(formVal(f,'totale_pagato'));
    const order={
      numero_ordine:`ORD-${today().replaceAll('-','')}-${String(Date.now()).slice(-5)}`,
      cliente_id:formVal(f,'cliente_id'),data_ordine:formVal(f,'data_ordine'),stato:formVal(f,'stato'),totale:total,
      metodo_pagamento:formVal(f,'metodo_pagamento')||null,pagato:formVal(f,'stato_pagamento')==='pagato',
      stato_pagamento:formVal(f,'stato_pagamento'),spese:expenses,totale_pagato:paid,guadagno:profit,note:formVal(f,'note')||null,user_id:state.user.id
    };
    const r=await state.supabase.from('ordini').insert(order).select('id').single();
    if(r.error)return toast(r.error.message);
    const rows=ls.map(l=>({...l,ordine_id:r.data.id,sconto:0,user_id:state.user.id}));
    const lr=await state.supabase.from('righe_ordine').insert(rows);
    if(lr.error){await state.supabase.from('ordini').delete().eq('id',r.data.id);return toast(lr.error.message);}
    closeModal();await loadAll();go('ordini');toast('Ordine creato');
  }

  function openOrderDetail(id){
    const o=state.orders.find(x=>x.id===id);if(!o)return;
    const c=state.clients.find(x=>x.id===o.cliente_id),ls=state.lines.filter(x=>x.ordine_id===id),ship=state.shipments.find(x=>x.ordine_id===id);
    const root=modal(o.numero_ordine,`<section class="card"><h3>${esc([c?.nome,c?.cognome].filter(Boolean).join(' '))}</h3><p>${fmtDate(o.data_ordine)}</p>
      ${ls.map(l=>{const p=state.products.find(x=>x.id===l.prodotto_id);return `<p>${esc(p?.nome||'Articolo')} × ${l.quantita} • ${euro(l.totale_riga||l.prezzo_unitario*l.quantita)}</p>`}).join('')}
      <p><strong>Totale:</strong> ${euro(o.totale)} • <strong>Guadagno:</strong> ${euro(orderProfit(o))}</p></section>
      <form id="orderEdit"><div class="form-grid"><label class="field">Stato<select name="stato">${orderStates().map(([v,l])=>`<option value="${v}" ${v===o.stato?'selected':''}>${l}</option>`).join('')}</select></label>
      <label class="field">Pagamento<select name="stato_pagamento"><option value="da_pagare" ${o.stato_pagamento==='da_pagare'?'selected':''}>Da pagare</option><option value="parziale" ${o.stato_pagamento==='parziale'?'selected':''}>Parziale</option><option value="pagato" ${o.stato_pagamento==='pagato'?'selected':''}>Pagato</option><option value="rimborsato" ${o.stato_pagamento==='rimborsato'?'selected':''}>Rimborsato</option></select></label>
      ${field('totale_pagato','Totale pagato',num(o.totale_pagato),false,'number','0.01')}</div>
      <div class="modal-footer"><button class="btn btn-primary">Aggiorna</button>${ship?'':'<button type="button" id="shipFromOrder" class="btn btn-info">Crea spedizione</button>'}</div></form>`,{large:true});
    $('#orderEdit',root).addEventListener('submit',async e=>{
      e.preventDefault();const f=e.currentTarget,st=formVal(f,'stato_pagamento');
      const r=await state.supabase.from('ordini').update({stato:formVal(f,'stato'),stato_pagamento:st,pagato:st==='pagato',totale_pagato:num(formVal(f,'totale_pagato'))}).eq('id',id);
      if(r.error)return toast(r.error.message);
      closeModal();await loadAll();toast('Ordine aggiornato');
    });
    if(!ship)$('#shipFromOrder',root).addEventListener('click',()=>openShipmentForm(null,id));
  }

  function openShipmentForm(id,orderId=''){
    const s=state.shipments.find(x=>x.id===id)||{}, selectedOrder=orderId||s.ordine_id||'';
    const root=modal(id?'Modifica spedizione':'Nuova spedizione',`<form id="shipmentForm"><div class="form-grid">
      <label class="field full">Ordine<select name="ordine_id" required><option value="">Seleziona ordine</option>${state.orders.filter(o=>o.stato!=='annullato').map(o=>{const c=state.clients.find(x=>x.id===o.cliente_id);return `<option value="${o.id}" ${o.id===selectedOrder?'selected':''}>${esc(o.numero_ordine)} • ${esc([c?.nome,c?.cognome].filter(Boolean).join(' '))}</option>`}).join('')}</select></label>
      ${field('corriere','Corriere',s.corriere||'')}${field('tracking_code','Codice tracking',s.tracking_code||'')}${field('tracking_url','Link tracking',s.tracking_url||'',false,'url')}
      ${field('data_spedizione','Data spedizione',s.data_spedizione||'',false,'date')}
      <label class="field">Stato<select name="stato">${shipmentStates().map(([v,l])=>`<option value="${v}" ${v===(s.stato||'da_spedire')?'selected':''}>${l}</option>`).join('')}</select></label>
      ${field('data_prevista_consegna','Consegna prevista',s.data_prevista_consegna||'',false,'date')}${field('data_effettiva_consegna','Consegna effettiva',s.data_effettiva_consegna||'',false,'date')}
      ${field('costo_spedizione','Costo spedizione',num(s.costo_spedizione),false,'number','0.01')}
      <label class="field full">Note<textarea name="note">${esc(s.note||'')}</textarea></label></div>
      <div class="modal-footer">${id?'<button type="button" id="deleteShipment" class="btn btn-danger">Elimina</button>':''}<button class="btn btn-primary">Salva</button></div></form>`,{large:true});
    $('#shipmentForm',root).addEventListener('submit',e=>saveShipment(e,id));
    if(id)$('#deleteShipment',root).addEventListener('click',()=>deleteShipment(id));
  }

  async function saveShipment(e,id){
    e.preventDefault();const f=e.currentTarget,oid=formVal(f,'ordine_id'),o=state.orders.find(x=>x.id===oid);
    if(!o)return toast('Ordine non valido.');
    const p={
      ordine_id:oid,cliente_id:o.cliente_id,corriere:formVal(f,'corriere')||null,tracking_code:formVal(f,'tracking_code')||null,
      tracking_url:formVal(f,'tracking_url')||null,data_spedizione:formVal(f,'data_spedizione')||null,stato:formVal(f,'stato'),
      data_prevista_consegna:formVal(f,'data_prevista_consegna')||null,data_effettiva_consegna:formVal(f,'data_effettiva_consegna')||null,
      costo_spedizione:num(formVal(f,'costo_spedizione')),note:formVal(f,'note')||null,user_id:state.user.id
    };
    const r=id?await state.supabase.from('spedizioni').update(p).eq('id',id):await state.supabase.from('spedizioni').insert(p);
    if(r.error)return toast(r.error.message);
    const orderUpdate={tracking_code:p.tracking_code,tracking_url:p.tracking_url,corriere:p.corriere};
    if(['spedita','in_transito','in_consegna','consegnata'].includes(p.stato))orderUpdate.stato=p.stato==='consegnata'?'consegnato':'spedito';
    await state.supabase.from('ordini').update(orderUpdate).eq('id',oid);
    closeModal();await loadAll();go('spedizioni');toast('Spedizione salvata');
  }

  async function deleteShipment(id){
    if(!confirm('Eliminare la spedizione?'))return;
    const {error}=await state.supabase.from('spedizioni').delete().eq('id',id);
    if(error)return toast(error.message);
    closeModal();await loadAll();toast('Spedizione eliminata');
  }

  async function shareProduct(id,channel){
    const p=state.products.find(x=>x.id===id);if(!p)return;
    const text=`${p.nome}\nCod. ${p.sku||'—'}\nPrezzo ${euro(productPrice(p))}`, url=location.href.split('#')[0];
    if(navigator.share){
      try{await navigator.share({title:p.nome,text,url});return}
      catch(e){if(e.name==='AbortError')return;}
    }
    const shareUrl=channel==='tg'
      ?`https://t.me/share/url?url=${encodeURIComponent(url)}&text=${encodeURIComponent(text)}`
      :`https://www.facebook.com/sharer/sharer.php?u=${encodeURIComponent(url)}&quote=${encodeURIComponent(text)}`;
    window.open(shareUrl,'_blank','noopener');
  }

  async function copyText(t){
    try{await navigator.clipboard.writeText(t);toast('Tracking copiato');}
    catch{toast('Impossibile copiare');}
  }

  function openTracking(id){
    const s=state.shipments.find(x=>x.id===id);if(!s)return;
    if(s.tracking_url)return window.open(s.tracking_url,'_blank','noopener');
    const q=encodeURIComponent(`${s.corriere||'corriere'} tracking ${s.tracking_code||''}`);
    window.open(`https://www.google.com/search?q=${q}`,'_blank','noopener');
  }

  function openGlobalSearch(){
    state.current='dashboard';
    const root=modal('Ricerca globale',`<input id="globalQ" style="width:100%;padding:14px;border:1.5px solid var(--border);border-radius:16px" placeholder="Cerca articoli, clienti, ordini, spedizioni…" autofocus><div id="globalResults" class="search-results"></div>`,{large:true});
    const input=$('#globalQ',root),out=$('#globalResults',root);
    const run=()=>{
      const q=input.value.trim().toLowerCase();
      if(q.length<2){out.innerHTML='<div class="empty">Scrivi almeno 2 caratteri.</div>';return;}
      const hits=[];
      state.products.filter(p=>`${p.nome} ${p.sku} ${p.marca}`.toLowerCase().includes(q)).slice(0,8).forEach(p=>hits.push(['Articolo',p.nome,`Cod. ${p.sku||'—'}`]));
      state.clients.filter(c=>`${c.nome} ${c.cognome} ${c.telefono} ${c.email}`.toLowerCase().includes(q)).slice(0,8).forEach(c=>hits.push(['Cliente',[c.nome,c.cognome].filter(Boolean).join(' '),c.telefono||c.email||'']));
      state.orders.filter(o=>`${o.numero_ordine} ${o.tracking_code}`.toLowerCase().includes(q)).slice(0,8).forEach(o=>hits.push(['Ordine',o.numero_ordine,orderLabel(o.stato)]));
      state.shipments.filter(s=>`${s.tracking_code} ${s.corriere}`.toLowerCase().includes(q)).slice(0,8).forEach(s=>hits.push(['Spedizione',s.tracking_code||'Senza tracking',shipLabel(s.stato)]));
      out.innerHTML=hits.map(h=>`<div class="search-hit"><small>${esc(h[0])}</small><strong style="display:block">${esc(h[1])}</strong><span>${esc(h[2])}</span></div>`).join('')||'<div class="empty">Nessun risultato.</div>';
    };
    input.addEventListener('input',debounce(run,120));run();
  }

  function exportBackup(){
    closeDrawer();
    const data={version:2,exported_at:new Date().toISOString(),categories:state.categories,products:state.products,product_photos:state.photos,clients:state.clients,orders:state.orders,order_lines:state.lines,shipments:state.shipments};
    const blob=new Blob([JSON.stringify(data,null,2)],{type:'application/json'}),a=document.createElement('a');
    a.href=URL.createObjectURL(blob);a.download=`gestionale-backup-${today()}.json`;a.click();
    setTimeout(()=>URL.revokeObjectURL(a.href),1500);toast('Backup esportato');
  }

  function toast(msg){
    const t=$('#toast');t.textContent=msg;t.classList.add('show');
    clearTimeout(toast._t);toast._t=setTimeout(()=>t.classList.remove('show'),2600);
  }

  function debounce(fn,ms){let t;return(...a)=>{clearTimeout(t);t=setTimeout(()=>fn(...a),ms)}}

  init();
})();