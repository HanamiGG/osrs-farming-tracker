# osrs-farming-tracker (Farmwatch)

A **RuneLite plugin** + **companion webpage** that mirrors your Old
School RuneScape farming patches from the stock Time Tracking plugin
into a live, filterable dashboard you can open in any browser.

The plugin reuses the stock Farming Tracker's varbit/enum mapping
(doesn't try to reinvent it) and exposes state two ways:

- **Live:** loopback HTTP server at `http://127.0.0.1:17060`
- **Snapshot:** JSON file at `~/.runelite/osrs-farming-tracker/state-*.json`

The webpage fetches the live endpoint when the client is running, and
falls back to drag-and-drop snapshot upload when it isn't.

> Independent plugin. Not endorsed by Jagex or the RuneLite developers.

## For players

1. **Install the plugin.** In RuneLite open `Configuration → Plugin
   Hub`, search `farmwatch`, click install. _(During review the plugin
   is side-loaded only — see dev section below until the Plugin Hub PR
   lands.)_
2. **Enable it.** Toggle it on in the plugin list. Default config is
   fine; the loopback server starts automatically.
3. **Open the dashboard.** <https://hanamigg.github.io/osrs-farming-tracker/>
4. **Log into OSRS.** As soon as you log in, Farmwatch primes itself
   from everything the stock Time Tracking plugin has already recorded.
   The dashboard will show your patches with countdowns, compost tier,
   and protection status within a few seconds.

**Don't have the plugin running right now?** The dashboard works
offline too. On the Connection page, click "Load sample data" to see
what it looks like, or drop in a saved `state-*.json` snapshot.

**Favorites + notifications.** Hit the ★ on any patch card and the
dashboard will optionally send a browser notification before that patch
ripens. Lead time is configurable in Settings.

## For developers

### Repo layout

```
src/      RuneLite plugin source (Java 11)
build.gradle, settings.gradle, gradlew, icon.png, runelite-plugin.properties
          — standard Plugin Hub layout
web/      Static dashboard (one HTML, one JS, one CSS; no build step)
vibe/     Design docs and the "limitations" hard-truth list
```

### Running the plugin

```bash
./gradlew runClient
```

Log in, visit a farming patch, then from any shell:

```bash
curl -s http://127.0.0.1:17060/state | jq
curl -s http://127.0.0.1:17060/health   # no CORS, no body-parse needed
```

### Running the webpage

**Local:** serve the `web/` folder with any static server (don't open
`index.html` directly via `file://` — browsers block `fetch()` on
relative URLs there).

```bash
cd web && python -m http.server 8000
# then open http://127.0.0.1:8000
```

**Deployed:** `.github/workflows/pages.yml` publishes `web/` to GitHub
Pages on push to `main`. Live at
<https://hanamigg.github.io/osrs-farming-tracker/>.

## Known limitations

See [`vibe/04_limitations.md`](vibe/04_limitations.md) — the short
version:

- Patch state only updates **when visited** (same as the stock tracker).
- Browser localStorage can't read another app's data; the webpage uses
  localStorage only for its own UI preferences.
- The HTTP server is loopback-only by design.
- Mixed-content HTTPS Pages → `http://127.0.0.1` works on Chrome /
  Firefox / Edge (Secure Context exception); Safari is stricter.
- Birdhouses, Hespori, and farming-guild mini-content aren't tracked yet
  (planned for v1.1).

## License

BSD-2-Clause. See `LICENSE`.
