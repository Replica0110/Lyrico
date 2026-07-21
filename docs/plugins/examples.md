# 从零编写插件

这是开发者编写插件的第一站。本文从零开始构建一个完整的 Lyrico 插件，展示 manifest、插件函数、配置项、运行结果字段、辅助脚本和打包方式如何配合工作。

manifest 只声明插件身份、入口、能力和必要配置。插件应在运行结果中用 `fields` 返回宿主标准字段，用 `internal` 返回平台私有上下文。

示例插件名为 **"MusicLib"**，对接一个虚构的 `https://api.musiclib.example.com` 音乐 API。

## 插件目标

- 支持歌曲搜索、歌词获取、封面搜索
- 使用用户配置的 API Key 进行鉴权
- 返回标题、艺术家、专辑、封面等标准元数据
- 提供超时时间、地区等可配置选项

## 目录结构

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
  "description": "MusicLib 音乐 API 搜索源",
  "apiVersion": 3,
  "entry": "source.js",
  "includeDirs": ["lib"],
  "icon": "icon.png",
  "capabilities": ["searchSongs", "getLyrics", "searchCovers"],
  "configFields": [
    {
      "key": "api_key",
      "title": "API Key",
      "summary": "MusicLib API 访问密钥",
      "group": "鉴权",
      "type": "password",
      "required": true,
      "defaultValue": ""
    },
    {
      "key": "region",
      "title": "地区",
      "summary": "API 请求的地区参数",
      "group": "请求",
      "type": "dropdown",
      "required": true,
      "defaultValue": "cn",
      "options": [
        { "value": "cn", "label": "中国大陆" },
        { "value": "us", "label": "美国" },
        { "value": "jp", "label": "日本" }
      ]
    },
    {
      "key": "timeout",
      "title": "超时（秒）",
      "summary": "HTTP 请求超时时间",
      "group": "请求",
      "type": "number",
      "defaultValue": "15"
    },
    {
      "key": "cover_size",
      "title": "封面尺寸",
      "group": "封面",
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

## lib/01_api.js — API 通信层

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

## lib/02_lyrics.js — 歌词解析

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

## source.js — 入口文件

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

## 打包

将上述文件按目录结构组织后，打包为 ZIP：

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

ZIP 包根层级即插件根目录（`com.musiclib.source/`），不要包含多余的外层目录。

## 导入验证

将 ZIP 文件导入 Lyrico 后，系统会执行以下验证流程：

1. 解压到临时目录
2. 找到 `com.musiclib.source/manifest.json`
3. 验证 `id` 格式、API 版本兼容性和 `capabilities` 合法性
4. 验证 `source.js` 存在、`.js` 扩展名、大小合法
5. 验证 `lib/` 目录存在
6. 验证 `icon.png` 存在且格式合法
7. 所有检查通过，安装到 `plugins/sources/com.musiclib.source/`

## 常见问题

### Q: 如何调试插件？

使用 `Platform.log.debug()` 输出日志到 Android Logcat：

```javascript
Platform.log.debug("MusicLib", "Request URL: " + url);
Platform.log.debug("MusicLib", "Response: " + JSON.stringify(response).substring(0, 200));
```

过滤 Logcat 标签 `PlatformPlugin` 或自定义标签（如 `"MusicLib"`）。

### Q: 如何处理分页？

`searchSongs` 接收 `page` 和 `pageSize` 参数，将分页参数转发给 API：

```javascript
var page = Math.max(1, Number(request.page || 1));
var pageSize = Number(request.pageSize || 20);
var offset = (page - 1) * pageSize;
```

### Q: 如何支持多艺术家？

使用 `request.separator` 连接多个艺术家：

```javascript
var artist = artists
  .map(function (a) { return a.name || ""; })
  .filter(function (n) { return n; })
  .join(request.separator || "/");
```

### Q: 入口文件未运行时辅助脚本如何访问？

所有辅助脚本已拼接在入口脚本之前执行。只需将共享逻辑挂载到全局对象（如 `window` 或自定义命名空间）：

```javascript
// lib/01_api.js
var MusicLib = MusicLib || {};
MusicLib.BASE_URL = "...";

// source.js — 可直接使用
var data = MusicLib.get("/search", params, config);
```
