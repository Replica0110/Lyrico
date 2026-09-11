# Plugin Functions

This page describes the function interfaces that plugins expose to Lyrico. Use it when implementing song search, lyrics retrieval, and cover search.

The plugin entry script must define global functions for the host to call. The host parses each request into a JavaScript object and serializes the returned value to JSON. Return an object, array, string, or `null` directly. Do not call `JSON.stringify()`, because that double-serializes the result and fails on the Android host.

## Function Overview

| Function | Trigger | Return type | Capability |
|----------|---------|-------------|------------|
| `searchSongs(request)` | User searches songs | JavaScript array | `searchSongs` |
| `getLyrics(request)` | Search lyrics candidates | JavaScript candidate array in API 4; lyrics object, string, or `null` in API 1–3 | `getLyrics` |
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

API 4 should return an array of lyrics objects. A wrapper using `items`, `results`, or
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
responses and valid candidate objects inside the API 4 array.

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

### Structured line formats

`original` and `romanization` both accept word-level lines:

```
[lineStartMs, lineEndMs, [[wordStartMs, wordEndMs, "text"], ...], extensions?]
```

They also accept whole-line text. `translated` uses only this form:

```
[lineStartMs, lineEndMs, "text"]
```

When exported as TTML, word-level romanization keeps its timing. Lyrico inserts spaces between adjacent syllables when needed.

### TTML extensions

The fields in this section affect TTML output only. TTML-specific structure is not retained when exporting to LRC.

This section describes the TTML subset available through the structured plugin payload; it is not a replacement for the AMLL TTML DB submission specification. Ruby and unknown XML nodes cannot currently be represented by a structured payload. Return `type: "rawTtml"` when the complete source document must be retained. If the user later applies script conversion, track filtering, or another transformation, Lyrico will parse and rewrite that document, and unmodeled structures may be lost.

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
| `bodyDur` | `<body dur>`; the raw TTML time expression (e.g. `"04:24.660"`), a reference total duration. Passed through without conversion and left unchanged by time offset |
| `translatedLang` | `xml:lang` on the inline translation |
| `romanizationLang` | `xml:lang` on `<transliteration>` |

Use BCP 47 language tags such as `zh-Hans` and `ja-Latn`.

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
| `translated` | `Line[] \| null` | Used only by `type: "structured"`, translated lyrics |
| `romanization` | `Line[] \| null` | Used only by `type: "structured"`, romanized lyrics; lines may be word-level (syllable reading) or whole-line text |
| `agents` | `Agent[]` | Used only by `type: "structured"`, performer list (optional; written to TTML head `<ttm:agent>`, see the extension fields section above) |
| `metadata` | `MetadataElement[]` | Used only by `type: "structured"`, elements added to the TTML head (optional; see constraints above) |
| `timing` | `string` | Used only by `type: "structured"`, timing granularity flag (optional; pass `"Word"` for word-level, written to root `<tt itunes:timing>`) |
| `language` | `string` | Used only by `type: "structured"`, original-language code BCP47 (optional; written to root `<tt xml:lang>`) |
| `bodyDur` | `string` | Used only by `type: "structured"`, reference total duration for `<body dur>` (optional; the raw TTML time expression is passed through verbatim, e.g. `"04:24.660"`; `body_dur` also accepted) |
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
ID. In API 4, every result must include title, artist, album, year, and a cover URL so the user can
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
