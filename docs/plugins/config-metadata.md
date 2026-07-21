# 配置与运行时字段

本文档说明三个方面的数据传递：

1. **用户配置注入**：`configFields` 中声明的配置项如何传入插件函数
2. **插件返回数据**：`fields` 和 `internal` 两个 JSON 对象的作用与规范
3. **宿主任意**：Lyrico 如何根据用户策略将 `fields` 中的值写入音频标签

---

## 1. 用户配置注入

用户在插件配置界面填写并保存的值，会在每次调用插件函数时通过 `request.config` 传入。

假设 manifest 中声明了以下 `configFields`：

```json
{
  "key": "api_key", "title": "API Key", "type": "password", "required": true,
  "key": "lyrics_source", "title": "歌词来源", "type": "dropdown", "defaultValue": "official",
  "options": [
    { "value": "official", "label": "官方歌词" },
    { "value": "translated", "label": "翻译歌词" }
  ]
}
```

用户保存 `api_key = "abc123"` 且歌词来源保持默认值不变后，调用 `searchSongs` 时传入的 `request` 形如：

```json
{
  "keyword": "晴天 周杰伦",
  "page": 1,
  "pageSize": 20,
  "config": {
    "api_key": "abc123",
    "lyrics_source": "official"
  }
}
```

**注意事项**：

- `config` 中所有值都是 `string` 类型，包括 `switch`（`"true"` / `"false"`）和 `number`（`"15"`）。
- `type: "markdown"` 的配置项不会出现在 `config` 中，它仅用于在配置界面渲染说明文本。
- 依赖条件未满足而隐藏的配置项，其值会保留上一次保存的内容（或默认值），仍会出现在 `config` 中。

---

## 2. fields — 标准元数据字段

插件在 `searchSongs` 的返回结果中，通过 `fields` 对象返回宿主认可的标准元数据。

### 返回格式

```json
{
  "id": "12345",
  "title": "晴天",
  "artist": "周杰伦",
  "fields": {
    "title": "晴天",
    "artist": "周杰伦",
    "album": "叶惠美",
    "date": "2003",
    "track_number": "3",
    "cover_url": "https://img.example.com/cover/12345.jpg"
  },
  "internal": {
    "song_id": "12345"
  }
}
```

`fields` 中的值与顶层 `title`、`artist` 等字段作用相似，都会被纳入元数据候选。不同之处在于 `fields` 可以包含顶层不支持的字段（如 `genre`、`composer` 等）。

触发歌词请求时，`fields` 中的 `lyrics` 字段会作为候选歌词来源。

### 标准字段列表

插件只能使用以下预定义的 key 来填充 `fields`。使用未知 key 会被丢弃并在调试日志中产生 warning。

| key | 含义 | 示例值 |
|-----|------|--------|
| `title` | 歌曲标题 | `"晴天"` |
| `artist` | 艺术家 | `"周杰伦"` |
| `album` | 专辑名称 | `"叶惠美"` |
| `album_artist` | 专辑艺术家 | `"周杰伦"` |
| `genre` | 流派 | `"Pop"` |
| `date` | 发行日期 | `"2003-07-31"` |
| `track_number` | 音轨号 | `"3"` |
| `disc_number` | 碟片号 | `"1"` |
| `composer` | 作曲 | `"周杰伦"` |
| `lyricist` | 作词 | `"方文山"` |
| `comment` | 备注 | `"..."` |
| `lyrics` | 歌词（LRC 文本） | `"[00:00.00]晴天\n..."` |
| `cover_url` | 封面图片 URL | `"https://..."` |
| `language` | 语言 | `"Chinese"` |
| `copyright` | 版权信息 | `"© 2003 JVR Music"` |
| `rating` | 评分（0-100） | `"85"` |
| `replaygain_track_gain` | 音轨增益（dB） | `"-8.50 dB"` |
| `replaygain_track_peak` | 音轨峰值 | `"0.98"` |
| `replaygain_album_gain` | 专辑增益（dB） | `"-7.20 dB"` |
| `replaygain_album_peak` | 专辑峰值 | `"0.95"` |
| `replaygain_reference_loudness` | 参考响度（LUFS） | `"-14.00 LUFS"` |

除此以外的 key 都会被忽略。不要把平台 ID、hash、token 等放入 `fields`，它们应该放入 `internal`。

---

## 3. internal — 插件私有上下文

插件在 `searchSongs` 中返回的 `internal` 对象，用于保存非标准元数据的上下文信息。

### 适用场景

- 平台的歌曲 ID、专辑 ID、歌词 ID
- 请求 hash、签名、cookie token
- 用于后续请求的路由信息
- 任何不应对用户展示、不应写入标签、不应参与跨插件匹配的私有数据

### 返回格式

```json
{
  "id": "12345",
  "title": "晴天",
  "fields": { "title": "晴天" },
  "internal": {
    "song_id": "12345",
    "album_id": "67890",
    "lyrics_id": "abc"
  }
}
```

### 约束

| 约束 | 值 |
|------|-----|
| 单个 key 最大长度 | 64 字符 |
| 单个 value 最大长度 | 4096 字符 |
| 最多保留条目数 | 64 条 |

超出约束的条目会被静默丢弃。

### 传递规则

- `internal` **不展示**给用户
- `internal` **不入写入**音频标签
- `internal` **不参与**批量匹配的字段选择
- `internal` **不传给**其他插件
- `internal` 只会在后续请求中**原样传回同一个插件**

例如，`searchSongs` 返回的歌曲中 `internal.lyrics_id = "abc"` 会在该歌曲的 `getLyrics` 请求中出现在 `request.song.internal.lyrics_id`：

```json
{
  "song": {
    "id": "12345",
    "title": "晴天",
    "fields": { "title": "晴天" },
    "internal": { "lyrics_id": "abc" }
  },
  "config": { ... }
}
```

JS 端读取：
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

## 4. 写入策略

`fields` 中的值是否写入音频标签、写入到哪个字段、以什么方式写入——这些完全由 Lyrico 宿主控制，插件无需也无法干预。

### 三态写入模式

批量匹配页中，用户可以为每个标准字段选择写入模式：

| 模式 | 行为 |
|------|------|
| 禁用 | 不写入该字段的标签 |
| 补充 | 仅当本地标签为空时才用插件数据填充 |
| 覆盖 | 始终用插件数据替换本地标签 |

### 数据流转总览

```
manifest.json                    JS 插件                          Lyrico 宿主
─────────────                    ──────                          ──────────
configFields ──────────► request.config ────► 发起 API 请求
                           (用户配置值)
                                                          ▲
                             返回 JSON                   │
                        ┌──── fields ─────────► 展示/写入候选
                        │
                        └──── internal ──────► 传回同一插件
                                                 (下次请求时)
```

插件开发者只需关注第一步（读取 `request.config`）和第二步（构造 `fields` 与 `internal`）。写入策略由用户在 Lyrico 中自行控制。
