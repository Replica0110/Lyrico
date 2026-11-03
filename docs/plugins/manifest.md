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
| `i18n` | `object \| null` | 否 | `null` | 默认语言与资源路径映射，见[插件国际化](./i18n) |
| `updateUrl` | `string \| null` | 否 | `null` | 插件自行检查更新所用的绝对 HTTP/HTTPS 目录地址 |

名称、描述和配置界面中的文本都是普通字符串：以 `@` 开头表示引用语言资源里的键，其他内容按字面量显示。规则见[插件国际化](./i18n)。

旧版本中用于声明宿主 API、返回字段或写入策略的字段已经不再需要；新插件不要继续写已废弃的声明。

## 示例

```json
{
  "id": "com.example.source",
  "name": "Example Source",
  "versionCode": 1,
  "versionName": "1.0.0",
  "author": "Plugin Author",
  "description": "Example source plugin",
  "apiVersion": 5,
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

`apiVersion` 用于插件协议兼容检查。当前插件协议版本为 5，可以加载 `apiVersion` 为 1、2、3、4 或 5 的插件，但会拒绝声明更高版本的插件。`apiVersion` 与 `Platform` 宿主 API 版本相互独立；当前宿主 API 版本为 4。各版本的具体变化见 [API 版本沿革](./api-versions.md)。

`minHostApiVersion` 表示插件实际需要的最低宿主 API 版本。它必须大于等于 1 且不高于当前宿主版本；需要具体宿主能力时，插件仍可通过 `Platform.runtime.getInfo().supportedHostApis` 检查，缺失能力会在运行时返回标准化错误。

`capabilities` 支持：

| 能力 | 函数 |
|------|------|
| `searchSongs` | `searchSongs(request)` |
| `getLyrics` | `getLyrics(request)` |
| `searchCovers` | `searchCovers(request)` |

插件只会出现在与其能力匹配的调用场景中，三项能力可以独立声明，`getLyrics` 和
`searchCovers` 不依赖 `searchSongs`。未声明或声明为空数组的旧插件按仅支持
`searchSongs` 处理。

宿主会直接显示插件声明的能力：

| 能力类型 | 声明的能力 |
|----------|----------|
| 元数据源 | 支持 `searchSongs` |
| 歌词源 | 支持 `getLyrics` |
| 封面源 | 支持 `searchCovers` |

能力不是互斥分类：同时具备三项能力的插件会同时显示元数据、歌词和封面标签。
插件管理页只提供元数据源、歌词源和封面源三种来源类型，类型会显示在插件名称下方。元数据源同时用于单曲主搜索和批量元数据匹配。单曲操作分为主搜索、歌词和封面三个入口；批量操作分为元数据、歌词和封面三个任务，
不会在一次任务中跨能力自动拼接。能力组合在安装时写入宿主数据库，
插件升级时会随新的 manifest 一并更新。

`includeDirs` 只能引用插件包内的相对目录。不能使用绝对路径、`..`、网络 URL 或跨插件文件。

## configFields

`configFields` 声明用户可以配置的选项。用户填写的值会在每次调用插件函数时通过 `request.config` 传入。

配置项的 JSON 结构：

| 字段 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| `key` | `string` | 是 | - | 配置键，在 JS 中通过 `request.config[key]` 读取 |
| `title` | `string` | 是 | - | 配置界面中显示的标题 |
| `summary` | `string` | 否 | `null` | 说明文字，显示在输入框或开关下方 |
| `group` | `string` | 否 | `""` | 分组名，同一 `group` 的项在界面中归入一张卡片。为空时归入"基础"组 |
| `type` | `string` | 是 | - | 输入控件类型，决定界面渲染方式 |
| `required` | `boolean` | 否 | `false` | 是否必填，保存时校验非空 |
| `defaultValue` | `string` | 否 | `""` | 默认值，首次加载时作为初始值 |
| `options` | `Option[]` | 否 | `[]` | 选项列表，仅 `dropdown` 类型需要 |
| `dependency` | `Dependency` | 否 | `null` | 条件可见规则，满足条件才在界面显示，见 [依赖系统](#依赖系统-dependency) |

`Option` 结构：

| 字段 | 类型 | 说明 |
|------|------|------|
| `value` | `string` | 选项存入配置的实际值 |
| `label` | `string` | 选项在下拉列表中显示的文本 |
| `summary` | `string` | 选项的额外说明，在下拉列表项中灰色显示 |

### 类型详解

每种 `type` 决定配置项在界面上的表现和交互方式。

#### text — 单行文本

```json
{ "key": "server_url", "title": "服务器地址", "summary": "API 的基础 URL", "type": "text", "required": true, "defaultValue": "https://api.example.com" }
```

#### password — 密码 / 密钥

输入内容被遮挡，适合 API Key、Token、Cookie 等敏感信息。

```json
{ "key": "api_token", "title": "API Token", "summary": "在服务商后台获取", "type": "password", "required": true, "defaultValue": "" }
```

#### number — 数字

限制输入为数字字符，例如超时秒数、页码大小。

```json
{ "key": "timeout", "title": "超时（秒）", "summary": "HTTP 请求超时时间", "type": "number", "defaultValue": "15" }
```

#### switch — 开关

类似布尔值，实际存储为字符串 `"true"` 或 `"false"`。在 JS 中需要自行转换。

```json
{ "key": "use_proxy", "title": "使用代理", "summary": "通过代理服务器发起请求", "type": "switch", "defaultValue": "false" }
```

JS 端读取：`var useProxy = request.config.use_proxy === "true";`

#### dropdown — 下拉选项

```json
{
  "key": "lyrics_source",
  "title": "歌词来源",
  "summary": "优先返回哪一种歌词",
  "type": "dropdown",
  "required": true,
  "defaultValue": "official",
  "options": [
    { "value": "official", "label": "官方歌词", "summary": "由版权方提供的 LRC" },
    { "value": "user", "label": "用户上传歌词", "summary": "用户自行上传的翻译" }
  ]
}
```

#### textarea — 多行文本

适合长 JSON 配置、自定义脚本、多行说明等。

```json
{ "key": "custom_headers", "title": "自定义请求头", "summary": "每行一个 Header，格式：Key: Value", "type": "textarea", "defaultValue": "" }
```

#### markdown — 说明文本

不参与运行时配置，不存入 `request.config`。适合展示使用说明、注意事项、赞助信息等长文本。`defaultValue` 作为 Markdown 正文渲染，`title` 作为标题显示。

```json
{
  "key": "help_note",
  "title": "使用说明",
  "type": "markdown",
  "defaultValue": "### 获取 API Key\n\n1. 注册账号\n2. 进入「API 管理」\n3. 点击「生成 Key」\n\n> 免费用户每天限 1000 次请求"
}
```

### 分组（group）

相同 `group` 的配置项在界面上归入同一张卡片，卡片标题即为组名。不填 `group` 或留空的项默认归入"基础"组。

```json
"configFields": [
  { "key": "api_key", "title": "API Key", "group": "鉴权", "type": "password", "required": true },
  { "key": "api_secret", "title": "API Secret", "group": "鉴权", "type": "password" },
  { "key": "region", "title": "地区", "group": "请求", "type": "dropdown", "defaultValue": "cn", "options": [...] },
  { "key": "timeout", "title": "超时（秒）", "group": "请求", "type": "number", "defaultValue": "15" },
  { "key": "cover_size", "title": "封面尺寸", "group": "封面", "type": "dropdown", "defaultValue": "800", "options": [...] }
]
```

界面中会渲染为三张卡片：**鉴权**（2 项）、**请求**（2 项）、**封面**（1 项）。

### 依赖系统（dependency）

`dependency` 控制配置项的可见性：只有当依赖条件满足时，该配置项才在界面中显示。支持四种子类型：

| 类型 | 说明 |
|------|------|
| `match` | 指定字段等于特定值时可见 |
| `and` | 所有子条件同时满足时可见 |
| `or` | 任一子条件满足时可见 |
| `not` | 条件不满足时可见 |

#### match — 简单匹配

```json
{
  "key": "proxy_url",
  "title": "代理地址",
  "type": "text",
  "dependency": { "match": { "key": "use_proxy", "value": "true" } }
}
```

只有当"使用代理"开关打开时，"代理地址"输入框才会出现。

#### and — 多条件同时满足

```json
{
  "key": "token_url",
  "title": "Token 端点",
  "type": "text",
  "dependency": {
    "and": {
      "conditions": [
        { "match": { "key": "auth_type", "value": "oauth" } },
        { "match": { "key": "custom_server", "value": "true" } }
      ]
    }
  }
}
```

#### or — 多条件任一满足

```json
{
  "key": "proxy_exclude",
  "title": "代理排除域名",
  "type": "text",
  "dependency": {
    "or": {
      "conditions": [
        { "match": { "key": "use_proxy", "value": "true" } },
        { "match": { "key": "use_vpn", "value": "true" } }
      ]
    }
  }
}
```

#### not — 条件取反

```json
{
  "key": "custom_host",
  "title": "自定义 Host",
  "type": "text",
  "dependency": {
    "not": {
      "condition": { "match": { "key": "server_mode", "value": "auto" } }
    }
  }
}
```

只有当 `server_mode` 不是 `"auto"` 时才显示。

### 完整配置示例

将以上能力组合在一起，展示一个完整插件的 `configFields`：

```json
"configFields": [
  {
    "key": "help_intro",
    "title": "欢迎使用",
    "type": "markdown",
    "defaultValue": "此插件对接 **MusicApi** 服务。\n\n请先填写下方的 API Key 再使用。"
  },
  {
    "key": "api_key",
    "title": "API Key",
    "summary": "从 https://example.com/console 获取",
    "group": "鉴权",
    "type": "password",
    "required": true
  },
  {
    "key": "use_custom_host",
    "title": "自定义服务器",
    "group": "鉴权",
    "type": "switch",
    "defaultValue": "false"
  },
  {
    "key": "custom_host",
    "title": "服务器地址",
    "group": "鉴权",
    "type": "text",
    "dependency": { "match": { "key": "use_custom_host", "value": "true" } }
  },
  {
    "key": "lyrics_source",
    "title": "歌词来源",
    "group": "歌词",
    "type": "dropdown",
    "defaultValue": "official",
    "options": [
      { "value": "official", "label": "官方歌词" },
      { "value": "translated", "label": "翻译歌词" }
    ]
  },
  {
    "key": "cover_size",
    "title": "封面尺寸",
    "group": "封面",
    "type": "dropdown",
    "defaultValue": "800",
    "options": [
      { "value": "300", "label": "300 × 300" },
      { "value": "800", "label": "800 × 800" },
      { "value": "1200", "label": "1200 × 1200" }
    ]
  },
  {
    "key": "timeout",
    "title": "超时（秒）",
    "group": "请求",
    "type": "number",
    "defaultValue": "15"
  },
  {
    "key": "help_footer",
    "title": "注意事项",
    "type": "markdown",
    "defaultValue": "> 免费用户每天限 1000 次请求\n> 遇到问题请在 GitHub 提 Issue"
  }
]
```

## 运行时数据返回

插件函数通过 `fields` 和 `internal` 两个 JSON 对象返回搜索数据。`fields` 放宿主标准元数据字段（标题、艺术家等），`internal` 放插件私有上下文（平台 ID、token 等）。

详细说明见 [配置与运行时字段](./config-metadata.md)，包括标准字段完整列表、`internal` 大小约束以及主机写入策略。

## 在线目录与更新

本地 ZIP 导入继续保留。manifest 可声明 `updateUrl`，指向下列格式的目录。在线订阅和单插件更新使用同一格式，只有一个插件时也使用 `plugins` 数组。

```json
{
  "schemaVersion": 1,
  "plugins": [
    {
      "id": "com.example.music",
      "name": "Music",
      "versionCode": 2,
      "versionName": "1.1.0",
      "apiVersion": 5,
      "minHostApiVersion": 4,
      "downloadUrl": "./com.example.music-1.1.0.zip",
      "sha256": "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
      "size": 123456,
      "changelog": "## 1.1.0\n\n- 修复歌曲搜索结果缺失的问题。"
    }
  ]
}
```

插件 ID 发布后保持不变，新旧版本只比较 `versionCode`；`schemaVersion` 表示目录格式版本。下载地址可相对于最终目录响应地址解析，manifest 中的 `updateUrl` 必须使用绝对 HTTP/HTTPS 地址。地址不接受内嵌账号密码或片段。

发布时先上传单插件 ZIP，再根据实际产物计算 `size`（字节）和 `sha256`，最后发布目录。`changelog` 可省略；`minHostApiVersion` 默认为 1，其余示例条目字段必填。下载包 manifest 的 ID、版本号、版本名和 API 要求必须与目录一致。

目录仅提供下载信息，不与已安装插件绑定。安装后以包内 manifest 的 `updateUrl` 为准，移除或修改导入链接不会改变已安装插件。后续包会替换这个地址，省略字段即清除旧地址；找不到匹配 ID 时不会改用其他条目更新。

`changelog` 使用 Markdown，显示在插件更新页。官方插件共用对应 GitHub Release 的正文作为更新日志。
