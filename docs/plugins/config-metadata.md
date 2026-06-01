# 配置与运行结果字段

插件协议现在只保留 `configFields` 作为 manifest 中的用户配置声明。插件不再通过 `metadataFields` 声明可写元数据，也不再声明默认写入目标或写入模式。

## 配置项

`configFields` 用于影响插件运行时行为。宿主会把用户保存的配置放入每个函数请求的 `config` 对象：

```json
{
  "keyword": "晴天 周杰伦",
  "page": 1,
  "pageSize": 20,
  "config": {
    "lyrics_source": "official"
  }
}
```

支持类型：`text`、`password`、`number`、`switch`、`dropdown`、`textarea`、`markdown`。其中 `markdown` 只用于说明，不会保存到运行时配置。

## fields

插件返回的 `fields` 只放宿主标准元数据字段：

```json
{
  "id": "12345",
  "title": "歌曲名",
  "artist": "歌手",
  "fields": {
    "title": "歌曲名",
    "artist": "歌手",
    "album": "专辑名",
    "date": "2024",
    "lyrics": "[00:00.00]..."
  }
}
```

未知字段会被忽略并在调试信息中提示。不要把平台私有字段塞进 `fields`。

## internal

平台私有上下文放入 `internal`：

```json
{
  "id": "12345",
  "fields": {
    "title": "歌曲名"
  },
  "internal": {
    "song_id": "12345",
    "album_id": "67890",
    "lyrics_id": "abc"
  }
}
```

`internal` 不展示、不写入标签、不参与批量匹配字段配置，也不会传给其他插件。它只会原样传回产生该结果的同一个插件，例如 `getLyrics(request.song.internal.lyrics_id)`。

## 写入策略

写入策略由 Lyrico 宿主管理。批量匹配页会按宿主标准字段生成三态策略：

| 模式 | 行为 |
|------|------|
| 禁用 | 不写入该字段 |
| 补充 | 当前标签为空时写入 |
| 覆盖 | 总是用候选值覆盖 |

插件只负责拿数据，宿主负责根据用户策略写标签。
