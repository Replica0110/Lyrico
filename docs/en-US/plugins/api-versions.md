# Plugin Protocol Versions, Migration, and Troubleshooting

This page answers three developer questions: which versions a plugin should declare, exactly what changed in each version, and where to start when a plugin fails in the Devkit or on Android.

## Distinguish the two version fields first

The two version fields in `manifest.json` define separate compatibility boundaries:

| Manifest field | Current host version | What it controls |
|---|---:|---|
| `apiVersion` | 5 | Return contracts of `searchSongs`, `getLyrics`, and `searchCovers` |
| `minHostApiVersion` | 4 | The set of callable `Platform.*` host functions |

For example, a plugin that returns API5 extended lyrics candidates but only uses `Platform.http` should declare:

```json
{
  "apiVersion": 5,
  "minHostApiVersion": 1
}
```

The current host accepts `apiVersion` 1 through 5 and `minHostApiVersion` 1 through 4. Higher versions are rejected during installation so an unknown protocol or missing host function does not fail later at runtime.

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

The `JSON.stringify` call is correct because it creates a cache string. Do not serialize a plugin callback's final return value.

## API4: lyrics and cover candidate contracts

API4 changes getLyrics and searchCovers result contracts so independent sources can return identifiable candidates. TTML payload extensions belong to API5; localization belongs to Host API4.

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

Serializing twice produces a JSON string wrapped inside another JSON string and fails parsing:

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

## API5: extended lyrics payloads and TTML preservation

API5 extends API4 lyrics candidates with word-timed romanization, line attributes, performers, head metadata, timing and language fields, paragraph time windows, body duration, and timed multi-syllable Ruby. Candidate arrays, required identification tags, and searchSongs / searchCovers contracts remain unchanged. These changes originate in commits [0876c815](https://github.com/Replica0110/Lyrico/commit/0876c815) and [1071e09e](https://github.com/Replica0110/Lyrico/commit/1071e09e).

API1–4 plugins remain supported. Extension fields are optional; declaring API5 does not reconstruct missing source data.

### TTML metadata in structured lyrics

A lyrics result with `type: "structured"` may carry TTML-specific information. TTML-only structures are dropped in LRC; word-timed romanization can retain its timing in word-timed LRC; a plugin that returns just `original`, `translated`, and `romanization` behaves as it did with API3.

| Payload location | Content | Written back as |
|---|---|---|
| 4th element of an `original` line | Line-level extension attributes such as `ttm:agent`, `itunes:song-part`, `divBegin`/`divEnd` | `<p ttm:agent>`, `<div itunes:song-part>`, paragraph `<div begin/end>` |
| 4th element of an `original` word | Ruby syllable array `[[startMs, endMs, "reading"], ...]` | `<span tts:ruby="container">` / `<span tts:ruby="text">`, with missing syllable boundaries normalized by the host |
| `original` / `translated` / `romanization` line, 3rd element | Word array or whole-line text | Each track keeps its own line or word timing; TTML and word-timed LRC support word timestamps |
| `agents` | `{ id, type?, name? }[]` | `<ttm:agent>` elements in `<head>` |
| `metadata` | `{ name, namespace?, attributes?, text?, children? }[]` | Metadata nodes in `<head>` |
| `timing` | `"Word"` or `"Line"` | `<tt itunes:timing>` |
| `language` | Original language tag (BCP 47) | `<tt xml:lang>` |
| `bodyDur` | A TTML time expression | `<body dur>` |
| `translatedLang` / `romanizationLang` | Language tags of the translation / romanization track (BCP 47) | `xml:lang` of the corresponding track |

The host regenerates `itunes:key`, so plugins never supply it. Extension attributes accept unprefixed names and the `ttm:` and `itunes:` prefixes only; any other prefix is ignored, and an invalid `bodyDur` is dropped.

Field formats, `metadata` constraints, word-level timing and missing-boundary handling, and when to use `rawTtml` are documented under Structured Lyrics Line Format and TTML Extensions in [Plugin Functions](./plugin-functions.md).

## Host API4: plugin internationalization

Host API4 independently adds two localization functions. API5 lyrics extensions alone do not require Host API4.

| Added function | Signature | Return value and behavior |
|---|---|---|
| Current locale | `Platform.i18n.getLocale()` | Returns the selected language tag, for example `"zh-Hans"`; returns `"und"` when the plugin has no `i18n` |
| Text lookup | `Platform.i18n.t(key, ...args)` | Returns the text of that key in the current language; formats positional placeholders when arguments are passed |

Plugins that use `@` string references in the manifest, or call `Platform.i18n` from a script, must raise `minHostApiVersion` to 4. Resource files and placeholder rules are described in [Plugin Internationalization](./i18n.md).

## Migrating from API3 to API4

1. Change `manifest.json` to `apiVersion: 4`.
2. Declare only the callbacks actually implemented in `capabilities`.
3. Keep the `searchSongs` request and result unchanged.
4. Change `getLyrics` from one result to an array; return `[]` when empty and add `tags.ti`, `tags.ar`, `tags.al`, and `tags.date` to every item.
5. Add `title`, `artist`, `album`, `date`, and a cover URL to every `searchCovers` item; `id` may be omitted.
6. Return objects, arrays, strings, or `null` directly. Do not call `JSON.stringify` on the final callback result.
7. Raise `minHostApiVersion` to 2, 3, or 4 only when using Base64URL, cache functions, or text localization, respectively.

## Migrating from API4 to API5

1. Set manifest `apiVersion: 5`, retaining candidate arrays and `tags.ti/ar/al/date`.
2. Return word arrays for timed romanization and add the new TTML fields only when present in the source; legacy line strings remain valid.
3. Follow [Plugin Functions](./plugin-functions.md) for field shapes and validation. Use `null` for missing Ruby boundaries; invalid `bodyDur` is dropped.
4. Set `minHostApiVersion` according to Platform functions used (1–4), not to 5. Localization requires Host API4.
5. Hosts with a maximum protocol of 4 reject API5 plugins. Update Lyrico and the Devkit before installing or validating them.

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
| Plugin protocol rejected during installation | `manifest.apiVersion` | Greater than 5, or the Platform version was placed in this field |
| Host API rejected during installation | `manifest.minHostApiVersion` | Greater than 4 |
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
