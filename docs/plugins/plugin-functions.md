# 插件函数

本文说明插件需要暴露给 Lyrico 的函数接口。开发者实现搜索、歌词获取和封面搜索时，主要查阅这一页。

插件入口脚本必须定义全局函数作为宿主调用的接口。宿主把请求解析成 JavaScript 对象后传入，并负责把返回值序列化为 JSON。插件应直接返回对象、数组、字符串或 `null`，不要调用 `JSON.stringify()`，否则结果会被重复序列化并导致真机解析失败。

## 函数总览

| 函数 | 触发场景 | 返回类型 | 对应能力 |
|------|----------|----------|----------|
| `searchSongs(request)` | 用户搜索歌曲 | JavaScript 数组 | `searchSongs` |
| `getLyrics(request)` | 搜索歌词候选 | API 4 为 JavaScript 候选数组；API 1–3 为歌词对象、字符串或 `null` | `getLyrics` |
| `searchCovers(request)` | 搜索封面图片 | JavaScript 数组 | `searchCovers` |

函数通过 QuickJS 的全局作用域暴露，不需要（也不能）使用 `export`：

```javascript
function searchSongs(request) { ... }   // ✅ 全局函数
function getLyrics(request) { ... }     // ✅ 全局函数
function searchCovers(request) { ... }  // ✅ 全局函数
```

---

## `searchSongs(request)`

歌曲搜索，宿主将用户输入的关键词传递给此函数。

### 请求参数

宿主传入的 JSON 对象（被序列化前）：

```json
{
  "keyword": "示例歌曲",
  "page": 1,
  "pageSize": 20,
  "separator": "/",
  "config": {
    "cover_size": "1200"
  }
}
```

| 字段 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `keyword` | `string` | - | 用户输入的搜索关键词 |
| `page` | `int` | `1` | 页数（从 1 开始） |
| `pageSize` | `int` | `20` | 每页数量 |
| `separator` | `string` | `"/"` | 多艺术家之间的分隔符 |
| `config` | `object` | `{}` | 用户在设置页面配置的键值对 |

### 返回值

直接返回 JavaScript 数组或包含结果数组的对象。支持两种顶层格式：

**格式 1：直接返回数组（推荐）**

```javascript
function searchSongs(request) {
  return [
    {
      id: "12345",
      title: "示例歌曲",
      artist: "示例歌手",
      album: "示例专辑",
      duration: 240000,
      date: "2024-01-01",
      trackNumber: "2",
      picUrl: "https://cdn.example.com/cover/abc.jpg",
      fields: {
        title: "示例歌曲",
        artist: "示例歌手",
        album: "示例专辑",
        date: "2024-01-01"
      }
    }
  ];
}
```

**格式 2：包装在对象中（兼容多个键名）**

```javascript
function searchSongs(request) {
  return {
    items: [...]    // 也可用 "results"、"songs"、"data"
  };
}
```

### Song 对象字段

解析器支持灵活的字段名映射：

| 语义 | 支持的 JSON 键名（任意一个即可） |
|------|-------------------------------|
| 歌曲 ID | `id`, `songId`, `trackId` |
| 标题 | `title`, `name`, `songName` |
| 艺术家 | `artist`, `artists`, `singer` |
| 专辑 | `album`, `albumName` |
| 时长 | `duration`, `durationMs`, `duration_ms` |
| 发行日期 | `date`, `releaseDate`, `release_date` |
| 音轨号 | `trackNumber`, `trackerNumber`, `track_number` |
| 封面 URL | `picUrl`, `coverUrl`, `cover_url`, `artworkUrl` |
| 标准元数据字段 | `fields` |
| 插件私有上下文 | `internal` |

`artist` 字段还支持数组格式（自动以 `/` 连接）：

```json
{
  "id": "12345",
  "title": "歌曲名",
  "artist": ["歌手A", "歌手B"]
}
```

### fields 标准字段

`fields` 只允许放入宿主标准字段。未知 key 会被忽略并产生调试 warning；平台私有 ID、hash、token 等上下文必须放入 `internal`。

```json
{
  "id": "12345",
  "title": "歌曲名",
  "artist": "歌手",
  "fields": {
    "title": "歌曲名",
    "artist": "歌手",
    "album": "专辑名",
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

当前标准字段包括：`title`、`artist`、`album`、`album_artist`、`genre`、`date`、`track_number`、`disc_number`、`composer`、`lyricist`、`comment`、`lyrics`、`cover_url`、`language`、`copyright`、`rating`、`replaygain_track_gain`、`replaygain_track_peak`、`replaygain_album_gain`、`replaygain_album_peak`、`replaygain_reference_loudness`。

`internal` 不展示、不写入标签、不参与批量匹配字段选择，只会原样传回产生该结果的同一个插件。

---

## `getLyrics(request)`

编辑页的独立歌词搜索会把当前歌曲的标题、艺术家、专辑和年份放在 `song` 中传入。
`getLyrics` 可以直接用这些普通字段搜索，不要求插件实现 `searchSongs`，也不要求用户
提供平台歌曲 ID。只要插件同时声明 `searchSongs`，无论其 API 版本，宿主都会先让用户
选择该插件的同源歌曲候选，再把选中的 `id`、`fields` 和 `internal` 原样传给同一插件的
`getLyrics`。单曲搜索页不会调用独立歌词源。

### 请求参数

```json
{
  "song": {
    "id": "12345",
    "title": "示例歌曲",
    "artist": "示例歌手",
    "album": "示例专辑",
    "duration": 240000,
    "sourceId": "com.example.music_source",
    "pluginId": "com.example.music_source",
    "fields": {
      "title": "示例歌曲"
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

| 字段 | 类型 | 说明 |
|------|------|------|
| `song.id` | `string` | 歌曲 ID；独立歌词搜索时不保证是源平台 ID |
| `song.title` | `string` | 歌曲标题 |
| `song.artist` | `string` | 艺术家 |
| `song.album` | `string` | 专辑名 |
| `song.duration` | `long` | 时长（毫秒） |
| `song.sourceId` | `string` | 源插件 ID |
| `song.pluginId` | `string` | 插件 ID |
| `song.fields` | `object` | 搜索时返回的标准字段 |
| `song.internal` | `object` | 搜索时返回的插件私有上下文 |
| `page` | `int` | 候选页码，从 `1` 开始；不分页的插件可以忽略 |
| `pageSize` | `int` | 本页期望返回的候选数量 |
| `config` | `object` | 用户配置项 |

### 返回值

API 4 应返回歌词对象数组（也可包装在 `items`、`results` 或 `candidates` 中）。每个歌词
对象必须在 `tags` 中提供 `ti`（标题）、`ar`（艺术家）、`al`（专辑）和 `date`（年份），
宿主从这些既有歌词标签生成候选列表，避免再声明一套重复的顶层歌曲字段：

```javascript
function getLyrics(request) {
  return [{
    type: "rawPlainLrc",
    tags: {
      ti: "示例歌曲",
      ar: "示例歌手",
      al: "示例专辑",
      date: "2024"
    },
    rawPlainLrc: "[00:00.00]第一句歌词"
  }];
}
```

API 1–3 的函数签名和原有返回完全不变：可返回单个结构化歌词对象、完整原始歌词文本，
或 `null` 表示未找到歌词。宿主会把旧结果包装为一个候选，并使用请求中的歌曲信息供
用户判断。下面各格式既是 API 1–3 的顶层返回格式，也是 API 4 数组中的候选格式。

宿主先读取 `type` 判断载荷类型；当 `type` 为 `structured` 时解析 `original` /
`translated` / `romanization` 列表，当 `type` 为 raw 类型时直接使用对应 raw 字段。

**格式 1：结构化逐词歌词（推荐）**

```javascript
function getLyrics(request) {
  return {
    type: "structured",
    tags: {
      ti: "歌曲标题",
      ar: "艺术家",
      al: "专辑名"
    },
    original: [
      [0, 2000, [[0, 500, "第一"], [500, 1000, "句"], [1000, 2000, "歌词"]]],
      [2000, 4000, [[2000, 3000, "第二"], [3000, 4000, "句"]]]
    ],
    translated: [
      [0, 2000, "First line lyrics"],
      [2000, 4000, "Second line lyrics"]
    ],
    romanization: null
  };
}
```

**`original` 行格式**（逐词）：

```
[lineStartMs, lineEndMs, [[wordStartMs, wordEndMs, "text"], ...]]
```

**`translated` / `romanization` 行格式**（整行文本）：

```
[lineStartMs, lineEndMs, "text"]
```

**格式 2：完整原始歌词文本**

```javascript
function getLyrics(request) {
  return {
    type: "rawPlainLrc",
    tags: {
      ti: "歌曲标题",
      ar: "艺术家",
      al: "专辑名"
    },
    rawPlainLrc: "[00:00.00]第一句歌词\n[00:05.00]第二句歌词"
  };
}
```

支持的 raw `type` 值与对应内容字段：

| `type` | 内容字段 | 说明 |
|------|------|------|
| `rawPlainLrc` | `rawPlainLrc` | 普通 LRC |
| `rawVerbatimLrc` | `rawVerbatimLrc` | 逐字 LRC |
| `rawEnhancedLrc` | `rawEnhancedLrc` | 增强型逐字 LRC |
| `rawTtml` | `rawTtml` | TTML |
| `rawMultiPersonEnhancedLrc` | `rawMultiPersonEnhancedLrc` | 多人增强 LRC |

若插件没有显式提供 `type`，宿主会按 `structured` 处理；这只用于兼容旧插件，新插件应显式声明。

**格式 3：返回 `null` 表示无歌词**

```javascript
function getLyrics(request) {
  if (noLyricsFound) {
    return null;
    // 或者
    return { notFound: true };
  }
}
```

### LyricsResult 字段

| 字段 | 类型 | 说明 |
|------|------|------|
| `type` | `string` | `structured` 或 raw 类型 |
| `tags` | `object` | 歌曲元信息标签 |
| `original` | `Line[]` | 仅 `type: "structured"` 使用，原文歌词（逐词或整行） |
| `translated` | `Line[] \| null` | 仅 `type: "structured"` 使用，翻译歌词 |
| `romanization` | `Line[] \| null` | 仅 `type: "structured"` 使用，音译歌词（罗马音等） |
| `rawPlainLrc` | `string` | 仅 `type: "rawPlainLrc"` 使用 |
| `rawVerbatimLrc` | `string` | 仅 `type: "rawVerbatimLrc"` 使用 |
| `rawEnhancedLrc` | `string` | 仅 `type: "rawEnhancedLrc"` 使用 |
| `rawTtml` | `string` | 仅 `type: "rawTtml"` 使用 |
| `rawMultiPersonEnhancedLrc` | `string` | 仅 `type: "rawMultiPersonEnhancedLrc"` 使用 |

---

## `searchCovers(request)`

封面图片搜索。宿主直接按用户输入的关键词调用 `searchCovers`，不要求插件实现
`searchSongs`，也没有前置歌曲候选选择步骤。

### 请求参数

```json
{
  "keyword": "示例歌曲",
  "page": 1,
  "pageSize": 5,
  "config": {}
}
```

| 字段 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `keyword` | `string` | - | 搜索关键词 |
| `page` | `int` | `1` | 页数（从 1 开始） |
| `pageSize` | `int` | `5` | 结果数量 |
| `config` | `object` | `{}` | 用户配置项 |

### 返回值

顶层格式与 `searchSongs` 相同，但封面候选不要求平台歌曲 ID。API 4 的每个结果必须
返回标题、艺术家、专辑、年份以及封面 URL，供用户判断后应用；日期可使用 `year`、
`date` 或 `releaseDate`，封面可使用 `picUrl`、`coverUrl` 等兼容键名。API 1–3 的既有
返回格式继续兼容。

```javascript
function searchCovers(request) {
  return [{
    title: "示例歌曲",
    artist: "示例歌手",
    album: "示例专辑",
    year: "2024",
    picUrl: "https://cdn.example.com/cover.jpg"
  }];
}
```

---

## 错误处理

插件函数内部异常会被宿主捕获并记录到 Logcat。确保使用 `try...catch` 处理可预见的错误：

```javascript
function searchSongs(request) {
  try {
    // 主要搜索逻辑
    return searchByEapi(request);
  } catch (e) {
    Platform.log.warn("Plugin", "Primary search failed: " + e.message);
    // 回退逻辑
    return searchByFallback(request);
  }
}
```

函数未定义时的行为：
- 若能力中未声明某函数（如 `getLyrics`），宿主不会调用该函数
- 若声明了但函数不存在，调用会失败并被忽略

## 数据解析容错

宿主解析器是 **宽松的**：
- JSON 键名有多个候选项（如 `id`/`songId`/`trackId`）
- 多余的字段会被忽略
- 顶级可以是数组或包装对象
- `null` 字段当作默认值处理
