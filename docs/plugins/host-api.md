# 4. 宿主 API 参考

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

## 1. Platform.app — 应用信息

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

## 2. Platform.runtime — 运行时信息

### `runtime.getInfo()`

返回 JS 运行时环境信息。

**参数**：无

**返回值**：

```json
{
  "pluginApiVersion": 1,
  "hostApiVersion": 1,
  "engine": "quickjs",
  "engineVersion": null,
  "supportedHostApis": [
    "app.info", "app.userAgent", "...",
    "http.getText", "...",
    "log.debug", "log.warn", "log.error"
  ]
}
```

**示例**：

```javascript
var rt = Platform.runtime.getInfo();
if (rt.pluginApiVersion !== 1) {
  throw new Error("Unsupported API version");
}
```

---

## 3. Platform.crypto — 加密

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

## 4. Platform.base64 — Base64 编码

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

---

## 5. Platform.bytes — 字节运算

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

## 6. Platform.compression — 压缩

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

## 7. Platform.http — HTTP 请求

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

## 8. Platform.log — 日志

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
| `log.debug(tag, message) \| log.debug(message)` | `tag?, message: string` | `""` |
| `log.warn(tag, message) \| log.warn(message)` | `tag?, message: string` | `""` |
| `log.error(tag, message) \| log.error(message)` | `tag?, message: string` | `""` |
