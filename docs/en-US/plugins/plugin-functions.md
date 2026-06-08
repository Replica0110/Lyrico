# Plugin Functions

This page describes the function interfaces that plugins expose to Lyrico. Use it when implementing song search, lyrics retrieval, and cover search.

The plugin entry script must define global functions for the host to call. These functions receive JSON string arguments and return JSON string results.

## Function Overview

| Function | Trigger | Return type | Capability |
|----------|---------|-------------|------------|
| `searchSongs(request)` | User searches songs | JSON array string | `searchSongs` |
| `getLyrics(request)` | Lyrics are requested for one song | JSON object string or `null` | `getLyrics` |
| `searchCovers(request)` | Cover images are searched | JSON array string | `searchCovers` |

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

Return the result after `JSON.stringify()`. Two top-level formats are supported.

**Format 1: return an array directly, recommended**

```javascript
function searchSongs(request) {
  return JSON.stringify([
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
  ]);
}
```

**Format 2: wrap the array in an object**

```javascript
function searchSongs(request) {
  return JSON.stringify({
    items: [...]    // "results", "songs", or "data" are also accepted
  });
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

Retrieves lyrics for one song.

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
  "config": {}
}
```

| Field | Type | Description |
|------|------|-------------|
| `song.id` | `string` | Song ID on the source platform |
| `song.title` | `string` | Song title |
| `song.artist` | `string` | Artist |
| `song.album` | `string` | Album title |
| `song.duration` | `long` | Duration in milliseconds |
| `song.sourceId` | `string` | Source plugin ID |
| `song.pluginId` | `string` | Plugin ID |
| `song.fields` | `object` | Standard fields returned by search |
| `song.internal` | `object` | Plugin-private context returned by search |
| `config` | `object` | User config values |

### Return Value

Return structured lyrics data, full raw lyrics text, or `null` when no lyrics are found. The host first reads `type` to determine payload type. For `type: "structured"`, it parses `original` / `translated` / `romanization` lists. For raw types, it directly uses the matching raw field.

**Format 1: structured word-level lyrics, recommended**

```javascript
function getLyrics(request) {
  return JSON.stringify({
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
  });
}
```

**`original` line format, word-level:**

```
[lineStartMs, lineEndMs, [[wordStartMs, wordEndMs, "text"], ...]]
```

**`translated` / `romanization` line format, whole-line text:**

```
[lineStartMs, lineEndMs, "text"]
```

**Format 2: full raw lyrics text**

```javascript
function getLyrics(request) {
  return JSON.stringify({
    type: "rawPlainLrc",
    tags: {
      ti: "Song Title",
      ar: "Artist",
      al: "Album Title"
    },
    rawPlainLrc: "[00:00.00]First line lyrics\n[00:05.00]Second line lyrics"
  });
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
    return JSON.stringify({ notFound: true });
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
| `romanization` | `Line[] \| null` | Used only by `type: "structured"`, romanized lyrics |
| `rawPlainLrc` | `string` | Used only by `type: "rawPlainLrc"` |
| `rawVerbatimLrc` | `string` | Used only by `type: "rawVerbatimLrc"` |
| `rawEnhancedLrc` | `string` | Used only by `type: "rawEnhancedLrc"` |
| `rawTtml` | `string` | Used only by `type: "rawTtml"` |
| `rawMultiPersonEnhancedLrc` | `string` | Used only by `type: "rawMultiPersonEnhancedLrc"` |

---

## `searchCovers(request)`

Searches cover images. This usually delegates to `searchSongs` and filters results that have covers.

### Request

```json
{
  "keyword": "Example Song",
  "pageSize": 5,
  "config": {}
}
```

| Field | Type | Default | Description |
|------|------|---------|-------------|
| `keyword` | `string` | - | Search keyword |
| `pageSize` | `int` | `5` | Result count |
| `config` | `object` | `{}` | User config values |

### Return Value

The format is identical to `searchSongs`. The host filters results that have `picUrl`.

```javascript
function searchCovers(request) {
  return searchSongs({
    keyword: request.keyword,
    page: 1,
    pageSize: request.pageSize || 5,
    separator: "/",
    config: request.config || {}
  }).filter(function (song) {
    return song.picUrl;
  });
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
