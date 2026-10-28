# Plugin design

## Lifecycle

`FarmwatchPlugin extends net.runelite.client.plugins.Plugin`:

- `startUp()`:
  1. Create `FarmwatchState` (in-memory, keyed by account hash).
  2. Resolve `exportDir` (default `~/.runelite/osrs-farming-tracker/`).
     Create if missing.
  3. Instantiate `StateExporter`.
  4. Instantiate `LocalServer`, call `start()` — binds to
     `127.0.0.1:<configPort>`. On `BindException`, increments the port
     up to +9 and retries, then logs the actual port and surfaces it
     via a chat message.
  5. Prime state from the stock Farming Tracker's config profile for
     the current account (last-known patch states, compost, protection
     flags) via `ConfigManager.getConfiguration(...)`.
- `shutDown()`: stop server; flush snapshot to disk.

## Event subscriptions

- `@Subscribe public void onGameStateChanged(GameStateChanged e)`
  - When state becomes `LOGGED_IN`, remember current account hash;
    load that account's state from stock FT config if we haven't yet.
- `@Subscribe public void onVarbitChanged(VarbitChanged e)`
  - For varbits in the farming set (see TODO list below), update
    the in-memory `PatchState` and mark the account dirty.
- `@Subscribe public void onGameTick(GameTick e)`
  - If any account is dirty: recompute derived fields (`readyAt`,
    `state`), flush snapshot to disk (debounced to 1 tick), and
    update server's cached payload.
- `@Subscribe public void onConfigChanged(ConfigChanged e)`
  - Watch stock FT config group for compost/protection changes made
    outside our visibility; mirror them in.

## Varbit → PatchState mapping

**Reality check (confirmed from RuneLite source 2026-10-28):**

- `FarmingPatch` is **package-private** — unreachable from a third-party
  plugin. The option-1 approach (iterate `FarmingPatch.values()`) is
  dead.
- `PatchImplementation.forVarbitValue(int)` is public but returns a
  package-private `PatchState`, so even that is unusable from outside.
- `Produce` and `CropState` *are* public, but we have no way to feed
  them the right varbit values without the patch table.
- Public injectable services like `FarmingWorld` return collections of
  `FarmingPatch` — same package-private wall.

So v1 goes with the **config mirror**:

The stock `FarmingTracker` persists state on `ConfigChanged` events in
group `timetracking` with:

```
key   = "<regionID>.<varbitID>"       (both numeric)
value = "<varbitValue>:<unixTime>"
```

(Format verified against the current source: see `FarmingTracker.java`
→ comment on the storage format, plus the `strVarbit + ":" + unixNow`
line in `updateData`.)

`PatchReader.handleStockConfigChange` matches that key shape with a
regex and mirrors each entry into our `PatchState` keyed by
`"<regionID>.<varbitID>"`. We store the raw ints; decoding to
produce/cycle time happens on the web side via a lookup table.

### What the webpage needs to decode state

A `web/patch-decoder.json` file mapping:

- `(regionID, varbitID)` → `{patch name, region label, implementation}`
- `(regionID, varbitID, varbitValue)` → `{produce, cropState, stage}`
- `produce` → `{tickRate, stages}` (so the UI can compute readyAt)

This table is derived from RuneLite's `FarmingWorld` static init +
`PatchImplementation.forVarbitValue` tables. Since RuneLite is BSD-2
licensed, we can port the data with attribution. **Deferred to v1.1**;
the sample fixture at `web/samples/sample-state.json` uses pre-decoded
fields instead so the UI is exercise-able today.

### Compost + protection

The stock plugin's `CompostTracker` and `PaymentTracker` siblings
persist to their own config keys under the same `timetracking` group
(not matching `<regionID>.<varbitID>`). v1 ignores them.

**v1.1 TODO:** extend `handleStockConfigChange` to also recognize
`compost.*` and `payment.*` keys and mirror into `PatchState.compost` /
`PatchState.protectedPaid`.

## Compost / protection

The stock FT already tracks these. They are stored in its config
profile under keys like `timetracking.farming.<accountHash>.<patchId>`
with sub-fields. Preferred read path:

```java
String json = configManager.getConfiguration(
    "timetracking", accountKey, patchKey);
```

The value is JSON-ish. Parse once; cache per patch. Re-read on
`ConfigChanged` for the matching key prefix.

## Multi-account

- Key everything by `client.getAccountHash()` (long).
- Store the most recent display name alongside the hash, but never
  make it the key — names change.
- `FarmwatchState` is `ConcurrentHashMap<Long, AccountState>`.

## File export

- Written by `StateExporter` on every state change, debounced to once
  per `GameTick`.
- Path: `<exportDir>/state-<accthash>.json` + a single
  `<exportDir>/state.json` that includes all accounts (for the web UI
  file-upload fallback).
- Written atomically: write to `*.tmp`, then `Files.move(..., ATOMIC)`.

## Thread safety

- All state mutations happen on the client thread (RuneLite's event
  dispatch).
- The HTTP server runs on JDK's default thread pool. It reads a
  pre-serialized `byte[]` snapshot (an `AtomicReference<byte[]>`
  updated on each tick), so no locks on the hot path.
