# Lyrico 插件系统文档

## 目录

| 文档 | 说明 |
|------|------|
| [1. 插件系统概述](./overview.md) | 系统架构、完整流程、生命周期、宿主能力 |
| [2. 插件组成](./composition.md) | 文件结构、入口文件、include 包含机制、限制说明 |
| [3. Manifest 字段参考](./manifest.md) | 所有字段的完整参考、可选值、填写限制 |
| [4. 宿主 API 参考](./host-api.md) | Platform.* 所有 27 个 API 的完整文档 |
| [5. 插件函数参考](./plugin-functions.md) | searchSongs、getLyrics、searchCovers 接口定义 |
| [6. 配置与元数据](./config-metadata.md) | 用户配置、配置依赖、元数据写入 |
| [7. 完整示例](./examples.md) | 从零构建一个可运行的插件 |

## 快速开始

一个 Lyrico 插件是一个包含 `manifest.json` 和 `source.js` 的 ZIP 压缩包：

```
my-plugin.zip
└── com.example.source/
    ├── manifest.json    # 插件声明（必填）
    ├── source.js         # 插件入口（必填，默认）
    └── lib/              # 辅助脚本（可选）
        └── helper.js
```

### 最小 manifest.json

```json
{
  "id": "com.example.source",
  "name": "示例插件",
  "versionCode": 1,
  "versionName": "1.0.0",
  "apiVersion": 1
}
```

### 最小 source.js

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

## 设计约束

- 插件运行在 QuickJS 嵌入式引擎中，不支持 ES Module（无 `import`/`export`）
- 通过 `globalThis.Platform` 访问宿主提供的原生能力
- 单一执行线程，无需考虑并发安全
- 默认执行超时 15 秒，内存限制 64 MB
