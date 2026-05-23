# 3. Manifest 字段参考

`manifest.json` 是插件的核心声明文件，定义了插件的元数据、能力、依赖和配置项。

## 字段总览

| 字段 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| `id` | `string` | **是** | - | 插件唯一标识（反向域名格式） |
| `name` | `string` | **是** | - | 显示名称 |
| `versionCode` | `int` | **是** | - | 版本号（整数，≥ 1） |
| `versionName` | `string` | **是** | - | 版本名（语义化，如 `"1.0.0"`） |
| `apiVersion` | `int` | **是** | - | 插件 API 版本（必须等于宿主 API 版本） |
| `author` | `string` | 否 | `""` | 作者名称 |
| `description` | `string` | 否 | `""` | 插件描述 |
| `entry` | `string` | 否 | `"source.js"` | 入口 JS 文件名 |
| `includeDirs` | `string[]` | 否 | `[]` | 辅助脚本目录列表 |
| `icon` | `string \| null` | 否 | `null` | 图标文件相对路径 |
| `capabilities` | `string[]` | 否 | `[]` | 能力声明 |
| `requiredHostApis` | `string[]` | 否 | `[]` | 所需宿主 API 列表 |
| `configFields` | `ConfigField[]` | 否 | `[]` | 用户可配置项 |
| `metadataFields` | `MetadataField[]` | 否 | `[]` | 可写入音频文件的元数据声明 |

---

## `id` — 插件唯一标识

**类型**：`string`  
**必填**：是  
**格式**：反向域名格式，正则 `^[a-zA-Z][a-zA-Z0-9_]*(\.[a-zA-Z][a-zA-Z0-9_]*)+$`

每个段必须以字母开头，仅包含字母、数字、下划线。至少包含一个点号。

```json
// ✅ 合法
"id": "com.example.music_source"
"id": "org.example.my_plugin_v2"

// ❌ 非法
"id": "myplugin"                     // 缺少点号
"id": "com.123plugin.source"         // 段以数字开头
"id": "com.my-plugin.source"         // 连字符不合法
```

---

## `name` — 显示名称

**类型**：`string`  
**必填**：是  
**限制**：不能为空（空白字符串会被拒绝）

```json
"name": "示例音乐源"
```

---

## `versionCode` — 版本号

**类型**：`int`  
**必填**：是  
**限制**：必须 ≥ 1

用于判断更新/降级关系：

```json
"versionCode": 1
```

- 新插件 `versionCode >` 旧 → `UPDATE`
- 新插件 `versionCode ==` 旧 → `OVERWRITE`
- 新插件 `versionCode <` 旧 → `DOWNGRADE`（默认拒绝，需显式允许）

---

## `versionName` — 版本名

**类型**：`string`  
**必填**：是  
**用途**：面向用户的语义化版本号

```json
"versionName": "0.1.0"
```

---

## `apiVersion` — 插件 API 版本

**类型**：`int`  
**必填**：是  
**限制**：必须等于 `HostApiRegistry.PLUGIN_API_VERSION`（当前为 **1**）

此字段用于确保插件与宿主运行时兼容。不匹配的版本将被拒绝导入。

```json
"apiVersion": 1
```

---

## `author` — 作者

**类型**：`string`  
**必填**：否  
**默认值**：`""`

```json
"author": "Developer"
```

---

## `description` — 描述

**类型**：`string`  
**必填**：否  
**默认值**：`""`

```json
"description": "示例音乐搜索源插件"
```

---

## `entry` — 入口文件

**类型**：`string`  
**必填**：否  
**默认值**：`"source.js"`

插件入口 JavaScript 文件，相对于插件根目录的路径。

- 必须是 `.js` 扩展名
- 路径不能逃逸插件根目录（不能包含 `..`、`\`、`\0`，不能以 `/` 或 `\` 开头）
- 文件大小 ≤ 1 MB

```json
// 默认（可省略）
"entry": "source.js"

// 自定义入口
"entry": "main.js"
```

---

## `includeDirs` — 辅助脚本目录

**类型**：`string[]`  
**必填**：否  
**默认值**：`[]`

声明包含辅助 JavaScript 文件的目录，这些文件在入口脚本之前执行。

- 目录路径相对于插件根目录
- 目录必须真实存在
- 不能为 `"."`（表示根目录本身）
- 不能逃逸插件根目录

目录内的 `.js` 文件按路径名排序，逐个拼接到入口脚本之前。

```json
"includeDirs": ["lib"]
```

多个目录：

```json
"includeDirs": ["lib", "utils"]
```

拼接顺序：先 `lib/` 下所有 `.js`（排序），再 `utils/` 下所有 `.js`（排序），最后入口文件。

---

## `icon` — 图标

**类型**：`string | null`  
**必填**：否  
**默认值**：`null`

插件图标的相对路径。支持的格式：`png`、`jpg`、`jpeg`、`webp`。

```json
"icon": "icon.png"
```

不提供图标时省略或设为 `null`：

```json
// 省略
// 或显式设为 null
"icon": null
```

---

## `capabilities` — 能力声明

**类型**：`string[]`  
**必填**：否  
**默认值**：`[]`（等同于仅有 `searchSongs`）

声明插件支持的功能。可选值：

| 值 | 说明 |
|----|------|
| `"searchSongs"` | 支持歌曲搜索 |
| `"getLyrics"` | 支持获取歌词 |
| `"searchCovers"` | 支持封面搜索 |

**约束**：若声明了任何能力，则必须包含 `searchSongs`。

```json
// 仅搜索
"capabilities": ["searchSongs"]

// 完整能力（推荐）
"capabilities": ["searchSongs", "getLyrics", "searchCovers"]

// 若全部留空，则相当于 ["searchSongs"]
"capabilities": []
```

---

## `requiredHostApis` — 所需宿主 API

**类型**：`string[]`  
**必填**：否  
**默认值**：`[]`

声明插件使用的宿主 API 列表。安装时验证 API 是否受支持，不支持的 API 会导致安装失败。

完整的 27 个有效 API 标识符见下表：

<details>
<summary>展开查看全部 27 个 API 标识符</summary>

| API 标识符 | 分类 |
|------------|------|
| `app.info` | app |
| `app.userAgent` | app |
| `runtime.info` | runtime |
| `crypto.md5` | crypto |
| `crypto.aesEcbPkcs5EncryptBase64` | crypto |
| `crypto.aesEcbPkcs5EncryptHex` | crypto |
| `crypto.aesEcbPkcs5DecryptBase64ToText` | crypto |
| `base64.encodeText` | base64 |
| `base64.decodeText` | base64 |
| `base64.dropBytes` | base64 |
| `base64.decodeBytes` | base64 |
| `base64.encodeBytes` | base64 |
| `bytes.xor` | bytes |
| `bytes.xorBase64` | bytes |
| `compression.inflateBytesToText` | compression |
| `compression.inflateBase64ToText` | compression |
| `http.getText` | http |
| `http.postText` | http |
| `http.postBytes` | http |
| `http.get` | http |
| `http.post` | http |
| `http.getBytes` | http |
| `http.postBytesResponse` | http |
| `log.debug` | log |
| `log.warn` | log |
| `log.error` | log |

</details>

```json
"requiredHostApis": [
  "http.getText",
  "http.postText",
  "crypto.aesEcbPkcs5EncryptHex",
  "crypto.aesEcbPkcs5EncryptBase64",
  "crypto.aesEcbPkcs5DecryptBase64ToText"
]
```

---

## `configFields` — 用户配置项

**类型**：`ConfigField[]`  
**必填**：否  
**默认值**：`[]`

定义用户可在插件设置界面中修改的配置项。配置值通过 `request.config` 在函数调用时传递给插件。

### ConfigField 结构

```json
{
  "key": "cover_size",
  "title": "封面大小",
  "summary": "QQ 音乐封面图片尺寸",
  "group": "封面",
  "type": "dropdown",
  "required": true,
  "defaultValue": "1200",
  "options": [
    { "value": "500", "label": "500 x 500" },
    { "value": "800", "label": "800 x 800" },
    { "value": "1200", "label": "1200 x 1200" }
  ],
  "dependency": null
}
```

### ConfigField 字段说明

| 字段 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| `key` | `string` | **是** | - | 配置键名，通过 `request.config[key]` 获取 |
| `title` | `string` | **是** | - | 在设置页面显示的标签 |
| `summary` | `string` | 否 | `""` | 补充说明/提示文本 |
| `group` | `string` | 否 | `""` | 分组名称，同组配置会聚合显示 |
| `type` | `string` | **是** | - | 控件类型 |
| `required` | `boolean` | 否 | `false` | 是否必填 |
| `defaultValue` | `string` | 否 | `""` | 默认值 |
| `options` | `Option[]` | 否 | `[]` | 下拉选项（仅 `dropdown` 类型） |
| `dependency` | `Dependency \| null` | 否 | `null` | 条件可见性 |

### type — 控件类型

| 值 | 说明 |
|----|------|
| `"text"` | 文本输入框 |
| `"password"` | 密码输入框（遮蔽显示） |
| `"number"` | 数字输入框 |
| `"switch"` | 开关（值为 `"true"` 或 `"false"`） |
| `"dropdown"` | 下拉选择框（需提供 `options`） |

### options — 下拉选项

仅 `type` 为 `"dropdown"` 时有效：

```json
{
  "type": "dropdown",
  "options": [
    { "value": "zh-CN", "label": "zh-CN", "summary": "简体中文" },
    { "value": "en-US", "label": "en-US", "summary": "English" }
  ]
}
```

每个选项包含：
- `value`：实际值，传递给插件
- `label`：显示文本
- `summary`（可选，默认为 `""`）：选项的补充说明

### dependency — 条件可见性

通过配置依赖系统控制字段的显示/隐藏。见 [配置与元数据 § 配置依赖](./config-metadata.md#配置依赖-条件可见性)。

---

## `metadataFields` — 元数据声明

**类型**：`MetadataField[]`  
**必填**：否  
**默认值**：`[]`

声明插件可以写入音频文件的元数据字段。插件在 `searchSongs` 返回结果的 `fields` 中包含这些字段的键名。

### MetadataField 结构

```json
{
  "key": "title",
  "title": "歌曲标题",
  "summary": "",
  "group": "基本信息",
  "type": "text",
  "writeable": true,
  "internal": false,
  "defaultTarget": "TITLE",
  "defaultMode": "OVERWRITE",
  "defaultCustomTagKey": "",
  "targetOptions": []
}
```

### MetadataField 字段说明

| 字段 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| `key` | `string` | **是** | - | 对应 `fields` 中的键名 |
| `title` | `string` | **是** | - | 显示标题 |
| `summary` | `string` | 否 | `""` | 补充说明 |
| `group` | `string` | 否 | `"extended"` | 分组名称 |
| `type` | `string` | 否 | `"text"` | 数据类型 |
| `writeable` | `boolean` | 否 | `true` | 是否可写入 |
| `internal` | `boolean` | 否 | `false` | `true` 时隐藏，不展示给用户 |
| `defaultTarget` | `string` | 否 | `"COMMENT"` | 默认写入目标。`"CUSTOM"` 表示自定义标签，可配合 `defaultCustomTagKey` 建议默认键名 |
| `defaultMode` | `string` | 否 | `"DISABLED"` | 默认写入模式 |
| `defaultCustomTagKey` | `string` | 否 | `""` | 当 `defaultTarget` 为 `"CUSTOM"` 时的默认自定义标签键名 |
| `targetOptions` | `string[]` | 否 | `[]` | 限定用户可选的写入目标枚举值列表。取值为下方 `defaultTarget` 枚举列表中的任一值。空数组时所有 22 个目标均可选；指定后仅列出的可选 |

### type — 数据类型

| 值 | 说明 |
|----|------|
| `"text"` | 文本 |
| `"number"` | 数字 |
| `"date"` | 日期 |
| `"lyrics"` | 歌词 |
| `"cover"` | 封面图片 |
| `"binary"` | 二进制数据 |
| `"url"` | URL 链接 |

### defaultTarget — 写入目标

| 值 | 说明 |
|----|------|
| `TITLE` | 歌曲标题 |
| `ARTIST` | 艺术家 |
| `ALBUM` | 专辑 |
| `ALBUM_ARTIST` | 专辑艺术家 |
| `GENRE` | 流派 |
| `DATE` | 发行日期 |
| `TRACK_NUMBER` | 音轨号 |
| `DISC_NUMBER` | 碟片号 |
| `COMPOSER` | 作曲 |
| `LYRICIST` | 作词 |
| `COMMENT` | 注释 |
| `LYRICS` | 歌词 |
| `COVER` | 封面 |
| `LANGUAGE` | 语言 |
| `COPYRIGHT` | 版权 |
| `RATING` | 评分 |
| `REPLAY_GAIN_TRACK_GAIN` | 音轨回放增益 |
| `REPLAY_GAIN_TRACK_PEAK` | 音轨回放峰值 |
| `REPLAY_GAIN_ALBUM_GAIN` | 专辑回放增益 |
| `REPLAY_GAIN_ALBUM_PEAK` | 专辑回放峰值 |
| `REPLAY_GAIN_REFERENCE_LOUDNESS` | 参考响度 |
| `CUSTOM` | 自定义 |

### defaultMode — 写入模式

| 值 | 说明 |
|----|------|
| `"DISABLED"` | 默认不写入 |
| `"SUPPLEMENT"` | 补充模式（当目标为空时写入） |
| `"OVERWRITE"` | 覆盖模式（始终写入） |

---

## 最小示例

一个只支持搜索的极简插件：

```json
{
  "id": "com.example.source",
  "name": "示例源",
  "versionCode": 1,
  "versionName": "1.0.0",
  "apiVersion": 1
}
```

## 完整示例

```json
{
  "id": "com.example.music_source",
  "name": "示例音乐源",
  "versionCode": 1,
  "versionName": "0.1.0",
  "author": "Developer",
  "description": "示例音乐搜索源插件",
  "apiVersion": 1,
  "entry": "source.js",
  "includeDirs": ["lib"],
  "capabilities": ["searchSongs", "getLyrics", "searchCovers"],
  "requiredHostApis": [
    "app.userAgent",
    "runtime.info",
    "http.getText",
    "http.postText",
    "http.get",
    "http.post"
  ],
  "configFields": [
    {
      "key": "lyrics_provider",
      "title": "歌词源",
      "summary": "选择歌词接口来源",
      "group": "歌词",
      "type": "dropdown",
      "required": true,
      "defaultValue": "third_party",
      "options": [
        { "value": "third_party", "label": "第三方" },
        { "value": "official", "label": "官方" }
      ]
    },
    {
      "key": "token",
      "title": "Token",
      "summary": "使用官方歌词源时必填",
      "group": "歌词",
      "type": "password",
      "required": true,
      "defaultValue": "",
      "dependency": { "match": { "key": "lyrics_provider", "value": "official" } }
    }
  ],
  "metadataFields": [
    {
      "key": "title",
      "title": "歌曲标题",
      "group": "基本信息",
      "type": "text",
      "writeable": true,
      "defaultTarget": "TITLE",
      "defaultMode": "OVERWRITE"
    },
    {
      "key": "artist",
      "title": "艺术家",
      "group": "基本信息",
      "type": "text",
      "writeable": true,
      "defaultTarget": "ARTIST",
      "defaultMode": "OVERWRITE"
    },
    {
      "key": "platform_id",
      "title": "平台 ID",
      "group": "内部",
      "type": "text",
      "writeable": false,
      "internal": true,
      "defaultTarget": "CUSTOM",
      "defaultMode": "DISABLED",
      "defaultCustomTagKey": "PLATFORM_ID",
      "targetOptions": ["COMMENT", "CUSTOM"]
    }
  ]
}
```
