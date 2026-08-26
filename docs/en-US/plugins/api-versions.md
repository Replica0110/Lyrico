# Plugin Protocol Versions, Migration, and Troubleshooting

This page answers three developer questions: which versions a plugin should declare, exactly which functions changed in each version, and where to start when a plugin fails in the Devkit or on Android.

## Distinguish the two version fields first

The two version fields in `manifest.json` define separate compatibility boundaries:

| Manifest field | Current host version | What it controls |
|---|---:|---|
| `apiVersion` | 4 | Return contracts of `searchSongs`, `getLyrics`, and `searchCovers` |
| `minHostApiVersion` | 3 | The set of callable `Platform.*` host functions |

For example, a plugin that returns API4 lyrics candidates but only uses `Platform.http` should declare:

```json
{
  "apiVersion": 4,
  "minHostApiVersion": 1
}
```

The current host accepts `apiVersion` 1 through 4 and `minHostApiVersion` 1 through 3. Higher versions are rejected during installation so an unknown protocol or missing host function does not fail later at runtime.

Inspect the effective runtime when diagnosing compatibility:

```javascript
const runtime = Platform.runtime.getInfo();

Platform.log.debug("plugin", JSON.stringify({
  pluginApiVersion: runtime.pluginApiVersion,
  hostApiVersion: runtime.hostApiVersion,
  engine: runtime.engine,
  supportedHostApis: runtime.supportedHostApis
}));

if (!runtime.supportedHostApis.includes("cache.get")) {
  throw new Error("This plugin requires Platform.cache (Host API 3)");
}
```

`pluginApiVersion` is the highest plugin protocol understood by the host, not the current plugin's own `manifest.apiVersion`.

## API1: baseline plugin callbacks

API1 defines three independent global callbacks. A plugin only needs to implement the callbacks listed in `capabilities`; a legacy plugin without `capabilities` is treated as `searchSongs`-only.

| Callback | Request | API1 return value | Purpose |
|---|---|---|---|
| `searchSongs(request)` | `{ keyword, page, pageSize, separator, config }` | `SongSearchResult[]` | Search songs and metadata |
| `getLyrics(request)` | `{ song, config }` | One `LyricsResult`, an LRC string, or `null` | Fetch one lyrics result |
| `searchCovers(request)` | `{ keyword, song?, pageSize, config }` | `SongSearchResult[]` | Search cover artwork |

The Host API 1 baseline includes app/runtime information, HTTP, logging, common crypto, Base64, byte operations, inflation, and XML processing. See [Host API](./host-api.md) for complete signatures.

## API2: Base64URL host functions

API2 does not change the request or return contract of any plugin callback. Platform Host API 2 adds exactly these functions:

| Added function | Parameter | Return value |
|---|---|---|
| `Platform.base64.encodeUrlText(text)` | UTF-8 text | Unpadded Base64URL string |
| `Platform.base64.decodeUrlText(base64Url)` | Base64URL string | UTF-8 text |
| `Platform.base64.encodeUrlBytes(bytes)` | Byte array | Unpadded Base64URL string |
| `Platform.base64.decodeUrlBytes(base64Url)` | Base64URL string | Byte array |
| `Platform.base64.toUrl(base64)` | Standard Base64 string | Base64URL string |
| `Platform.base64.fromUrl(base64Url)` | Base64URL string | Padded standard Base64 string |

Only plugins that call these functions need `minHostApiVersion: 2`. A plugin that declares the API2 callback protocol without using the added Platform functions may keep `minHostApiVersion: 1`.

## API3: private per-plugin cache

API3 does not change the request or return contract of any plugin callback. Platform Host API 3 adds exactly four cache functions:

| Added function | Signature | Return value and behavior |
|---|---|---|
| Read | `Platform.cache.get(key)` | Returns a string; returns `""` when missing, expired, or corrupt |
| Write | `Platform.cache.set(key, value, ttlMs?)` | Returns `""`; expires when `ttlMs > 0`, otherwise never expires |
| Remove | `Platform.cache.remove(key)` | Removes one key and returns `""` |
| Clear | `Platform.cache.clear()` | Clears this plugin's cache and returns `""` |

The cache is isolated by plugin ID. It is intended for cookies, anonymous sessions, and temporary tokens. It is not the song-search result cache: search-result caching belongs to the host UI and has no plugin-callable function.

The cache stores strings, so encode and decode objects explicitly:

```javascript
const CACHE_KEY = "session.cookies";

function saveCookies(cookies) {
  Platform.cache.set(CACHE_KEY, JSON.stringify(cookies), 12 * 60 * 60 * 1000);
}

function loadCookies() {
  const raw = Platform.cache.get(CACHE_KEY);
  return raw ? JSON.parse(raw) : [];
}
```

This `JSON.stringify` is correct because it creates a cache string. Do not serialize a plugin callback's final return value.

## API4: independent lyrics and cover result contracts

API4 adds no Platform function; the current Platform Host API remains version 3. It changes the result contracts of `getLyrics` and `searchCovers` so lyrics and cover providers without `searchSongs` can still return candidates a user can identify.

### API3-to-API4 callback comparison

| Callback | Request changed? | API1–3 return value | API4 return value |
|---|---|---|---|
| `searchSongs` | No | `SongSearchResult[]` | Unchanged |
| `getLyrics` | Yes; optional `page` and `pageSize` added | One `LyricsResult`, an LRC string, or `null` | `LyricsResult[]`; every item identifies title, artist, album, and date through `tags.ti/ar/al/date` |
| `searchCovers` | Yes; optional `page` added | `SongSearchResult[]`; legacy fields remain compatible | `SongSearchResult[]`; every item requires title, artist, album, date, and a cover URL; a platform song `id` is optional |

The current host also supplies optional `page` and `pageSize` fields to `getLyrics`, allowing an API4 lyrics source without `searchSongs` to paginate candidates. Legacy plugins may ignore these additive fields; the callback signature remains one `request` object.

Callbacks receive JavaScript objects and must return JavaScript values directly. The Android host performs exactly one JSON serialization:

```javascript
function getLyrics(request) {
  return [{
    type: "rawPlainLrc",
    tags: {
      ti: "Song title",
      ar: "Artist",
      al: "Album",
      date: "2026"
    },
    rawPlainLrc: "[00:00.00]Example"
  }];
}
```

This produces a JSON string wrapped inside another JSON string and fails parsing:

```javascript
// Wrong: do not serialize a callback's final return value
return JSON.stringify([{ id: "1", title: "Song" }]);
```

Lyrics candidates reuse standard lyrics tags instead of duplicating top-level metadata:

| Lyrics tag | Displayed as |
|---|---|
| `tags.ti` | Title |
| `tags.ar` | Artist |
| `tags.al` | Album |
| `tags.date` | Date or year |

API4 cover result example:

```javascript
function searchCovers(request) {
  return [{
    title: "Song title",
    artist: "Artist",
    album: "Album",
    date: "2026",
    picUrl: "https://example.com/cover.jpg"
  }];
}
```

Cover URL aliases `coverUrl`, `cover_url`, and `artworkUrl` are also accepted. See [Plugin functions](./plugin-functions.md) for aliases and complete lyrics payload formats.

## Actual host call flow

`capabilities` controls where a plugin can be used. Metadata sources are shared by single-song Main Search and batch metadata matching; Lyrics and Covers sources are shared by their corresponding single-song and batch operations.

| Scenario | Eligible sources | Call order |
|---|---|---|
| Single-song main search | Enabled Metadata sources with `searchSongs` | Calls `searchSongs`; lyrics UI and `getLyrics` are available only when the plugin that produced the result also declares `getLyrics` |
| Batch metadata matching | Enabled metadata sources with `searchSongs` | Searches, scores, and writes fields other than lyrics and covers |
| Batch lyrics matching | Enabled lyrics sources with `getLyrics` | Selects a song first when `searchSongs` exists; otherwise requests lyrics candidates directly |
| Batch cover matching | Enabled cover sources with `searchCovers` | Calls `searchCovers` and scores results against the local song |
| Independent lyrics search, with `searchSongs` | Enabled lyrics sources with `getLyrics` | Calls that same plugin's `searchSongs`; after selection, passes the selected result unchanged to the same plugin's `getLyrics` |
| Independent lyrics search, without `searchSongs` | Enabled API4 lyrics sources with `getLyrics` | Calls `getLyrics` directly with current local-song metadata |
| Cover search | Enabled sources with `searchCovers` | Calls each source's `searchCovers` directly |

Song IDs, `internal`, lyrics, and covers are never joined across plugins. The lyrics screen's All tab only preserves and displays cached per-source results on one screen; it never passes a song from one source to another source's lyrics callback.

## Migrating from API3 to API4

1. Change `manifest.json` to `apiVersion: 4`.
2. Declare only the callbacks actually implemented in `capabilities`.
3. Keep the `searchSongs` request and result unchanged.
4. Change `getLyrics` from one result to an array; return `[]` when empty and add `tags.ti`, `tags.ar`, `tags.al`, and `tags.date` to every item.
5. Add `title`, `artist`, `album`, `date`, and a cover URL to every `searchCovers` item; `id` may be omitted.
6. Return objects, arrays, strings, or `null` directly. Do not call `JSON.stringify` on the final callback result.
7. Raise `minHostApiVersion` to 2 or 3 only when using Base64URL or cache functions, respectively.

## Diagnosing with the Devkit

Run these commands from the `Lyrico-Plugins` repository root:

```bash
node tools/plugin-devkit/src/cli.js validate ./my-plugin
node tools/plugin-devkit/src/cli.js inspect ./my-plugin
node tools/plugin-devkit/src/cli.js test ./my-plugin searchSongs --keyword "test" --page-size 5
node tools/plugin-devkit/src/cli.js test ./my-plugin getLyrics --song ./song.json --logs
node tools/plugin-devkit/src/cli.js test ./my-plugin searchCovers --keyword "test" --page-size 5
```

Add `--json` to print the complete `request`, host-serialized `raw`, parsed `parsed`, `warnings`, `errors`, and plugin logs. Start with the first `errors` entry, then compare `raw` with `parsed`:

| Symptom or error | Check first | Common cause |
|---|---|---|
| Plugin protocol rejected during installation | `manifest.apiVersion` | Greater than 4, or the Platform version was placed in this field |
| Host API rejected during installation | `manifest.minHostApiVersion` | Greater than 3 |
| `returned JSON.stringify(...) instead of a JavaScript value` | Final `return` in the callback | The plugin serialized once and Android serialized it again |
| `getLyrics returned no usable lyrics candidates` | `raw`, lyrics `type`, and its payload field | Empty array or an unparseable lyrics object |
| `lyrics candidate[n] is missing ...` | `tags.ti/ar/al/date` | Missing API4 judgement metadata |
| `cover result[n] is missing ...` | `title/artist/album/date` and cover URL | Incomplete API4 cover result |
| Plugin absent from single-song search | `searchSongs` capability and Metadata enabled state | Missing `searchSongs` or disabled as a Metadata source |
| Song search succeeds but selecting lyrics fails | `raw/errors/logs` from `getLyrics --song` | Search success does not verify lyrics HTTP, decryption, or result parsing |
| `InternalError: interrupted` | Per-callback duration and loop size | Android QuickJS has a 15-second load/call deadline; avoid fetching and decrypting several candidates inside one `getLyrics` call |
| `Platform.xxx is not a function` | `Platform.runtime.getInfo().supportedHostApis` | Old Devkit/host or an understated `minHostApiVersion` |
| Devkit passes but Android fails | `raw`, Android logs, and QuickJS compatibility | Network/TLS, deadline, or JavaScript-engine differences; do not test only `searchSongs` |

The Devkit uses Node.js to simulate the host and cannot replace Android QuickJS device testing, but it should eliminate manifest, result-contract, double-serialization, and required-field failures first.
