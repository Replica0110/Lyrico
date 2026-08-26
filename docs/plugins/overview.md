# 架构与生命周期

本文面向维护者和高级插件开发者，说明 Lyrico 如何导入、验证、安装、加载、执行和卸载插件。编写第一个插件不需要先阅读这一页。

当前协议中，manifest 只声明身份、版本、入口、能力和 `configFields`。插件运行结果通过 `fields` 返回标准元数据，通过 `internal` 返回插件私有上下文；字段应用策略由 Lyrico 宿主管理。

## 系统架构

Lyrico 插件系统是一个基于 **QuickJS 嵌入式 JavaScript 引擎** 的源插件框架，运行于 Android 端。插件以 JavaScript 编写，通过 JNI 桥接层在原生 QuickJS 运行时中执行。

### 整体分层

```
┌─────────────────────────────────────────────┐
│  插件 JS 文件 (manifest.json + source.js)    │  ← 开发者编写
├─────────────────────────────────────────────┤
│  插件运行时层                                 │
│  QuickJsRuntime  /  PluginJsRuntime          │  ← JS 引擎
│  QuickJsHostApi                              │  ← 宿主能力注入
│  HostApiRegistry                             │  ← API 注册表
├─────────────────────────────────────────────┤
│  插件管理层                                   │
│  SourcePluginInstaller                       │  ← 导入/安装/卸载
│  PluginSearchSourceManager                   │  ← 缓存/激活
│  ScriptSearchSourceFactory                   │  ← 构建脚本源
├─────────────────────────────────────────────┤
│  数据层                                      │
│  PluginManifest (数据模型)                     │
│  SourcePluginEntity (Room DB)                 │
│  SourcePluginRepository (DAO)                │
├─────────────────────────────────────────────┤
│  应用层                                      │
│  PluginViewModel                             │  ← UI 状态管理
│  SearchSourceProvider                        │  ← 对外暴露搜索源
└─────────────────────────────────────────────┘
```

### 核心组件职责

| 组件 | 职责 |
|------|------|
| `PluginManifest` | 插件声明数据模型，定义插件基础信息、能力、配置 |
| `SourcePluginInstaller` | 从 ZIP 文件导入、验证、安装插件到设备 |
| `ScriptSearchSourceFactory` | 读取 manifest + JS 文件，拼接生成完整脚本 |
| `PluginSearchSourceManager` | 缓存所有已启动插件的 ScriptSearchSource 实例 |
| `ScriptSearchSource` | 包装单个插件的搜索源，管理其 JS 运行时生命周期 |
| `QuickJsRuntime` | QuickJS 引擎封装，执行 JS 脚本并调用其全局函数 |
| `QuickJsHostApi` | 实现所有宿主 API（HTTP、加密、编码、压缩、XML 等） |
| `PluginJsonParser` | 将插件返回的 JSON 解析为应用内部数据模型 |

## 完整流程

### 阶段 1：导入与验证

1. 用户从文件管理器选择 `.zip` 文件
2. `SourcePluginInstaller.prepareImport()` 将 ZIP 解压到临时目录
3. 递归查找包内所有 `manifest.json` 文件
4. 对每个 manifest 执行以下验证：

| 验证项 | 规则 |
|--------|------|
| ID 格式 | 必须匹配 `^[a-zA-Z][a-zA-Z0-9_]*(\.[a-zA-Z][a-zA-Z0-9_]*)+$`（反向域名） |
| API 版本 | 插件协议 `apiVersion` 必须在支持范围 **1..4** 内；`minHostApiVersion` 不得高于当前宿主 API **3** |
| 能力声明 | 仅允许已知能力；三项能力可独立声明；缺省或空数组按旧插件的 `searchSongs` 处理 |
| 入口文件 | 必须存在、`.js` 扩展名、路径不能逃逸插件根目录、≤ 1 MB |
| 包含目录 | `includeDirs` 中的目录必须存在且在插件根目录内 |
| 图标 | 若指定，必须存在且扩展名为 `png`/`jpg`/`jpeg`/`webp` |

5. 检查版本冲突（与新安装插件比较）：

| 场景 | 冲突类型 |
|------|----------|
| 插件不存在 | `NONE` |
| 新版本号 > 旧版本号 | `UPDATE` |
| 新版本号 == 旧版本号 | `OVERWRITE` |
| 新版本号 < 旧版本号 | `DOWNGRADE`（默认拒绝） |

6. 返回 `PluginImportSession`（含候选列表和失败列表）

### 阶段 2：安装

1. `installPrepared()` 将待安装候选逐个处理
2. 使用 **暂存目录**（`.staging-<id>-<timestamp>`）进行原子安装：
   - 复制插件根目录下所有文件到暂存目录
   - 自动排除嵌套子插件目录
   - 验证总大小 ≤ **5 MB**（`maxSinglePluginBytes`）
3. 暂存成功后，替换原有插件目录（如果存在则先删除）

### 阶段 3：存入数据库

安装完成后，插件元数据写入 Room 数据库的 `source_plugins` 表，持久化以下信息：

| 字段 | 说明 |
|------|------|
| `id` | 插件唯一标识 |
| `name` | 显示名称 |
| `versionCode` / `versionName` | 版本信息 |
| `author` / `description` | 作者和描述 |
| `apiVersion` | 插件 API 版本 |
| `pluginDir` | 插件安装目录的绝对路径 |
| `entryFile` | 入口文件名 |
| `includeDirsJson` | 包含目录的 JSON 序列化 |
| `capabilitiesJson` | 能力组合的 JSON 序列化 |
| `iconPath` | 图标绝对路径（可选） |
| `enabled` | 旧版主搜索启用状态（仅为数据库兼容保留） |
| `metadataEnabled` | 批量匹配启用状态 |
| `lyricsEnabled` | 歌词源启用状态 |
| `coverEnabled` | 封面源启用状态 |
| `sortOrder` | 旧版主搜索优先级（仅为数据库兼容保留） |
| `metadataSortOrder` | 批量匹配优先级 |
| `lyricsSortOrder` | 歌词源优先级 |
| `coverSortOrder` | 封面源优先级 |
| `installedAt` / `updatedAt` | 时间戳 |

### 阶段 4：加载与激活

1. `PluginSearchSourceManager.buildSourcesLocked()` 加载已安装插件；各调用场景随后按自己的能力和启用状态过滤
2. 对每个插件调用 `ScriptSearchSourceFactory.create()`：
   - 读取 `manifest.json`
   - 按顺序拼接 JS 脚本：先拼接 `includeDirs` 中所有 `.js` 文件（按路径排序），再拼接入口文件
   - 在脚本头部注入 `include()` 函数实现的 bootstrap
3. 生成 `ScriptSearchSource` 实例并加入缓存（以插件 ID 为键）
4. JS 运行时采用 **惰性初始化**：首次调用 `searchSongs`/`getLyrics`/`searchCovers` 时才创建 `QuickJsRuntime` 并执行完整脚本

### 阶段 5：运行时调用

1. 单曲编辑页右上角统一提供主搜索、歌词和封面三个搜索入口；主搜索加载已启用且声明 `searchSongs` 的元数据源，并仅对同时声明 `getLyrics` 的插件结果显示歌词页签与操作
2. 编辑页的独立歌词搜索只加载具备 `getLyrics` 的源；任何同时具备 `searchSongs` 的源都会先展示该源的歌曲候选，用户选择后再调用同一源的 `getLyrics`；没有 `searchSongs` 的 API 4 源直接返回歌词候选，并由 `tags.ti/ar/al/date` 提供判断信息
3. 独立封面搜索只加载 `searchCovers` 源，并直接按关键词请求封面候选
4. 批量匹配同样分为三个任务入口：元数据任务调用 `searchSongs`，歌词任务调用 `getLyrics`，封面任务调用 `searchCovers`
5. `ScriptSearchSource` 将请求序列化为 JSON，通过 JNI 调用对应的插件全局函数
6. 插件直接返回 JavaScript 值；JNI 将其序列化一次，`PluginJsonParser` 再转换为宿主结果模型

单曲主搜索与批量元数据匹配共用元数据源的开关和顺序；单曲与批量歌词共用歌词源顺序，单曲与批量封面共用封面源顺序。

### 阶段 6：启用/禁用

- `PluginViewModel.setEnabled(id, sourceType, enabled)` 只更新当前类型的启用字段
- `PluginSearchSourceManager.invalidate(pluginId)` 从缓存中移除并关闭对应运行时
- `SearchSourceProvider` 按当前 `sourceType` 同时检查能力声明和该类型的启用状态

### 阶段 7：卸载

1. 从 Room 数据库中删除记录
2. 调用 `PluginSearchSourceManager.invalidate(pluginId)` 关闭运行时
3. 移除插件对应的用户配置设置
4. 递归删除 `plugins/sources/<pluginId>/` 目录

### 阶段 8：关闭/释放

- `PluginSearchSourceManager.close()` 关闭所有缓存的 `ScriptSearchSource` 实例
- `ScriptSearchSource.close()` 关闭 QuickJS 运行时并停止专用线程池
- 导入临时目录通过 `SourcePluginInstaller.discardImport()` 清除

## 导入限制

| 限制项 | 默认值 |
|--------|--------|
| 压缩包总大小（解压后） | 30 MB |
| 单个插件目录大小 | 5 MB |
| manifest 文件大小 | 128 KB |
| 入口脚本大小 | 1 MB |
| 单包最大插件数 | 20 |
| 单包最大文件数 | 1000 |
| ZIP 条目路径深度 | 16 |

### ZIP 条目安全规则

- 不允许空名称
- 不允许包含 `\0`（NUL 字节）
- 不允许绝对路径（不能以 `/` 或 `\` 开头）
- 不允许包含反斜杠（仅正斜杠路径）
- 不允许 `..`（目录穿越攻击）
- 所有文件必须解压到目标目录内

## 运行时限制

| 限制项 | 值 |
|--------|-----|
| 内存限制 | 64 MB |
| 栈大小 | 2 MB |
| 默认执行超时 | 15 秒 |
| 插件操作超时（UI 触发） | 30 秒 |
| 每个插件独占单线程执行器 | `QuickJS-<pluginId>` |

## 宿主能力总览

插件通过 `globalThis.Platform` 对象访问宿主能力，共 **41 个 API**：

| 分类 | API 数量 | 功能 |
|------|----------|------|
| `app` | 2 | 获取宿主应用信息、UserAgent |
| `runtime` | 1 | 获取运行时信息 |
| `cache` | 4 | 插件私有字符串缓存，支持过期和删除 |
| `crypto` | 4 | MD5、AES-ECB 加解密 |
| `base64` | 11 | Base64/Base64URL 编码、解码、截断和字节转换 |
| `bytes` | 2 | XOR 字节运算 |
| `compression` | 2 | zlib inflate 解压 |
| `http` | 8 | GET/POST 请求（文本/二进制），新旧两套 API |
| `xml` | 4 | XML/TTML 查询和改写 |
| `log` | 3 | debug/warn/error 日志输出 |

详细 API 参考见 [宿主 API 参考](./host-api.md)。

## 搜索源接口

每个启用的插件以 `SearchSource` 接口的形式暴露给上层：

```kotlin
interface SearchSource {
    val id: String           // 插件唯一 ID
    val name: String         // 显示名称
    val capabilities: Set<SearchSourceCapability>  // SEARCH_SONGS, GET_LYRICS, SEARCH_COVERS
    val configFields: List<PluginConfigField>      // 可配置字段
    val apiVersion: Int                            // 插件协议版本

    suspend fun searchSongs(keyword, page, separator, pageSize): List<SongSearchResult>
    suspend fun getLyrics(song): LyricsResult?
    suspend fun getLyricsCandidates(song, page, pageSize): List<LyricsCandidateResult>
    suspend fun searchCovers(keyword, page, pageSize): List<SongSearchResult>
}
```
