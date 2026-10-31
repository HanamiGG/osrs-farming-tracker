(() => {
  'use strict';

  // ================================================================
  // Constants + persistence keys
  // ================================================================
  const LS = {
    account:   'farmwatch.account',
    endpoint:  'farmwatch.endpoint',
    snapshot:  'farmwatch.lastSnapshot',
    theme:     'farmwatch.theme',
    density:   'farmwatch.density',
    favorites: 'farmwatch.favorites',
    hidden:    'farmwatch.hiddenImpls',
    patchFilt: 'farmwatch.patches.filters',
    collapsed: imp => `farmwatch.collapsed.${imp}`,
    notify:    'farmwatch.notify',
  };

  const DEFAULT_ENDPOINT = 'http://127.0.0.1:17060';
  const POLL_MS          = 15000;
  const NEAR_READY_MS    = 10 * 60 * 1000;
  const IMP_ORDER = ['HERB','ALLOTMENT','TREE','FRUIT_TREE','BUSH','HOPS','CACTUS','FLOWER','MUSHROOM','HARDWOOD_TREE','ANIMA','CORAL','SEAWEED','GRAPES','CALQUAT','SPIRIT_TREE','CELASTRUS','CRYSTAL_TREE','REDWOOD','BELLADONNA','HESPORI','COMPOST','BIG_COMPOST','OTHER'];
  const PAGES = {
    dashboard:  { label: 'Dashboard',   render: renderDashboard  },
    patches:    { label: 'Patches',     render: renderPatches    },
    timeline:   { label: 'Timeline',    render: renderTimeline   },
    planner:    { label: 'Run planner', render: renderPlanner    },
    stats:      { label: 'Statistics',  render: renderStats      },
    reference:  { label: 'Reference',   render: renderReference  },
    connection: { label: 'Connection',  render: renderConnection },
    settings:   { label: 'Settings',    render: renderSettings   },
    about:      { label: 'About',       render: renderAbout      },
  };

  // ================================================================
  // State
  // ================================================================
  const STATE = {
    endpoint:   safeGet(LS.endpoint) || DEFAULT_ENDPOINT,
    data:       null,
    etag:       null,
    account:    safeGet(LS.account) || 'ALL',
    route:      'dashboard',
    timer:      null,
    offline:    true,
    decoder:    null,
    decoderErr: null,
    varbitInv:  {},
    search:     '',
    favorites:  new Set(parseJSON(safeGet(LS.favorites)) || []),
    hidden:     new Set(parseJSON(safeGet(LS.hidden)) || []),
    density:    safeGet(LS.density) || 'comfortable',
    patchFilt:  Object.assign({ state: 'ALL', imp: 'ALL', region: 'ALL', group: 'imp', showHidden: false, onlyFav: false },
                              parseJSON(safeGet(LS.patchFilt)) || {}),
    notified:   new Set(),
    notify:     parseJSON(safeGet(LS.notify)) || { enabled: false, leadMin: 5 },
  };

  // ================================================================
  // Boot
  // ================================================================
  document.addEventListener('DOMContentLoaded', () => {
    applyTheme(safeGet(LS.theme) || 'auto');
    bindShell();
    bindRouter();
    loadDecoder();
    hydrateFromCache();
    renderRoute();
    tryFetch();
    STATE.timer = setInterval(tryFetch, POLL_MS);
    setInterval(renderTicking, 1000);
    setInterval(maybeNotify, 30 * 1000);
  });

  function bindShell() {
    const navToggle = document.getElementById('nav-toggle');
    const sidebar   = document.getElementById('sidebar');
    navToggle.addEventListener('click', () => {
      const open = sidebar.classList.toggle('open');
      navToggle.setAttribute('aria-expanded', String(open));
    });

    document.getElementById('theme-toggle').addEventListener('click', () => {
      const next = currentTheme() === 'dark' ? 'light' : 'dark';
      applyTheme(next); safeSet(LS.theme, next);
    });

    const search = document.getElementById('search-input');
    search.value = STATE.search;
    search.addEventListener('input', () => {
      STATE.search = search.value.trim();
      renderRoute();
    });

    document.getElementById('account-select').addEventListener('change', e => {
      STATE.account = e.target.value; safeSet(LS.account, STATE.account);
      renderRoute();
    });

    document.addEventListener('keydown', e => {
      if (e.target.matches('input,select,textarea')) return;
      if (e.key === '/') { e.preventDefault(); search.focus(); return; }
      const map = { '1':'dashboard','2':'patches','3':'timeline','4':'planner','5':'stats','6':'reference','7':'connection','8':'settings','9':'about' };
      if (map[e.key]) { location.hash = '#/' + map[e.key]; }
    });
  }

  function bindRouter() {
    window.addEventListener('hashchange', renderRoute);
  }

  function parseRoute() {
    const h = location.hash || '';
    const m = h.match(/^#\/([a-z]+)/i);
    const r = m ? m[1].toLowerCase() : '';
    return PAGES[r] ? r : 'dashboard';
  }

  function renderRoute() {
    STATE.route = parseRoute();
    document.querySelectorAll('.nav-link').forEach(a => {
      a.classList.toggle('active', a.dataset.route === STATE.route);
    });
    document.getElementById('crumb-page').textContent = PAGES[STATE.route].label;
    document.title = PAGES[STATE.route].label + ' · Farmwatch';
    const main = document.getElementById('main');
    main.innerHTML = '';
    PAGES[STATE.route].render(main);
    document.getElementById('sidebar').classList.remove('open');
    main.scrollTop = 0;
    populateAccountSelect();
  }

  // ================================================================
  // Data: decoder, fetch, hydrate, decode
  // ================================================================
  function loadDecoder() {
    fetch('patch-decoder.json?v=' + Date.now(), { cache: 'no-store' })
      .then(async r => {
        if (!r.ok) throw new Error('HTTP ' + r.status + ' ' + r.statusText);
        const txt = await r.text();
        try { return JSON.parse(txt); }
        catch (e) { throw new Error('JSON parse: ' + e.message + ' (first bytes: ' + txt.slice(0, 60) + ')'); }
      })
      .then(d => {
        STATE.decoder = d; STATE.decoderErr = null;
        rebuildVarbitInv();
        renderRoute();
      })
      .catch(err => {
        STATE.decoderErr = String(err && err.message || err);
        console.error('[farmwatch] decoder load failed:', err);
        renderRoute();
      });
  }

  function rebuildVarbitInv() {
    STATE.varbitInv = {};
    const map = (STATE.data && STATE.data.varbitMap) || (STATE.decoder && STATE.decoder.varbitMap);
    if (!map) return;
    for (const [sym, id] of Object.entries(map)) STATE.varbitInv[id] = sym;
  }

  function hydrateFromCache() {
    const raw = safeGet(LS.snapshot);
    if (!raw) return;
    try { STATE.data = JSON.parse(raw); } catch { safeRemove(LS.snapshot); }
  }

  function tryFetch() {
    const ac = new AbortController();
    const t = setTimeout(() => ac.abort(), 2500);
    const headers = STATE.etag ? { 'If-None-Match': STATE.etag } : {};
    fetch(STATE.endpoint.replace(/\/+$/, '') + '/state', { signal: ac.signal, headers })
      .then(async r => {
        clearTimeout(t);
        if (r.status === 304) { setConnected(true); return; }
        if (!r.ok) throw new Error('HTTP ' + r.status);
        const etag = r.headers.get('ETag');
        const json = await r.json();
        STATE.data = json; STATE.etag = etag;
        rebuildVarbitInv();
        safeSet(LS.snapshot, JSON.stringify(json));
        setConnected(true);
        renderRoute();
      })
      .catch(() => { clearTimeout(t); setConnected(false); });
  }

  function readSnapshotFile(f) {
    const r = new FileReader();
    r.onload = () => {
      try {
        STATE.data = JSON.parse(r.result); STATE.etag = null;
        rebuildVarbitInv();
        safeSet(LS.snapshot, r.result);
        setConnected(false, 'snapshot');
        renderRoute();
      } catch { setChip('Bad snapshot', 'chip-bad'); }
    };
    r.readAsText(f);
  }

  function loadSampleData() {
    return fetch('samples/sample-state.json').then(r => r.json()).then(j => {
      STATE.data = j; rebuildVarbitInv();
      safeSet(LS.snapshot, JSON.stringify(j));
      setConnected(false, 'snapshot');
      renderRoute();
    });
  }

  function setConnected(ok, mode) {
    STATE.offline = !ok;
    if (ok) setChip('Live', 'chip-ok');
    else if (mode === 'snapshot') setChip('Snapshot', 'chip-neutral');
    else setChip('Offline', 'chip-bad');
  }

  function setChip(label, cls) {
    const el = document.getElementById('connection-chip');
    el.textContent = label;
    el.className = 'chip ' + cls;
  }

  function populateAccountSelect() {
    const sel = document.getElementById('account-select');
    if (!sel) return;
    const accs = (STATE.data && STATE.data.accounts) || [];
    const opts = ['<option value="ALL">All accounts</option>'];
    for (const a of accs) {
      const n = a.displayName || `(${a.hash.slice(0,6)}…)`;
      opts.push(`<option value="${esc(a.hash)}">${esc(n)}</option>`);
    }
    sel.innerHTML = opts.join('');
    sel.value = STATE.account;
  }

  function currentAccountPatches() {
    const accs = (STATE.data && STATE.data.accounts) || [];
    const slice = STATE.account === 'ALL'
      ? accs.flatMap(a => (a.patches || []).map(p => ({ ...p, _acc: a })))
      : (accs.find(a => a.hash === STATE.account)?.patches || []).map(p => ({ ...p, _acc: accs.find(a => a.hash === STATE.account) }));
    return slice.map(enrichPatch);
  }

  function allPatchesFromCatalog() {
    const dec = STATE.decoder;
    if (!dec || !dec.regions) return [];
    const live = currentAccountPatches();
    const byKey = new Map();
    for (const p of live) byKey.set(`${p.regionId}.${p.varbitId}`, p);
    const out = [];
    for (const region of dec.regions) {
      for (const patchDef of region.patches) {
        const varbitInt = (dec.varbitMap || {})[patchDef.varbit];
        if (!varbitInt) continue;
        const key = `${region.regionId}.${varbitInt}`;
        const hit = byKey.get(key);
        if (hit) { out.push(hit); continue; }
        out.push({
          id: key,
          regionId: region.regionId,
          varbitId: varbitInt,
          region: region.name + (patchDef.name ? ` (${patchDef.name})` : ''),
          implementation: patchDef.implementation,
          produce: null,
          state: 'UNTRACKED',
          _synthetic: true,
        });
      }
    }
    return out;
  }

  function patchSortKey(p) {
    const order = { HARVESTABLE: 0, GROWING: 2, DISEASED: 4, DEAD: 5, EMPTY: 6, UNCHECKED: 6, FILLING: 2, UNTRACKED: 7 };
    const now = Date.now();
    const base = order[p.state] ?? 3;
    if (p.state === 'GROWING' && p.readyAt) {
      const r = Date.parse(p.readyAt);
      if (r - now < NEAR_READY_MS) return 1;
      return 2 + Math.min(1, (r - now) / (24 * 60 * 60 * 1000));
    }
    return base;
  }

  function enrichPatch(p) {
    if (!p || (p.produce && p.state && p.implementation)) return p;
    const dec = STATE.decoder;
    if (!dec) return p;
    const sym = STATE.varbitInv[p.varbitId];
    if (!sym) return p;
    const region = dec.regions.find(r => r.regionId === p.regionId);
    if (!region) return p;
    const patchMeta = region.patches.find(x => x.varbit === sym);
    if (!patchMeta) return p;
    p.implementation = p.implementation || patchMeta.implementation;
    p.region = p.region || region.name + (patchMeta.name ? ` (${patchMeta.name})` : '');
    const ranges = dec.implementations[patchMeta.implementation] || [];
    const hit = ranges.find(r => p.varbitValue >= r.lo && p.varbitValue <= r.hi);
    if (!hit) return p;
    p.produce = p.produce || hit.produce;
    p.state = p.state || hit.state;
    if (hit.produce && dec.produce[hit.produce]) {
      p.wikiFile = p.wikiFile || dec.produce[hit.produce].wikiFile || null;
    }
    if (p.state === 'GROWING' && hit.produce && p.lastUpdated) {
      const prod = dec.produce[hit.produce];
      if (prod && prod.tickrate > 0) {
        const stage = p.varbitValue - hit.lo;
        const stagesRemaining = Math.max(0, prod.stages - 1 - stage);
        const observedUnix = Math.floor(Date.parse(p.lastUpdated) / 1000);
        const tickTime = alignToFarmTick(observedUnix, prod.tickrate, p._acc);
        const readyAtUnix = tickTime + stagesRemaining * prod.tickrate * 60;
        p.cycleStartedAt = p.cycleStartedAt || p.lastUpdated;
        p.readyAt = p.readyAt || new Date(readyAtUnix * 1000).toISOString();
      }
    } else if (p.state === 'HARVESTABLE') {
      p.readyAt = p.readyAt || p.lastUpdated;
    }
    return p;
  }

  function alignToFarmTick(requestedUnix, tickRateMin, acc) {
    let offsetSec = 0;
    if (acc && acc.farmTickOffset != null && acc.farmTickOffsetPrecision != null) {
      const prec = acc.farmTickOffsetPrecision;
      if (prec >= tickRateMin || prec >= 40) offsetSec = (acc.farmTickOffset % tickRateMin) * 60;
    }
    const shifted = requestedUnix + offsetSec;
    const period = tickRateMin * 60;
    const tickStart = shifted - (shifted % period);
    return tickStart - offsetSec;
  }

  // ================================================================
  // Page: Dashboard
  // ================================================================
  function renderDashboard(root) {
    const all = allPatchesFromCatalog();
    const live = all.filter(p => !p._synthetic);
    const counts = countStates(live);
    const total = live.length;
    const upnext = live
      .filter(p => p.readyAt && p.state !== 'DEAD')
      .sort((a, b) => Date.parse(a.readyAt) - Date.parse(b.readyAt))
      .slice(0, 6);
    const favs = live.filter(p => STATE.favorites.has(`${p.regionId}.${p.varbitId}`));

    const connHint = (!STATE.data)
      ? `<div class="panel span-12 onboard-panel">
          <h2 class="panel-head">Welcome &mdash; no plugin data yet</h2>
          <p class="subtle">Farmwatch mirrors your OSRS farming patches from the stock Time Tracking plugin so you can watch countdowns in your browser.</p>
          <div class="onboard-actions">
            <button class="btn" id="dash-load-sample">Try it with sample data</button>
            <a class="btn btn-ghost" href="#/connection">Connect the plugin &rsaquo;</a>
            <a class="btn btn-ghost" href="#/about">Setup guide &rsaquo;</a>
          </div>
        </div>`
      : '';

    root.innerHTML = `
      <div class="page-head">
        <div>
          <h1>Dashboard</h1>
          <p>Live overview of every farming patch you've touched.</p>
        </div>
        <div class="page-head-tools">
          <a class="btn btn-ghost btn-sm" href="#/patches">All patches &rsaquo;</a>
        </div>
      </div>

      <div class="dash-grid">
        ${connHint}

        <div class="kpi kpi-ready span-3">
          <div class="kpi-label">Ready now</div>
          <div class="kpi-value">${counts.ready}</div>
          <div class="kpi-sub">of ${total} tracked</div>
        </div>
        <div class="kpi kpi-soon span-3">
          <div class="kpi-label">Due soon</div>
          <div class="kpi-value">${counts.soon}</div>
          <div class="kpi-sub">next ${Math.round(NEAR_READY_MS/60000)} min</div>
        </div>
        <div class="kpi kpi-growing span-3">
          <div class="kpi-label">Growing</div>
          <div class="kpi-value">${counts.growing}</div>
          <div class="kpi-sub">healthy cycles</div>
        </div>
        <div class="kpi kpi-dead span-3">
          <div class="kpi-label">Attention</div>
          <div class="kpi-value">${counts.diseased + counts.dead}</div>
          <div class="kpi-sub">${counts.diseased} diseased, ${counts.dead} dead</div>
        </div>

        <div class="panel span-6">
          <h2 class="panel-head">Up next <span class="right">${upnext.length} entries</span></h2>
          ${upnext.length === 0 ? emptyBlock('Nothing waiting', 'Visit a farming patch in-game and this list will fill in.') : `
            <ul class="list-upnext">
              ${upnext.map(p => {
                const t = Date.parse(p.readyAt);
                const icon = p.wikiFile ? `<img class="ico" referrerpolicy="no-referrer" alt="" src="https://oldschool.runescape.wiki/images/${esc(p.wikiFile)}" onerror="this.remove()">` : '<span class="ico"></span>';
                return `<li>
                  ${icon}
                  <span>
                    <div class="name">${esc(prettyProduce(p.produce))}</div>
                    <div class="where">${esc(prettyRegion(p.region))}</div>
                  </span>
                  <span class="when" data-ready-at="${esc(p.readyAt)}">${esc(formatRemaining(t - Date.now()))}</span>
                </li>`;
              }).join('')}
            </ul>
          `}
        </div>

        <div class="panel span-6">
          <h2 class="panel-head">Favorites <span class="right">${favs.length} starred</span></h2>
          ${favs.length === 0 ? emptyBlock('No favorites yet', 'Hit the ★ on a patch card to pin it here and (optionally) get a browser notification when it ripens.') : `
            <ul class="list-upnext">
              ${favs.sort((a,b) => patchSortKey(a) - patchSortKey(b)).slice(0, 8).map(p => {
                const t = p.readyAt ? Date.parse(p.readyAt) : null;
                const icon = p.wikiFile ? `<img class="ico" referrerpolicy="no-referrer" alt="" src="https://oldschool.runescape.wiki/images/${esc(p.wikiFile)}" onerror="this.remove()">` : '<span class="ico"></span>';
                const when = t ? `<span class="when" data-ready-at="${esc(p.readyAt)}">${esc(formatRemaining(t - Date.now()))}</span>` : `<span class="when subtle">${esc(prettyState(p.state))}</span>`;
                return `<li>${icon}<span><div class="name">${esc(prettyProduce(p.produce))}</div><div class="where">${esc(prettyRegion(p.region))}</div></span>${when}</li>`;
              }).join('')}
            </ul>
          `}
        </div>

        <div class="panel span-6">
          <h2 class="panel-head">Suggested run</h2>
          ${renderRunSuggestion(live)}
        </div>

        <div class="panel span-6">
          <h2 class="panel-head">Breakdown by patch type</h2>
          ${renderQuickBreakdown(live)}
        </div>
      </div>
    `;
    const sampleBtn = root.querySelector('#dash-load-sample');
    if (sampleBtn) sampleBtn.addEventListener('click', loadSampleData);
  }

  function renderRunSuggestion(patches) {
    const now = Date.now();
    const soon = patches.filter(p => p.readyAt && Date.parse(p.readyAt) - now < NEAR_READY_MS && p.state !== 'DEAD');
    if (soon.length < 2) return `<p class="subtle">Fewer than two patches ready soon. Check back in a bit, or <a href="#/planner">open the planner</a> for the full teleport list.</p>`;
    const ROUTE = ['CATHERBY','ARDOUGNE','FALADOR','TROLL_STRONGHOLD','HARMONY','WEISS','HOSIDIUS','FARMING_GUILD','VARLAMORE'];
    soon.sort((a,b) => (ROUTE.indexOf(a.region) + 999) - (ROUTE.indexOf(b.region) + 999));
    return `<ol class="plan">${soon.slice(0, 8).map(p => `
      <li>${p.wikiFile ? `<img class="plan-ico" referrerpolicy="no-referrer" src="https://oldschool.runescape.wiki/images/${esc(p.wikiFile)}" alt="" onerror="this.remove()">` : ''}
        <strong>${esc(prettyRegion(p.region))}</strong> &mdash; ${esc(prettyProduce(p.produce))}
      </li>
    `).join('')}</ol>`;
  }

  function renderQuickBreakdown(patches) {
    const byImp = {};
    for (const p of patches) {
      const k = p.implementation || 'OTHER';
      (byImp[k] = byImp[k] || []).push(p);
    }
    const rows = Object.keys(byImp).sort((a,b) => (IMP_ORDER.indexOf(a)+999) - (IMP_ORDER.indexOf(b)+999)).map(imp => {
      const list = byImp[imp];
      const ready = list.filter(p => p.state === 'HARVESTABLE').length;
      const total = list.length;
      const pct = total ? Math.round((ready / total) * 100) : 0;
      return `<div class="bar-row">
        <span class="bar-label">${esc(prettyImp(imp))}</span>
        <span class="bar-track"><span class="bar-fill ready" style="width:${pct}%"></span></span>
        <span class="bar-val">${ready}/${total}</span>
      </div>`;
    });
    if (!rows.length) return `<p class="subtle">Nothing tracked yet.</p>`;
    return `<div class="bar-list">${rows.join('')}</div>`;
  }

  // ================================================================
  // Page: Patches
  // ================================================================
  function renderPatches(root) {
    if (!STATE.decoder) return mountDecoderGate(root);
    const all = allPatchesFromCatalog();
    const live = all.filter(p => !p._synthetic);

    const impOptions = ['ALL', ...uniqueSorted(all.map(p => p.implementation || 'OTHER'), (a,b) => (IMP_ORDER.indexOf(a)+999)-(IMP_ORDER.indexOf(b)+999))];
    const regionOptions = ['ALL', ...uniqueSorted(all.map(p => p.region || '').filter(Boolean))];

    const f = STATE.patchFilt;
    const q = STATE.search.toLowerCase();

    let shown = all.filter(p => {
      if (!f.showHidden && STATE.hidden.has(p.implementation)) return false;
      if (f.onlyFav && !STATE.favorites.has(`${p.regionId}.${p.varbitId}`)) return false;
      if (f.state !== 'ALL') {
        if (f.state === 'READY'   && p.state !== 'HARVESTABLE') return false;
        if (f.state === 'SOON'    && !(p.state === 'GROWING' && p.readyAt && Date.parse(p.readyAt) - Date.now() < NEAR_READY_MS)) return false;
        if (f.state === 'GROWING' && p.state !== 'GROWING') return false;
        if (f.state === 'DEAD'    && p.state !== 'DEAD') return false;
        if (f.state === 'DISEASED'&& p.state !== 'DISEASED') return false;
        if (f.state === 'UNTRACKED'&& p.state !== 'UNTRACKED') return false;
      }
      if (f.imp !== 'ALL' && p.implementation !== f.imp) return false;
      if (f.region !== 'ALL' && p.region !== f.region) return false;
      if (q) {
        const hay = [p.produce, p.region, p.implementation, p.state].filter(Boolean).join(' ').toLowerCase();
        if (!hay.includes(q)) return false;
      }
      return true;
    });

    const total = shown.length;
    const trackedShown = shown.filter(p => !p._synthetic).length;

    const groups = groupBy(shown, p => (
      f.group === 'imp'    ? (p.implementation || 'OTHER') :
      f.group === 'region' ? (p.region || 'Unknown') :
      'ALL'
    ));
    const groupKeys = Object.keys(groups);
    if (f.group === 'imp') {
      groupKeys.sort((a,b) => (IMP_ORDER.indexOf(a)+999) - (IMP_ORDER.indexOf(b)+999));
    } else {
      groupKeys.sort();
    }
    for (const k of groupKeys) groups[k].sort((a,b) => patchSortKey(a) - patchSortKey(b));

    const stateChip = s => `<option value="${s}"${f.state === s ? ' selected':''}>${prettyFilterLabel('state', s)}</option>`;

    root.innerHTML = `
      <div class="page-head">
        <div>
          <h1>Patches</h1>
          <p>${trackedShown} tracked · ${total} shown · ${live.length} total touched across all accounts</p>
        </div>
      </div>

      <div class="filter-bar" id="patch-filter">
        <label>State
          <select data-f="state">
            ${['ALL','READY','SOON','GROWING','DISEASED','DEAD','UNTRACKED'].map(stateChip).join('')}
          </select>
        </label>
        <label>Type
          <select data-f="imp">
            ${impOptions.map(k => `<option value="${esc(k)}"${f.imp === k ? ' selected':''}>${k === 'ALL' ? 'All types' : esc(prettyImp(k))}</option>`).join('')}
          </select>
        </label>
        <label>Region
          <select data-f="region">
            ${regionOptions.map(k => `<option value="${esc(k)}"${f.region === k ? ' selected':''}>${k === 'ALL' ? 'All regions' : esc(prettyRegion(k))}</option>`).join('')}
          </select>
        </label>
        <label>Group by
          <select data-f="group">
            <option value="imp"    ${f.group === 'imp' ? 'selected':''}>Type</option>
            <option value="region" ${f.group === 'region' ? 'selected':''}>Region</option>
            <option value="none"   ${f.group === 'none' ? 'selected':''}>None</option>
          </select>
        </label>
        <label class="toggle"><input type="checkbox" data-f="onlyFav" ${f.onlyFav ? 'checked':''}> Favorites only</label>
        <label class="toggle"><input type="checkbox" data-f="showHidden" ${f.showHidden ? 'checked':''}> Show hidden types</label>
        <label class="toggle"><input type="checkbox" id="density-toggle" ${STATE.density === 'compact' ? 'checked':''}> Compact</label>
        <span class="filter-count">${total === 0 ? 'nothing matches' : `${total} patches`}</span>
      </div>

      <div id="patches-root"></div>
    `;

    const bar = document.getElementById('patch-filter');
    bar.addEventListener('change', e => {
      const key = e.target.dataset.f;
      if (!key) return;
      STATE.patchFilt[key] = e.target.type === 'checkbox' ? e.target.checked : e.target.value;
      safeSet(LS.patchFilt, JSON.stringify(STATE.patchFilt));
      renderRoute();
    });
    document.getElementById('density-toggle').addEventListener('change', e => {
      STATE.density = e.target.checked ? 'compact' : 'comfortable';
      safeSet(LS.density, STATE.density);
      renderRoute();
    });

    const host = document.getElementById('patches-root');
    if (total === 0) {
      host.innerHTML = emptyBlock('Nothing matches your filters', 'Try widening a selector above, or clear your search.');
      return;
    }

    const compactCls = STATE.density === 'compact' ? ' compact' : '';
    const parts = [];
    for (const k of groupKeys) {
      const items = groups[k];
      const live = items.filter(p => !p._synthetic).length;
      const chip = live < items.length ? `<span class="count">${live}/${items.length}</span>` : `<span class="count">${items.length}</span>`;
      const title = f.group === 'imp' ? prettyImp(k) : f.group === 'region' ? prettyRegion(k) : 'All patches';
      const collapsed = safeGet(LS.collapsed(k)) === '1';
      parts.push(`<section class="group ${collapsed?'collapsed':''}" data-imp="${esc(k)}">
        <h2 class="group-title">${esc(title)} ${chip}</h2>
        <div class="cards${compactCls}">${items.map(cardHtml).join('')}</div>
      </section>`);
    }
    host.innerHTML = parts.join('');

    host.querySelectorAll('.group-title').forEach(h => {
      h.addEventListener('click', () => {
        const g = h.closest('.group');
        g.classList.toggle('collapsed');
        safeSet(LS.collapsed(g.dataset.imp), g.classList.contains('collapsed') ? '1' : '0');
      });
    });
    host.querySelectorAll('.fav').forEach(b => {
      b.addEventListener('click', e => {
        e.stopPropagation();
        const key = b.dataset.key;
        if (STATE.favorites.has(key)) STATE.favorites.delete(key); else STATE.favorites.add(key);
        safeSet(LS.favorites, JSON.stringify([...STATE.favorites]));
        b.classList.toggle('on');
      });
    });
  }

  function cardHtml(p) {
    const now = Date.now();
    const ready = p.readyAt ? Date.parse(p.readyAt) : null;
    const started = p.cycleStartedAt ? Date.parse(p.cycleStartedAt) : null;
    const total = (ready && started) ? Math.max(1, ready - started) : null;
    const elapsed = (ready && started) ? Math.min(total, Math.max(0, now - started)) : 0;
    const frac = total ? elapsed / total : 0;
    const stateCls = stateClass(p, ready);
    const remaining = ready ? ready - now : null;

    const timeLabel = p.state === 'HARVESTABLE' ? 'Ready'
      : p.state === 'DEAD' ? 'Dead'
      : p.state === 'DISEASED' ? 'Diseased'
      : p.state === 'EMPTY' ? 'Empty'
      : p.state === 'UNCHECKED' ? 'Check'
      : p.state === 'UNTRACKED' ? '—'
      : p.state === 'FILLING' ? 'Filling'
      : ready ? formatRemaining(remaining) : '—';

    const circ = ringSvg(frac);
    const compostChip = compostLabel(p.compost);
    const prot = p.protectedPaid ? '<span class="chip chip-prot">✓ paid</span>' : '';
    const accTag = p._acc && STATE.account === 'ALL'
      ? `<span class="acc-tag">${esc(p._acc.displayName || p._acc.hash.slice(0,6))}</span>` : '';
    const icon = p.wikiFile
      ? `<img class="produce-icon" loading="lazy" alt="" referrerpolicy="no-referrer" src="https://oldschool.runescape.wiki/images/${esc(p.wikiFile)}" onerror="this.remove()">`
      : '';
    const produceLabel = p.produce ? prettyProduce(p.produce)
      : (p.state === 'UNTRACKED' ? prettyImp(p.implementation || '?') : 'Empty');
    const extraCls = p._synthetic ? 'card-synthetic' : '';
    const favKey = `${p.regionId}.${p.varbitId}`;
    const favOn = STATE.favorites.has(favKey);

    return `<article class="card ${stateCls} ${extraCls}">
      <button class="fav ${favOn ? 'on' : ''}" data-key="${esc(favKey)}" title="Toggle favorite" aria-pressed="${favOn}">★</button>
      <header>
        <span class="produce">${icon}${esc(produceLabel)}</span>
        <span class="region">${esc(prettyRegion(p.region))}</span>
      </header>
      <div class="ring-wrap">${circ}<span class="time" ${ready?`data-ready-at="${esc(p.readyAt)}"`:''}>${esc(timeLabel)}</span></div>
      <footer>${compostChip} ${prot} ${accTag}</footer>
    </article>`;
  }

  // ================================================================
  // Page: Timeline
  // ================================================================
  function renderTimeline(root) {
    const patches = applySearch(currentAccountPatches());
    const withTimes = patches.filter(p => p.readyAt).sort((a,b) => Date.parse(a.readyAt) - Date.parse(b.readyAt));
    root.innerHTML = `
      <div class="page-head"><div>
        <h1>Timeline</h1>
        <p>Next 24 hours of patch readiness.</p>
      </div></div>
    `;
    if (!withTimes.length) {
      root.appendChild(mkEmpty('No upcoming events', 'Plant something, or visit a patch so its countdown gets predicted.'));
      return;
    }
    const now = Date.now();
    const windowMs = 24 * 60 * 60 * 1000;
    const start = now - (windowMs / 3);
    const nowFrac = (now - start) / windowMs;
    const rows = withTimes.map(p => {
      const t = Date.parse(p.readyAt);
      const f = Math.max(0, Math.min(1, (t - start) / windowMs));
      const ico = p.wikiFile ? `<img class="tl-ico" loading="lazy" referrerpolicy="no-referrer" alt="" src="https://oldschool.runescape.wiki/images/${esc(p.wikiFile)}" onerror="this.remove()">` : '';
      return `<div class="tl-row" title="${esc(prettyProduce(p.produce))} @ ${esc(prettyRegion(p.region))} — ${new Date(t).toLocaleString()}">
        <span class="tl-label">${ico}${esc(prettyProduce(p.produce))} · ${esc(prettyRegion(p.region))}</span>
        <span class="tl-track"><span class="tl-now" style="left:${(nowFrac*100).toFixed(2)}%"></span><span class="tl-dot ${stateClass(p, t)}" style="left:${(f*100).toFixed(2)}%"></span></span>
      </div>`;
    }).join('');
    const host = document.createElement('div');
    host.className = 'panel';
    host.innerHTML = `<h2 class="panel-head">Patches by ready time</h2><div class="timeline">${rows}</div>`;
    root.appendChild(host);
  }

  // ================================================================
  // Page: Planner
  // ================================================================
  function renderPlanner(root) {
    const patches = applySearch(currentAccountPatches());
    const now = Date.now();
    const soon = patches.filter(p => p.readyAt && Date.parse(p.readyAt) - now < NEAR_READY_MS && p.state !== 'DEAD');
    const ROUTE = ['CATHERBY','ARDOUGNE','FALADOR','TROLL_STRONGHOLD','HARMONY','WEISS','HOSIDIUS','FARMING_GUILD','VARLAMORE'];
    soon.sort((a,b) => (ROUTE.indexOf(a.region) + 999) - (ROUTE.indexOf(b.region) + 999));

    root.innerHTML = `
      <div class="page-head"><div>
        <h1>Run planner</h1>
        <p>Teleport-ordered list of patches ready within ${Math.round(NEAR_READY_MS/60000)} minutes.</p>
      </div></div>
    `;
    if (!soon.length) {
      root.appendChild(mkEmpty('Nothing ready to run', 'Come back when a patch is close to ripening (its state will go to "soon").'));
      return;
    }
    const host = document.createElement('div');
    host.className = 'panel';
    host.innerHTML = `<h2 class="panel-head">Suggested route</h2><ol class="plan">
      ${soon.map(p => {
        const ico = p.wikiFile ? `<img class="plan-ico" referrerpolicy="no-referrer" alt="" src="https://oldschool.runescape.wiki/images/${esc(p.wikiFile)}" onerror="this.remove()">` : '';
        return `<li>${ico}<strong>${esc(prettyRegion(p.region))}</strong> — ${esc(prettyProduce(p.produce))} <small class="subtle">${esc(prettyState(p.state))}</small></li>`;
      }).join('')}
    </ol>`;
    root.appendChild(host);
  }

  // ================================================================
  // Page: Statistics
  // ================================================================
  function renderStats(root) {
    const live = currentAccountPatches();
    const total = live.length;
    const counts = countStates(live);
    const byImp = groupBy(live, p => p.implementation || 'OTHER');
    const comp = { NONE: 0, COMPOST: 0, SUPERCOMPOST: 0, ULTRACOMPOST: 0 };
    let protPaid = 0, protEligible = 0;
    for (const p of live) {
      const k = p.compost || 'NONE';
      if (comp[k] != null) comp[k]++;
      if (p.implementation && ['HERB','ALLOTMENT','TREE','FRUIT_TREE','BUSH','HOPS','CACTUS','HARDWOOD_TREE','REDWOOD','CALQUAT','CELASTRUS','CRYSTAL_TREE'].includes(p.implementation)) {
        protEligible++;
        if (p.protectedPaid) protPaid++;
      }
    }

    root.innerHTML = `
      <div class="page-head"><div>
        <h1>Statistics</h1>
        <p>Everything you've recorded, aggregated.</p>
      </div></div>

      <div class="dash-grid">
        <div class="panel span-6">
          <h2 class="panel-head">By state</h2>
          ${renderBarList([
            { label: 'Ready',    val: counts.ready,    total, cls: 'ready' },
            { label: 'Soon',     val: counts.soon,     total, cls: 'soon' },
            { label: 'Growing',  val: counts.growing,  total, cls: 'ok' },
            { label: 'Diseased', val: counts.diseased, total, cls: 'diseased' },
            { label: 'Dead',     val: counts.dead,     total, cls: 'dead' },
          ])}
          <p class="subtle" style="margin:8px 0 0;">${total || 'No'} tracked patches${STATE.account !== 'ALL' ? ' on selected account' : ' across all accounts'}.</p>
        </div>

        <div class="panel span-6">
          <h2 class="panel-head">By patch type</h2>
          ${renderBarList(
            Object.keys(byImp)
              .sort((a,b) => (IMP_ORDER.indexOf(a)+999) - (IMP_ORDER.indexOf(b)+999))
              .map(k => ({ label: prettyImp(k), val: byImp[k].length, total }))
          )}
        </div>

        <div class="panel span-6">
          <h2 class="panel-head">Compost usage</h2>
          ${renderBarList([
            { label: 'None',          val: comp.NONE,          total: total || 1 },
            { label: 'Compost',       val: comp.COMPOST,       total: total || 1 },
            { label: 'Supercompost',  val: comp.SUPERCOMPOST,  total: total || 1, cls: 'ok' },
            { label: 'Ultracompost',  val: comp.ULTRACOMPOST,  total: total || 1, cls: 'ready' },
          ])}
        </div>

        <div class="panel span-6">
          <h2 class="panel-head">Protection payment</h2>
          <dl class="kv">
            <dt>Protection-eligible patches tracked</dt><dd>${protEligible}</dd>
            <dt>Of those, paid for protection</dt><dd>${protPaid} (${protEligible ? Math.round(protPaid*100/protEligible) : 0}%)</dd>
          </dl>
          <p class="subtle" style="margin-top:10px;">Only populated for patches you've actually visited — the stock Time Tracking plugin only records protection payments at the patch.</p>
        </div>
      </div>
    `;
  }

  function renderBarList(rows) {
    const max = rows.reduce((m, r) => Math.max(m, r.val), 0) || 1;
    if (!rows.length) return `<p class="subtle">No data yet.</p>`;
    return `<div class="bar-list">${rows.map(r => `
      <div class="bar-row">
        <span class="bar-label">${esc(r.label)}</span>
        <span class="bar-track"><span class="bar-fill ${r.cls || ''}" style="width:${Math.round((r.val / max) * 100)}%"></span></span>
        <span class="bar-val">${r.val}</span>
      </div>
    `).join('')}</div>`;
  }

  // ================================================================
  // Page: Reference (produce catalog + teleport cheatsheet)
  // ================================================================
  function renderReference(root) {
    if (!STATE.decoder) return mountDecoderGate(root);
    const dec = STATE.decoder;
    const q = STATE.search.toLowerCase();
    const sort = parseJSON(safeGet('farmwatch.ref.sort')) || { col: 'produce', dir: 'asc' };

    // Build a lookup: which implementation(s) each produce belongs to.
    const produceImp = {};
    for (const [imp, ranges] of Object.entries(dec.implementations || {})) {
      for (const r of ranges) {
        if (!r.produce) continue;
        produceImp[r.produce] = produceImp[r.produce] || new Set();
        produceImp[r.produce].add(imp);
      }
    }

    let rows = Object.keys(dec.produce || {}).map(k => {
      const p = dec.produce[k];
      const imps = [...(produceImp[k] || new Set())];
      const totalMin = (p.stages || 0) * (p.tickrate || 0);
      return {
        key: k,
        name: prettyProduce(k),
        imp: imps[0] || 'OTHER',
        stages: p.stages || 0,
        tickrate: p.tickrate || 0,
        total: totalMin,
        wikiFile: p.wikiFile || null,
      };
    });
    if (q) rows = rows.filter(r => (r.name + ' ' + prettyImp(r.imp)).toLowerCase().includes(q));
    const dir = sort.dir === 'desc' ? -1 : 1;
    rows.sort((a,b) => {
      const av = a[sort.col], bv = b[sort.col];
      if (typeof av === 'number' && typeof bv === 'number') return (av - bv) * dir;
      return String(av).localeCompare(String(bv)) * dir;
    });

    root.innerHTML = `
      <div class="page-head"><div>
        <h1>Reference</h1>
        <p>${rows.length} produce entries · ${dec.regions.length} regions · ported from the RuneLite source + OSRS Wiki.</p>
      </div></div>

      <div class="ref-filters subtle">Tip: use the search box in the top bar to narrow this table.</div>

      <table class="ref-table" id="ref-tbl">
        <thead><tr>
          <th data-sort="name"     class="${sortCls(sort, 'name')}">Produce</th>
          <th data-sort="imp"      class="${sortCls(sort, 'imp')}">Type</th>
          <th data-sort="stages"   class="${sortCls(sort, 'stages')}">Stages</th>
          <th data-sort="tickrate" class="${sortCls(sort, 'tickrate')}">Tick (min)</th>
          <th data-sort="total"    class="${sortCls(sort, 'total')}">Total time</th>
        </tr></thead>
        <tbody>
          ${rows.map(r => {
            const ico = r.wikiFile ? `<img class="ref-ico" referrerpolicy="no-referrer" alt="" src="https://oldschool.runescape.wiki/images/${esc(r.wikiFile)}" onerror="this.remove()">` : '';
            const total = r.total ? formatMinutes(r.total) : '—';
            return `<tr>
              <td>${ico} ${esc(r.name)}</td>
              <td>${esc(prettyImp(r.imp))}</td>
              <td>${r.stages || '—'}</td>
              <td>${r.tickrate || '—'}</td>
              <td>${total}</td>
            </tr>`;
          }).join('')}
        </tbody>
      </table>

      <div class="panel" style="margin-top:20px;">
        <h2 class="panel-head">Teleport cheat sheet</h2>
        ${renderTeleportCheatsheet()}
      </div>
    `;

    root.querySelectorAll('#ref-tbl th[data-sort]').forEach(th => {
      th.addEventListener('click', () => {
        const col = th.dataset.sort;
        const next = { col, dir: sort.col === col && sort.dir === 'asc' ? 'desc' : 'asc' };
        safeSet('farmwatch.ref.sort', JSON.stringify(next));
        renderRoute();
      });
    });
  }

  function renderTeleportCheatsheet() {
    const T = [
      ['Herb', [
        ['Catherby',     'Catherby teleport (lunars) / Camelot teleport'],
        ['Ardougne',     'Ardougne cape / Ardougne teleport'],
        ['Falador',      'Explorer\'s ring 2+ cabbage port / Falador teleport'],
        ['Troll',        'Trollheim teleport'],
        ['Harmony',      'Ectophial'],
        ['Weiss',        'Icy basalt'],
        ['Hosidius',     'Xeric\'s talisman'],
        ['Farming Guild','Skills necklace'],
        ['Varlamore',    'Civitas illa Fortis teleport'],
      ]],
      ['Fruit tree', [
        ['Gnome Stronghold', 'Spirit tree'],
        ['Tree Gnome Village','Spirit tree'],
        ['Catherby',     'Catherby teleport / Lunar'],
        ['Brimhaven',    'Brimhaven tp tab'],
        ['Lletya',       'Teleport crystal'],
        ['Farming Guild','Skills necklace'],
      ]],
      ['Tree', [
        ['Lumbridge',    'Lumbridge home teleport'],
        ['Varrock',      'Varrock teleport'],
        ['Falador',      'Falador teleport'],
        ['Taverley',     'Falador teleport then run north'],
        ['Gnome Stronghold', 'Spirit tree'],
        ['Farming Guild','Skills necklace'],
      ]],
    ];
    return T.map(([head, rows]) => `
      <h3 style="margin:12px 0 4px;color:var(--ink);font-size:13px;">${esc(head)}</h3>
      <table class="ref-table" style="margin-bottom:8px;"><tbody>
        ${rows.map(([r, t]) => `<tr><td style="width:160px;">${esc(r)}</td><td class="subtle">${esc(t)}</td></tr>`).join('')}
      </tbody></table>
    `).join('');
  }

  function sortCls(sort, col) {
    return sort.col === col ? (sort.dir === 'desc' ? 'sort-desc' : 'sort-asc') : '';
  }

  // ================================================================
  // Page: Connection
  // ================================================================
  function renderConnection(root) {
    const sampleBtn = `<button class="btn btn-ghost btn-sm" id="load-sample">Load sample data</button>`;
    root.innerHTML = `
      <div class="page-head"><div>
        <h1>Connection</h1>
        <p>Point this dashboard at the Farmwatch plugin, or drop a snapshot file.</p>
      </div></div>

      <div class="panel">
        <h2 class="panel-head">Live endpoint</h2>
        <div class="form-grid">
          <label for="endpoint-input">Plugin URL</label>
          <div class="control">
            <input id="endpoint-input" type="url" value="${esc(STATE.endpoint)}" />
            <button class="btn" id="reconnect-btn">Reconnect</button>
          </div>
          <div class="form-help">The Farmwatch plugin binds <code>127.0.0.1:17060</code> by default. If you changed the port in RuneLite, update it here.</div>
        </div>
      </div>

      <div class="panel" style="margin-top:16px;">
        <h2 class="panel-head">Snapshot fallback</h2>
        <p class="subtle">Not running the plugin right now? Drop a <code>state-&lt;accthash&gt;.json</code> file to inspect the last state the plugin saved. ${sampleBtn}</p>
        <div class="drop-zone" id="drop-zone">
          <p>Drop a <code>state-*.json</code> file here, or pick one:</p>
          <input type="file" id="file-input" accept="application/json,.json" />
        </div>
        <div style="margin-top:10px;">
          <button class="btn btn-ghost btn-sm" id="clear-cache">Clear cached snapshot</button>
        </div>
      </div>

      <div class="panel" style="margin-top:16px;">
        <h2 class="panel-head">Current connection</h2>
        <dl class="kv">
          <dt>Status</dt><dd><span id="conn-stat"></span></dd>
          <dt>Endpoint</dt><dd><code>${esc(STATE.endpoint)}</code></dd>
          <dt>Accounts loaded</dt><dd>${(STATE.data && STATE.data.accounts && STATE.data.accounts.length) || 0}</dd>
          <dt>Last payload at</dt><dd>${STATE.data && STATE.data.generatedAt ? new Date(STATE.data.generatedAt).toLocaleString() : '—'}</dd>
          <dt>Plugin version</dt><dd>${esc((STATE.data && STATE.data.pluginVersion) || '—')}</dd>
        </dl>
      </div>
    `;

    document.getElementById('reconnect-btn').addEventListener('click', () => {
      STATE.endpoint = document.getElementById('endpoint-input').value.trim() || DEFAULT_ENDPOINT;
      safeSet(LS.endpoint, STATE.endpoint);
      STATE.etag = null;
      tryFetch();
      renderRoute();
    });

    document.getElementById('file-input').addEventListener('change', e => {
      const f = e.target.files && e.target.files[0];
      if (f) readSnapshotFile(f);
    });
    const dz = document.getElementById('drop-zone');
    dz.addEventListener('dragover', e => { e.preventDefault(); dz.classList.add('dropping'); });
    dz.addEventListener('dragleave', () => dz.classList.remove('dropping'));
    dz.addEventListener('drop', e => {
      e.preventDefault();
      dz.classList.remove('dropping');
      const f = e.dataTransfer.files && e.dataTransfer.files[0];
      if (f) readSnapshotFile(f);
    });

    document.getElementById('clear-cache').addEventListener('click', () => {
      safeRemove(LS.snapshot);
      STATE.data = null;
      renderRoute();
    });

    document.getElementById('load-sample').addEventListener('click', loadSampleData);

    const el = document.getElementById('conn-stat');
    if (STATE.offline && !STATE.data) el.innerHTML = '<span class="chip chip-bad">Offline</span>';
    else if (STATE.offline) el.innerHTML = '<span class="chip chip-neutral">Snapshot</span>';
    else el.innerHTML = '<span class="chip chip-ok">Live</span>';
  }

  // ================================================================
  // Page: Settings
  // ================================================================
  function renderSettings(root) {
    const allImps = STATE.decoder ? [...new Set(Object.keys(STATE.decoder.implementations || {}))].sort() : [];

    root.innerHTML = `
      <div class="page-head"><div>
        <h1>Settings</h1>
        <p>Personalize this dashboard. Settings are stored locally in your browser.</p>
      </div></div>

      <div class="panel">
        <h2 class="panel-head">Appearance</h2>
        <div class="form-grid">
          <label>Theme</label>
          <div class="control">
            <select id="theme-sel">
              <option value="auto"${(safeGet(LS.theme)||'auto') === 'auto' ? ' selected':''}>Follow system</option>
              <option value="light"${safeGet(LS.theme) === 'light' ? ' selected':''}>Light</option>
              <option value="dark"${safeGet(LS.theme) === 'dark' ? ' selected':''}>Dark</option>
            </select>
          </div>
          <label>Card density</label>
          <div class="control">
            <select id="density-sel">
              <option value="comfortable"${STATE.density === 'comfortable' ? ' selected':''}>Comfortable</option>
              <option value="compact"${STATE.density === 'compact' ? ' selected':''}>Compact</option>
            </select>
          </div>
        </div>
      </div>

      <div class="panel" style="margin-top:16px;">
        <h2 class="panel-head">Patch types to show</h2>
        <p class="subtle">Uncheck any patch type you don't farm. Hidden types vanish from the Patches page unless you toggle "Show hidden types" there.</p>
        <div class="checkbox-grid" id="hidden-grid">
          ${allImps.map(k => `
            <label><input type="checkbox" data-imp="${esc(k)}" ${STATE.hidden.has(k) ? '' : 'checked'}> ${esc(prettyImp(k))}</label>
          `).join('')}
        </div>
      </div>

      <div class="panel" style="margin-top:16px;">
        <h2 class="panel-head">Notifications</h2>
        <div class="form-grid">
          <label>Browser notifications</label>
          <div class="control">
            <label class="toggle"><input type="checkbox" id="notify-on" ${STATE.notify.enabled ? 'checked':''}> Enable for favorites</label>
            <span class="subtle" id="notify-state"></span>
          </div>
          <label>Lead time (minutes)</label>
          <div class="control">
            <input type="number" id="notify-lead" min="0" max="60" value="${STATE.notify.leadMin}" style="min-width:80px;" />
            <span class="subtle">Notify this many minutes before a favorite is ready.</span>
          </div>
        </div>
      </div>

      <div class="panel" style="margin-top:16px;">
        <h2 class="panel-head">Favorites &amp; cache</h2>
        <dl class="kv">
          <dt>Favorites saved</dt><dd>${STATE.favorites.size}</dd>
          <dt>Snapshot cached</dt><dd>${safeGet(LS.snapshot) ? 'yes' : 'no'}</dd>
        </dl>
        <div style="margin-top:8px;display:flex;gap:8px;flex-wrap:wrap;">
          <button class="btn btn-ghost btn-sm" id="clear-fav">Clear favorites</button>
          <button class="btn btn-ghost btn-sm" id="export-state">Export current state (JSON)</button>
          <button class="btn btn-ghost btn-sm" id="wipe-all">Wipe all local Farmwatch data</button>
        </div>
      </div>
    `;

    document.getElementById('theme-sel').addEventListener('change', e => {
      const v = e.target.value;
      applyTheme(v);
      if (v === 'auto') safeRemove(LS.theme); else safeSet(LS.theme, v);
    });
    document.getElementById('density-sel').addEventListener('change', e => {
      STATE.density = e.target.value;
      safeSet(LS.density, STATE.density);
    });
    document.getElementById('hidden-grid').addEventListener('change', e => {
      const imp = e.target.dataset.imp;
      if (!imp) return;
      if (e.target.checked) STATE.hidden.delete(imp); else STATE.hidden.add(imp);
      safeSet(LS.hidden, JSON.stringify([...STATE.hidden]));
    });
    document.getElementById('notify-on').addEventListener('change', async e => {
      if (e.target.checked && 'Notification' in window && Notification.permission !== 'granted') {
        const r = await Notification.requestPermission();
        if (r !== 'granted') { e.target.checked = false; }
      }
      STATE.notify.enabled = e.target.checked;
      safeSet(LS.notify, JSON.stringify(STATE.notify));
      refreshNotifyState();
    });
    document.getElementById('notify-lead').addEventListener('change', e => {
      STATE.notify.leadMin = Math.max(0, Math.min(60, parseInt(e.target.value, 10) || 0));
      safeSet(LS.notify, JSON.stringify(STATE.notify));
    });
    document.getElementById('clear-fav').addEventListener('click', () => {
      STATE.favorites.clear(); safeRemove(LS.favorites); renderRoute();
    });
    document.getElementById('export-state').addEventListener('click', () => {
      const blob = new Blob([JSON.stringify(STATE.data || {}, null, 2)], { type: 'application/json' });
      const a = document.createElement('a');
      a.href = URL.createObjectURL(blob);
      a.download = 'farmwatch-state-' + Date.now() + '.json';
      a.click();
      URL.revokeObjectURL(a.href);
    });
    document.getElementById('wipe-all').addEventListener('click', () => {
      if (!confirm('Clear every Farmwatch setting + cached snapshot? This cannot be undone.')) return;
      Object.values(LS).forEach(v => typeof v === 'string' && safeRemove(v));
      location.reload();
    });

    refreshNotifyState();

    function refreshNotifyState() {
      const el = document.getElementById('notify-state');
      if (!('Notification' in window)) { el.textContent = 'This browser does not support notifications.'; return; }
      el.textContent = STATE.notify.enabled && Notification.permission === 'granted' ? 'Permission: granted' :
        Notification.permission === 'denied' ? 'Permission: denied (change in browser settings)' : 'Permission: ask on enable';
    }
  }

  // ================================================================
  // Page: About
  // ================================================================
  function renderAbout(root) {
    root.innerHTML = `
      <div class="page-head"><div>
        <h1>About Farmwatch</h1>
        <p>What this is, how it works, and how to set it up.</p>
      </div></div>

      <div class="prose">
        <p>Farmwatch is a RuneLite plugin + a static web dashboard. The plugin mirrors the stock Time Tracking plugin's farming state and exposes it two ways:</p>
        <ul>
          <li>a loopback HTTP server at <code>127.0.0.1:17060</code> (live), and</li>
          <li>a JSON file at <code>~/.runelite/osrs-farming-tracker/state-&lt;accthash&gt;.json</code> (snapshot).</li>
        </ul>
        <p>This page fetches the live endpoint when the client is running, and falls back to a drag-dropped snapshot otherwise.</p>

        <h2>Quick setup</h2>
        <ol>
          <li>Install the <strong>Farmwatch</strong> plugin from the RuneLite Plugin Hub (or side-load it with <code>./gradlew runClient</code>).</li>
          <li>In RuneLite, open the Farmwatch config and leave <strong>Enable HTTP server</strong> on.</li>
          <li>Open this dashboard: <a href="https://hanamigg.github.io/osrs-farming-tracker/">hanamigg.github.io/osrs-farming-tracker</a>.</li>
          <li>Log into your account. Patches will populate as the plugin primes them from the stock tracker's config.</li>
        </ol>

        <h2>Keyboard shortcuts</h2>
        <ul>
          <li><code>/</code> — focus the search box</li>
          <li><code>1</code>–<code>9</code> — jump to each sidebar page in order</li>
        </ul>

        <h2>Limitations</h2>
        <ul>
          <li><strong>Patch state is only visible when visited.</strong> OSRS doesn't push farming state remotely; the plugin can only report what the stock tracker has seen.</li>
          <li><strong>Compost / protection state</strong> is read from the stock plugin's config, so it reflects what the game actually recorded.</li>
          <li><strong>Clock drift</strong>: <code>readyAt</code> is computed from the client clock. A skewed PC clock skews the countdowns.</li>
          <li><strong>Loopback-only</strong>: the plugin's HTTP server binds to <code>127.0.0.1</code>. For LAN or phone access we'd need an opt-in exposure mode + token (not shipped).</li>
        </ul>

        <h2>Credits</h2>
        <ul>
          <li>Produce icons &copy; OSRS Wiki contributors (CC BY-NC-SA 3.0).</li>
          <li>Varbit map / implementation ranges / tick rates ported from <a href="https://github.com/runelite/runelite" rel="noopener">RuneLite</a> (BSD-2-Clause, &copy; 2018&ndash;2024 Abex and contributors).</li>
          <li>Independent plugin. Not endorsed by Jagex or RuneLite.</li>
        </ul>
      </div>
    `;
  }

  // ================================================================
  // Decoder gate — shown when decoder failed to load
  // ================================================================
  function mountDecoderGate(root) {
    if (STATE.decoderErr) {
      root.innerHTML = `
        <div class="page-head"><div><h1>Patch catalog unavailable</h1></div></div>
        <div class="empty">
          <div class="empty-ico">!</div>
          <h3 class="empty-title">Couldn't load <code>patch-decoder.json</code></h3>
          <p class="empty-body">Error: <code>${esc(STATE.decoderErr)}</code>.<br>If you opened <code>web/index.html</code> directly via <code>file://</code>, browsers block relative <code>fetch()</code>. Open the hosted site at
          <a href="https://hanamigg.github.io/osrs-farming-tracker/">hanamigg.github.io/osrs-farming-tracker</a>, or run <code>python -m http.server</code> inside <code>web/</code> and visit <code>http://127.0.0.1:8000</code>.</p>
        </div>
      `;
    } else {
      root.innerHTML = `<div class="empty"><div class="empty-ico">⏳</div><h3 class="empty-title">Loading patch catalog…</h3></div>`;
    }
  }

  // ================================================================
  // Shared helpers (chips, rings, formatting, grouping)
  // ================================================================
  function applySearch(patches) {
    const q = STATE.search.toLowerCase();
    if (!q) return patches;
    return patches.filter(p => {
      const hay = [p.produce, p.region, p.implementation, p.state].filter(Boolean).join(' ').toLowerCase();
      return hay.includes(q);
    });
  }

  function countStates(patches) {
    const now = Date.now();
    const o = { ready: 0, soon: 0, growing: 0, diseased: 0, dead: 0 };
    for (const p of patches) {
      if (p.state === 'HARVESTABLE') o.ready++;
      else if (p.state === 'DISEASED') o.diseased++;
      else if (p.state === 'DEAD') o.dead++;
      else if (p.state === 'GROWING') {
        if (p.readyAt && Date.parse(p.readyAt) - now < NEAR_READY_MS) o.soon++;
        else o.growing++;
      }
    }
    return o;
  }

  function renderTicking() {
    document.querySelectorAll('[data-ready-at]').forEach(el => {
      const t = Date.parse(el.dataset.readyAt);
      el.textContent = formatRemaining(t - Date.now());
    });
  }

  function maybeNotify() {
    if (!STATE.notify.enabled) return;
    if (!('Notification' in window) || Notification.permission !== 'granted') return;
    const lead = (STATE.notify.leadMin || 0) * 60 * 1000;
    const now = Date.now();
    for (const p of currentAccountPatches()) {
      if (p.state !== 'GROWING' || !p.readyAt) continue;
      const k = `${p.regionId}.${p.varbitId}`;
      if (!STATE.favorites.has(k)) continue;
      const t = Date.parse(p.readyAt);
      if (t - now <= lead && !STATE.notified.has(k)) {
        STATE.notified.add(k);
        try {
          new Notification('Farmwatch — ready soon', {
            body: `${prettyProduce(p.produce)} at ${prettyRegion(p.region)}`,
            tag: 'farmwatch-' + k,
          });
        } catch {}
      } else if (t - now > lead + 60_000) {
        STATE.notified.delete(k);
      }
    }
  }

  function ringSvg(frac) {
    const r = 28, c = 2 * Math.PI * r;
    const dash = c * Math.max(0, Math.min(1, frac));
    return `<svg class="ring" viewBox="0 0 64 64" aria-hidden="true">
      <circle class="ring-bg" cx="32" cy="32" r="${r}"/>
      <circle class="ring-fg" cx="32" cy="32" r="${r}" stroke-dasharray="${dash.toFixed(2)} ${c.toFixed(2)}" transform="rotate(-90 32 32)"/>
    </svg>`;
  }

  function stateClass(p, readyAtMs) {
    if (p.state === 'DEAD')        return 'st-dead';
    if (p.state === 'DISEASED')    return 'st-diseased';
    if (p.state === 'HARVESTABLE') return 'st-ready';
    if (p.state === 'UNTRACKED')   return 'st-untracked';
    if (p.state === 'EMPTY' || p.state === 'UNCHECKED' || p.state === 'FILLING') return 'st-neutral';
    if (readyAtMs && readyAtMs - Date.now() < NEAR_READY_MS) return 'st-soon';
    return 'st-growing';
  }

  function compostLabel(c) {
    if (!c || c === 'NONE') return '<span class="chip chip-neutral">—</span>';
    const letter = c === 'ULTRACOMPOST' ? 'U' : c === 'SUPERCOMPOST' ? 'S' : 'C';
    return `<span class="chip chip-compost chip-${letter.toLowerCase()}">${letter}</span>`;
  }

  function prettyImp(k)     { return k ? k.replace(/_/g,' ').toLowerCase().replace(/\b\w/g, c => c.toUpperCase()) : '?'; }
  function prettyProduce(k) { return k ? prettyImp(k) : '?'; }
  function prettyRegion(k)  { return k ? prettyImp(k) : '?'; }
  function prettyState(s)   { return s ? s.charAt(0) + s.slice(1).toLowerCase() : '—'; }
  function prettyFilterLabel(_, s) {
    const m = { ALL:'All states', READY:'Ready', SOON:'Soon', GROWING:'Growing', DISEASED:'Diseased', DEAD:'Dead', UNTRACKED:'Untracked' };
    return m[s] || s;
  }

  function formatRemaining(ms) {
    if (ms == null || isNaN(ms)) return '—';
    if (ms <= 0) return 'Ready';
    const s = Math.floor(ms / 1000);
    const h = Math.floor(s / 3600);
    const m = Math.floor((s % 3600) / 60);
    const sec = s % 60;
    if (h > 0) return `${h}h ${m}m`;
    if (m > 0) return `${m}m ${sec}s`;
    return `${sec}s`;
  }

  function formatMinutes(min) {
    if (!min) return '—';
    const h = Math.floor(min / 60), m = min % 60;
    if (h && m) return `${h}h ${m}m`;
    if (h) return `${h}h`;
    return `${m}m`;
  }

  function groupBy(arr, keyFn) {
    const out = {};
    for (const x of arr) {
      const k = keyFn(x);
      (out[k] = out[k] || []).push(x);
    }
    return out;
  }

  function uniqueSorted(arr, sortFn) {
    return [...new Set(arr)].sort(sortFn || ((a,b) => String(a).localeCompare(String(b))));
  }

  function emptyBlock(title, body) {
    return `<div class="empty"><div class="empty-ico">&#x1F33F;</div><h3 class="empty-title">${esc(title)}</h3><p class="empty-body">${body || ''}</p></div>`;
  }
  function mkEmpty(title, body) {
    const t = document.getElementById('tpl-empty').content.cloneNode(true);
    t.querySelector('.empty-title').textContent = title;
    t.querySelector('.empty-body').innerHTML = body || '';
    return t;
  }

  function applyTheme(t) {
    const root = document.documentElement;
    if (t === 'auto') root.removeAttribute('data-theme');
    else root.setAttribute('data-theme', t);
  }
  function currentTheme() {
    const dt = document.documentElement.getAttribute('data-theme');
    if (dt) return dt;
    return matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
  }

  function esc(s) {
    return String(s == null ? '' : s)
      .replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;')
      .replace(/"/g,'&quot;').replace(/'/g,'&#39;');
  }

  function safeGet(k) { try { return localStorage.getItem(k); } catch { return null; } }
  function safeSet(k,v) { try { localStorage.setItem(k, v); } catch {} }
  function safeRemove(k) { try { localStorage.removeItem(k); } catch {} }
  function parseJSON(s)  { if (!s) return null; try { return JSON.parse(s); } catch { return null; } }
})();
