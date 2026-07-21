# Build A Plugin From Scratch

This is the first stop for plugin developers. It builds a complete Lyrico plugin from scratch and shows how the manifest, plugin functions, config fields, result fields, helper scripts, and packaging work together.

The manifest only declares plugin identity, entry, capabilities, and required configuration. Plugins should return host-standard metadata through `fields` and platform-private context through `internal`.

The example plugin is named **"MusicLib"** and connects to a fictional `https://api.musiclib.example.com` music API.

## Plugin Goals

- Support song search, lyrics retrieval, and cover search
- Authenticate with a user-configured API key
- Return standard metadata such as title, artist, album, and cover
- Provide configurable timeout, region, and cover size options

## Directory Structure

```
com.musiclib.source/
├── manifest.json
├── source.js
├── icon.png
└── lib/
    ├── 01_api.js
    └── 02_lyrics.js
```

---

## manifest.json

```json
{
  "id": "com.musiclib.source",
  "name": "MusicLib",
  "versionCode": 1,
  "versionName": "1.0.0",
  "author": "Your Name",
  "description": "MusicLib music API search source",
  "apiVersion": 3,
  "entry": "source.js",
  "includeDirs": ["lib"],
  "icon": "icon.png",
  "capabilities": ["searchSongs", "getLyrics", "searchCovers"],
  "configFields": [
    {
      "key": "api_key",
      "title": "API Key",
      "summary": "MusicLib API access key",
      "group": "Auth",
      "type": "password",
      "required": true,
      "defaultValue": ""
    },
    {
      "key": "region",
      "title": "Region",
      "summary": "API request region",
      "group": "Request",
      "type": "dropdown",
      "required": true,
      "defaultValue": "cn",
      "options": [
        { "value": "cn", "label": "Mainland China" },
        { "value": "us", "label": "United States" },
        { "value": "jp", "label": "Japan" }
      ]
    },
    {
      "key": "timeout",
      "title": "Timeout (seconds)",
      "summary": "HTTP request timeout",
      "group": "Request",
      "type": "number",
      "defaultValue": "15"
    },
    {
      "key": "cover_size",
      "title": "Cover Size",
      "group": "Cover",
      "type": "dropdown",
      "required": true,
      "defaultValue": "800",
      "options": [
        { "value": "300", "label": "300 × 300" },
        { "value": "800", "label": "800 × 800" },
        { "value": "1200", "label": "1200 × 1200" }
      ]
    }
  ]
}
```

---

## lib/01_api.js — API Layer

```javascript
var MusicLib = MusicLib || {};

MusicLib.BASE_URL = "https://api.musiclib.example.com/v1";

MusicLib.getConfig = function (request) {
  var config = request.config || {};
  return {
    apiKey: config.api_key || "",
    region: config.region || "cn",
    timeout: parseInt(config.timeout || "15", 10) * 1000,
    coverSize: config.cover_size || "800"
  };
};

MusicLib.buildHeaders = function (config) {
  return {
    "X-API-Key": config.apiKey,
    "X-Region": config.region,
    "Accept": "application/json"
  };
};

MusicLib.signRequest = function (path, params, config) {
  var keys = Object.keys(params).sort();
  var raw = path;
  for (var i = 0; i < keys.length; i++) {
    raw += keys[i] + String(params[keys[i]]);
  }
  raw += config.apiKey;

  return Platform.crypto.md5(raw);
};

MusicLib.get = function (path, params, config) {
  var url = MusicLib.BASE_URL + path;
  var queryParts = [];
  var keys = Object.keys(params || {});
  for (var i = 0; i < keys.length; i++) {
    queryParts.push(
      encodeURIComponent(keys[i]) + "=" + encodeURIComponent(params[keys[i]])
    );
  }
  if (queryParts.length > 0) {
    url += "?" + queryParts.join("&");
  }

  var signature = MusicLib.signRequest(path, params, config);
  var headers = MusicLib.buildHeaders(config);
  headers["X-Signature"] = signature;

  return JSON.parse(
    Platform.http.getText(url, {
      headers: headers,
      readTimeoutMs: config.timeout
    })
  );
};

MusicLib.buildCoverUrl = function (coverId, config) {
  if (!coverId) return "";
  return (
    "https://img.musiclib.example.com/covers/" +
    coverId +
    "_" +
    config.coverSize +
    "x" +
    config.coverSize +
    ".jpg"
  );
};
```

---

## lib/02_lyrics.js — Lyrics Parsing

```javascript
MusicLib.parsePlainLrc = function (lrcText) {
  if (!lrcText || typeof lrcText !== "string") {
    return [];
  }

  var lines = lrcText.split("\n");
  var result = [];

  var tagRegex = /\[(\d+):(\d+(?:\.\d+)?)\](.*)/;
  var timeRegex = /\[(\d+):(\d+(?:\.\d+)?)\]/g;

  for (var i = 0; i < lines.length; i++) {
    var line = lines[i].trim();
    if (!line) continue;

    var match = line.match(tagRegex);
    if (!match) continue;

    var minutes = parseInt(match[1], 10);
    var seconds = parseFloat(match[2]);
    var text = (match[3] || "").trim();
    if (!text) continue;

    var startMs = Math.round((minutes * 60 + seconds) * 1000);
    var endMs = startMs + 3000;

    result.push([startMs, endMs, text]);
  }

  return result;
};

MusicLib.mapLyrics = function (apiResponse) {
  var lyrics = apiResponse.data && apiResponse.data.lyrics;
  if (!lyrics) return null;

  var rawLrc = lyrics.original || "";
  var translatedLrc = lyrics.translated || "";
  var roma = lyrics.romanization || "";

  if (!rawLrc && !translatedLrc) return null;

  var original = MusicLib.parsePlainLrc(rawLrc);
  var translated = translatedLrc
    ? MusicLib.parsePlainLrc(translatedLrc)
    : null;
  var romanization = roma ? MusicLib.parsePlainLrc(roma) : null;

  return {
    type: "structured",
    tags: {
      ti: lyrics.title || "",
      ar: lyrics.artist || "",
      al: lyrics.album || ""
    },
    original: original,
    translated: translated,
    romanization: romanization
  };
};
```

---

## source.js — Entry File

```javascript
function formatDate(timestamp) {
  if (!timestamp) return "";
  var date = new Date(timestamp);
  var y = date.getFullYear();
  var m = String(date.getMonth() + 1).padStart(2, "0");
  var d = String(date.getDate()).padStart(2, "0");
  return y + "-" + m + "-" + d;
}

function mapSong(item, request) {
  var config = MusicLib.getConfig(request);
  var coverUrl = MusicLib.buildCoverUrl(item.cover_id, config);

  var fields = {
    title: item.name || "",
    artist: (Array.isArray(item.artists) ? item.artists : [])
      .map(function (a) { return a.name || ""; })
      .filter(function (n) { return n; })
      .join(request.separator || "/"),
    album: (item.album || {}).name || "",
    date: formatDate(item.release_time * 1000),
    track_number: String(item.track_number || ""),
    cover_url: coverUrl
  };

  var internal = {
    musiclib_id: String(item.id || "")
  };

  return {
    id: String(item.id || ""),
    title: fields.title,
    artist: fields.artist,
    album: fields.album,
    duration: Number(item.duration_ms || 0),
    date: fields.date,
    trackNumber: fields.track_number,
    picUrl: coverUrl,
    fields: fields,
    internal: internal
  };
}

function searchSongs(request) {
  try {
    var config = MusicLib.getConfig(request);
    var page = Math.max(1, Number(request.page || 1));
    var pageSize = Number(request.pageSize || 20);

    var response = MusicLib.get("/search", {
      q: request.keyword || "",
      page: page,
      limit: pageSize,
      region: config.region
    }, config);

    var items = (response.data && response.data.items) || [];
    return JSON.stringify(
      items
        .map(function (item) { return mapSong(item, request); })
        .filter(function (song) { return song.id && song.title; })
    );
  } catch (e) {
    Platform.log.error(
      "MusicLib",
      "searchSongs failed: " + (e && e.message ? e.message : e)
    );
    return JSON.stringify([]);
  }
}

function getLyrics(request) {
  var song = request.song || {};
  var internal = song.internal || {};
  var trackId = internal.musiclib_id || song.id || "";

  if (!trackId) return null;

  try {
    var config = MusicLib.getConfig(request);
    var response = MusicLib.get("/lyrics", { id: trackId }, config);
    var lyricsResult = MusicLib.mapLyrics(response);

    if (!lyricsResult) return null;

    lyricsResult.tags = lyricsResult.tags || {};
    lyricsResult.tags.ti = lyricsResult.tags.ti || song.title || "";
    lyricsResult.tags.ar = lyricsResult.tags.ar || song.artist || "";
    lyricsResult.tags.al = lyricsResult.tags.al || song.album || "";

    return JSON.stringify(lyricsResult);
  } catch (e) {
    Platform.log.warn(
      "MusicLib",
      "getLyrics failed: " + (e && e.message ? e.message : e)
    );
    return null;
  }
}

function searchCovers(request) {
  var songs = JSON.parse(
    searchSongs({
      keyword: request.keyword,
      page: 1,
      pageSize: request.pageSize || 5,
      separator: "/",
      config: request.config || {}
    })
  );

  return JSON.stringify(
    songs.filter(function (song) { return song.picUrl; })
  );
}
```

---

## Package

After arranging the files above, package them as a ZIP:

```
MusicLib-v1.0.0.zip
└── com.musiclib.source/
    ├── manifest.json
    ├── source.js
    ├── icon.png
    └── lib/
        ├── 01_api.js
        └── 02_lyrics.js
```

The ZIP root level should be the plugin root directory, `com.musiclib.source/`. Do not add an extra outer directory.

## Import Validation

After importing the ZIP into Lyrico, the system validates it as follows:

1. Extract the ZIP to a temporary directory
2. Find `com.musiclib.source/manifest.json`
3. Validate `id` format, API version compatibility, and legal `capabilities`
4. Validate that `source.js` exists, uses `.js`, and has a valid size
5. Validate that `lib/` exists
6. Validate that `icon.png` exists and has a supported format
7. If all checks pass, install it to `plugins/sources/com.musiclib.source/`

## FAQ

### Q: How do I debug a plugin?

Use `Platform.log.debug()` to write logs to Android Logcat:

```javascript
Platform.log.debug("MusicLib", "Request URL: " + url);
Platform.log.debug("MusicLib", "Response: " + JSON.stringify(response).substring(0, 200));
```

Filter Logcat by `PlatformPlugin` or a custom tag such as `"MusicLib"`.

### Q: How do I handle pagination?

`searchSongs` receives `page` and `pageSize`. Forward them to the API:

```javascript
var page = Math.max(1, Number(request.page || 1));
var pageSize = Number(request.pageSize || 20);
var offset = (page - 1) * pageSize;
```

### Q: How do I support multiple artists?

Join multiple artists with `request.separator`:

```javascript
var artist = artists
  .map(function (a) { return a.name || ""; })
  .filter(function (n) { return n; })
  .join(request.separator || "/");
```

### Q: How can the entry file access helper scripts before it runs?

All helper scripts are concatenated before the entry script and executed first. Put shared logic on a global object, such as `window` or your own namespace:

```javascript
// lib/01_api.js
var MusicLib = MusicLib || {};
MusicLib.BASE_URL = "...";

// source.js — can use it directly
var data = MusicLib.get("/search", params, config);
```
