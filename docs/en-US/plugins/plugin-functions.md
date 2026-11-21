# Plugin Functions

This page describes the function interfaces that plugins expose to Lyrico. Use it when implementing song search, lyrics retrieval, and cover search.

The plugin entry script must define global functions for the host to call. The host parses each request into a JavaScript object and serializes the returned value to JSON. Return an object, array, string, or `null` directly. Do not call `JSON.stringify()`, because that double-serializes the result and fails on the Android host.

## Function Overview

| Function | Trigger | Return type | Capability |
|----------|---------|-------------|------------|
| `searchSongs(request)` | User searches songs | JavaScript array | `searchSongs` |
| `getLyrics(request)` | Search lyrics candidates | JavaScript candidate array in API 4–5; lyrics object, string, or `null` in API 1–3 | `getLyrics` |
| `searchCovers(request)` | Cover images are searched | JavaScript array | `searchCovers` |

Functions are exposed through the QuickJS global scope. You do not need, and cannot use, `export`:

```javascript
function searchSongs(request) { ... }   // Global function
function getLyrics(request) { ... }     // Global function
function searchCovers(request) { ... }  // Global function
```

---

## `searchSongs(request)`

Searches songs. The host passes the user's keyword to this function.

### Request

The host passes this JSON object before serialization:

```json
{
  "keyword": "Example Song",
  "page": 1,
  "pageSize": 20,
  "separator": "/",
  "config": {
    "cover_size": "1200"
  }
}
```

| Field | Type | Default | Description |
|------|------|---------|-------------|
| `keyword` | `string` | - | Search keyword entered by the user |
| `page` | `int` | `1` | Page number, starting from 1 |
| `pageSize` | `int` | `20` | Number of items per page |
| `separator` | `string` | `"/"` | Separator between multiple artists |
| `config` | `object` | `{}` | User config key-value pairs |

### Return Value

Return a JavaScript array or an object containing the result array. Two top-level formats are supported.

**Format 1: return an array directly, recommended**

```javascript
function searchSongs(request) {
  return [
    {
      id: "12345",
      title: "Example Song",
      artist: "Example Artist",
      album: "Example Album",
      duration: 240000,
      date: "2024-01-01",
      trackNumber: "2",
      picUrl: "https://cdn.example.com/cover/abc.jpg",
      fields: {
        title: "Example Song",
        artist: "Example Artist",
        album: "Example Album",
        date: "2024-01-01"
      }
    }
  ];
}
```

**Format 2: wrap the array in an object**

```javascript
function searchSongs(request) {
  return {
    items: [...]    // "results", "songs", or "data" are also accepted
  };
}
```

### Song Object Fields

The parser accepts flexible field names:

| Meaning | Supported JSON keys, any one is enough |
|---------|----------------------------------------|
| Song ID | `id`, `songId`, `trackId` |
| Title | `title`, `name`, `songName` |
| Artist | `artist`, `artists`, `singer` |
| Album | `album`, `albumName` |
| Duration | `duration`, `durationMs`, `duration_ms` |
| Release date | `date`, `releaseDate`, `release_date` |
| Track number | `trackNumber`, `trackerNumber`, `track_number` |
| Cover URL | `picUrl`, `coverUrl`, `cover_url`, `artworkUrl` |
| Standard metadata fields | `fields` |
| Plugin-private context | `internal` |

The `artist` field can also be an array. It is joined with `/` automatically:

```json
{
  "id": "12345",
  "title": "Song Title",
  "artist": ["Artist A", "Artist B"]
}
```

### Standard `fields`

`fields` may only contain host-standard metadata fields. Unknown keys are ignored and produce a debug warning. Platform-specific IDs, hashes, tokens, and other context must be stored in `internal`.

```json
{
  "id": "12345",
  "title": "Song Title",
  "artist": "Artist",
  "fields": {
    "title": "Song Title",
    "artist": "Artist",
    "album": "Album Title",
    "date": "2024-01-01",
    "track_number": "3",
    "cover_url": "https://..."
  },
  "internal": {
    "song_id": "12345",
    "lyrics_id": "abc"
  }
}
```

Current standard fields are: `title`, `artist`, `album`, `album_artist`, `genre`, `date`, `track_number`, `disc_number`, `composer`, `lyricist`, `comment`, `lyrics`, `cover_url`, `language`, `copyright`, `rating`, `replaygain_track_gain`, `replaygain_track_peak`, `replaygain_album_gain`, `replaygain_album_peak`, `replaygain_reference_loudness`.

`internal` is not displayed, written to tags, or used by batch matching field selection. It is passed back unchanged only to the same plugin that produced the result.

---

## `getLyrics(request)`

Independent lyrics search passes the current title, artist, album, and year in `song`.
`getLyrics` may search directly using those ordinary fields: the plugin does not need to
implement `searchSongs`, and the user does not need to provide a platform song ID. Whenever a
plugin also declares `searchSongs`, regardless of its API version, the host first offers that
plugin's own song candidates, then passes the selected `id`, `fields`, and `internal` unchanged to
the same plugin's `getLyrics`. The single-song search screen does not call independent lyrics
sources.

### Request

```json
{
  "song": {
    "id": "12345",
    "title": "Example Song",
    "artist": "Example Artist",
    "album": "Example Album",
    "duration": 240000,
    "sourceId": "com.example.music_source",
    "pluginId": "com.example.music_source",
    "fields": {
      "title": "Example Song"
    },
    "internal": {
      "lyrics_id": "abc"
    }
  },
  "page": 1,
  "pageSize": 20,
  "config": {}
}
```

| Field | Type | Description |
|------|------|-------------|
| `song.id` | `string` | Song ID; independent lyrics search does not guarantee a source-platform ID |
| `song.title` | `string` | Song title |
| `song.artist` | `string` | Artist |
| `song.album` | `string` | Album title |
| `song.duration` | `long` | Duration in milliseconds |
| `song.sourceId` | `string` | Source plugin ID |
| `song.pluginId` | `string` | Plugin ID |
| `song.fields` | `object` | Standard fields returned by search |
| `song.internal` | `object` | Plugin-private context returned by search |
| `page` | `int` | Candidate page number starting at `1`; non-paginated plugins may ignore it |
| `pageSize` | `int` | Requested candidate count for this page |
| `config` | `object` | User config values |

### Return Value

API 4–5 should return an array of lyrics objects. A wrapper using `items`, `results`, or
`candidates` is also accepted. Every object must provide `ti` (title), `ar` (artist), `al`
(album), and `date` (year) in `tags`. The host builds the candidate list from these existing
lyrics tags instead of requiring a duplicate set of top-level song fields:

```javascript
function getLyrics(request) {
  return [{
    type: "rawPlainLrc",
    tags: {
      ti: "Example Song",
      ar: "Example Artist",
      al: "Example Album",
      date: "2024"
    },
    rawPlainLrc: "[00:00.00]First line lyrics"
  }];
}
```

API 1–3 signatures and existing return values are unchanged: they may return one structured
lyrics object, full raw lyrics text, or `null`. The host wraps a legacy result as one candidate
and uses the requested song metadata for display. The formats below are both API 1–3 top-level
responses and valid candidate objects inside the API 4–5 array.

The host first reads `type` to determine payload type. For `type: "structured"`, it parses
`original` / `translated` / `romanization` lists. For raw types, it uses the matching raw field.

**Format 1: structured word-level lyrics, recommended**

```javascript
function getLyrics(request) {
  return {
    type: "structured",
    tags: {
      ti: "Song Title",
      ar: "Artist",
      al: "Album Title"
    },
    original: [
      [0, 2000, [[0, 500, "First"], [500, 1000, "line"], [1000, 2000, "lyrics"]]],
      [2000, 4000, [[2000, 3000, "Second"], [3000, 4000, "line"]]]
    ],
    translated: [
      [0, 2000, "First line lyrics"],
      [2000, 4000, "Second line lyrics"]
    ],
    romanization: null
  };
}
```

The single-object format examples below illustrate candidate payloads. Actual API 4–5 getLyrics callbacks must wrap each payload in an array (`return [result]`); return `[]` when empty.

### Structured line formats

API 5 adds word-timed romanization, line extensions, agents and metadata, timing and language fields, bodyDur, and timed multi-syllable Ruby. Declare `apiVersion: 5` when returning these extensions. Candidate arrays and required tags remain as introduced in API 4. Legacy line strings remain supported. Host API remains independently versioned at 4.

`original`, `translated`, and `romanization` share the same line format and all accept word-level lines:

```
[lineStartMs, lineEndMs, [[wordStartMs, wordEndMs, "text"], ...], extensions?]
```

A word in `original` may use a fourth item for Ruby annotation syllables:

```
[wordStartMs, wordEndMs, "base text", [[syllableStartMs, syllableEndMs, "ruby"], ...]]
```

One base run may map to multiple annotation syllables; a single annotation still uses a one-element array. Syllable times are absolute milliseconds. Use `null` placeholders when either boundary is unavailable. TTML export materializes complete timings: it first joins adjacent syllable boundaries, evenly distributes consecutive fully untimed syllables over the available word range, then falls back to the word boundaries. Words without Ruby keep the original three-item form.

```javascript
[27820, 27950, "詮", [[27820, 27880, "せ"], [27880, 27950, "ん"]]]
```

All three tracks also accept whole-line text. A string keeps line timing; a word array keeps word timing, including an array containing only one word. Track roles do not restrict timing granularity:

```
[lineStartMs, lineEndMs, "text"]
```

TTML and word-timed LRC preserve word timing on all three tracks. Lyrico inserts spaces between adjacent romanization syllables when needed. Plain LRC and plain text flatten each line. LRC has no track role markers: on re-import, two lines sharing a line timestamp follow the original/translation convention, and three follow original/romanization/translation. Word timestamps cannot identify track roles.

LRC output contains lyrics only; it does not append metadata tags such as `[ti:...]`, `[hash:...]`, or `[key:...]`. Existing LRC offsets are applied to timestamps when rewriting. Enhanced LRC keeps vocal-agent prefixes, for example `[00:01.000]v1:<00:01.000>lyrics<00:02.000>`, including the same prefix on linked translation and romanization lines. Existing TTML `vN` IDs are retained; other IDs map consistently to unused `vN` values. Other TTML extensions may be lost.

### TTML extensions

The fields in this section primarily affect TTML output. Enhanced LRC can retain vocal-agent prefixes; other TTML-specific structures without a corresponding LRC representation are not retained.

This section describes the TTML subset available through the structured plugin payload; it is not a replacement for the AMLL TTML DB submission specification. Unmodeled XML nodes may still be unavailable through a structured payload. Return `type: "rawTtml"` when the complete source document must be retained. If the user later applies script conversion, track filtering, or another transformation, Lyrico will parse and rewrite that document, and unmodeled structures may be lost.

An `original` line may include extension attributes as its fourth item:

```javascript
[0, 6000, [[0, 500, "First"], [500, 1000, "line"]], {
  "ttm:agent": "v1",
  "itunes:song-part": "Verse",
  "divBegin": "0",
  "divEnd": "6000"
}]
```

- `ttm:agent` refers to an entry in `agents`.
- `itunes:song-part` creates a `<div itunes:song-part="...">`. The legacy `itunes:songPart` spelling is accepted on input, but output always uses `song-part`.
- `divBegin` and `divEnd` are Lyrico transport fields for a section's time range, in milliseconds. Put them on the section's first line; they become the containing `<div>`'s `begin` and `end` attributes.

Lyrico generates continuous `itunes:key` values (`L1`, `L2`, …) for every output `<p>`, so plugins do not need to provide them. Extension attributes may be unprefixed or use the `ttm:` and `itunes:` prefixes; other prefixes are ignored.

`agents` generates `<ttm:agent>` elements. `id` is required; `type` and `name` are optional:

```javascript
agents: [
  { "id": "v1", "type": "person", "name": "Artist A" },
  { "id": "v1000", "type": "group" }
]
```

`metadata` adds elements to `<head>`. Each node has the form `{ name, namespace?, attributes?, text?, children? }`. `songwriters` is written inside Apple-style `<iTunesMetadata>`; other nodes are written inside regular `<metadata>`. Current constraints are:

- `songwriters` must contain one or more `songwriter` children with text;
- `translations`, `transliterations`, and `ttm:agent` have dedicated fields and should not also appear in `metadata`;
- a custom prefix requires `namespace`, for example `{ "name": "amll:meta", "namespace": "http://www.example.com/ns/amll", ... }`.

The following fields set root/body attributes and auxiliary-track languages:

| Field | TTML location |
|------|---------------|
| `timing` | `<tt itunes:timing>`; commonly `Word` or `Line` |
| `language` | `<tt xml:lang>` |
| `bodyDur` | `<body dur>`; a valid TTML time expression (`body_dur` is also accepted), unchanged by lyric offsets; invalid values are discarded |
| `translatedLang` | `xml:lang` on the inline translation |
| `romanizationLang` | `xml:lang` on `<transliteration>` |

Use BCP 47 language tags such as `zh-Hans` and `ja-Latn`.

The structured model covers only the `dur` attribute on `<body>`. Other body attributes and extra attributes on Ruby annotation spans are not preserved through a structured round trip.

**Format 2: full raw lyrics text**

```javascript
function getLyrics(request) {
  return {
    type: "rawPlainLrc",
    tags: {
      ti: "Song Title",
      ar: "Artist",
      al: "Album Title"
    },
    rawPlainLrc: "[00:00.00]First line lyrics\n[00:05.00]Second line lyrics"
  };
}
```

Supported raw `type` values and content fields:

| `type` | Content field | Description |
|------|---------------|-------------|
| `rawPlainLrc` | `rawPlainLrc` | Plain LRC |
| `rawVerbatimLrc` | `rawVerbatimLrc` | Word-by-word LRC |
| `rawEnhancedLrc` | `rawEnhancedLrc` | Enhanced word-level LRC |
| `rawTtml` | `rawTtml` | TTML |
| `rawMultiPersonEnhancedLrc` | `rawMultiPersonEnhancedLrc` | Multi-person enhanced LRC |

If a plugin does not explicitly provide `type`, the host treats it as `structured`. This is only for compatibility with old plugins; new plugins should declare `type` explicitly.

**Format 3: return `null` for no lyrics**

```javascript
function getLyrics(request) {
  if (noLyricsFound) {
    return null;
    // Or:
    return { notFound: true };
  }
}
```

### LyricsResult Fields

| Field | Type | Description |
|------|------|-------------|
| `type` | `string` | `structured` or a raw type |
| `tags` | `object` | Song metadata tags |
| `original` | `Line[]` | Used only by `type: "structured"`, original lyrics, word-level or whole-line |
| `translated` | `Line[] \| null` | Used only by `type: "structured"`, translated lyrics; accepts word-level or whole-line text |
| `romanization` | `Line[] \| null` | Used only by `type: "structured"`, romanized lyrics; lines may be word-level (syllable reading) or whole-line text |
| `agents` | `Agent[]` | Used only by `type: "structured"`, performer list (optional; written to TTML head `<ttm:agent>`, see the extension fields section above) |
| `metadata` | `MetadataElement[]` | Used only by `type: "structured"`, elements added to the TTML head (optional; see constraints above) |
| `timing` | `string` | Used only by `type: "structured"`, timing granularity flag (optional; pass `"Word"` for word-level, written to root `<tt itunes:timing>`) |
| `language` | `string` | Used only by `type: "structured"`, original-language code BCP47 (optional; written to root `<tt xml:lang>`) |
| `bodyDur` | `string` | Used only by `type: "structured"`, the original TTML time expression for `<body dur>` (optional; `body_dur` is also accepted) |
| `translatedLang` | `string` | Used only by `type: "structured"`, translation-track language code BCP47 (optional; written to the inline translation's `xml:lang`) |
| `romanizationLang` | `string` | Used only by `type: "structured"`, romanization-track language code BCP47 (optional; written to the head romanization's `xml:lang`) |
| `rawPlainLrc` | `string` | Used only by `type: "rawPlainLrc"` |
| `rawVerbatimLrc` | `string` | Used only by `type: "rawVerbatimLrc"` |
| `rawEnhancedLrc` | `string` | Used only by `type: "rawEnhancedLrc"` |
| `rawTtml` | `string` | Used only by `type: "rawTtml"` |
| `rawMultiPersonEnhancedLrc` | `string` | Used only by `type: "rawMultiPersonEnhancedLrc"` |

---

## `searchCovers(request)`

Searches cover images. The host calls `searchCovers` directly with the user's keyword. The plugin
does not need to implement `searchSongs`, and there is no preceding song-candidate selection.

### Request

```json
{
  "keyword": "Example Song",
  "page": 1,
  "pageSize": 5,
  "config": {}
}
```

| Field | Type | Default | Description |
|------|------|---------|-------------|
| `keyword` | `string` | - | Search keyword |
| `page` | `int` | `1` | Page number, starting from 1 |
| `pageSize` | `int` | `5` | Result count |
| `config` | `object` | `{}` | User config values |

### Return Value

The top-level format matches `searchSongs`, but cover candidates do not require a platform song
ID. In API 4–5, every result must include title, artist, album, year, and a cover URL so the user can
judge the match. A date may use `year`, `date`, or `releaseDate`; the cover may use `picUrl`,
`coverUrl`, and other compatible aliases. Existing API 1–3 return formats remain compatible.

```javascript
function searchCovers(request) {
  return [{
    title: "Example Song",
    artist: "Example Artist",
    album: "Example Album",
    year: "2024",
    picUrl: "https://cdn.example.com/cover.jpg"
  }];
}
```

---

## Error Handling

Exceptions inside plugin functions are caught by the host and written to Logcat. Use `try...catch` for predictable failures:

```javascript
function searchSongs(request) {
  try {
    // Main search logic
    return searchByEapi(request);
  } catch (e) {
    Platform.log.warn("Plugin", "Primary search failed: " + e.message);
    // Fallback logic
    return searchByFallback(request);
  }
}
```

Behavior when a function is undefined:

- If a capability does not declare a function, such as `getLyrics`, the host will not call it
- If the capability is declared but the function is missing, the call fails and is ignored

## Parser Tolerance

The host parser is **lenient**:

- JSON keys have multiple candidates, such as `id`/`songId`/`trackId`
- Extra fields are ignored
- The top level can be an array or a wrapper object
- `null` fields are treated as default values
