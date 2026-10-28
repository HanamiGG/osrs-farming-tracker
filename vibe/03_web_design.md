# Webpage design

## Shape

One-page static app. No build step, no bundler. `index.html` + `app.js`
+ `style.css`. All renders are plain DOM — no React, no Vue. The data
model is small enough that this is cleaner than any framework.

## Views

Tab bar at the top with three views; the active tab persists in
`localStorage` under `farmwatch.view`.

### 1. Status board (default)

Cards grouped by `implementation` (herb, allotment, tree, fruit tree,
bush, hop, cactus, flower, special). Each card shows:

- Produce icon (left)
- Patch region label (top-right)
- Countdown ring — SVG `<circle>` with `stroke-dasharray` driven by
  `(now - cycleStartedAt) / cycleLength`
- Time remaining or "Ready" or "Diseased" or "Dead"
- Compost tier chip: `U` / `S` / `C` / `—`
- Protection check: ✓ when `protected: true`, blank otherwise

Color band per state (full dark+light values live in `style.css`):

- `GROWING` — green
- Near-ready (<10 min) — amber
- `HARVESTABLE` — blue
- `DISEASED` — orange
- `DEAD` — red
- `EMPTY` / `UNCHECKED` — neutral grey

Groups collapse. Collapsed state persists in `localStorage` under
`farmwatch.collapsed.<implementation>`.

### 2. Timeline

Horizontal strip, 24 h wide, "now" is a vertical dotted line one third
in from the left. Each patch is a row; its ready-time is a dot.
Hovering shows a popup with the full `PatchState`.

- Rows sort by `readyAt` ascending; dead/diseased sink to bottom.
- Zoom buttons: 6 h / 24 h / 7 d.

### 3. Run planner

If 2+ patches are `HARVESTABLE` or within 15 min of ready:

- List patches in a sensible teleport-route order (hard-coded, not a
  TSP solver): Catherby → Ardougne → Falador → Troll Stronghold →
  Harmony Island → Weiss → Hosidius → Farming Guild → Varlamore (new
  content order appended as it ships).
- Each row: teleport-method hint (just text, no icons v1) + patch
  summary.

## Account switcher

Dropdown in header. Shows `displayName (hash short-form)` for each
known account. "All accounts" option sums patches across. Selection
persists in `localStorage` under `farmwatch.account`.

## Connection panel

Lives in a collapsed detail at the top:

- Status chip: `Connected` / `Reconnecting` / `Offline`
- Current endpoint URL + port picker (if user wants to change port
  without touching plugin config — picker just changes the fetch URL
  for next poll)
- "Load snapshot file" drop target
- "Clear cached snapshot" button

## Fetch lifecycle

```
on load:
  try fetch(baseUrl + '/state')  with AbortController, 1s timeout
    on 200: render; schedule poll every 15s
    on CORS fail or network fail: show offline panel; wait for file drop
on file drop:
  parse; render; expose "live mode" button to retry fetch
on poll success:
  if ETag unchanged: skip render (cheap no-op)
  else: diff by patch id, animate updated cards
```

## localStorage keys

**Important:** localStorage here is for *this page's own prefs*, not
cross-app data. The user's original ask had the common misconception
that a webpage can read another app's local storage — it can't.

- `farmwatch.view` — current tab
- `farmwatch.account` — selected account hash
- `farmwatch.collapsed.<implementation>` — bool
- `farmwatch.port` — manual port override
- `farmwatch.lastSnapshot` — the last-rendered JSON (so a reload
  without the plugin still shows the previous data, muted)

## Theming

Parchment light + dark. CSS custom properties on `:root` for both
themes; `prefers-color-scheme: dark` plus a manual toggle. See
`style.css` for the five-stop sequential palette used for the
countdown ring gradient.

## Accessibility

- All cards keyboard-reachable (tab order follows DOM order which is
  implementation-grouped).
- Color is paired with text labels — never color-only (red is also
  "Dead", amber is also "<10 min").
- Prefers-reduced-motion disables the ring sweep animation.

## No framework / why

- The data model is tiny (~50 patches max). Hand-rolled DOM is 20x
  less code than a React setup.
- One file to debug when the user asks "why isn't the Catherby patch
  showing."
- Fits the "elegant tool" vibe of the other HeadroomAI projects.
