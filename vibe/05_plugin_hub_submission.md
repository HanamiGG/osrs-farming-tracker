# Plugin Hub submission — step by step

Opens Farmwatch to every RuneLite user without them needing `gradlew
runClient`. Submission is a single PR against
[`runelite/plugin-hub`](https://github.com/runelite/plugin-hub); a
RuneLite dev reviews it manually (typical turnaround 1–2 weeks).

## What's already in place

- Repo layout matches Plugin Hub's expected shape (`build.gradle`,
  `settings.gradle`, `src/`, `runelite-plugin.properties`, `icon.png`
  all at the repo root).
- `runelite-plugin.properties` has `build=standard`, author `HanamiGG`,
  and a review-friendly description that states up front the server is
  on by default and explains the defenses.
- `v0.3.0` tag is pushed; manifest file content ready at
  `vibe/plugin-hub-manifest.txt` (keep the `commit=` line in sync with
  the latest pre-submission commit on `main`).
- Icon at `icon.png` (48×48, under the 48×72 max).
- `LICENSE` is BSD-2-Clause (Plugin Hub's recommendation).
- Full test suite (`./gradlew test`) is green: 94 cases across the
  config-mirror parser, potion planner, bank/equipment trackers, herb
  yield math, varbit decoder, growing-patch aggregator, and the
  farm-tick alignment helper.

## Pre-flight check: likely review concerns

The reviewer will almost certainly ask about:

1. **The loopback HTTP server is on by default.** This was an
   intentional reversal from an earlier "off by default" draft — the
   first-launch friction of making users dig through the config panel
   before the dashboard worked hurt the product more than it helped
   the review case. Defense:
   - **Binds to `127.0.0.1` only**, never `0.0.0.0`. Code: see
     `LocalServer#start` — the `InetSocketAddress` is literal
     loopback, there is no code path that binds anywhere else.
   - **CORS allowlist** (`allowedOrigins` config). Default lets in the
     published Pages origin plus `http://localhost:*` /
     `http://127.0.0.1:*` plus `null` (for `file://` drag-drop use).
     Unlisted origins get 403.
   - **Read-only endpoints**: `/state`, `/accounts`, `/state/:hash`,
     `/health`. No mutating verbs, no auth bypass, no filesystem
     escape.
   - **Opt-out is one toggle** in the config panel (`enableServer`).
     File snapshots keep working independently.
   - Only data served is what the stock Time Tracking plugin already
     persists to the player's own `settings.properties` — we're not
     reading anything new.
2. **Reflection into `net.runelite.api.gameval.VarbitID`.** Defense:
   it's a public class in the public API; we only read
   `FARMING_TRANSMIT_*` static int fields, and the mapping is emitted
   as data for the web decoder — no behavior change in-game.
3. **Duplicate of the stock Time Tracking plugin.** Defense: this
   doesn't *replace* anything; it *exports* the same data to disk /
   loopback so a browser can render it with better visuals, keyboard
   shortcuts, and a run planner. The in-game panel still works
   unchanged.

If the reviewer insists the server default must be off, the fallback
is a one-line change to `FarmwatchConfig#enableServer` returning
`false` and a matching tweak to the `description=` in
`runelite-plugin.properties`. The dashboard already shows a clear
"plugin up but origin blocked" diagnostic (added in commit `7b038dd`),
so users following the readme will survive the extra step.

## Step 1 — fork plugin-hub

```powershell
gh repo fork runelite/plugin-hub --clone --remote
# or do it in the GitHub UI, then `git clone` the fork.
```

## Step 2 — add the manifest

In your plugin-hub fork, create a new file at **`plugins/farmwatch`**
(no extension, plain text, two lines) with the current contents of
`vibe/plugin-hub-manifest.txt`:

```
repository=https://github.com/HanamiGG/osrs-farming-tracker.git
commit=<latest-SHA-from-main>
```

The pinned commit must point at a commit that is actually pushed to
`HanamiGG/osrs-farming-tracker` on GitHub — the Plugin Hub CI clones
from the public remote, not your local repo. The current pin is in
`vibe/plugin-hub-manifest.txt`; update that line (and this file's
example) if you push more commits before opening the PR.

## Step 3 — commit and open the PR

```powershell
cd plugin-hub
git checkout -b add-farmwatch
git add plugins/farmwatch
git commit -m "Add Farmwatch"
git push -u origin add-farmwatch
gh pr create --repo runelite/plugin-hub `
  --title "Add Farmwatch" `
  --body "Farmwatch mirrors the stock Time Tracking farming data to disk and over a loopback HTTP server for a companion web dashboard at https://hanamigg.github.io/osrs-farming-tracker/.`n`n- Reads only, no writes to game state or stock plugin config`n- HTTP server loopback-only (127.0.0.1), CORS allowlist, read-only endpoints`n- Server is on by default so the dashboard works first-launch; one toggle disables it`n- File snapshots work whether or not the server is enabled`n`nSource: https://github.com/HanamiGG/osrs-farming-tracker`nRelease: v0.3.0"
```

## Step 4 — babysit the CI

Two checks run on your PR:

- **`build (pull_request)`** — Plugin Hub's CI clones your repo at the
  pinned commit, replaces `build.gradle`/`settings.gradle` with its
  own standard versions, and runs the build. If it fails, fix the
  underlying issue in `osrs-farming-tracker`, push, then update
  `plugins/farmwatch` with the new SHA and `git push --force-with-lease`
  the PR branch.
- **`RuneLite Plugin Hub Checks`** — automated license / metadata
  linter. Only acts up if it says "Changes are needed."

Expect a human reviewer comment within ~1–2 weeks. Respond to any asks
in the PR; iterate; merge comes from a RuneLite maintainer.

## After merge

- The plugin appears in RuneLite's Plugin Hub list within ~1 hour of
  merge (the hub rebuilds on a schedule).
- Users install with one click from inside their RuneLite client.
- Updates are shipped by bumping the `commit=` line in your merged
  plugin-hub manifest via a follow-up PR.
