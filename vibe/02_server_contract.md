# HTTP server contract

## Bind

- Host: `127.0.0.1` only. Never `0.0.0.0`.
- Port: `17060` default, user-configurable via `FarmwatchConfig#httpPort`.
- On `BindException`, retry at port+1 up to port+9, then give up and
  log — the config still shows the user's configured port but the
  actual port is logged and shown in game chat.

## Endpoints

```
GET /state           -> full snapshot across all known accounts
GET /accounts        -> account roster
GET /state/{hash}    -> single account's state
OPTIONS *            -> CORS preflight
```

All responses: `Content-Type: application/json; charset=utf-8`.

### `GET /state`

```json
{
  "version": 1,
  "generatedAt": "2026-10-28T15:00:00Z",
  "pluginVersion": "0.1.0",
  "accounts": [
    {
      "hash": "9123456789012345",
      "displayName": "Zezima",
      "lastSeen": "2026-10-28T14:58:11Z",
      "patches": [
        {
          "id": "ARDOUGNE_HERB",
          "implementation": "HERB",
          "region": "ARDOUGNE",
          "produce": "RANARR",
          "state": "GROWING",
          "readyAt": "2026-10-28T18:20:00Z",
          "cycleStartedAt": "2026-10-28T14:00:00Z",
          "compost": "ULTRA",
          "protected": true,
          "diseased": false,
          "dead": false
        }
      ]
    }
  ]
}
```

Enum values:
- `implementation`: `ALLOTMENT`, `HERB`, `TREE`, `FRUIT_TREE`, `BUSH`,
  `HOP`, `CACTUS`, `FLOWER`, `MUSHROOM`, `BELLADONNA`, `SEAWEED`,
  `GRAPE`, `BIG_COMPOST`, `COMPOST`, `BIRDHOUSE`, `HESPORI`, `ANIMA`,
  `CELASTRUS`, `CRYSTAL_TREE`, `REDWOOD` — matches
  `PatchImplementation`.
- `state`: `UNCHECKED`, `GROWING`, `HARVESTABLE`, `DISEASED`, `DEAD`,
  `EMPTY`.
- `compost`: `NONE`, `COMPOST`, `SUPERCOMPOST`, `ULTRACOMPOST`.

### `GET /accounts`

```json
[
  { "hash": "9123...", "displayName": "Zezima",   "lastSeen": "..." },
  { "hash": "4876...", "displayName": "ZezimasAlt","lastSeen": "..." }
]
```

### `GET /state/{hash}`

Same shape as a single `accounts[]` entry from `/state`. 404 if
unknown.

## CORS

Allowlist from `FarmwatchConfig#allowedOrigins` (comma-separated).
Defaults:
- `https://hanamigg.github.io` (the deployed Pages site).
- `http://localhost:*` and `http://127.0.0.1:*` for dev.
- `null` origin (for `file://` opens of the local HTML).

Preflight (`OPTIONS`):
- `Access-Control-Allow-Origin: <echoed if matched, else 403>`
- `Access-Control-Allow-Methods: GET, OPTIONS`
- `Access-Control-Max-Age: 600`

Mismatched Origin → `403` with no CORS header. This prevents a random
tab from scraping localhost even if the user has the plugin running.

## No auth

The only threat model is same-machine browser tabs, which the CORS
allowlist handles. If a future version adds LAN exposure, add a
`Authorization: Bearer <token>` requirement with a token generated at
startup and shown in game chat.

## Caching

The server stores a single `AtomicReference<byte[]>` holding the
serialized JSON. It is replaced wholesale on each tick when state
changes. Readers do a single atomic reference load, zero copying.

`ETag`: SHA-256 of the payload bytes, so pollers that pass
`If-None-Match` get `304`.
