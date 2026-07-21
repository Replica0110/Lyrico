# 宿主 API 参考

本文是 `globalThis.Platform` 的 API 参考，适合在插件需要访问网络、加密、编码、压缩、XML 处理或日志能力时查阅。

插件通过 `globalThis.Platform` 对象访问宿主暴露的原生能力。该对象在 JS 运行时初始化时由 bootstrap 脚本注入。

## 访问方式

```javascript
// 顶层简写（推荐就近访问时使用）
globalThis.app.getInfo();

// 完整路径
globalThis.Platform.app.getInfo();
globalThis.Platform.http.getText("https://api.example.com");
globalThis.Platform.crypto.md5("hello");
```

简写的 `app` 和 `runtime` 也挂载在 `globalThis` 上：

```javascript
globalThis.app.getInfo();        // 等价于 Platform.app.getInfo()
globalThis.runtime.getInfo();    // 等价于 Platform.runtime.getInfo()
```

---

## Platform.app — 应用信息

### `app.getInfo()`

返回宿主应用元数据。

**参数**：无

**返回值**：

```json
{
  "name": "Lyrico",
  "packageName": "com.lonx.lyrico",
  "versionName": "0.0.0",
  "versionCode": 0,
  "buildType": "unknown",
  "debug": false
}
```

**示例**：

```javascript
var info = Platform.app.getInfo();
Platform.log.debug("App", "Running in " + info.name + " v" + info.versionName);
```

### `app.getUserAgent()`

返回 Lyrico 默认 User-Agent 字符串。

**参数**：无

**返回值**：`"Lyrico/<versionName>"`

**示例**：

```javascript
var ua = Platform.app.getUserAgent();  // "Lyrico/0.0.0"
```

---

## Platform.runtime — 运行时信息

### `runtime.getInfo()`

返回 JS 运行时环境信息。

**参数**：无

**返回值**：

```json
{
  "pluginApiVersion": 3,
  "hostApiVersion": 3,
  "engine": "quickjs",
  "engineVersion": null,
  "supportedHostApis": [
    "app.info", "app.userAgent", "...",
    "base64.encodeUrlText", "base64.decodeUrlText", "...",
    "http.getText", "...",
    "xml.getRootAttributes", "xml.findElements", "...",
    "log.debug", "log.warn", "log.error"
  ]
}
```

**示例**：

```javascript
var rt = Platform.runtime.getInfo();
if (rt.hostApiVersion < 3) {
  throw new Error("Host API 3 or later is required");
}
```

---

## Platform.cache — 插件缓存

缓存按插件隔离，适合保存有有效期的 cookie、匿名登录态、临时 token 等字符串数据。缓存项过期后 `get()` 返回空字符串。

### `cache.get(key)`

读取缓存字符串。

```javascript
var token = Platform.cache.get("apple.webplay.developer_token");
```

### `cache.set(key, value, ttlMs)`

写入缓存字符串。`ttlMs` 为有效期毫秒数；传 `0` 或省略表示不过期。

```javascript
Platform.cache.set("session.cookies", JSON.stringify(cookies), 12 * 60 * 60 * 1000);
```

### `cache.remove(key)`

删除单个缓存项。

```javascript
Platform.cache.remove("session.cookies");
```

### `cache.clear()`

清空当前插件的全部缓存。

```javascript
Platform.cache.clear();
```

---

## Platform.crypto — 加密

### `crypto.md5(text)`

计算 MD5 哈希。

| 参数 | 类型 | 说明 |
|------|------|------|
| `text` | `string` | 输入文本 |

**返回值**：32 位十六进制字符串（小写）

```javascript
var hash = Platform.crypto.md5("hello");
// "5d41402abc4b2a76b9719d911017c592"
```

### `crypto.aesEcbPkcs5EncryptBase64(text, key)`

AES-ECB-PKCS5Padding 加密，输出 Base64。

| 参数 | 类型 | 说明 |
|------|------|------|
| `text` | `string` | 明文 |
| `key` | `string` | 密钥（UTF-8 编码后作为 AES 密钥） |

```javascript
var enc = Platform.crypto.aesEcbPkcs5EncryptBase64("plaintext", "mysecretkey12345");
```

### `crypto.aesEcbPkcs5EncryptHex(text, key)`

AES-ECB-PKCS5Padding 加密，输出十六进制大写字符串。

| 参数 | 类型 | 说明 |
|------|------|------|
| `text` | `string` | 明文 |
| `key` | `string` | 密钥 |

```javascript
var enc = Platform.crypto.aesEcbPkcs5EncryptHex("plaintext", "mysecretkey12345");
```

### `crypto.aesEcbPkcs5DecryptBase64ToText(base64, key)`

AES-ECB-PKCS5Padding 解密 Base64 密文。

| 参数 | 类型 | 说明 |
|------|------|------|
| `base64` | `string` | Base64 密文 |
| `key` | `string` | 密钥 |

```javascript
var plain = Platform.crypto.aesEcbPkcs5DecryptBase64ToText(encryptedBase64, "mysecretkey12345");
```

---

## Platform.base64 — Base64 编码

### `base64.encodeText(text)`

UTF-8 文本 → Base64 编码。

```javascript
var b64 = Platform.base64.encodeText("Hello World");
```

### `base64.decodeText(base64)`

Base64 解码 → UTF-8 文本。

```javascript
var text = Platform.base64.decodeText("SGVsbG8gV29ybGQ=");
// "Hello World"
```

### `base64.dropBytes(base64, count)`

丢弃 Base64 解码后数据的前 N 字节，重新 Base64 编码。

```javascript
var truncated = Platform.base64.dropBytes(encodedData, 4);
```

### `base64.decodeBytes(base64)`

Base64 解码 → 字节数组。

```javascript
var bytes = Platform.base64.decodeBytes("AQIDBA==");
// 返回 [1, 2, 3, 4]
```

### `base64.encodeBytes(bytes)`

字节数组 → Base64 编码。

```javascript
var b64 = Platform.base64.encodeBytes([1, 2, 3, 4]);
// "AQIDBA=="
```

### `base64.encodeUrlText(text)`

UTF-8 文本 → Base64URL 编码，不带 padding。

```javascript
var b64url = Platform.base64.encodeUrlText("Hello World?");
// "SGVsbG8gV29ybGQ_"
```

### `base64.decodeUrlText(base64Url)`

Base64URL 解码 → UTF-8 文本。输入可以带 padding，也可以不带 padding。

```javascript
var text = Platform.base64.decodeUrlText("SGVsbG8gV29ybGQ_");
// "Hello World?"
```

### `base64.encodeUrlBytes(bytes)`

字节数组 → Base64URL 编码，不带 padding。

```javascript
var b64url = Platform.base64.encodeUrlBytes([251, 255, 254]);
// "-__-"
```

### `base64.decodeUrlBytes(base64Url)`

Base64URL 解码 → 字节数组。输入可以带 padding，也可以不带 padding。

```javascript
var bytes = Platform.base64.decodeUrlBytes("-__-");
// 返回 [251, 255, 254]
```

### `base64.toUrl(base64)`

标准 Base64 → Base64URL，并移除末尾 padding。

```javascript
var b64url = Platform.base64.toUrl("+//+");
// "-__-"
```

### `base64.fromUrl(base64Url)`

Base64URL → 标准 Base64，自动补齐 padding。

```javascript
var b64 = Platform.base64.fromUrl("SGVsbG8gV29ybGQ_");
// "SGVsbG8gV29ybGQ/"
```

---

## Platform.bytes — 字节运算

### `bytes.xor(bytes, key)`

字节数组与密钥进行 XOR 运算（密钥循环使用）。

```javascript
var result = Platform.bytes.xor([0x12, 0x34], [0x56, 0x78]);
```

### `bytes.xorBase64(base64, key)`

Base64 解码后的数据与密钥 XOR，结果再 Base64 编码。

```javascript
var result = Platform.bytes.xorBase64("base64data", [0x01, 0x02, 0x03]);
```

---

## Platform.compression — 压缩

### `compression.inflateBytesToText(bytes)`

zlib inflate 解压 → UTF-8 文本。

```javascript
var uncompressed = Platform.compression.inflateBytesToText(compressedBytes);
```

### `compression.inflateBase64ToText(base64)`

Base64 解码后 zlib inflate 解压 → UTF-8 文本。

```javascript
var uncompressed = Platform.compression.inflateBase64ToText(compressedBase64);
```

---

## Platform.http — HTTP 请求

HTTP 模块提供**旧 API**（仅返回 body）和**新 API**（返回完整响应对象）两套接口。

### HTTP 选项对象

所有 HTTP 方法接受一个 options 对象：

```json
{
  "headers": { "Content-Type": "application/json" },
  "contentType": "application/json; charset=utf-8",
  "connectTimeoutMs": 8000,
  "readTimeoutMs": 12000,
  "followRedirects": true,
  "bodyBase64": "",
  "bodyBytes": null
}
```

| 选项 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `headers` | `object` | `{}` | 请求头键值对 |
| `contentType` | `string` | 文本：`application/json; charset=utf-8`；二进制：`application/octet-stream` | Content-Type |
| `connectTimeoutMs` | `int` | `8000` | 连接超时（毫秒） |
| `readTimeoutMs` | `int` | `12000` | 读取超时（毫秒） |
| `followRedirects` | `bool` | `true` | 是否跟随重定向 |
| `bodyBase64` | `string` | `""` | Base64 编码的请求体（POST/PUT 时使用） |
| `bodyBytes` | `int[]` | `null` | 字节数组请求体（POST/PUT 时使用） |

- 若未显式设置 `User-Agent` 请求头，自动添加 Lyrico 默认 User-Agent
- `body`（字符串）、`bodyBase64`、`bodyBytes` 三选一，优先级：`bodyBase64` > `bodyBytes` > `body`

---

### 旧 API（仅返回 body 字符串）

#### `http.getText(url, options?)`

GET 请求，返回响应体文本。

```javascript
var html = Platform.http.getText("https://example.com/api/search?q=test");
```

#### `http.postText(url, body, options?)`

POST 请求，返回响应体文本。

```javascript
var result = Platform.http.postText(
  "https://api.example.com/data",
  JSON.stringify({ key: "value" }),
  {
    headers: { "X-API-Key": "secret" },
    contentType: "application/json; charset=utf-8"
  }
);
```

#### `http.postBytes(url, body, options?)`

POST 请求，返回响应体的 Base64 编码（适用于二进制响应）。

```javascript
var base64Body = Platform.http.postBytes(
  "https://api.example.com/binary",
  bodyString,
  { contentType: "application/octet-stream" }
);
```

---

### 新 API（返回完整响应对象）

新 API 返回包含状态码、响应头和响应体的完整对象。

**文本响应格式**（`get`、`post`）：

```json
{
  "code": 200,
  "message": "OK",
  "headers": {
    "Content-Type": ["application/json"],
    "Set-Cookie": ["session=abc123; Path=/"]
  },
  "body": "{...}",
  "bodyBase64": ""
}
```

**二进制响应格式**（`getBytes`、`postBytesResponse`）：

```json
{
  "code": 200,
  "message": "OK",
  "headers": {
    "Content-Type": ["image/png"]
  },
  "body": "",
  "bodyBase64": "iVBORw0KGgo..."
}
```

#### `http.get(url, options?)`

GET 请求，返回完整文本响应。

```javascript
var res = Platform.http.get("https://api.example.com/data", {
  headers: { "Accept": "application/json" }
});
if (res.code === 200) {
  var data = JSON.parse(res.body);
}
// 可获取响应中的 Set-Cookie
var cookies = res.headers["Set-Cookie"] || [];
```

#### `http.post(url, body, options?)`

POST 请求，返回完整文本响应。

```javascript
var res = Platform.http.post(
  "https://api.example.com/submit",
  JSON.stringify({ data: "value" }),
  { headers: { "X-Token": token } }
);
```

#### `http.getBytes(url, options?)`

GET 请求，返回完整二进制响应（body 在 `bodyBase64` 字段）。

```javascript
var res = Platform.http.getBytes("https://example.com/image.png");
var imageBytes = Platform.base64.decodeBytes(res.bodyBase64);
```

#### `http.postBytesResponse(url, body, options?)`

POST 请求，返回完整二进制响应。

支持三种请求体格式：
- `body`（字符串）
- `options.bodyBase64`（Base64 编码）
- `options.bodyBytes`（字节数组）

```javascript
var res = Platform.http.postBytesResponse(
  "https://api.example.com/upload",
  "string body",
  {
    contentType: "application/octet-stream",
    bodyBase64: base64Data
  }
);
var responseBytes = Platform.base64.decodeBytes(res.bodyBase64);
```

---

## Platform.xml — XML 处理

XML 模块提供轻量级 XML/TTML 查询和改写能力，适合处理歌词源返回的 TTML、带命名空间前缀属性的 `translation` 节点等场景。

这些方法会由宿主解析并重新序列化 XML。输出会保留元素、文本和属性，但不保证原始缩进、注释、处理指令或字节级格式完全不变。属性名中的前缀会作为名称的一部分保留，例如 `xml:lang`。

### XML 查询对象

`findElements` 和 `removeElements` 使用相同的查询对象：

```json
{
  "tag": "translation",
  "attrs": {
    "xml:lang": "zh-Hans"
  }
}
```

| 字段 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `tag` | `string` | `""` | 元素标签名；为空时匹配任意元素 |
| `attrs` | `object` | `{}` | 属性精确匹配条件，所有属性都匹配才命中 |

### XML 元素对象

`findElements` 返回的每个元素形如：

```json
{
  "tag": "translation",
  "attrs": {
    "xml:lang": "zh-Hans",
    "type": "replacement"
  },
  "text": "纯文本内容",
  "innerXml": "<text for=\"l1\">...</text>",
  "children": []
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| `tag` | `string` | 元素标签名 |
| `attrs` | `object` | 元素属性 |
| `text` | `string` | 递归合并后的纯文本内容 |
| `innerXml` | `string` | 子节点重新序列化后的 XML |
| `children` | `object[]` | 子元素列表，不包含纯文本节点 |

### `xml.getRootAttributes(xml)`

解析 XML 并返回根元素属性。

| 参数 | 类型 | 说明 |
|------|------|------|
| `xml` | `string` | XML 文本 |

**返回值**：根元素属性对象；无根元素时返回 `{}`。

```javascript
var attrs = Platform.xml.getRootAttributes(ttml);
var language = attrs["xml:lang"] || attrs.lang || "";
```

### `xml.findElements(xml, query)`

递归查找匹配的元素。

| 参数 | 类型 | 说明 |
|------|------|------|
| `xml` | `string` | XML 文本 |
| `query` | `object` | XML 查询对象 |

**返回值**：XML 元素对象数组。

```javascript
var translations = Platform.xml.findElements(ttml, {
  tag: "translation",
  attrs: {
    "xml:lang": "zh-Hans"
  }
});

var first = translations[0];
var innerXml = first ? first.innerXml : "";
```

### `xml.replaceChildrenByAttr(xml, options)`

查找指定标签，并按某个属性值替换其全部子节点。常用于把翻译文本按 `itunes:key` 写回 TTML 的 `<p>` 节点。

```json
{
  "targetTag": "p",
  "keyAttr": "itunes:key",
  "replacements": {
    "line-1": {
      "mode": "text",
      "value": "替换后的文本"
    },
    "line-2": {
      "mode": "xml",
      "value": "<span begin=\"0s\">替换后的 XML 片段</span>"
    }
  },
  "rootAttributes": {
    "xml:lang": "zh-Hans"
  }
}
```

| 选项 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `targetTag` | `string` | `""` | 要替换子节点的元素标签名 |
| `keyAttr` | `string` | `""` | 用于查找替换项的属性名 |
| `replacements` | `object` | `{}` | `keyAttr` 属性值到替换内容的映射 |
| `rootAttributes` | `object` | `{}` | 同时写入根元素的属性 |

`replacements` 中每个替换项支持：

| 字段 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `mode` | `"text"` 或 `"xml"` | `"text"` | `text` 写入纯文本；`xml` 将 `value` 作为 XML 片段解析 |
| `value` | `string` | `""` | 替换内容 |

当 `targetTag` 或 `keyAttr` 为空时，方法直接返回原 XML。

```javascript
var localized = Platform.xml.replaceChildrenByAttr(ttml, {
  targetTag: "p",
  keyAttr: "itunes:key",
  replacements: {
    "l1": {
      mode: "text",
      value: "第一行歌词"
    },
    "l2": {
      mode: "xml",
      value: "<span begin=\"1s\">第二行歌词</span>"
    }
  },
  rootAttributes: {
    "xml:lang": "zh-Hans"
  }
});
```

### `xml.removeElements(xml, query)`

递归删除匹配的子元素，并返回改写后的 XML。根元素本身不会被删除。

| 参数 | 类型 | 说明 |
|------|------|------|
| `xml` | `string` | XML 文本 |
| `query` | `object` | XML 查询对象 |

```javascript
var cleaned = Platform.xml.removeElements(ttml, {
  tag: "translation",
  attrs: {
    "xml:lang": "zh-Hans",
    type: "replacement"
  }
});
```

---

## Platform.log — 日志

日志输出到 Android Logcat，tag 最多截取 48 字符。

### `log.debug(tag, message)`

或 `log.debug(message)`（tag 默认为 `"PlatformPlugin"`）。

```javascript
Platform.log.debug("Search", "Searching for: " + keyword);

// 单参数形式
Platform.log.debug("Simple debug message");
```

### `log.warn(tag, message)`

```javascript
Platform.log.warn("NE", "EAPI search failed: " + err.message);
```

### `log.error(tag, message)`

```javascript
Platform.log.error("Plugin", "Fatal error: " + err);
```

---

## API 速查表

| API | 参数 | 返回值类型 |
|-----|------|-----------|
| `app.getInfo()` | 无 | `object` |
| `app.getUserAgent()` | 无 | `string` |
| `runtime.getInfo()` | 无 | `object` |
| `crypto.md5(text)` | `text: string` | `string`（32 位 hex） |
| `crypto.aesEcbPkcs5EncryptBase64(text, key)` | `text, key: string` | `string`（Base64） |
| `crypto.aesEcbPkcs5EncryptHex(text, key)` | `text, key: string` | `string`（hex 大写） |
| `crypto.aesEcbPkcs5DecryptBase64ToText(base64, key)` | `base64, key: string` | `string` |
| `base64.encodeText(text)` | `text: string` | `string`（Base64） |
| `base64.decodeText(base64)` | `base64: string` | `string` |
| `base64.dropBytes(base64, count)` | `base64: string, count: int` | `string`（Base64） |
| `base64.decodeBytes(base64)` | `base64: string` | `int[]` |
| `base64.encodeBytes(bytes)` | `bytes: int[]` | `string`（Base64） |
| `base64.encodeUrlText(text)` | `text: string` | `string`（Base64URL，无 padding） |
| `base64.decodeUrlText(base64Url)` | `base64Url: string` | `string` |
| `base64.encodeUrlBytes(bytes)` | `bytes: int[]` | `string`（Base64URL，无 padding） |
| `base64.decodeUrlBytes(base64Url)` | `base64Url: string` | `int[]` |
| `base64.toUrl(base64)` | `base64: string` | `string`（Base64URL，无 padding） |
| `base64.fromUrl(base64Url)` | `base64Url: string` | `string`（Base64，补齐 padding） |
| `bytes.xor(bytes, key)` | `bytes, key: int[]` | `int[]` |
| `bytes.xorBase64(base64, key)` | `base64: string, key: int[]` | `string`（Base64） |
| `compression.inflateBytesToText(bytes)` | `bytes: int[]` | `string` |
| `compression.inflateBase64ToText(base64)` | `base64: string` | `string` |
| `http.getText(url, options?)` | `url: string, options?: object` | `string` |
| `http.postText(url, body, options?)` | `url, body: string, options?: object` | `string` |
| `http.postBytes(url, body, options?)` | `url, body: string, options?: object` | `string`（Base64） |
| `http.get(url, options?)` | `url: string, options?: object` | `object`（含 code/headers/body） |
| `http.post(url, body, options?)` | `url, body: string, options?: object` | `object`（含 code/headers/body） |
| `http.getBytes(url, options?)` | `url: string, options?: object` | `object`（body 在 bodyBase64 中） |
| `http.postBytesResponse(url, body, options?)` | `url, body: string, options?: object` | `object`（body 在 bodyBase64 中） |
| `xml.getRootAttributes(xml)` | `xml: string` | `object` |
| `xml.findElements(xml, query)` | `xml: string, query: object` | `object[]` |
| `xml.replaceChildrenByAttr(xml, options)` | `xml: string, options: object` | `string` |
| `xml.removeElements(xml, query)` | `xml: string, query: object` | `string` |
| `log.debug(tag, message) \| log.debug(message)` | `tag?, message: string` | `""` |
| `log.warn(tag, message) \| log.warn(message)` | `tag?, message: string` | `""` |
| `log.error(tag, message) \| log.error(message)` | `tag?, message: string` | `""` |
