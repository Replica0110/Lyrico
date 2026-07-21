# Configuration And Result Fields

This document covers three aspects of data flow:

1. **User config injection**: How `configFields` values reach plugin functions
2. **Plugin return data**: The role and specification of `fields` and `internal` JSON objects
3. **Host write policy**: How Lyrico applies `fields` values to audio tags based on user preferences

---

## 1. User Config Injection

Values saved by users in the plugin config UI are passed to every plugin function call through `request.config`.

Given these `configFields` in the manifest:

```json
{
  "key": "api_key", "title": "API Key", "type": "password", "required": true,
  "key": "lyrics_source", "title": "Lyrics source", "type": "dropdown", "defaultValue": "official",
  "options": [
    { "value": "official", "label": "Official lyrics" },
    { "value": "translated", "label": "Translated lyrics" }
  ]
}
```

After the user saves `api_key = "abc123"` and keeps the lyrics source at its default, a call to `searchSongs` receives:

```json
{
  "keyword": "Song Artist",
  "page": 1,
  "pageSize": 20,
  "config": {
    "api_key": "abc123",
    "lyrics_source": "official"
  }
}
```

**Important notes**:

- Every value in `config` is a `string`, including `switch` (`"true"` / `"false"`) and `number` (`"15"`).
- Fields with `type: "markdown"` never appear in `config` — they are for display only.
- Fields hidden by unsatisfied dependencies still appear in `config` with their last saved (or default) values.

---

## 2. fields — Standard Metadata Fields

In `searchSongs` return values, plugins use the `fields` object for host-recognized standard metadata.

### Return Format

```json
{
  "id": "12345",
  "title": "Song Title",
  "artist": "Artist Name",
  "fields": {
    "title": "Song Title",
    "artist": "Artist Name",
    "album": "Album Name",
    "date": "2003",
    "track_number": "3",
    "cover_url": "https://img.example.com/cover/12345.jpg"
  },
  "internal": {
    "song_id": "12345"
  }
}
```

Values in `fields` work like their top-level counterparts (`title`, `artist`). The difference is that `fields` can include keys not supported at the top level (e.g. `genre`, `composer`).

When a lyrics request is triggered, the `lyrics` field inside `fields` serves as a candidate lyrics source.

### Standard Field Reference

Only the following predefined keys are accepted in `fields`. Unknown keys are silently discarded and produce a debug warning.

| key | Meaning | Example value |
|-----|---------|---------------|
| `title` | Song title | `"Song Title"` |
| `artist` | Artist | `"Artist Name"` |
| `album` | Album name | `"Album Name"` |
| `album_artist` | Album artist | `"Artist Name"` |
| `genre` | Genre | `"Pop"` |
| `date` | Release date | `"2003-07-31"` |
| `track_number` | Track number | `"3"` |
| `disc_number` | Disc number | `"1"` |
| `composer` | Composer | `"Artist Name"` |
| `lyricist` | Lyricist | `"Lyricist Name"` |
| `comment` | Comment | `"..."` |
| `lyrics` | Lyrics (LRC text) | `"[00:00.00]Song\n..."` |
| `cover_url` | Cover image URL | `"https://..."` |
| `language` | Language | `"Chinese"` |
| `copyright` | Copyright info | `"© 2003 Label"` |
| `rating` | Rating (0-100) | `"85"` |
| `replaygain_track_gain` | Track gain (dB) | `"-8.50 dB"` |
| `replaygain_track_peak` | Track peak | `"0.98"` |
| `replaygain_album_gain` | Album gain (dB) | `"-7.20 dB"` |
| `replaygain_album_peak` | Album peak | `"0.95"` |
| `replaygain_reference_loudness` | Reference loudness (LUFS) | `"-14.00 LUFS"` |

Any key outside this list is ignored. Do not put platform IDs, hashes, or tokens in `fields` — they belong in `internal`.

---

## 3. internal — Plugin-Private Context

The `internal` object returned by `searchSongs` holds contextual data that is not standard metadata.

### Use Cases

- Platform-specific song/album/lyrics IDs
- Request hashes, signatures, cookie tokens
- Routing information for subsequent requests
- Any data that should not be shown to users, written to tags, or shared across plugins

### Return Format

```json
{
  "id": "12345",
  "title": "Song Title",
  "fields": { "title": "Song Title" },
  "internal": {
    "song_id": "12345",
    "album_id": "67890",
    "lyrics_id": "abc"
  }
}
```

### Constraints

| Constraint | Value |
|------------|-------|
| Max key length | 64 characters |
| Max value length | 4096 characters |
| Max retained entries | 64 |

Entries exceeding these limits are silently dropped.

### Passing Rules

- `internal` is **not displayed** to users
- `internal` is **not written** to audio tags
- `internal` is **not offered** in batch matching field selection
- `internal` is **not shared** with other plugins
- `internal` is **passed back unchanged** to the same plugin in subsequent requests

For example, `searchSongs` returns a song with `internal.lyrics_id = "abc"`. When `getLyrics` is called for that song, the ID appears in `request.song.internal.lyrics_id`:

```json
{
  "song": {
    "id": "12345",
    "title": "Song Title",
    "fields": { "title": "Song Title" },
    "internal": { "lyrics_id": "abc" }
  },
  "config": { ... }
}
```

JS:
```javascript
function getLyrics(request) {
  var lyricsId = request.song.internal.lyrics_id;
  if (!lyricsId) return null;
  var response = Platform.http.getText(
    "https://api.example.com/lyrics?id=" + encodeURIComponent(lyricsId),
    { headers: { "Authorization": "Bearer " + request.config.api_key } }
  );
  return response;
}
```

---

## 4. Write Policy

Whether `fields` values are written to audio tags, which tag field they map to, and how they are written — all of this is controlled by Lyrico. Plugins have no control over the write process.

### Three-State Write Mode

In batch matching, users can choose a write mode for each standard field:

| Mode | Behavior |
|------|----------|
| Disabled | Do not write this field |
| Supplement | Write only when the local tag is empty |
| Overwrite | Always replace with the plugin value |

### Data Flow Summary

```
manifest.json                    JS Plugin                       Lyrico Host
─────────────                    ────────                       ──────────
configFields ──────────► request.config ────►  API request
                           (user values)
                                                          ▲
                        return JSON                      │
                   ┌──── fields ─────────► display / write candidate
                   │
                   └──── internal ──────► passed back to same plugin
                                            (next request)
```

Plugin developers only need to read `request.config` and construct `fields` and `internal`. Write policy is controlled by users in Lyrico's UI.
