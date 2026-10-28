# Limitations

Baked into the README so users see them before they file a bug.

## 1. Patch state only updates when visited

OSRS does not push farming state to the client. The server knows your
rananrs are ready; the *client* doesn't until you load the relevant
region. `VarbitChanged` only fires when you're near the patch (same
constraint as the stock Farming Tracker). The plugin extrapolates
countdowns from the last observed state + the crop's known cycle time,
exactly like the stock plugin does.

**Consequence:** if you planted a herb at Ardougne, logged out, then
checked the dashboard, it shows "Ready at ~X" based on the last tick
it saw — if the server-side state has diverged (disease you didn't
know about), the dashboard won't know either.

## 2. Compost / protection state is partially inferred

The stock Farming Tracker records what compost tier you applied *in
its view*. If you composted via a different interface, bank-cheated
the input, or the state was pre-existing from before the plugin ran,
the data may be wrong.

## 3. Account hash can change

`client.getAccountHash()` is stable **within an account's lifetime** but:
- A full account reset / rename / rollback can mint a new hash.
- Display names change on name-change (expected, not a bug).

The plugin uses the hash as the key but stores the most-recently-seen
display name next to it.

## 4. Mini-content coverage

v1 covers whatever the stock `FarmingPatch` enum covers — which is
nearly all farming patches, but **not**:
- Birdhouses (different timing model — may add in v1.1 since the stock
  plugin tracks these in a sibling class)
- Hespori (one-shot, not a cycle)
- Any special event / seasonal patches

## 5. The HTTP server is loopback-only

By design — bind address is `127.0.0.1`, not `0.0.0.0`. If you want
to open the dashboard on your phone, you need a LAN exposure mode,
which v1 does not have (and would require a per-user token on first
launch, because the loopback allowlist assumption breaks).

## 6. Plugin Hub review takes 1–2 weeks

Until published, the plugin side-loads via `./gradlew runClient`. This
is a one-time setup for the user, documented in the README.

## 7. Mixed-content on GitHub Pages

Modern Chrome/Firefox/Edge treat `127.0.0.1` as a Secure Context, so
an HTTPS page fetching `http://127.0.0.1:17060` is **allowed** without
flags. Safari has historically been stricter — if the user is on
Safari and the dashboard refuses to fetch, they can either run the
local `web/index.html` directly or (in the plugin config) point the
server at a self-signed cert (deferred to v1.1).

## 8. Browser localStorage ≠ plugin storage

The original ask included "pull from your local storage" — a common
misunderstanding. A webpage can only read localStorage that **it
itself** wrote under the same origin. It cannot read files on disk
unless the user drag-drops them, and it cannot read another app's
localStorage. This is why the architecture has two legitimate paths
(HTTP server + file upload), not three.

## 9. Clock drift

`readyAt` is computed on the plugin side using the client's wall
clock. If the user's PC clock is off, countdowns will be off by the
same amount. All times are serialized as ISO-8601 UTC; the web UI
displays local time.

## 10. No historical / retention data

v1 only serves current state. If you want a chart of yields over
time, that's v1.1 territory (needs OSRS Wiki price feed + a local
SQLite file).

## 11. Not endorsed by Jagex / RuneLite

Independent plugin. Nothing in this project claims official status.
