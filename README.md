# Lyrico

安卓平台的音频元数据编辑器与逐字歌词搜索应用，随缘更新...

## 说明

本项目的开发主要围绕维护者自身的实际使用需求展开，同时兼顾通用性与可维护性。

在功能设计上，将优先考虑：

* 通用性（对多数用户有价值）
* 简洁性（避免过度复杂）
* 可维护性（长期演进成本可控）

对于以下类型的需求，通常不会纳入主线：

* 高度个性化或定制化的功能
* 使用场景较小众的需求
* 会显著增加系统复杂度的改动

如有个性化需求，建议通过 fork 项目自行实现


## 功能特性

- **音频元数据编辑**: 修改本地音乐文件的标题、艺术家、专辑等信息
- **封面获取**: 支持从网络搜索并嵌入歌曲封面到音频文件中
- **格式支持**: 支持读取/编辑 **MP3**、**WAV**、**FLAC**、**OGG**、**AAC** 等格式音频文件
- **歌词搜索**:
    - 支持从 **酷狗音乐**、**QQ音乐** 和 **网易云音乐** 搜索歌曲信息
    - 支持逐行、逐字、增强型逐字歌词匹配
    - 仅下载翻译功能
    - 罗马音下载功能
- **批量功能**:
    - 长按歌曲项进入多选模式
    - 支持配置批量匹配及查看批量匹配详情
    - 支持文件批量分享及删除
## 未来计划

- [ ] **新增音源**: 补充 **酷我音乐** 的搜索源。
- [ ] **UI/UX 优化**: 改进界面外观和交互。 


## 致谢
项目在开发过程中参考/使用了以下优秀的项目: 
- [LDDC](https://github.com/chenmozhijin/LDDC) - 简单易用的精准歌词(逐字歌词/卡拉OK歌词)下载匹配工具
- [any-listen-extension-online-metadata](https://github.com/any-listen/any-listen-extension-online-metadata) - any-listen 音频元数据搜索插件
- [SaltUI](https://github.com/Moriafly/SaltUI) - 跨平台的 Compose UI 组件库
- [音乐标签](https://www.cnblogs.com/vinlxc/p/11932130.html) - 一款可以编辑歌曲的标题，专辑，艺术家，歌词，封面等信息的应用程序， 支持多种音频格式
- [taglib](https://github.com/taglib/taglib) - TagLib Audio Meta-Data Library
- ~[Auxio - musikr](https://github.com/OxygenCobalt/Auxio/tree/dev/musikr) - Musikr 是一个高度主观设计（highly opinionated）且支持多线程的音乐加载器，用于支持 Auxio 的高级音乐功能~
