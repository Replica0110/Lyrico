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

**`original` line format, word-level:**

```
[lineStartMs, lineEndMs, [[wordStartMs, wordEndMs, "text"], ...], extensions?]
```

The 4th element `extensions` (optional, object): line-level extension attributes. Keys are namespace-prefixed TTML attribute names (e.g. `"ttm:agent"`, `"itunes:songPart"`), values are attribute value strings. They are written verbatim onto the corresponding `<p>` tag when writing TTML. The following keys are consumed by the host and **not emitted on `<p>`**:

- `"itunes:songPart"`: section annotation, extracted by the host to rebuild `<div itunes:songPart="...">` grouping (AMLL spec 7.1);
- `"divBegin"` / `"divEnd"`: section time window (millisecond strings), carried on the first line of a section; the host writes them to that section's `<div begin="..." end="...">`, enabling faithful round-trip of purely annotated sections without time semantics.

div grouping rule: a new div is forced when the `songPart` value changes **or** a `divBegin` appears (explicit time-window segmentation).

Prefix whitelist: `ttm:` / `itunes:` / no prefix; attributes with other prefixes are dropped at parse time (the root node has no corresponding namespace declaration, so writing them would produce invalid XML). Old plugins that omit this element behave as before. Attribute values must be strings; non-string values or a non-object 4th element cause the whole group to be ignored (the line itself is unaffected).

```javascript
// Example: the first line of a section carries songPart + the section time window; lines carry ttm:agent
[0, 6000, [[0, 500, "First"], [500, 1000, "line"]], { "itunes:songPart": "Verse", "ttm:agent": "v1", "divBegin": "0", "divEnd": "6000" }]
```

**`translated` line format, whole-line text** (translations have no word-level semantics):

```
[lineStartMs, lineEndMs, "text"]
```

**`romanization` line format, word-level or whole-line:**

Word-level (syllable-by-syllable reading, same shape as `original` word-level; per-word timing is preserved when writing TTML):

```
[lineStartMs, lineEndMs, [[wordStartMs, wordEndMs, "text"], ...]]
```

Whole-line (backward compatible with older plugins):

```
[lineStartMs, lineEndMs, "text"]
```

**Extension fields: `agents` and `metadata` (optional)**

Besides `original` / `translated` / `romanization`, a structured payload may carry two extension fields for passing through TTML head information. Old plugins that omit them behave as before.

`agents`: performer list, corresponding to TTML head `<ttm:agent>`. An array of objects; `id` is required (entries missing it are dropped), `type` (person / character / organization / group / other) and `name` are optional. `type` is passed through verbatim as a string, with no enum mapping, to avoid losing information. Lines reference an agent via the line-level extension attribute `"ttm:agent": "id"`:

```javascript
agents: [
  { "id": "v1", "type": "person", "name": "Artist A" },
  { "id": "v1000", "type": "group" }
]
```

`metadata`: a head metadata element tree, mapping one-to-one to elements inside TTML `<head>`. Each node is `{ name, namespace?, attributes?, text?, children? }`; repeated sibling elements with the same name = multiple same-name nodes in the array. The plugin constructs the tree itself (sibling stays sibling, children stay children); the host does not normalize.

Host handling rules for `metadata` (three branches, dispatched on the top-level element name; children are not re-validated, the tree shape is the plugin's responsibility):

| Branch | Condition | Behavior |
|------|------|------|
| Official key + official structure | e.g. `songwriters` wrapping `songwriter` children with text | Written back per the official spec (to the corresponding TTML head position) |
| Non-official key | Element names not present in the official spec | Passed through verbatim (element tree preserved) |
| Official key + wrong structure | e.g. `songwriters` with text directly on top, or children not named `songwriter` | Whole subtree dropped with a warn log (no guessing the plugin's intent, no corrective fallback) |

Official keys are case-sensitive: AMLL spec element names are all-lowercase (`songwriters` / `songwriter` / ...); camelCase forms (e.g. `songWriters`) are treated as non-official keys and take the passthrough branch, with no normalization.

The following official keys are already carried by dedicated structured fields (`translated` / `romanization` / `agents`); providing them in `metadata` is necessarily duplicate → dropped with a warn: `translations`, `transliterations`, `ttm:agent`.

Prefixed element names (with prefixes other than the built-in `ttm:` / `itunes:` / `xml:`) must provide a `namespace` URI, otherwise the host cannot emit valid XML → the node is dropped with a warn.

```javascript
metadata: [
  // Official key: songwriters wrapping songwriter children (AMLL spec)
  {
    "name": "songwriters",
    "children": [
      { "name": "songwriter", "text": "Songwriter A" },
      { "name": "songwriter", "text": "Songwriter B" }
    ]
  },
  // Non-official key: passthrough. A non-built-in prefix must provide the namespace URI —
  // the address declared via xmlns:amll on the source document root (the host does not know
  // custom prefixes; without the URI it cannot emit valid XML). The host collects the
  // "prefix → URI" mapping and appends a single xmlns:prefix="..." declaration on the root element.
  {
    "name": "amll:meta",
    "namespace": "http://www.example.com/ns/amll",
    "attributes": { "key": "musicName", "value": "Song Title" }
  }
]
```

**Extension fields: language codes and root attributes (`timing` / `language` / `translatedLang` / `romanizationLang`, optional)**

A structured payload may also carry the following top-level fields (all optional, default empty string; writing behavior is unchanged when omitted):

| Field | Written to TTML at | Description |
|------|------|-------------|
| `timing` | Root `<tt itunes:timing="...">` | Timing granularity flag; pass `"Word"` for word-level data |
| `language` | Root `<tt xml:lang="...">` | Original-language code (BCP47) |
| `translatedLang` | Inline translation `<span ttm:role="x-translation" xml:lang="...">` | Translation-track language code (BCP47) |
| `romanizationLang` | Head romanization `<transliteration xml:lang="...">` | Romanization-track language code (BCP47, e.g. `zh-Latn-jyutping`) |

Language codes are passed through verbatim, with no folding or enum mapping (full BCP47 tags are preserved).

```javascript
{
  timing: "Word",
  language: "zh-Hans",
  translatedLang: "zh-Hant",
  romanizationLang: "zh-Latn-jyutping",
  original: [...], translated: [...], romanization: [...]
}
```

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
| `metadata` | `MetadataElement[]` | Used only by `type: "structured"`, head metadata element tree (optional; official keys written per spec, non-official passed through, wrong-structure dropped — see the extension fields section above) |
| `timing` | `string` | Used only by `type: "structured"`, timing granularity flag (optional; pass `"Word"` for word-level, written to root `<tt itunes:timing>`) |
| `language` | `string` | Used only by `type: "structured"`, original-language code BCP47 (optional; written to root `<tt xml:lang>`) |
| `translatedLang` | `string` | Used only by `type: "structured"`, translation-track language code BCP47 (optional; written to the inline translation's `xml:lang`) |
| `romanizationLang` | `string` | Used only by `type: "structured"`, romanization-track language code BCP47 (optional; written to the head romanization's `xml:lang`) |
| `rawPlainLrc` | `string` | Used only by `type: "rawPlainLrc"` |
| `rawVerbatimLrc` | `string` | Used only by `type: "rawVerbatimLrc"` |
| `rawEnhancedLrc` | `string` | Used only by `type: "rawEnhancedLrc"` |
| `rawTtml` | `string` | Required for `type: "rawTtml"`; may also accompany `type: "structured"` (TTML source as a fidelity backstop — the host round-trips it through the passthrough pipeline, so any detail not covered by the structured projection is preserved) |
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
