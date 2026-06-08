# Host API Reference

This page is the API reference for `globalThis.Platform`. Use it when a plugin needs host capabilities such as network access, crypto, encoding, compression, XML processing, or logging.

Plugins access native host capabilities through `globalThis.Platform`. The object is injected by the bootstrap script when the JS runtime is initialized.

## Access

```javascript
// Top-level shorthand, recommended for nearby access
globalThis.app.getInfo();

// Full path
globalThis.Platform.app.getInfo();
globalThis.Platform.http.getText("https://api.example.com");
globalThis.Platform.crypto.md5("hello");
```

The `app` and `runtime` shorthands are also mounted on `globalThis`:

```javascript
globalThis.app.getInfo();        // Same as Platform.app.getInfo()
globalThis.runtime.getInfo();    // Same as Platform.runtime.getInfo()
```

---

## Platform.app — App Information

### `app.getInfo()`

Returns host app metadata.

**Parameters**: none

**Return value**:

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

**Example**:

```javascript
var info = Platform.app.getInfo();
Platform.log.debug("App", "Running in " + info.name + " v" + info.versionName);
```

### `app.getUserAgent()`

Returns Lyrico's default User-Agent string.

**Parameters**: none

**Return value**: `"Lyrico/<versionName>"`

**Example**:

```javascript
var ua = Platform.app.getUserAgent();  // "Lyrico/0.0.0"
```

---

## Platform.runtime — Runtime Information

### `runtime.getInfo()`

Returns JS runtime environment information.

**Parameters**: none

**Return value**:

```json
{
  "pluginApiVersion": 1,
  "hostApiVersion": 1,
  "engine": "quickjs",
  "engineVersion": null,
  "supportedHostApis": [
    "app.info", "app.userAgent", "...",
    "http.getText", "...",
    "xml.getRootAttributes", "xml.findElements", "...",
    "log.debug", "log.warn", "log.error"
  ]
}
```

**Example**:

```javascript
var rt = Platform.runtime.getInfo();
if (rt.pluginApiVersion !== 1) {
  throw new Error("Unsupported API version");
}
```

---

## Platform.crypto — Crypto

### `crypto.md5(text)`

Calculates an MD5 hash.

| Parameter | Type | Description |
|-----------|------|-------------|
| `text` | `string` | Input text |

**Return value**: 32-character lowercase hexadecimal string

```javascript
var hash = Platform.crypto.md5("hello");
// "5d41402abc4b2a76b9719d911017c592"
```

### `crypto.aesEcbPkcs5EncryptBase64(text, key)`

Encrypts with AES-ECB-PKCS5Padding and returns Base64.

| Parameter | Type | Description |
|-----------|------|-------------|
| `text` | `string` | Plain text |
| `key` | `string` | Key, UTF-8 encoded as the AES key |

```javascript
var enc = Platform.crypto.aesEcbPkcs5EncryptBase64("plaintext", "mysecretkey12345");
```

### `crypto.aesEcbPkcs5EncryptHex(text, key)`

Encrypts with AES-ECB-PKCS5Padding and returns uppercase hexadecimal text.

| Parameter | Type | Description |
|-----------|------|-------------|
| `text` | `string` | Plain text |
| `key` | `string` | Key |

```javascript
var enc = Platform.crypto.aesEcbPkcs5EncryptHex("plaintext", "mysecretkey12345");
```

### `crypto.aesEcbPkcs5DecryptBase64ToText(base64, key)`

Decrypts Base64 ciphertext with AES-ECB-PKCS5Padding.

| Parameter | Type | Description |
|-----------|------|-------------|
| `base64` | `string` | Base64 ciphertext |
| `key` | `string` | Key |

```javascript
var plain = Platform.crypto.aesEcbPkcs5DecryptBase64ToText(encryptedBase64, "mysecretkey12345");
```

---

## Platform.base64 — Base64

### `base64.encodeText(text)`

UTF-8 text → Base64.

```javascript
var b64 = Platform.base64.encodeText("Hello World");
```

### `base64.decodeText(base64)`

Base64 → UTF-8 text.

```javascript
var text = Platform.base64.decodeText("SGVsbG8gV29ybGQ=");
// "Hello World"
```

### `base64.dropBytes(base64, count)`

Drops the first N bytes from the decoded Base64 data, then encodes the result as Base64 again.

```javascript
var truncated = Platform.base64.dropBytes(encodedData, 4);
```

### `base64.decodeBytes(base64)`

Base64 → byte array.

```javascript
var bytes = Platform.base64.decodeBytes("AQIDBA==");
// Returns [1, 2, 3, 4]
```

### `base64.encodeBytes(bytes)`

Byte array → Base64.

```javascript
var b64 = Platform.base64.encodeBytes([1, 2, 3, 4]);
// "AQIDBA=="
```

---

## Platform.bytes — Byte Operations

### `bytes.xor(bytes, key)`

Runs XOR between a byte array and a key. The key repeats as needed.

```javascript
var result = Platform.bytes.xor([0x12, 0x34], [0x56, 0x78]);
```

### `bytes.xorBase64(base64, key)`

Decodes Base64, XORs the bytes with a key, then encodes the result as Base64.

```javascript
var result = Platform.bytes.xorBase64("base64data", [0x01, 0x02, 0x03]);
```

---

## Platform.compression — Compression

### `compression.inflateBytesToText(bytes)`

zlib inflate → UTF-8 text.

```javascript
var uncompressed = Platform.compression.inflateBytesToText(compressedBytes);
```

### `compression.inflateBase64ToText(base64)`

Base64 decode, then zlib inflate → UTF-8 text.

```javascript
var uncompressed = Platform.compression.inflateBase64ToText(compressedBase64);
```

---

## Platform.http — HTTP Requests

The HTTP module provides **old APIs** that return only the body and **new APIs** that return a full response object.

### HTTP Options Object

All HTTP methods accept an options object:

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

| Option | Type | Default | Description |
|--------|------|---------|-------------|
| `headers` | `object` | `{}` | Request headers |
| `contentType` | `string` | Text: `application/json; charset=utf-8`; binary: `application/octet-stream` | Content-Type |
| `connectTimeoutMs` | `int` | `8000` | Connect timeout in milliseconds |
| `readTimeoutMs` | `int` | `12000` | Read timeout in milliseconds |
| `followRedirects` | `bool` | `true` | Whether redirects are followed |
| `bodyBase64` | `string` | `""` | Base64-encoded request body for POST/PUT |
| `bodyBytes` | `int[]` | `null` | Byte-array request body for POST/PUT |

- If `User-Agent` is not explicitly set, Lyrico automatically adds its default User-Agent
- `body` string, `bodyBase64`, and `bodyBytes` are mutually exclusive; priority is `bodyBase64` > `bodyBytes` > `body`

---

### Old APIs, Body String Only

#### `http.getText(url, options?)`

Sends a GET request and returns response body text.

```javascript
var html = Platform.http.getText("https://example.com/api/search?q=test");
```

#### `http.postText(url, body, options?)`

Sends a POST request and returns response body text.

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

Sends a POST request and returns the response body as Base64, useful for binary responses.

```javascript
var base64Body = Platform.http.postBytes(
  "https://api.example.com/binary",
  bodyString,
  { contentType: "application/octet-stream" }
);
```

---

### New APIs, Full Response Object

The new APIs return a full object with status code, headers, and body.

**Text response format** (`get`, `post`):

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

**Binary response format** (`getBytes`, `postBytesResponse`):

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

Sends a GET request and returns a full text response.

```javascript
var res = Platform.http.get("https://api.example.com/data", {
  headers: { "Accept": "application/json" }
});
if (res.code === 200) {
  var data = JSON.parse(res.body);
}
// Set-Cookie is available from response headers
var cookies = res.headers["Set-Cookie"] || [];
```

#### `http.post(url, body, options?)`

Sends a POST request and returns a full text response.

```javascript
var res = Platform.http.post(
  "https://api.example.com/submit",
  JSON.stringify({ data: "value" }),
  { headers: { "X-Token": token } }
);
```

#### `http.getBytes(url, options?)`

Sends a GET request and returns a full binary response. The body is stored in `bodyBase64`.

```javascript
var res = Platform.http.getBytes("https://example.com/image.png");
var imageBytes = Platform.base64.decodeBytes(res.bodyBase64);
```

#### `http.postBytesResponse(url, body, options?)`

Sends a POST request and returns a full binary response.

Three request body formats are supported:

- `body` string
- `options.bodyBase64`, Base64-encoded
- `options.bodyBytes`, byte array

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

## Platform.xml — XML Processing

The XML module provides lightweight XML/TTML lookup and rewriting, useful for TTML returned by lyrics sources and `translation` nodes with prefixed attributes.

These methods parse and re-serialize XML through the host. Output preserves elements, text, and attributes, but original indentation, comments, processing instructions, and byte-level formatting are not guaranteed. Attribute prefixes are preserved as part of the name, for example `xml:lang`.

### XML Query Object

`findElements` and `removeElements` use the same query object:

```json
{
  "tag": "translation",
  "attrs": {
    "xml:lang": "zh-Hans"
  }
}
```

| Field | Type | Default | Description |
|------|------|---------|-------------|
| `tag` | `string` | `""` | Element tag name; empty matches any element |
| `attrs` | `object` | `{}` | Exact-match attributes; all listed attributes must match |

### XML Element Object

Each element returned by `findElements` has this shape:

```json
{
  "tag": "translation",
  "attrs": {
    "xml:lang": "zh-Hans",
    "type": "replacement"
  },
  "text": "plain text content",
  "innerXml": "<text for=\"l1\">...</text>",
  "children": []
}
```

| Field | Type | Description |
|------|------|-------------|
| `tag` | `string` | Element tag name |
| `attrs` | `object` | Element attributes |
| `text` | `string` | Recursively combined plain text |
| `innerXml` | `string` | Re-serialized XML of child nodes |
| `children` | `object[]` | Child elements, excluding text nodes |

### `xml.getRootAttributes(xml)`

Parses XML and returns root element attributes.

| Parameter | Type | Description |
|-----------|------|-------------|
| `xml` | `string` | XML text |

**Return value**: root element attributes; `{}` when no root element exists.

```javascript
var attrs = Platform.xml.getRootAttributes(ttml);
var language = attrs["xml:lang"] || attrs.lang || "";
```

### `xml.findElements(xml, query)`

Recursively finds matching elements.

| Parameter | Type | Description |
|-----------|------|-------------|
| `xml` | `string` | XML text |
| `query` | `object` | XML query object |

**Return value**: array of XML element objects.

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

Finds elements by tag and replaces all child nodes based on one attribute value. This is useful for writing translated text back to TTML `<p>` nodes by `itunes:key`.

```json
{
  "targetTag": "p",
  "keyAttr": "itunes:key",
  "replacements": {
    "line-1": {
      "mode": "text",
      "value": "Replacement text"
    },
    "line-2": {
      "mode": "xml",
      "value": "<span begin=\"0s\">Replacement XML fragment</span>"
    }
  },
  "rootAttributes": {
    "xml:lang": "zh-Hans"
  }
}
```

| Option | Type | Default | Description |
|--------|------|---------|-------------|
| `targetTag` | `string` | `""` | Tag name of elements whose children should be replaced |
| `keyAttr` | `string` | `""` | Attribute name used to find replacement entries |
| `replacements` | `object` | `{}` | Mapping from `keyAttr` value to replacement content |
| `rootAttributes` | `object` | `{}` | Attributes to write to the root element as part of the operation |

Each replacement item supports:

| Field | Type | Default | Description |
|------|------|---------|-------------|
| `mode` | `"text"` or `"xml"` | `"text"` | `text` writes plain text; `xml` parses `value` as an XML fragment |
| `value` | `string` | `""` | Replacement content |

If `targetTag` or `keyAttr` is empty, the original XML is returned.

```javascript
var localized = Platform.xml.replaceChildrenByAttr(ttml, {
  targetTag: "p",
  keyAttr: "itunes:key",
  replacements: {
    "l1": {
      mode: "text",
      value: "First lyric line"
    },
    "l2": {
      mode: "xml",
      value: "<span begin=\"1s\">Second lyric line</span>"
    }
  },
  rootAttributes: {
    "xml:lang": "zh-Hans"
  }
});
```

### `xml.removeElements(xml, query)`

Recursively removes matching child elements and returns the rewritten XML. The root element itself is not removed.

| Parameter | Type | Description |
|-----------|------|-------------|
| `xml` | `string` | XML text |
| `query` | `object` | XML query object |

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

## Platform.log — Logging

Logs are written to Android Logcat. Tags are truncated to at most 48 characters.

### `log.debug(tag, message)`

Or `log.debug(message)`, where the tag defaults to `"PlatformPlugin"`.

```javascript
Platform.log.debug("Search", "Searching for: " + keyword);

// Single-parameter form
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

## API Quick Reference

| API | Parameters | Return type |
|-----|------------|-------------|
| `app.getInfo()` | none | `object` |
| `app.getUserAgent()` | none | `string` |
| `runtime.getInfo()` | none | `object` |
| `crypto.md5(text)` | `text: string` | `string`, 32-character hex |
| `crypto.aesEcbPkcs5EncryptBase64(text, key)` | `text, key: string` | `string`, Base64 |
| `crypto.aesEcbPkcs5EncryptHex(text, key)` | `text, key: string` | `string`, uppercase hex |
| `crypto.aesEcbPkcs5DecryptBase64ToText(base64, key)` | `base64, key: string` | `string` |
| `base64.encodeText(text)` | `text: string` | `string`, Base64 |
| `base64.decodeText(base64)` | `base64: string` | `string` |
| `base64.dropBytes(base64, count)` | `base64: string, count: int` | `string`, Base64 |
| `base64.decodeBytes(base64)` | `base64: string` | `int[]` |
| `base64.encodeBytes(bytes)` | `bytes: int[]` | `string`, Base64 |
| `bytes.xor(bytes, key)` | `bytes, key: int[]` | `int[]` |
| `bytes.xorBase64(base64, key)` | `base64: string, key: int[]` | `string`, Base64 |
| `compression.inflateBytesToText(bytes)` | `bytes: int[]` | `string` |
| `compression.inflateBase64ToText(base64)` | `base64: string` | `string` |
| `http.getText(url, options?)` | `url: string, options?: object` | `string` |
| `http.postText(url, body, options?)` | `url, body: string, options?: object` | `string` |
| `http.postBytes(url, body, options?)` | `url, body: string, options?: object` | `string`, Base64 |
| `http.get(url, options?)` | `url: string, options?: object` | `object`, with code/headers/body |
| `http.post(url, body, options?)` | `url, body: string, options?: object` | `object`, with code/headers/body |
| `http.getBytes(url, options?)` | `url: string, options?: object` | `object`, body in bodyBase64 |
| `http.postBytesResponse(url, body, options?)` | `url, body: string, options?: object` | `object`, body in bodyBase64 |
| `xml.getRootAttributes(xml)` | `xml: string` | `object` |
| `xml.findElements(xml, query)` | `xml: string, query: object` | `object[]` |
| `xml.replaceChildrenByAttr(xml, options)` | `xml: string, options: object` | `string` |
| `xml.removeElements(xml, query)` | `xml: string, query: object` | `string` |
| `log.debug(tag, message) \| log.debug(message)` | `tag?, message: string` | `""` |
| `log.warn(tag, message) \| log.warn(message)` | `tag?, message: string` | `""` |
| `log.error(tag, message) \| log.error(message)` | `tag?, message: string` | `""` |
