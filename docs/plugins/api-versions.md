# 插件协议版本、迁移与排障

本页用于回答三个开发问题：插件应该声明哪个版本、每个版本具体改了什么，以及插件在 Devkit 或真机中失败时从哪里开始排查。

## 先区分两个版本字段

`manifest.json` 中的两个版本字段控制不同的兼容边界，不能互相替代：

| Manifest 字段 | 当前宿主版本 | 控制内容 |
|---|---:|---|
| `apiVersion` | 5 | 插件回调 `searchSongs`、`getLyrics`、`searchCovers` 的返回协议 |
| `minHostApiVersion` | 4 | 插件调用的 `Platform.*` 宿主函数集合 |

例如，一个返回 API5 扩展歌词候选、但只使用 `Platform.http` 的插件应声明：

```json
{
  "apiVersion": 5,
  "minHostApiVersion": 1
}
```

宿主目前接受 `apiVersion: 1` 到 `5`，以及 `minHostApiVersion: 1` 到 `4`。声明更高版本时会在安装阶段被拒绝，避免插件进入运行阶段后才因未知协议或缺少宿主函数失败。

插件可以在运行时检查实际能力：

```javascript
const runtime = Platform.runtime.getInfo();

Platform.log.debug("plugin", JSON.stringify({
  pluginApiVersion: runtime.pluginApiVersion,
  hostApiVersion: runtime.hostApiVersion,
  engine: runtime.engine,
  supportedHostApis: runtime.supportedHostApis
}));

if (!runtime.supportedHostApis.includes("cache.get")) {
  throw new Error("This plugin requires Platform.cache (Host API 3)");
}
```

`pluginApiVersion` 是宿主能够解析的最高插件协议版本，不是当前插件自己的 `manifest.apiVersion`。

## API1：基础插件回调协议

API1 定义了三个相互独立的全局回调。插件只需实现 `capabilities` 中声明的回调；旧插件未声明 `capabilities` 时按仅支持 `searchSongs` 处理。

| 回调 | 请求 | API1 返回值 | 用途 |
|---|---|---|---|
| `searchSongs(request)` | `{ keyword, page, pageSize, separator, config }` | `SongSearchResult[]` | 搜索歌曲和元数据 |
| `getLyrics(request)` | `{ song, config }` | 单个 `LyricsResult`、LRC 字符串或 `null` | 获取一份歌词 |
| `searchCovers(request)` | `{ keyword, song?, pageSize, config }` | `SongSearchResult[]` | 搜索封面 |

API1 对应的基础 `Platform` 能力包括应用与运行时信息、HTTP、日志、常用加密、Base64、字节运算、解压和 XML 处理。完整签名见[宿主 API](./host-api.md)。

## API2：新增 Base64URL 宿主函数

API2 没有改变三个插件回调的请求或返回值。变化发生在 Platform Host API 2，精确新增以下函数：

| 新增函数 | 参数 | 返回值 |
|---|---|---|
| `Platform.base64.encodeUrlText(text)` | UTF-8 文本 | 无 padding 的 Base64URL 字符串 |
| `Platform.base64.decodeUrlText(base64Url)` | Base64URL 字符串 | UTF-8 文本 |
| `Platform.base64.encodeUrlBytes(bytes)` | 字节数组 | 无 padding 的 Base64URL 字符串 |
| `Platform.base64.decodeUrlBytes(base64Url)` | Base64URL 字符串 | 字节数组 |
| `Platform.base64.toUrl(base64)` | 标准 Base64 字符串 | Base64URL 字符串 |
| `Platform.base64.fromUrl(base64Url)` | Base64URL 字符串 | 补齐 padding 的标准 Base64 字符串 |

只有实际调用新增 Platform 函数的插件才需要 `minHostApiVersion: 2`。仅使用 API2 回调协议、但不依赖新增 Platform 函数的插件仍可声明 `minHostApiVersion: 1`。

## API3：新增插件私有缓存

API3 没有改变三个插件回调的请求或返回值。Platform Host API 3 精确新增四个缓存函数：

| 新增函数 | 签名 | 返回值与行为 |
|---|---|---|
| 读取 | `Platform.cache.get(key)` | 返回字符串；不存在、已过期或损坏时返回 `""` |
| 写入 | `Platform.cache.set(key, value, ttlMs?)` | 返回 `""`；`ttlMs > 0` 时到期，省略或传 `0` 时不过期 |
| 删除 | `Platform.cache.remove(key)` | 删除一个键并返回 `""` |
| 清空 | `Platform.cache.clear()` | 清空当前插件的所有缓存并返回 `""` |

缓存按插件 ID 隔离。它适合保存 cookie、匿名登录态和临时 token，不是歌曲搜索结果缓存；搜索页面的结果缓存由宿主界面管理，插件没有可调用的“搜索结果缓存函数”。

缓存对象或数组时，插件需要自行编码和解析，因为缓存只保存字符串：

```javascript
const CACHE_KEY = "session.cookies";

function saveCookies(cookies) {
  Platform.cache.set(CACHE_KEY, JSON.stringify(cookies), 12 * 60 * 60 * 1000);
}

function loadCookies() {
  const raw = Platform.cache.get(CACHE_KEY);
  return raw ? JSON.parse(raw) : [];
}
```

这里的 `JSON.stringify` 用于把对象保存为缓存字符串，是正确用法；不要用它序列化插件回调的最终返回值。

## API4：歌词与封面候选契约

API4 调整 getLyrics 和 searchCovers 的结果契约，使独立歌词源和封面源能够返回可识别的候选。TTML 载荷扩展属于 API5；国际化属于 Host API4。

### 三个回调的 API3 与 API4 对比

| 回调 | 请求是否变化 | API1–3 返回值 | API4 返回值 |
|---|---|---|---|
| `searchSongs` | 否 | `SongSearchResult[]` | 不变 |
| `getLyrics` | 是；新增可选 `page`、`pageSize` | 单个 `LyricsResult`、LRC 字符串或 `null` | `LyricsResult[]`；每项以 `tags.ti/ar/al/date` 提供标题、艺术家、专辑、日期 |
| `searchCovers` | 是；新增可选 `page` | `SongSearchResult[]`，旧字段继续兼容 | `SongSearchResult[]`；每项必须有标题、艺术家、专辑、日期和封面 URL，平台歌曲 `id` 可省略 |

当前宿主还会向 `getLyrics` 提供可选的 `page` 和 `pageSize`，供不实现 `searchSongs` 的 API4 歌词源分页返回候选。旧插件可以忽略新增的 page 和 pageSize 字段；函数调用签名仍是单个 `request` 对象。

插件回调接收的是 JavaScript 对象，必须直接返回 JavaScript 值。Android 宿主会统一完成一次 JSON 序列化：

```javascript
function getLyrics(request) {
  return [{
    type: "rawPlainLrc",
    tags: {
      ti: "歌曲标题",
      ar: "艺术家",
      al: "专辑",
      date: "2026"
    },
    rawPlainLrc: "[00:00.00]Example"
  }];
}
```

以下写法会被宿主再次序列化，形成字符串包字符串，解析必然失败：

```javascript
// 错误：不要序列化回调的最终返回值
return JSON.stringify([{ id: "1", title: "Song" }]);
```

歌词结果的四项判断信息复用标准歌词标签，不增加重复的顶层字段：

| 歌词标签 | 搜索结果中显示为 |
|---|---|
| `tags.ti` | 标题 |
| `tags.ar` | 艺术家 |
| `tags.al` | 专辑 |
| `tags.date` | 日期或年份 |

API4 封面结果示例：

```javascript
function searchCovers(request) {
  return [{
    title: "歌曲标题",
    artist: "艺术家",
    album: "专辑",
    date: "2026",
    picUrl: "https://example.com/cover.jpg"
  }];
}
```

封面 URL 也兼容 `coverUrl`、`cover_url` 和 `artworkUrl`。字段别名与完整歌词格式见[插件函数](./plugin-functions.md)。

## 宿主的实际调用链

`capabilities` 决定插件在哪类调用中可用。元数据源同时用于单曲主搜索和批量元数据匹配；歌词源和封面源分别用于对应的单曲与批量操作。

| 场景 | 会调用哪些源 | 调用顺序 |
|---|---|---|
| 单曲主搜索 | 已启用且具备 `searchSongs` 的元数据源 | 先调用 `searchSongs`；只有结果所属插件同时声明 `getLyrics` 时，才显示歌词入口并调用 `getLyrics` |
| 批量元数据匹配 | 已启用且具备 `searchSongs` 的元数据源 | 搜索、评分并写入非歌词、非封面字段 |
| 批量歌词匹配 | 已启用且具备 `getLyrics` 的歌词源 | 有 `searchSongs` 时先选歌，否则直接请求歌词候选 |
| 批量封面匹配 | 已启用且具备 `searchCovers` 的封面源 | 调用 `searchCovers` 并按本地歌曲信息评分 |
| 独立歌词搜索，有 `searchSongs` | 具备 `getLyrics` 且当前歌词页已启用的源 | 先调用同一插件的 `searchSongs`；用户选择歌曲后，再把该结果原样传给同一插件的 `getLyrics` |
| 独立歌词搜索，无 `searchSongs` | 具备 `getLyrics` 且当前歌词页已启用的 API4 源 | 直接以当前本地歌曲信息调用 `getLyrics` |
| 封面搜索 | 具备 `searchCovers` 且当前封面页已启用的源 | 直接调用各自的 `searchCovers` |

歌曲 ID、`internal`、歌词和封面不会跨插件拼接。歌词页面中的“全部”只是在同一个页面保留并展示各源缓存的搜索结果，不会把一个源返回的歌曲交给另一个源获取歌词。

## API5：歌词返回格式扩展与 TTML 信息保留

API5 在 API4 歌词候选格式上扩展逐词音译、行级属性、演唱者、head 元数据、时间粒度、语言码、段落时间窗、正文时长和带时间的多音节 Ruby 注音。候选数组、必填歌曲标签以及 searchSongs / searchCovers 的契约不变。逐词音译等扩展来自提交 [0876c815](https://github.com/Replica0110/Lyrico/commit/0876c815) 和 [1071e09e](https://github.com/Replica0110/Lyrico/commit/1071e09e)。

继续兼容 API1–4 插件。扩展字段可选；声明 API5 不会自动补出源数据中缺失的信息。

### 结构化歌词的 TTML 元数据

`type: "structured"` 的歌词结果可以选择携带 TTML 专属信息。TTML 专属结构在导出为 LRC 时不会保留；逐词音译可在逐字 LRC 中保留时间戳。旧插件只返回 `original`、`translated`、`romanization` 时，与 API3 的行为一致。

| 载荷位置 | 内容 | 写回 TTML |
|---|---|---|
| `original` 行的第 4 个元素 | 行级扩展属性，例如 `ttm:agent`、`itunes:song-part`、`divBegin`/`divEnd` | `<p ttm:agent>`、`<div itunes:song-part>`、段落 `<div begin/end>` |
| `original` 词的第 4 个元素 | Ruby 注音音节数组 `[[startMs, endMs, "注音"], ...]` | `<span tts:ruby="container">` / `<span tts:ruby="text">`，缺失的音节边界由宿主规范化 |
| `original` / `translated` / `romanization` 行第 3 个元素 | 词数组或整行文本 | 三个轨道分别保留逐句或逐字粒度；TTML 与逐字 LRC 支持逐词时间 |
| `agents` | `{ id, type?, name? }[]` | `<head>` 中的 `<ttm:agent>` |
| `metadata` | `{ name, namespace?, attributes?, text?, children? }[]` | `<head>` 中的元数据节点 |
| `timing` | `"Word"` 或 `"Line"` | `<tt itunes:timing>` |
| `language` | 原文语言码（BCP 47） | `<tt xml:lang>` |
| `bodyDur` | TTML 时间表达式 | `<body dur>` |
| `translatedLang` / `romanizationLang` | 翻译轨 / 音译轨语言码（BCP 47） | 对应轨的 `xml:lang` |

`itunes:key` 由宿主重新生成，插件不需要提供。扩展属性只接受无前缀名称与 `ttm:`、`itunes:` 前缀，其他前缀会被忽略；非法的 `bodyDur` 会被丢弃。

字段格式、`metadata` 的约束、逐词时间与缺失边界的处理，以及 `rawTtml` 的应用场景见[插件函数](./plugin-functions.md)的「结构化歌词的行格式」与「TTML 扩展」。

## Host API4：插件国际化

宿主 API4 独立新增两个本地化函数。仅使用 API5 歌词扩展并不要求 Host API4。

| 新增函数 | 签名 | 返回值与行为 |
|---|---|---|
| 当前语言 | `Platform.i18n.getLocale()` | 返回选中语言的标签，例如 `"zh-Hans"`；插件没有 `i18n` 时返回 `"und"` |
| 取文本 | `Platform.i18n.t(key, ...args)` | 返回该键在当前语言下的文本；传入参数时按位置占位符格式化 |

Manifest 中使用 `@` 字符串引用，或脚本调用 `Platform.i18n` 的插件，需要把 `minHostApiVersion` 提高到 4。资源文件写法与占位符规则见[插件国际化](./i18n.md)。

## 从 API3 升级到 API4

1. 将 `manifest.json` 的 `apiVersion` 改为 `4`。
2. 按实际实现填写 `capabilities`；不要声明未实现的回调。
3. 保持 `searchSongs` 的请求和返回值不变。
4. 把 `getLyrics` 的单个结果改为数组；无结果返回 `[]`，每项补齐 `tags.ti`、`tags.ar`、`tags.al`、`tags.date`。
5. 为每个 `searchCovers` 结果补齐 `title`、`artist`、`album`、`date` 和封面 URL；`id` 可以省略。
6. 所有回调直接返回对象、数组、字符串或 `null`，不要对最终返回值调用 `JSON.stringify`。
7. 只有使用了 Base64URL、缓存或文本本地化时，才把 `minHostApiVersion` 分别提高到 2、3 或 4。

## 从 API4 升级到 API5

1. 将 manifest 中的 `apiVersion` 设为 `5`，保留 `getLyrics` 候选数组和 `tags.ti/ar/al/date`。
2. 有逐词音译时返回词数组；按源数据补充行级扩展、agents、metadata、语言与时间粒度、bodyDur 和 Ruby。旧的整行字符串仍有效。
3. 遵循[插件函数](./plugin-functions.md)中的字段格式和校验规则；Ruby 缺失边界用 `null`，非法 bodyDur 会被丢弃。
4. `minHostApiVersion` 按实际使用的 Platform 能力填写（1–4），不要改成 5；国际化需要 Host API4。
5. 协议上限为 4 的旧宿主会拒绝 API5 插件，安装或验证前需更新 Lyrico 和 Devkit。

## 用 Devkit 定位问题

在 `Lyrico-Plugins` 仓库根目录执行：

```bash
node tools/plugin-devkit/src/cli.js validate ./my-plugin
node tools/plugin-devkit/src/cli.js inspect ./my-plugin
node tools/plugin-devkit/src/cli.js test ./my-plugin searchSongs --keyword "晴天" --page-size 5
node tools/plugin-devkit/src/cli.js test ./my-plugin getLyrics --song ./song.json --logs
node tools/plugin-devkit/src/cli.js test ./my-plugin searchCovers --keyword "晴天" --page-size 5
```

增加 `--json` 可以输出完整的 `request`、宿主序列化后的 `raw`、解析后的 `parsed`、`warnings`、`errors` 和插件日志。排查时先看第一条 `errors`，再对照 `raw` 与 `parsed`：

| 现象或错误 | 先检查什么 | 常见原因 |
|---|---|---|
| 安装时提示插件协议不支持 | `manifest.apiVersion` | 高于宿主支持的 5，或把 Platform 版本误填到了这里 |
| 安装时提示宿主 API 不支持 | `manifest.minHostApiVersion` | 高于宿主支持的 4 |
| `returned JSON.stringify(...) instead of a JavaScript value` | 回调中的最终 `return` | 插件提前序列化，Android 宿主又序列化一次 |
| `getLyrics returned no usable lyrics candidates` | `raw`、歌词 `type` 与对应载荷字段 | 返回的数组为空，或歌词对象不能被解析 |
| `lyrics candidate[n] is missing ...` | `tags.ti/ar/al/date` | API4 歌词候选缺少用户判断信息 |
| `cover result[n] is missing ...` | `title/artist/album/date` 和封面 URL | API4 封面候选字段不完整 |
| 单曲搜索中看不到插件 | `searchSongs` 能力与元数据源启用状态 | 未声明 `searchSongs`，或未启用为元数据源 |
| 歌词搜索能搜到歌曲，点选后失败 | `getLyrics --song` 的 `raw/errors/logs` | `searchSongs` 正常不代表解密、歌词接口或结果格式正常 |
| `InternalError: interrupted` | 单次回调耗时和循环规模 | Android QuickJS 的一次加载或调用有 15 秒执行期限；避免在一次 `getLyrics` 中批量请求并解密多首候选 |
| `Platform.xxx is not a function` | `Platform.runtime.getInfo().supportedHostApis` | Devkit/宿主过旧，或 `minHostApiVersion` 声明过低 |
| Devkit 通过但真机失败 | `raw`、Android 日志、QuickJS 兼容性 | 网络/TLS、执行期限或 JavaScript 引擎差异；不要只验证 `searchSongs` |

Devkit 用 Node.js 模拟宿主，不能替代 Android QuickJS 真机验证，但它应当先消除 manifest、返回协议、双重序列化和必填字段错误。
