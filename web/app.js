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
        onDecoderLoaded();
        renderAll();
      })
      .catch(() => {});
  }

  function rebuildVarbitInv() {
    STATE.varbitInv = {};
    // Prefer the live varbit map from the plugin (always current), fall back
    // to the one shipped in patch-decoder.json so the dashboard works even
    // without a connected plugin.
    const map = (STATE.data && STATE.data.varbitMap) || (STATE.decoder && STATE.decoder.varbitMap);
    if (!map) return;
    for (const [sym, id] of Object.entries(map)) {
      STATE.varbitInv[id] = sym;
    }
  }

  // Rebuild varbit inverse whenever either source lands.
  function onDecoderLoaded() {
    rebuildVarbitInv();
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
    renderSummary(d);
    if (STATE.view === 'board')    renderBoard();
    if (STATE.view === 'timeline') renderTimeline(d);
    if (STATE.view === 'planner')  renderPlanner(d);
  }

  function renderSummary(patches) {
    const now = Date.now();
    let ready = 0, soon = 0, diseased = 0, dead = 0;
    for (const p of patches) {
      if (p.state === 'HARVESTABLE') ready++;
      else if (p.state === 'DISEASED') diseased++;
      else if (p.state === 'DEAD') dead++;
      else if (p.readyAt && Date.parse(p.readyAt) - now < NEAR_READY_MS) soon++;
    }
    let el = document.getElementById('summary-strip');
    if (!el) {
      el = document.createElement('div');
      el.id = 'summary-strip';
      el.className = 'summary-strip';
      const main = document.querySelector('main');
      main.parentNode.insertBefore(el, main);
    }
    const parts = [];
    if (ready)    parts.push(`<span class="chip chip-sum chip-sum-ready">${ready} ready</span>`);
    if (soon)     parts.push(`<span class="chip chip-sum chip-sum-soon">${soon} soon</span>`);
    if (diseased) parts.push(`<span class="chip chip-sum chip-sum-diseased">${diseased} diseased</span>`);
    if (dead)     parts.push(`<span class="chip chip-sum chip-sum-dead">${dead} dead</span>`);
    el.innerHTML = parts.length ? parts.join(' ') : '<span class="chip chip-neutral">Nothing urgent</span>';
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
    const accs = (STATE.data && STATE.data.accounts) || [];
    const slice = STATE.account === 'ALL'
      ? accs.flatMap(a => (a.patches || []).map(p => ({ ...p, _acc: a })))
      : (accs.find(a => a.hash === STATE.account)?.patches || []).map(p => ({ ...p, _acc: accs.find(a => a.hash === STATE.account) }));
    return slice.map(enrichPatch);
  }

  // Build the FULL catalog of every farming patch in the world, overlaying
  // live data where available. Patches with no live data get a synthetic
  // "UNTRACKED" state so the dashboard shows the complete farming map
  // instead of just the handful the player has visited.
  function allPatchesFromCatalog() {
    const dec = STATE.decoder;
    if (!dec || !dec.regions) return [];
    const live = currentAccountPatches();
    const byKey = new Map();
    for (const p of live) {
      byKey.set(`${p.regionId}.${p.varbitId}`, p);
    }
    const out = [];
    for (const region of dec.regions) {
      for (const patchDef of region.patches) {
        const varbitInt = (dec.varbitMap || {})[patchDef.varbit];
        if (!varbitInt) continue;
        const key = `${region.regionId}.${varbitInt}`;
        const hit = byKey.get(key);
        if (hit) {
          out.push(hit);
        } else {
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
    }
    return out;
  }

  // Sort order within each group: ready > soon > growing > diseased > dead > untracked > empty.
  function patchSortKey(p) {
    const order = { HARVESTABLE: 0, GROWING: 2, DISEASED: 4, DEAD: 5, EMPTY: 6, UNCHECKED: 6, FILLING: 2, UNTRACKED: 7 };
    const now = Date.now();
    const base = order[p.state] ?? 3;
    if (p.state === 'GROWING' && p.readyAt) {
      const r = Date.parse(p.readyAt);
      if (r - now < NEAR_READY_MS) return 1;      // soon
      return 2 + Math.min(1, (r - now) / (24 * 60 * 60 * 1000));
    }
    return base;
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
    if (!hit) return p;
    p.produce = p.produce || hit.produce;
    p.state = p.state || hit.state;
    if (hit.produce && dec.produce[hit.produce]) {
      p.wikiFile = p.wikiFile || dec.produce[hit.produce].wikiFile || null;
    }

    if (p.state === 'GROWING' && hit.produce && p.lastUpdated) {
      const prod = dec.produce[hit.produce];
      if (prod && prod.tickrate > 0) {
        // Port of FarmingTracker.predictPatch (RuneLite BSD-2):
        //   tickTime     = getTickTime(tickrate, 0, observed)
        //   doneEstimate = tickTime + (stages - 1 - stage) * tickrate * 60s
        // Stage within the GROWING range is value - lo (ascending encoding).
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

  // Port of FarmingTracker.getTickTime(tickRate, 0, requestedTime, profile).
  // Returns the unix-seconds timestamp of the farming tick that contains
  // `requestedUnix`, respecting the account's observed tick offset when its
  // precision is good enough (>= tickRate, or >= 40 min which covers all
  // tree-type ticks). Offsets are stored as positive but represent negative
  // shifts, per the FarmingTracker comment.
  function alignToFarmTick(requestedUnix, tickRateMin, acc) {
    let offsetSec = 0;
    if (acc && acc.farmTickOffset != null && acc.farmTickOffsetPrecision != null) {
      const prec = acc.farmTickOffsetPrecision;
      if (prec >= tickRateMin || prec >= 40) {
        offsetSec = (acc.farmTickOffset % tickRateMin) * 60;
      }
    }
    const shifted = requestedUnix + offsetSec;
    const period = tickRateMin * 60;
    const tickStart = shifted - (shifted % period);
    return tickStart - offsetSec;
  }

  function renderBoard() {
    const root = document.getElementById('view-board');
    if (!STATE.decoder) {
      root.innerHTML = `<p class="empty">Loading patch catalog…</p>`;
      return;
    }
    const all = allPatchesFromCatalog();
    const live = all.filter(p => !p._synthetic);
    const tracked = live.length;

    const hint = (!STATE.data)
      ? `<div class="onboard">
          <h3>Connect the plugin</h3>
          <p>No data from the plugin yet. In RuneLite's Farmwatch config, make sure <strong>Enable HTTP server</strong> is on, then come back. You can also drop a <code>state-*.json</code> snapshot into the Connection panel below for an offline view.</p>
        </div>`
      : (tracked === 0)
        ? `<div class="onboard subtle">
            <h3>Visit a patch to populate state</h3>
            <p>Connected, but no farming activity recorded yet. The stock Time Tracking plugin only reads a patch when you're near it — go check a herb patch and this board will light up.</p>
          </div>`
        : '';

    // Group by implementation; sort within each group by patchSortKey.
    const groups = {};
    for (const p of all) {
      const key = p.implementation || 'OTHER';
      (groups[key] = groups[key] || []).push(p);
    }
    const order = ['HERB','ALLOTMENT','TREE','FRUIT_TREE','BUSH','HOPS','CACTUS','FLOWER','MUSHROOM','HARDWOOD_TREE','ANIMA','CORAL','SEAWEED','GRAPES','CALQUAT','SPIRIT_TREE','CELASTRUS','CRYSTAL_TREE','REDWOOD','BELLADONNA','HESPORI','COMPOST','BIG_COMPOST','OTHER'];
    const seen = new Set();
    const parts = [];
    for (const imp of order) {
      if (!groups[imp]) continue;
      seen.add(imp);
      groups[imp].sort((a, b) => patchSortKey(a) - patchSortKey(b));
      const liveInGroup = groups[imp].filter(p => !p._synthetic).length;
      const total = groups[imp].length;
      const countChip = liveInGroup < total
        ? `<span class="count">${liveInGroup}/${total}</span>`
        : `<span class="count">${total}</span>`;
      const collapsed = safeGet(LS.collapsed(imp)) === '1';
      parts.push(`<section class="group ${collapsed?'collapsed':''}" data-imp="${imp}">
        <h2 class="group-title">${esc(prettyImp(imp))} ${countChip}</h2>
        <div class="cards">${groups[imp].map(cardHtml).join('')}</div>
      </section>`);
    }
    // Catch any implementation not in the explicit order list.
    for (const imp of Object.keys(groups)) {
      if (seen.has(imp)) continue;
      groups[imp].sort((a, b) => patchSortKey(a) - patchSortKey(b));
      parts.push(`<section class="group" data-imp="${imp}">
        <h2 class="group-title">${esc(prettyImp(imp))} <span class="count">${groups[imp].length}</span></h2>
        <div class="cards">${groups[imp].map(cardHtml).join('')}</div>
      </section>`);
    }

    root.innerHTML = hint + parts.join('');
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
      : p.state === 'EMPTY' ? 'Empty'
      : p.state === 'UNCHECKED' ? 'Check'
      : p.state === 'UNTRACKED' ? '—'
      : p.state === 'FILLING' ? 'Filling'
      : ready ? formatRemaining(remaining) : '—';

    const circ = ringSvg(frac);
    const compostChip = compostLabel(p.compost);
    const prot = p.protectedPaid ? '<span class="chip chip-prot">✓ paid</span>' : '';
    const accTag = p._acc && STATE.account === 'ALL'
      ? `<span class="acc-tag">${esc(p._acc.displayName || p._acc.hash.slice(0,6))}</span>`
      : '';

    const icon = p.wikiFile
      ? `<img class="produce-icon" loading="lazy" alt="" referrerpolicy="no-referrer" src="https://oldschool.runescape.wiki/images/${esc(p.wikiFile)}" onerror="this.remove()">`
      : '';

    // For untracked patches, show the implementation name as the main
    // label (there's no produce to display).
    const produceLabel = p.produce
      ? prettyProduce(p.produce)
      : (p.state === 'UNTRACKED' ? prettyImp(p.implementation || '?') : 'Empty');

    const extraCls = p._synthetic ? 'card-synthetic' : '';

    return `<article class="card ${stateCls} ${extraCls}">
      <header>
        <span class="produce">${icon}${esc(produceLabel)}</span>
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
      const ico = p.wikiFile
        ? `<img class="tl-ico" loading="lazy" alt="" referrerpolicy="no-referrer" src="https://oldschool.runescape.wiki/images/${esc(p.wikiFile)}" onerror="this.remove()">`
        : '';
      return `<div class="tl-row" title="${esc(prettyProduce(p.produce))} @ ${esc(prettyRegion(p.region))} — ${new Date(t).toLocaleString()}">
        <span class="tl-label">${ico}${esc(prettyProduce(p.produce))} · ${esc(prettyRegion(p.region))}</span>
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
    root.innerHTML = `<ol class="plan">${soon.map(p => {
      const ico = p.wikiFile
        ? `<img class="plan-ico" loading="lazy" alt="" referrerpolicy="no-referrer" src="https://oldschool.runescape.wiki/images/${esc(p.wikiFile)}" onerror="this.remove()">`
        : '';
      return `<li>${ico}<strong>${esc(prettyRegion(p.region))}</strong> — ${esc(prettyProduce(p.produce))} <small>${esc(p.state || '')}</small></li>`;
    }).join('')}</ol>`;
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
