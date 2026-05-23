# 6. 配置与元数据

## 用户配置（configFields）

插件可通过 `manifest.json` 中的 `configFields` 定义用户可配置项。这些配置项在插件的设置页面展示，运行时通过 `request.config` 传递给插件函数。

### 配置示例

```json
{
  "configFields": [
    {
      "key": "lyrics_provider",
      "title": "歌词源",
      "summary": "选择歌词数据来源",
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
      "type": "password",
      "required": true,
      "defaultValue": "",
      "dependency": {
        "match": { "key": "lyrics_provider", "value": "official" }
      }
    },
    {
      "key": "cover_size",
      "title": "封面大小",
      "type": "dropdown",
      "required": true,
      "defaultValue": "1200",
      "options": [
        { "value": "500", "label": "500 × 500" },
        { "value": "800", "label": "800 × 800" },
        { "value": "1200", "label": "1200 × 1200" }
      ]
    }
  ]
}
```

### 运行时访问配置

配置值通过 `request.config` 传递给插件函数：

```javascript
function searchSongs(request) {
  var coverSize = request.config.cover_size || "1200";
  var provider = request.config.lyrics_provider || "third_party";
  var token = request.config.token || "";

  Platform.log.debug("Config", "Cover size: " + coverSize);
}
```

`request.config` 是一个键值对 Map，键名对应 `configFields` 中的 `key`，值始终为字符串。

### 控件类型

| 类型 | UI 表现 | 存储值示例 |
|------|---------|-----------|
| `"text"` | 文本输入框 | `"任意字符串"` |
| `"password"` | 密码输入框（遮蔽显示） | `"secret_token"` |
| `"number"` | 数字输入框 | `"42"` |
| `"switch"` | 开关 | `"true"` 或 `"false"` |
| `"dropdown"` | 下拉选择框 | `"option_value"` |

### 开关类型说明

`type: "switch"` 配置的值是字符串 `"true"` 或 `"false"`，JavaScript 中需注意比较：

```javascript
var enabled = request.config.my_switch === "true";
```

---

## 配置依赖（条件可见性）

配置依赖系统控制某个配置字段的显示/隐藏，基于其他字段的值。

### 依赖类型

| 类型 | JSON 结构 | 说明 |
|------|-----------|------|
| `match` | `{ "match": { "key": "K", "value": "V" } }` | 当 `config[K] == V` 时显示 |
| `and` | `{ "and": { "conditions": [...] } }` | 所有条件都满足时显示 |
| `or` | `{ "or": { "conditions": [...] } }` | 任一条件满足时显示 |
| `not` | `{ "not": { "condition": {...} } }` | 条件不满足时显示 |

### 示例：match 依赖

当 `lyrics_provider` 的值为 `"official"` 时，显示 `token` 字段：

```json
{
  "key": "token",
  "title": "Token",
  "type": "password",
  "dependency": {
    "match": { "key": "lyrics_provider", "value": "official" }
  }
}
```

### 示例：复合条件

当 `advanced_mode` 为 `"true"` **且** `source_type` 为 `"external"` 时显示：

```json
{
  "dependency": {
    "and": {
      "conditions": [
        { "match": { "key": "advanced_mode", "value": "true" } },
        { "match": { "key": "source_type", "value": "external" } }
      ]
    }
  }
}
```

### 示例：取反条件

当 `use_proxy` 的值**不等于** `"true"` 时显示：

```json
{
  "dependency": {
    "not": {
      "condition": {
        "match": { "key": "use_proxy", "value": "true" }
      }
    }
  }
}
```

### 完整示例：连锁依赖

```json
{
  "configFields": [
    {
      "key": "lyrics_provider",
      "title": "歌词源",
      "type": "dropdown",
      "options": [
        { "value": "third_party", "label": "第三方" },
        { "value": "official", "label": "官方" },
        { "value": "none", "label": "无" }
      ]
    },
    {
      "key": "token",
      "title": "Token",
      "type": "password",
      "dependency": { "match": { "key": "lyrics_provider", "value": "official" } }
    },
    {
      "key": "third_party_url",
      "title": "第三方 API 地址",
      "type": "text",
      "dependency": { "match": { "key": "lyrics_provider", "value": "third_party" } }
    }
  ]
}
```

效果：
- 选择"官方" → 显示 Token 输入框，隐藏 API 地址
- 选择"第三方" → 显示 API 地址输入框，隐藏 Token
- 选择"无" → 两者都隐藏

---

## 元数据字段（metadataFields）

`metadataFields` 声明插件可以写入音频文件的元数据。各字段的定义和可选值详见 [Manifest 字段参考](./manifest.md) 中的 `metadataFields` 章节。

### 写入流程

1. 插件在 `searchSongs` 返回的 `fields` 中包含键名，键名对应 `metadataFields` 中的 `key`：

```javascript
{
  id: "12345",
  title: "歌曲名",
  fields: {
    title: "歌曲名",
    artist: "歌手",
    album: "专辑名",
    date: "2024-01-01",
    track_number: "3",
    source_platform_key: "encrypted_metadata_string..."
  }
}
```

2. 用户（或系统）根据 `metadataFields` 声明决定将哪些字段写入音频文件。每个字段的 `defaultTarget` 决定默认写入哪个标签，`defaultMode` 决定写入策略：

| `fields` 键 | `defaultTarget` | 效果 |
|-------------|-----------------|------|
| `title` | `TITLE` | 写入歌曲标题标签 |
| `artist` | `ARTIST` | 写入艺术家标签 |
| `album` | `ALBUM` | 写入专辑标签 |
| `date` | `DATE` | 写入发行日期标签 |
| `track_number` | `TRACK_NUMBER` | 写入音轨号标签 |
| `source_platform_key` | `COMMENT` | 写入注释标签（默认不启用） |

### 写入模式

| 模式 | 行为 |
|------|------|
| `DISABLED` | 默认不写入，用户需手动启用 |
| `SUPPLEMENT` | 仅在目标字段为空时写入（不覆盖已有内容） |
| `OVERWRITE` | 始终以插件数据覆盖目标字段 |

### internal 字段

`"internal": true` 的元数据字段在元数据管理界面中不会展示给用户，但系统内部仍可使用。适用于：

- 平台专属 ID（如平台用户 ID、专辑 ID）
- 后续查询需要的持久化密钥
- 不需要用户关注的内部数据

```json
{
  "key": "platform_id",
  "title": "平台 ID",
  "internal": true,
  "defaultTarget": "CUSTOM",
  "defaultMode": "DISABLED",
  "defaultCustomTagKey": "PLATFORM_ID"
}
```

---

## 完整配置示例

以下是一个包含所有配置字段类型和依赖组合的完整示例：

```json
{
  "configFields": [
    {
      "key": "api_type",
      "title": "API 类型",
      "summary": "选择要使用的 API 接口",
      "group": "API",
      "type": "dropdown",
      "required": true,
      "defaultValue": "standard",
      "options": [
        { "value": "standard", "label": "标准接口" },
        { "value": "premium", "label": "高级接口" }
      ]
    },
    {
      "key": "api_key",
      "title": "API Key",
      "summary": "高级接口需要的密钥",
      "group": "API",
      "type": "password",
      "required": false,
      "dependency": {
        "and": {
          "conditions": [
            { "match": { "key": "api_type", "value": "premium" } }
          ]
        }
      }
    },
    {
      "key": "timeout",
      "title": "超时时间（秒）",
      "summary": "HTTP 请求超时时间",
      "group": "网络",
      "type": "number",
      "required": true,
      "defaultValue": "30"
    },
    {
      "key": "enable_cache",
      "title": "启用缓存",
      "group": "性能",
      "type": "switch",
      "defaultValue": "true"
    }
  ]
}
```

对应插件代码中的使用：

```javascript
function searchSongs(request) {
  var cfg = request.config;
  var apiType = cfg.api_type || "standard";
  var apiKey = cfg.api_key || "";
  var timeout = parseInt(cfg.timeout || "30", 10);
  var useCache = cfg.enable_cache === "true";

  if (apiType === "premium" && !apiKey) {
    Platform.log.warn("Plugin", "Premium API selected but no API key provided");
  }

  // 使用配置发起请求...
}
```
