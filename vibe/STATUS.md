# STATUS

**Project:** osrs-farming-tracker (working name "Farmwatch")
**Location:** `C:\HeadroomAI\osrs-farming-tracker`
**Last update:** 2026-10-29 (decoder + compost/protection + prime-on-login)

**Live:**
- Repo: https://github.com/HanamiGG/osrs-farming-tracker (public)
- Dashboard: https://hanamigg.github.io/osrs-farming-tracker/
- JDK 11 installed, Gradle wrapper committed, `./gradlew build` green.

**Working:**
- Loopback HTTP server + file snapshot
- Config mirror of varbit, compost tier, protection payment
- Prime-on-login via `ConfigManager#getRSProfileConfigurationKeys` — patches
  appear in the dashboard immediately when the player logs in, without
  needing to re-visit them
- Web decoder: 43 regions, 85 produce varieties, 23 implementations
  (SEAWEED/GRAPES/CELASTRUS/CRYSTAL_TREE/CALQUAT/COMPOST/BIG_COMPOST
  all included)

**Still rough / deferred:**
- `readyAt` countdown is approximate (ignores `FARM_TICK_OFFSET` precision)
- No patch icons yet
- Plugin Hub submission not prepped

## What this is

A RuneLite plugin + a GitHub Pages webpage. The plugin reads farming
patch state from the running RuneLite client (reusing the stock
Farming Tracker's varbit/enum mapping — not re-deriving it) and exposes
it two ways:

1. **Live:** embedded loopback HTTP server at `127.0.0.1:17060`
2. **Snapshot:** JSON file at
   `~/.runelite/osrs-farming-tracker/state-<accthash>.json`

The webpage fetches the live endpoint when the client is running and
accepts a drag-dropped snapshot otherwise.

## Current state

- Scaffolded: directory tree, Gradle plugin, main Java classes with
  full lifecycle wiring, web dashboard, Pages workflow, docs.
- **Architecture pivoted after reading RuneLite source** (2026-10-28):
  `FarmingPatch` is package-private, so iterating `FarmingPatch.values()`
  from a third-party plugin is impossible. v1 instead *mirrors the stock
  tracker's config* on group `timetracking` — key format
  `<regionID>.<varbitID>`, value `<varbitValue>:<unixTime>`. See
  `vibe/01_plugin_design.md`.
- `PatchReader.java` has a working ConfigChanged mirror. Open gaps:
  - **Prime-on-login** — needs `ConfigManager#getConfigurationKeys`
    verification against current RuneLite; left as a documented TODO.
  - **Compost/protection mirroring** — stock plugin stores these under
    sibling config keys (CompostTracker / PaymentTracker); deferred to
    v1.1.
  - **Patch metadata decode** (region name, produce, cycle time) —
    plugin now ships raw numeric IDs; webpage will need a
    `web/patch-decoder.json` lookup table built from the OSRS Wiki.
    Deferred to v1.1; sample fixture at `web/samples/sample-state.json`
    uses pre-decoded fields so the UI can still be exercised today.
- No local Java 11 / Gradle on this machine (Java 8 only), so the
  plugin has not been compiled here. User will see compile outcome on
  first `./gradlew build`.
- **No git yet.** `git init` + push when ready.

## Next actions

### What you need to do (manual steps)

1. **Install prerequisites on this machine:**
   - JDK 11 (Temurin or Microsoft Build of OpenJDK, 64-bit). Current
     local Java is 1.8, which the RuneLite client won't accept.
   - Gradle isn't needed if we add a Gradle wrapper; see next item.
2. **Generate the Gradle wrapper so you don't need Gradle on PATH:**
   `cd plugin && gradle wrapper --gradle-version 8.8`
   (one-time, requires a scratch Gradle install — or copy the wrapper
   from any other RuneLite plugin-hub template, since they're identical).
3. **Create the GitHub repo** `osrs-farming-tracker`. First push should
   be from `C:\HeadroomAI\osrs-farming-tracker`:
   ```
   git init && git add . && git commit -m "initial scaffold"
   git remote add origin git@github.com:<you>/osrs-farming-tracker.git
   git push -u origin main
   ```
4. **Enable Pages** on the repo (Settings → Pages → source: GitHub
   Actions). The workflow in `.github/workflows/pages.yml` auto-publishes
   `web/` on push.
5. **Update `FarmwatchConfig#allowedOrigins`** default to replace
   `https://example.github.io` with your real Pages URL — otherwise the
   browser will get 403s from the plugin.
6. **Try `./gradlew build`** — if it fails, paste the first error back
   here. Most likely spots: ConfigManager API drift,
   lombok annotation processor version mismatch, or
   runelite-api/jshell artifact names.
7. **`./gradlew runClient`**, log in, visit a farming patch, then
   `curl -s http://127.0.0.1:17060/state | jq .` — confirm a patch
   entry appears.

### What's already testable without the plugin

- Open `web/index.html` in a browser, drag
  `web/samples/sample-state.json` into the drop zone. All three views
  (Status, Timeline, Run planner) should render with the fixture data.

### Deferred to v1.1

- Prime-on-login via `getConfigurationKeys`
- Compost / protection mirroring
- `web/patch-decoder.json` — the lookup table that turns raw
  `<regionID>.<varbitID>` + varbitValue into human-readable patch names,
  produce, implementation, cycle length. Without this, the live view
  shows raw numeric IDs. Build this from the OSRS Wiki or by porting
  (BSD-attributed) excerpts of RuneLite's `FarmingWorld` static init.

## Design docs

- `vibe/01_plugin_design.md` — plugin internals, data flow
- `vibe/02_server_contract.md` — HTTP endpoints, JSON schema, CORS
- `vibe/03_web_design.md` — webpage UX, views, color system
- `vibe/04_limitations.md` — hard-truth list, baked into README

## Longer-term

Deferred to v1.1+:
- Yield / GP history (needs OSRS Wiki price feed)
- LAN-exposure mode (phone dashboard) + per-user token
- Plugin Hub submission PR
