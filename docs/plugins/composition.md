# 2. 插件组成

## 文件结构

一个完整的 Lyrico 插件包含以下文件：

```
<plugin-root>/
├── manifest.json       # 【必填】插件声明文件
├── source.js           # 【必填】插件入口脚本（或通过 manifest.entry 指定其他名称）
├── lib/                # 【可选】辅助脚本目录（需在 manifest.includeDirs 中声明）
│   ├── 01_http.js      #   按文件名排序后逐个拼接
│   └── 02_parser.js
└── icon.png            # 【可选】插件图标（需在 manifest.icon 中声明）
```

## 入口文件

### 指定入口

通过 `manifest.json` 的 `entry` 字段指定入口文件名：

```json
{
  "entry": "source.js"
}
```

- **默认值**：`"source.js"`
- **限制**：必须是 `.js` 扩展名，路径相对于插件根目录
- **校验规则**：
  - 不允许包含 `..`、`\`、`\0`
  - 不允许以 `/` 或 `\` 开头
  - 必须位于插件根目录内（不能通过符号链接逃逸）
  - 文件大小 ≤ 1 MB

### 入口脚本职责

入口脚本必须定义 **全局函数**（非模块导出），QuickJS 不支持 ES Module：

```javascript
// ✅ 正确：全局函数声明
function searchSongs(request) {
  return JSON.stringify([...]);
}

// ❌ 错误：不支持 export
export function searchSongs(request) { ... }
```

## Include 包含系统

插件可以将公共逻辑拆分到 `lib/` 目录下的辅助文件中，运行时将这些文件的内容 **拼接** 为一个完整脚本执行。

### 声明包含目录

```json
{
  "includeDirs": ["lib"]
}
```

- **类型**：`string[]`
- **默认值**：`[]`
- **限制**：
  - 目录必须是插件根目录下的相对路径
  - 不能是 `"."`
  - 不能在插件根目录之外
  - 必须真实存在且为目录

### 拼接规则

1. 按 `includeDirs` 声明顺序遍历每个目录
2. 目录内的 `.js` 文件按**相对路径排序**
3. 所有辅助脚本拼接在入口脚本 **之前**
4. 每个脚本前添加 `sourceURL` 注释用于调试

**拼接后的脚本结构：**

```
[bootstrap: include() 实现]
[semicolon 分隔]
// ===== Platform include: lib/01_http.js =====
[01_http.js 内容]
//# sourceURL=lib/01_http.js
[semicolon 分隔]
// ===== Platform include: lib/02_parser.js =====
[02_parser.js 内容]
//# sourceURL=lib/02_parser.js
[semicolon 分隔]
// ===== Platform entry: source.js =====
[source.js 内容]
//# sourceURL=source.js
```

### include() 函数

辅助脚本可以通过 `include(path)` 声明对其他辅助脚本的依赖：

```javascript
// lib/02_parser.js
include("lib/01_http.js");  // 已声明于 => OK

include("lib/secret.js");   // 未声明于 includeDirs => 抛出 Error
```

- `include()` 本质上是 **路径验证**：因为所有辅助脚本已经拼接完毕
- 传入未在 `includeDirs` 中声明的路径会抛出异常
- `include()` 在 bootstrap 中被注入为 `globalThis.include`

## 图标

```json
{
  "icon": "icon.png"
}
```

- **类型**：`string | null`
- **默认值**：`null`
- **支持的格式**：`png`、`jpg`、`jpeg`、`webp`
- **限制**：图标文件必须在插件根目录内、真实存在

## 完整文件结构示例

### 示例音乐源插件

```
com.example.source/
├── manifest.json        # 插件声明
├── source.js            # 入口：searchSongs/getLyrics/searchCovers
└── lib/
    ├── 01_http.js       # API 加密通信、签名、请求封装
    └── 02_lrc.js        # LRC 歌词解析、行对齐合并
```

### 带配置项的插件示例

```
com.example.source/
├── manifest.json        # 含多个配置项（API 类型、Token、封面大小）
├── source.js            # 入口：搜索、歌词获取、封面
└── lib/
    └── 01_api.js        # JWT Token 验证、请求封装
```

## 包内多插件组织

单个 ZIP 可以包含多个插件，每个插件有各自的 `manifest.json`：

```
multi-plugins.zip
├── com.plugin1.source/
│   ├── manifest.json
│   ├── source.js
│   └── lib/
├── com.plugin2.source/
│   ├── manifest.json
│   ├── source.js
│   └── lib/
└── shared-lib/              # 注意：不在任何插件根目录内，不会被安装
    └── common.js
```

**安装时的排除规则**：如果一个插件的根目录位于另一个插件的根目录之下，安装时会自动排除嵌套子目录，防止重复安装。

## 文件限制速查

| 限制项 | 上限值 |
|--------|--------|
| manifest.json 大小 | 128 KB |
| 入口脚本大小 | 1 MB |
| 单个插件总大小 | 5 MB |
| 压缩包总大小 | 30 MB |
| 单包文件数 | 1000 |
| 单包插件数 | 20 |
| ZIP 条目深度 | 16 层 |
| 入口脚本扩展名 | 必须是 `.js` |
| 图标扩展名 | `.png` / `.jpg` / `.jpeg` / `.webp` |
