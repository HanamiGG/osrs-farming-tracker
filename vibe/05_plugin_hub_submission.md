# Plugin Hub submission — step by step

Opens Farmwatch to every RuneLite user without them needing `gradlew
runClient`. Submission is a single PR against
[`runelite/plugin-hub`](https://github.com/runelite/plugin-hub); a
RuneLite dev reviews it manually (typical turnaround 1–2 weeks).

## What's already in place

- Repo layout matches Plugin Hub's expected shape (build.gradle,
  settings.gradle, src/, runelite-plugin.properties, icon.png all at
  root).
- `runelite-plugin.properties` has `build=standard` + a review-friendly
  description that explains the HTTP server is off by default.
- The pinned commit is already pushed to `main`:
  `c51dec5504ec1bf7c5b1d4bbd472b416de56019a`.
- Manifest file content ready at `vibe/plugin-hub-manifest.txt`.
- Icon at `icon.png` (48×48, under the 48×72 max).
- LICENSE is BSD-2-Clause (Plugin Hub's recommendation).

## Pre-flight check: likely review concerns

The reviewer will almost certainly ask about:

1. **The loopback HTTP server.** Defense: it's off by default
   (`enableServer` → `false`), binds to `127.0.0.1` only, has a CORS
   allowlist, serves no mutating endpoints, and reads data the stock
   Time Tracking plugin already persists. Users opt in via the config
   panel; file snapshots work without the server.
2. **Reflection into `net.runelite.api.gameval.VarbitID`.** Defense:
   it's a public class in the public API, we only read `FARMING_TRANSMIT_*`
   static int fields, and the mapping is emitted as data for the web
   decoder — no behavior change in-game.
3. **Duplicate of the stock Time Tracking plugin.** Defense: this
   doesn't *replace* anything; it *exports* the same data to disk /
   loopback so a browser can render it. The in-game panel still works
   unchanged.

If the reviewer asks for the server to be removed entirely, the fallback
plan is a file-snapshot-only build (comment out the `LocalServer`
instantiation in `FarmwatchPlugin#startUp`).

## Step 1 — fork plugin-hub

```
gh repo fork runelite/plugin-hub --clone --remote
```

(or do it in the GitHub UI, then `git clone` the fork.)

## Step 2 — add the manifest

In your plugin-hub fork, create a new file at
`plugins/farmwatch` with these exact contents (also in
`vibe/plugin-hub-manifest.txt`):

```
repository=https://github.com/HanamiGG/osrs-farming-tracker.git
commit=c51dec5504ec1bf7c5b1d4bbd472b416de56019a
```

**Important:** no file extension on `plugins/farmwatch`. Plain text
file, two lines.

If you want to pin a later commit (e.g. after another push to
osrs-farming-tracker), update the `commit=` line to the new SHA.

## Step 3 — commit and open the PR

```
cd plugin-hub
git checkout -b add-farmwatch
git add plugins/farmwatch
git commit -m "Add Farmwatch"
git push -u origin add-farmwatch
gh pr create --repo runelite/plugin-hub \
  --title "Add Farmwatch" \
  --body "$(cat <<'EOF'
Farmwatch mirrors the stock Time Tracking farming data to disk and
(opt-in) exposes it via a loopback HTTP server for a companion web
dashboard at https://hanamigg.github.io/osrs-farming-tracker/.

- Reads only, no writes to game state or stock plugin config
- HTTP server disabled by default; CORS allowlist is configurable
- Loopback bind only (127.0.0.1), never 0.0.0.0
- File snapshots work whether or not the server is enabled

Source: https://github.com/HanamiGG/osrs-farming-tracker
Pinned commit: c51dec5504ec1bf7c5b1d4bbd472b416de56019a
EOF
)"
```

## Step 4 — babysit the CI

Two checks run on your PR:

- **`build (pull_request)`** — Plugin Hub's CI clones your repo at the
  pinned commit, replaces `build.gradle`/`settings.gradle` with its
  own standard versions, and runs the build. If it fails, fix the
  underlying issue in `osrs-farming-tracker`, push, then update
  `plugins/farmwatch` with the new SHA and `git push --force-with-lease`
  the PR branch.
- **`RuneLite Plugin Hub Checks`** — automated license/metadata linter.
  Only acts up if it says "Changes are needed."

Expect a human reviewer comment within ~1–2 weeks. Respond to any
asks in the PR; iterate; merge comes from a RuneLite maintainer.

## After merge

- The plugin appears in RuneLite's Plugin Hub list within ~1 hour of
  merge (the hub rebuilds on a schedule).
- Users install with one click from inside their RuneLite client.
- Updates are shipped by bumping the `commit=` line in your merged
  plugin-hub manifest via a follow-up PR.
