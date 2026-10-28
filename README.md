# osrs-farming-tracker (Farmwatch)

A **RuneLite plugin** + **GitHub Pages webpage** for watching your Old
School RuneScape farming patches outside the game.

The plugin reuses the stock Farming Tracker's varbit/enum mapping
(doesn't try to reinvent it) and exposes state two ways:

- **Live:** loopback HTTP server at `http://127.0.0.1:17060`
- **Snapshot:** JSON file at `~/.runelite/osrs-farming-tracker/state-*.json`

The webpage fetches the live endpoint when the client is running, and
falls back to drag-and-drop snapshot upload when it isn't.

> Independent plugin. Not endorsed by Jagex or the RuneLite developers.

## Repo layout

```
plugin/   RuneLite plugin, Gradle, Java 11
web/      Static dashboard (one HTML, one JS, one CSS; no build step)
vibe/     Design docs and the "limitations" hard-truth list
```

## Running the plugin (dev)

Standard RuneLite plugin dev flow:

```bash
cd plugin
./gradlew runClient
```

Log in. Visit a farming patch. Then from any shell:

```bash
curl -s http://127.0.0.1:17060/state | jq
```

## Running the webpage

**Local:** open `web/index.html` directly, or serve the `web/` folder
with any static server.

**Deployed:** `.github/workflows/pages.yml` publishes `web/` to GitHub
Pages on push to `main`. Once Pages is enabled on the repo and the
first push lands, the URL will be
`https://<github-user>.github.io/osrs-farming-tracker/`.

Then edit the plugin's `Allowed origins` config to include that URL.

## Known limitations

See [`vibe/04_limitations.md`](vibe/04_limitations.md) — the short
version:

- Patch state only updates **when visited** (same as the stock tracker).
- Browser localStorage can't read another app's data; the webpage uses
  localStorage only for its own UI preferences.
- The HTTP server is loopback-only by design.
- Mixed-content HTTPS Pages → `http://127.0.0.1` works on Chrome /
  Firefox / Edge (Secure Context exception); Safari is stricter.
- Not yet on the RuneLite Plugin Hub; side-load via Gradle for now.

## License

BSD-2-Clause. See `LICENSE`.
