(() => {
  'use strict';

  const LS = {
    view: 'farmwatch.view',
    account: 'farmwatch.account',
    port: 'farmwatch.endpoint',
    snapshot: 'farmwatch.lastSnapshot',
    theme: 'farmwatch.theme',
    collapsed: imp => `farmwatch.collapsed.${imp}`,
  };

  const DEFAULT_ENDPOINT = 'http://127.0.0.1:17060';
  const POLL_MS = 15000;
  const NEAR_READY_MS = 10 * 60 * 1000;

  const STATE = {
    endpoint: safeGet(LS.port) || DEFAULT_ENDPOINT,
    data: null,
    etag: null,
    account: safeGet(LS.account) || 'ALL',
    view: safeGet(LS.view) || 'board',
    timer: null,
    offline: true,
    decoder: null,
    varbitInv: {},
  };

  const FARMING_TICK_MINUTES = 5;

  // ------- boot -------

  document.addEventListener('DOMContentLoaded', () => {
    applyTheme(safeGet(LS.theme) || 'auto');
    bindUi();
    loadDecoder();
    hydrateFromCache();
    setView(STATE.view);
    tryFetch();
    STATE.timer = setInterval(tryFetch, POLL_MS);
    setInterval(renderTicking, 1000);
  });

  function loadDecoder() {
    fetch('patch-decoder.json', { cache: 'no-store' })
      .then(r => r.ok ? r.json() : null)
      .then(d => {
        if (!d) return;
        STATE.decoder = d;
        rebuildVarbitInv();
        renderAll();
      })
      .catch(() => {});
  }

  function rebuildVarbitInv() {
    STATE.varbitInv = {};
    const map = STATE.data && STATE.data.varbitMap;
    if (!map) return;
    for (const [sym, id] of Object.entries(map)) {
      STATE.varbitInv[id] = sym;
    }
  }

  function bindUi() {
    document.querySelectorAll('.tab').forEach(t => {
      t.addEventListener('click', () => setView(t.dataset.view));
    });
    document.getElementById('endpoint-input').value = STATE.endpoint;
    document.getElementById('reconnect-btn').addEventListener('click', () => {
      STATE.endpoint = document.getElementById('endpoint-input').value.trim() || DEFAULT_ENDPOINT;
      safeSet(LS.port, STATE.endpoint);
      STATE.etag = null;
      tryFetch();
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
    document.getElementById('clear-cache-btn').addEventListener('click', () => {
      safeRemove(LS.snapshot);
      STATE.data = null;
      renderAll();
    });
    document.getElementById('account-select').addEventListener('change', e => {
      STATE.account = e.target.value;
      safeSet(LS.account, STATE.account);
      renderAll();
    });
    document.getElementById('theme-toggle').addEventListener('click', () => {
      const next = currentTheme() === 'dark' ? 'light' : 'dark';
      applyTheme(next);
      safeSet(LS.theme, next);
    });
  }

  function setView(v) {
    STATE.view = v;
    safeSet(LS.view, v);
    document.querySelectorAll('.tab').forEach(t => {
      t.setAttribute('aria-selected', String(t.dataset.view === v));
    });
    document.querySelectorAll('.view').forEach(s => {
      s.hidden = s.dataset.view !== v;
    });
    renderAll();
  }

  // ------- data -------

  function hydrateFromCache() {
    const raw = safeGet(LS.snapshot);
    if (!raw) return;
    try { STATE.data = JSON.parse(raw); }
    catch { safeRemove(LS.snapshot); }
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
        STATE.data = json;
        STATE.etag = etag;
        rebuildVarbitInv();
        safeSet(LS.snapshot, JSON.stringify(json));
        setConnected(true);
        renderAll();
      })
      .catch(() => {
        clearTimeout(t);
        setConnected(false);
      });
  }

  function readSnapshotFile(f) {
    const r = new FileReader();
    r.onload = () => {
      try {
        STATE.data = JSON.parse(r.result);
        STATE.etag = null;
        rebuildVarbitInv();
        safeSet(LS.snapshot, r.result);
        setConnected(false, 'snapshot');
        renderAll();
      } catch (e) {
        setChip('Bad snapshot file', 'chip-bad');
      }
    };
    r.readAsText(f);
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

  // ------- render -------

  function renderAll() {
    populateAccountSelect();
    const d = currentAccountPatches();
    if (STATE.view === 'board')    renderBoard(d);
    if (STATE.view === 'timeline') renderTimeline(d);
    if (STATE.view === 'planner')  renderPlanner(d);
  }

  function renderTicking() {
    // Cheap second-by-second countdown refresh — only recomputes label text.
    document.querySelectorAll('[data-ready-at]').forEach(el => {
      const t = Date.parse(el.dataset.readyAt);
      el.textContent = formatRemaining(t - Date.now());
    });
  }

  function populateAccountSelect() {
    const sel = document.getElementById('account-select');
    const accs = (STATE.data && STATE.data.accounts) || [];
    const options = ['<option value="ALL">All accounts</option>'];
    for (const a of accs) {
      const n = a.displayName || `(${a.hash.slice(0,6)}…)`;
      options.push(`<option value="${esc(a.hash)}">${esc(n)}</option>`);
    }
    sel.innerHTML = options.join('');
    sel.value = STATE.account;
  }

  function currentAccountPatches() {
    if (!STATE.data) return [];
    const accs = STATE.data.accounts || [];
    const slice = STATE.account === 'ALL'
      ? accs.flatMap(a => (a.patches || []).map(p => ({ ...p, _acc: a })))
      : (accs.find(a => a.hash === STATE.account)?.patches || []).map(p => ({ ...p, _acc: accs.find(a => a.hash === STATE.account) }));
    return slice.map(enrichPatch);
  }

  // Enrich a raw patch (regionId + varbitId + varbitValue) with human-readable
  // fields using the decoder table. Idempotent: if produce/state already set
  // (e.g. from a pre-decoded fixture), passes through unchanged.
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
    if (hit) {
      p.produce = p.produce || hit.produce;
      p.state = p.state || hit.state;
      if (p.state === 'GROWING' && hit.produce && p.lastUpdated) {
        const prod = dec.produce[hit.produce];
        if (prod) {
          // ticks remaining to leave the GROWING range → first HARVESTABLE varbit
          const ticksRemaining = Math.max(0, hit.hi + 1 - p.varbitValue);
          const minutes = ticksRemaining * prod.tickrate * FARMING_TICK_MINUTES;
          p.cycleStartedAt = p.cycleStartedAt || p.lastUpdated;
          p.readyAt = p.readyAt || new Date(Date.parse(p.lastUpdated) + minutes * 60_000).toISOString();
        }
      } else if (p.state === 'HARVESTABLE') {
        p.readyAt = p.readyAt || p.lastUpdated;
      }
    }
    return p;
  }

  function renderBoard(patches) {
    const root = document.getElementById('view-board');
    if (!patches.length) {
      root.innerHTML = `<p class="empty">${STATE.data ? 'No patches tracked yet. Visit a farming patch in-game to seed state.' : 'Waiting for plugin…'}</p>`;
      return;
    }
    const groups = {};
    for (const p of patches) {
      const key = p.implementation || 'OTHER';
      (groups[key] = groups[key] || []).push(p);
    }
    const parts = [];
    const order = ['HERB','ALLOTMENT','TREE','FRUIT_TREE','BUSH','HOP','CACTUS','FLOWER','MUSHROOM','SPECIAL','OTHER'];
    for (const imp of order) {
      if (!groups[imp]) continue;
      const collapsed = safeGet(LS.collapsed(imp)) === '1';
      parts.push(`<section class="group ${collapsed?'collapsed':''}" data-imp="${imp}">
        <h2 class="group-title">${esc(prettyImp(imp))} <span class="count">${groups[imp].length}</span></h2>
        <div class="cards">${groups[imp].map(cardHtml).join('')}</div>
      </section>`);
    }
    root.innerHTML = parts.join('');
    root.querySelectorAll('.group-title').forEach(h => {
      h.addEventListener('click', () => {
        const g = h.closest('.group');
        g.classList.toggle('collapsed');
        safeSet(LS.collapsed(g.dataset.imp), g.classList.contains('collapsed') ? '1' : '0');
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
      : p.state === 'EMPTY' || p.state === 'UNCHECKED' ? '—'
      : ready ? formatRemaining(remaining) : '—';

    const circ = ringSvg(frac);
    const compostChip = compostLabel(p.compost);
    const prot = p.protectedPaid ? '<span class="chip chip-prot">✓ paid</span>' : '';
    const accTag = p._acc && STATE.account === 'ALL'
      ? `<span class="acc-tag">${esc(p._acc.displayName || p._acc.hash.slice(0,6))}</span>`
      : '';

    return `<article class="card ${stateCls}">
      <header>
        <span class="produce">${esc(prettyProduce(p.produce))}</span>
        <span class="region">${esc(prettyRegion(p.region))}</span>
      </header>
      <div class="ring-wrap">${circ}<span class="time" ${ready?`data-ready-at="${esc(p.readyAt)}"`:''}>${esc(timeLabel)}</span></div>
      <footer>
        ${compostChip} ${prot} ${accTag}
      </footer>
    </article>`;
  }

  function renderTimeline(patches) {
    const root = document.getElementById('view-timeline');
    const withTimes = patches.filter(p => p.readyAt).sort((a,b) => Date.parse(a.readyAt) - Date.parse(b.readyAt));
    if (!withTimes.length) {
      root.innerHTML = `<p class="empty">No upcoming patch events.</p>`;
      return;
    }
    const now = Date.now();
    const windowMs = 24 * 60 * 60 * 1000;
    const start = now - (windowMs / 3);
    const end = start + windowMs;
    const nowFrac = (now - start) / windowMs;
    const rows = withTimes.map(p => {
      const t = Date.parse(p.readyAt);
      const f = Math.max(0, Math.min(1, (t - start) / windowMs));
      return `<div class="tl-row" title="${esc(prettyProduce(p.produce))} @ ${esc(prettyRegion(p.region))} — ${new Date(t).toLocaleString()}">
        <span class="tl-label">${esc(prettyProduce(p.produce))} · ${esc(prettyRegion(p.region))}</span>
        <span class="tl-track"><span class="tl-now" style="left:${(nowFrac*100).toFixed(2)}%"></span><span class="tl-dot ${stateClass(p, t)}" style="left:${(f*100).toFixed(2)}%"></span></span>
      </div>`;
    }).join('');
    root.innerHTML = `<div class="timeline">${rows}</div>`;
  }

  function renderPlanner(patches) {
    const now = Date.now();
    const soon = patches.filter(p => p.readyAt && Date.parse(p.readyAt) - now < NEAR_READY_MS && p.state !== 'DEAD');
    const ROUTE = ['CATHERBY','ARDOUGNE','FALADOR','TROLL_STRONGHOLD','HARMONY','WEISS','HOSIDIUS','FARMING_GUILD','VARLAMORE'];
    soon.sort((a,b) => (ROUTE.indexOf(a.region) + 999) - (ROUTE.indexOf(b.region) + 999));
    const root = document.getElementById('view-planner');
    if (!soon.length) {
      root.innerHTML = `<p class="empty">Nothing ready soon.</p>`;
      return;
    }
    root.innerHTML = `<ol class="plan">${soon.map(p => `
      <li><strong>${esc(prettyRegion(p.region))}</strong> — ${esc(prettyProduce(p.produce))} <small>${esc(p.state || '')}</small></li>
    `).join('')}</ol>`;
  }

  // ------- helpers -------

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
    if (p.state === 'EMPTY' || p.state === 'UNCHECKED') return 'st-neutral';
    if (readyAtMs && readyAtMs - Date.now() < NEAR_READY_MS) return 'st-soon';
    return 'st-growing';
  }

  function compostLabel(c) {
    if (!c || c === 'NONE') return '<span class="chip chip-neutral">—</span>';
    const letter = c === 'ULTRACOMPOST' ? 'U' : c === 'SUPERCOMPOST' ? 'S' : 'C';
    return `<span class="chip chip-compost chip-${letter.toLowerCase()}">${letter}</span>`;
  }

  function prettyImp(k) {
    return k.replace(/_/g,' ').toLowerCase().replace(/\b\w/g, c => c.toUpperCase());
  }
  function prettyProduce(k) { return k ? prettyImp(k) : '?'; }
  function prettyRegion(k)  { return k ? prettyImp(k) : '?'; }

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
})();
