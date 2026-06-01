# Manifest 参考

`manifest.json` 只描述插件身份、版本、入口、能力和配置项。插件不要在 manifest 中声明可能返回哪些字段、需要哪些 Host API、或字段如何写入音频标签。

字段写入策略属于 Lyrico 宿主；插件只在运行时返回实际拿到的数据。

## 字段总览

| 字段 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| `id` | `string` | 是 | - | 插件唯一标识，使用反向域名格式 |
| `name` | `string` | 是 | - | 显示名称 |
| `versionCode` | `int` | 是 | - | 版本号，必须大于等于 1 |
| `versionName` | `string` | 是 | - | 版本名 |
| `apiVersion` | `int` | 是 | - | 插件 API 版本 |
| `minHostApiVersion` | `int` | 否 | `1` | 最低宿主 API 版本 |
| `author` | `string` | 否 | `""` | 作者 |
| `description` | `string` | 否 | `""` | 描述 |
| `entry` | `string` | 否 | `"source.js"` | 入口 JS 文件 |
| `includeDirs` | `string[]` | 否 | `[]` | 需要加载的本地辅助脚本目录 |
| `icon` | `string \| null` | 否 | `null` | 图标文件相对路径 |
| `capabilities` | `string[]` | 否 | `[]` | 插件能力 |
| `configFields` | `ConfigField[]` | 否 | `[]` | 用户可配置项 |

已移除的旧字段会被忽略：`requiredHostApis`、`returnedFields`、`metadataFields`、`defaultTarget`、`defaultMode`、`targetOptions`、`writeable`、`internal`、`customTagKey`。

## 示例

```json
{
  "id": "com.example.source",
  "name": "Example Source",
  "versionCode": 1,
  "versionName": "1.0.0",
  "author": "Plugin Author",
  "description": "Example source plugin",
  "apiVersion": 1,
  "minHostApiVersion": 1,
  "entry": "source.js",
  "includeDirs": [
    "lib"
  ],
  "capabilities": [
    "searchSongs",
    "getLyrics",
    "searchCovers"
  ],
  "configFields": [
    {
      "key": "lyrics_source",
      "title": "歌词来源",
      "summary": "选择插件优先返回哪一种歌词",
      "type": "dropdown",
      "required": true,
      "defaultValue": "official",
      "options": [
        {
          "value": "official",
          "label": "官方歌词"
        },
        {
          "value": "user",
          "label": "用户上传歌词"
        }
      ]
    }
  ]
}
```

## 字段说明

`id` 必须是反向域名格式，例如 `com.example.music_source`。

`apiVersion` 用于插件协议兼容检查。插件需要 Host API 时直接调用运行时对象；宿主不再通过 `requiredHostApis` 做静态权限校验。缺失能力会在运行时返回标准化错误。

`capabilities` 支持：

| 能力 | 函数 |
|------|------|
| `searchSongs` | `searchSongs(request)` |
| `getLyrics` | `getLyrics(request)` |
| `searchCovers` | `searchCovers(request)` |

如果声明了 `capabilities`，搜索源插件必须包含 `searchSongs`。

`includeDirs` 只能引用插件包内的相对目录。不能使用绝对路径、`..`、网络 URL 或跨插件文件。

## configFields

`configFields` 是唯一保留的可扩展声明，因为它会影响插件运行时行为。用户填写的配置会通过函数请求中的 `config` 传给插件。

支持类型：

| 类型 | 用途 |
|------|------|
| `text` | 单行文本 |
| `password` | 密钥、Cookie、Token |
| `number` | 数字 |
| `switch` | 开关 |
| `dropdown` | 下拉选项 |
| `textarea` | 多行文本 |
| `markdown` | 说明文本，不写入运行时配置 |

插件如果存在多个平台字段可选，例如官方歌词和用户上传歌词，应通过 `configFields` 让用户选择，并最终只返回一个标准字段：

```json
{
  "key": "lyrics_source",
  "title": "歌词来源",
  "type": "dropdown",
  "defaultValue": "official",
  "options": [
    { "value": "official", "label": "官方歌词" },
    { "value": "user", "label": "用户上传歌词" }
  ]
}
```

## 运行结果字段

插件函数返回结果使用 `fields` 和 `internal` 分层：

| 字段 | 用途 |
|------|------|
| `fields` | 宿主标准元数据字段，可用于展示、匹配和写入候选 |
| `internal` | 插件私有上下文，只会传回同一个插件 |

`fields` 只允许标准 key。未知 key 会被忽略并在调试时 warning。平台 ID、hash、token、歌词 ID、专辑 ID 等必须放入 `internal`。

标准 key：`title`、`artist`、`album`、`album_artist`、`genre`、`date`、`track_number`、`disc_number`、`composer`、`lyricist`、`comment`、`lyrics`、`cover_url`、`language`、`copyright`、`rating`、`replaygain_track_gain`、`replaygain_track_peak`、`replaygain_album_gain`、`replaygain_album_peak`、`replaygain_reference_loudness`。
