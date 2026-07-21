# Lyrico 插件

Lyrico 的在线音乐信息搜索由插件提供。插件可以扩展歌曲搜索、歌词获取、封面搜索，也可以声明需要用户填写的配置项。

这组文档同时面向两类读者：

- **使用者**：想安装、启用、配置插件，并了解插件会影响哪些搜索和写入流程。
- **开发者**：想编写自己的搜索源插件，或维护已有插件。

## 我想使用插件

如果你只是想使用现成插件，建议先阅读：

| 文档 | 适合解决的问题 |
|------|----------------|
| [使用插件](./using.md) | 如何导入、启用、配置、停用或卸载插件 |
| [配置与结果字段](./config-metadata.md) | 插件配置项是什么，`fields` 和 `internal` 分别放什么 |

## 我想开发插件

如果你想编写插件，建议按下面的顺序阅读：

| 顺序 | 文档 | 内容 |
|------|------|------|
| 1 | [从零编写插件](./examples.md) | 用一个完整示例了解插件的基本写法和打包方式 |
| 2 | [插件包结构](./composition.md) | 了解 `manifest.json`、入口脚本、辅助脚本和 ZIP 结构 |
| 3 | [插件函数](./plugin-functions.md) | 实现 `searchSongs`、`getLyrics`、`searchCovers` |
| 4 | [Manifest 参考](./manifest.md) | 查询 manifest 字段、可选值和校验规则 |
| 5 | [宿主 API 参考](./host-api.md) | 使用 HTTP、加密、编码、压缩、XML、日志等宿主能力 |

## 我想理解运行机制

如果你想了解 Lyrico 如何导入、验证、安装、加载和执行插件，可以阅读 [架构与生命周期](./overview.md)。这一页偏向维护者和高级开发者，不是编写第一个插件的必读内容。

## 插件的基本形态

一个最小插件通常包含两个文件：

```text
com.example.source/
├── manifest.json
└── source.js
```

`manifest.json` 负责声明插件信息、能力、入口文件和配置项。`source.js` 负责实现插件函数。

最小 `manifest.json`：

```json
{
  "id": "com.example.source",
  "name": "示例插件",
  "versionCode": 1,
  "versionName": "1.0.0",
  "apiVersion": 3
}
```

最小 `source.js`：

```javascript
function searchSongs(request) {
  return JSON.stringify([
    {
      id: "12345",
      title: "示例歌曲",
      artist: "示例歌手"
    }
  ]);
}
```

## 运行环境

- 插件使用 JavaScript 编写，运行在 Android 端嵌入式 QuickJS 环境中。
- 插件函数是全局函数，不使用 ES Module，也就是不支持 `import` / `export`。
- 插件通过 `globalThis.Platform` 访问宿主提供的 HTTP、加密、编码、压缩、日志等能力。
- 插件运行有超时和内存限制，适合处理搜索、解析、转换等轻量任务。
