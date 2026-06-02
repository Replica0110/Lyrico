# Lyrico

[![Build Debug](https://github.com/Replica0110/Lyrico/actions/workflows/build-debug.yml/badge.svg?branch=master)](https://github.com/Replica0110/Lyrico/actions/workflows/build-debug.yml)
[![Docs](https://github.com/Replica0110/Lyrico/actions/workflows/deploy-docs.yml/badge.svg?branch=master)](https://github.com/Replica0110/Lyrico/actions/workflows/deploy-docs.yml)
[![Release](https://img.shields.io/github/v/release/Replica0110/Lyrico?include_prereleases&label=release)](https://github.com/Replica0110/Lyrico/releases)
[![License](https://img.shields.io/github/license/Replica0110/Lyrico)](./LICENSE)
[![Android](https://img.shields.io/badge/Android-9%2B-3DDC84?logo=android&logoColor=white)](#构建)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.3.21-7F52FF?logo=kotlin&logoColor=white)](./gradle/libs.versions.toml)

Lyrico 是一款面向 Android 的开源本地音乐标签编辑与歌词管理工具。它用于整理本地音乐库、读写音频元数据，并通过插件化搜索源补全歌词、封面和其他歌曲信息。

## 功能特性

- **本地音乐库管理**：扫描本地音乐文件，按歌曲、艺术家、专辑、文件夹等维度浏览与搜索。
- **音频元数据编辑**：读取和修改标题、艺术家、专辑、专辑艺术家、年份、流派、音轨号、歌词、封面、注释和自定义标签等信息。
- **多格式标签读写**：基于 TagLib 处理 MP3、FLAC、OGG/Opus、M4A/AAC、WAV 等常见音频格式。
- **歌词与封面补全**：通过插件搜索歌词、翻译、罗马音、封面和候选元数据，并写入本地音频文件。
- **歌词格式处理**：支持普通 LRC、增强型逐字歌词、TTML 等歌词内容的展示、匹配、转换与写入。
- **批量整理流程**：支持多选、批量匹配、批量编辑、批量重命名、批量响度分析、批量分享和批量删除。
- **插件化搜索源**：在线搜索能力由插件提供，应用本体负责运行时、配置界面、结果处理和标签写入。

## 插件系统

Lyrico 的在线音乐信息搜索采用插件化架构。搜索源插件以 JavaScript 编写，运行在 Android 端嵌入式 [quickjs-ng](https://github.com/quickjs-ng/quickjs) 运行时中。

插件可以声明并实现以下能力：

- 搜索歌曲信息
- 获取歌词
- 搜索封面
- 声明插件配置项

应用会根据插件 manifest 暴露配置界面、运行插件脚本，并将插件返回的结果转换为应用内部的歌曲、歌词和封面数据。插件文档从这里开始阅读：

- [插件使用](https://replica0110.github.io/Lyrico/plugins/using.html)
- [插件开发概览](https://replica0110.github.io/Lyrico/plugins/overview.html)
- [Manifest 参考](https://replica0110.github.io/Lyrico/plugins/manifest.html)
- [宿主 API](https://replica0110.github.io/Lyrico/plugins/host-api.html)

## 构建

推荐使用 Android Studio 打开项目，也可以直接通过 Gradle 构建。

基础环境：

- JDK 21
- Android SDK
- Android NDK 29
- CMake 4.1.2

常用命令：

```powershell
.\gradlew.bat :lyrico-app:assembleDebug
```

仅检查 Kotlin 编译：

```powershell
.\gradlew.bat :lyrico-app:compileDebugKotlin
```

主要模块：

- `lyrico-app`：Android 应用主模块，包含 UI、音乐库、搜索、插件运行时和业务逻辑。
- `lyrico-audiotag`：音频标签读写模块，封装底层音频元数据处理能力。
- `docs`：VitePress 文档站点，覆盖插件使用、插件开发和宿主 API。

## 维护取向

项目开发主要围绕维护者自身的实际使用需求展开，不追求覆盖所有可能的个性化场景。主线功能通常会优先考虑：

- 对多数用户有实际价值
- 交互和概念足够简单
- 长期维护成本可控
- 能通过插件或配置扩展的能力不硬编码进应用本体

高度定制化、使用场景过窄或会明显增加复杂度的功能，可能不会纳入主线。此类需求更适合通过插件、配置或 fork 项目实现。

## 参考项目与第三方组件

项目在开发过程中参考或使用了以下优秀项目：

- [LDDC](https://github.com/chenmozhijin/LDDC)：简单易用的精准歌词下载匹配工具
- [any-listen-extension-online-metadata](https://github.com/any-listen/any-listen-extension-online-metadata)：any-listen 音频元数据搜索插件
- [Miuix](https://github.com/compose-miuix-ui/miuix)：Xiaomi HyperOS 风格的 Compose UI 组件库
- [音乐标签](https://www.cnblogs.com/vinlxc/p/11932130.html)：音频标签编辑应用
- [quickjs-ng](https://github.com/quickjs-ng/quickjs)：嵌入式 JavaScript 运行时
- [TagLib](https://github.com/taglib/taglib)：音频元数据读写库
- [libebur128](https://github.com/jiixyj/libebur128)：EBU R128 音频响度标准实现

## 捐赠

如果 Lyrico 对你有帮助，可以通过 [爱发电](https://ifdian.net/a/replica0110) 支持项目维护。

## 许可证

Lyrico 本体代码基于 [Apache License 2.0](./LICENSE) 开源。

项目使用的第三方组件仍遵循各自许可证。Gradle/Android 依赖的许可信息可在应用内“开源许可”页面查看；随源码引入或嵌入构建的组件见 [THIRD_PARTY_NOTICES.md](./THIRD_PARTY_NOTICES.md)。
