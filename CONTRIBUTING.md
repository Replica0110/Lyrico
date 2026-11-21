# Lyrico 代码与重构规范

本规范用于 Android 客户端的开发、审查和重构。构建环境与命令见 [README](./README.md#构建)，插件公开契约见 [插件文档](./docs/plugins/index.md)。

## 1. 目录与职责

### 一级包

Android 应用模块的代码按下表归入 `com.lonx.lyrico` 下的对应包，包名与源码目录一致。根包承载 `App`、`MainActivity` 等应用入口，音频标签读写由 `lyrico-audiotag` 模块负责。

| 包 | 职责 |
| --- | --- |
| `ui` | Compose 页面、页面状态、交互、主题和共享控件 |
| `domain` | 业务规则和用例，例如标签应用、海报匹配、重命名和歌词处理 |
| `data` | 仓库、存储、外部数据转换及跨功能使用的数据类型 |
| `platform` | Android 系统接口和跨功能的设备能力，例如文件访问、播放器 Intent |
| `plugin` | 插件安装、分发、来源实现、运行时和宿主协议 |
| `diagnostics` | 诊断环境收集、复现录制和报告组装 |
| `worker` | WorkManager 任务入口、调度、进度、通知和任务处理器 |
| `di` | 依赖注入和组件组装 |

### 二级包和功能归属

| 包 | 放置内容 |
| --- | --- |
| `ui/screens/<feature>` | 同一功能的 Destination、页面 ViewModel、页面状态及专用组件。仅一处使用的小组件可跟页面同文件 |
| `ui/components/<role>` | 多个页面共用的视觉控件，按 `scaffold`、`bar`、`dialog`、`sheet`、`input`、`button`、`progress`、`text`、`tabs`、`image` 等职责归类；共享视觉效果归 `effect`。主题专用实现放在对应职责的 `salt`、`miuix` 子包；少量紧密配对的实现可同包使用主题后缀 |
| `ui`（根） | 跨页面共享的 UI 状态与文本，例如 `UiMessage`、`SharedSelectionManager` |
| `ui/theme` | 主题入口、调色板、排版和主题适配 |
| `ui/update` | 跨页面更新提示的展示状态、事件与协调 |
| `domain/<feature>` | 按业务职责组织规则和用例。歌词解析、转换和写出规则归 `domain/lyrics`。简单读写可直接调用仓库 |
| `data/song/<feature>` | 歌曲文件、扫描、索引查询和标签数据访问。扫描队列、枚举目录和扫描仓库归 `data/song/scan` |
| `data/song/search`、`data/plugin` | 前者承载歌曲搜索仓库、搜索源接口、会话及结果类型；后者承载已安装插件的数据访问、清单、运行配置和字段处理配置 |
| `data/artwork`、`data/artwork/coil` | 封面与海报数据读取及其 Coil 加载适配 |
| `data/editfield` | 标签编辑字段的定义、注册和配置存取 |
| `data/db` | Room 的 DAO、实体、查询投影和迁移。移动数据库入口前核对 schema 导出目录及迁移定义 |
| `data/settings`、`data/settings/backup` | 设置仓库、偏好键、默认值及持久化配置；备份子包承载备份文件类型及其与偏好存储的映射 |
| `data/log`、`data/batch` | 分别承载日志存储、记录入口与脱敏，以及批量任务存储、任务状态和匹配配置。任务执行由 `worker` 负责 |
| `data/update`、`data/contributor` | 更新与贡献者数据访问、外部响应类型及应用侧数据类型。更新对话框状态归 `ui/update` |
| `data/lyrics`、`data/metadata`、`data/rename` | 歌词数据与文档结构、标签字段与写入策略配置、重命名及字符映射配置；转换、应用和重命名规则归对应 `domain` 功能 |
| `data/cache`、`data/export`、`data/text` | 缓存管理及分类、导出目的地类型、繁简转换模式等各自功能的数据与配置 |
| `data/network` | 跨功能共用的网络基础设施，例如请求日志拦截器 |
| `platform/<capability>` | 按系统能力组织的适配，如 `player` 或 `storage` |
| `platform/player` | 外部播放器发现、Intent 构建与启动、系统选择器回退 |
| `plugin/runtime`、`plugin/source`、`plugin/i18n` | 分别承载脚本执行与宿主 API、插件搜索源适配及运行配置应用、插件本地化 |
| `plugin/install`、`plugin/distribution` | 分别承载插件包导入、安装事务与恢复，以及订阅目录、下载校验和更新协调。已安装记录的 DAO 和实体归 `data/db` |
| `worker/processor` | 各类后台任务的执行逻辑。可同时用于前台的业务规则放在所属功能 |

### 归属与依赖规则

- 新增代码先按职责查找已有功能包。页面与页面状态放在 `ui/screens/<feature>`，DAO 与实体放在 `data/db`，辅助函数跟随使用它的功能；不以 `utils` 等通用名称集中无关职责。表中未覆盖的职责，先明确归属并补充规则。
- `data` 按功能组织，仓库、共享数据类型和映射跟随功能所有者，不按 `repository`、`model` 或 `dto` 再建集中分类。外部响应 DTO 与应用模型在契约或用途不同时分别保留，放在负责解析的功能内；页面、Worker 或数据源独用的类型跟随使用者。
- `ui/components` 只放视觉控件及其展示参数，不持有页面状态或 ViewModel；同时不反向依赖 `ui/screens`。控件需要消费某个页面的状态时，控件随该页面归入 `ui/screens/<feature>`，由使用方组合。仅供一处使用的面板即使外观通用，也跟随它的页面。
- 按共同的**变化原因**归类。先明确谁创建、谁修改、谁消费，再找最小的共同所有者。同一职责的少量文件可直接放在该包下，只有形成独立职责时才增加子包。辅助函数跟随所有者。
- UI 消费状态和业务接口。领域和数据代码避免依赖 Compose 布局、主题色和页面类型。新边界优先通过模块内的包和可见性表达。新增顶层包或 Gradle 模块时，说明其负责的独立能力。
- `private` 用于文件或类型内部，`internal` 用于模块内部，公开声明服务跨模块或协议入口。已有公开签名需要一起收紧时，按调用边界分批迁移。
- 改包名时同时查 Kotlin/Java 引用、生成路由、DI、Manifest、R8 规则、JNI 注册与持久化类型名。对涉及的入口运行对应构建或验证样本。
- 带 native 方法的类移动时，同步 `lyrico-app/src/main/cpp` 里的 `Java_..._方法名`；`@Database` 类移动时连同 `lyrico-app/schemas/<数据库类限定名>` 目录一起移动，数据库文件名、版本号和迁移保持原样。
- 两个类型同名但职责不同时改其中一个：例如菜单的展示状态与页面持有的菜单内容分属 UI 与 ViewModel，名字要让调用处直接读出区别，而不是靠包名区分。

## 2. 命名与词汇

名称要让读者从调用处看懂职责。优先使用项目已经采用的术语，用包名、接收者和上下文交代归属，只在语义有区别时加限定词。

| 对象 | 规则 | 示例 |
| --- | --- | --- |
| 包 | 全小写，按职责或功能命名 | `ui.screens.library`、`domain.poster` |
| Kotlin 文件 | PascalCase。单一主要类型与文件同名，顶层组件按主入口命名，相关声明可放同一文件 | `LocalSearchScreen.kt`、`SectionHeader.kt` |
| 类、接口、对象、枚举类型 | PascalCase，使用表达职责的名词。后缀只表达实际角色 | `SongRepository`、`SearchUiState`、`SaveAudioTagsUseCase` |
| 函数、扩展、属性 | lowerCamelCase。函数用动词，属性用名词或状态。扩展接收者已说明的对象可省略 | `saveTags()`、`String.normalizeArtistName()`、`selectedTab`、`isLoading` |
| Compose | 输出 UI 且返回 `Unit` 的组件用 PascalCase，名称描述组件。返回值的函数遵循普通 Kotlin 命名 | `SectionHeader`、`rememberBarBlurBackdrop` |
| 常量、资源 | `const val` 用 UPPER_SNAKE_CASE，Android 资源用 lower_snake_case | `MAX_RESULTS`、`library_tab_songs` |

统一以下词汇及组合顺序。遇到旧名字，迁移该组的实际调用，避免再增加一个同义入口。

- **对象或用途在前，角色在后。** 页面用 `<Feature>Screen`，页面状态用 `<Feature>UiState`，渲染或布局区块按作用用 `Header`、`Row`、`Item`、`Sheet`。Sheet 和 Dialog 对应各自的呈现方式。
- **同一名词用同一拼法。** 新代码用 `Ui`、`Dto`、`Id`、`Uri` 作为标识符的一部分。第三方 API 名称、已有协议键和公开兼容名称沿用原契约。
- **用词区分动作。** `read` 表示读取一次，`observe` 表示持续观察，`refresh` 表示刷新已有结果，`save` 表示持久化。事件回调用 `onBack`、`onTabSelected`、`onCheckedChange` 等。修改外部状态或执行 I/O 的动作应能从名称或契约看出来。
- **通用名称描述实际用途。** 跨页面可用的列表区块标题叫 `SectionHeader`。只有搜索场景确有搜索行为时，`SearchSectionHeader` 才值得独立存在。两套实现需要区分时，优先用包名，同包内配对实现可统一使用 `<Component>Salt` 和 `<Component>Miuix`。
- **去掉不能帮助调用者判断的词。** 审查 `Base`、`Custom`、`Helper`、`Impl`、`V2` 等限定词。只有在能说明继承关系、具体实现或有效版本边界时保留。名称无强制字数，删减以调用处仍清楚为准。

应用内部的 Kotlin 名称随调用方一起迁移。保留旧名称前，先指出使用它的协议字段、持久化数据或外部调用方，并核对实际读写路径。例如 GitHub 响应的 `avatar_url` 是协议字段，普通 DTO 的类名可以直接改。只有实际存取的序列化类型标记等契约才需要保留旧类型名。框架异常中附带的 Kotlin 类名可以随重构更新。

## 3. 合并、拆分与复用

| 情况 | 处理原则 |
| --- | --- |
| 两个入口只有名称不同，参数和行为一致 | 将真正的实现改成准确的通用名称，迁移所有调用并删除旧壳。需要保留公开 API 时使用兼容入口并注明退出条件 |
| 相同规则在多处计算 | 在规则所有者处实现一份，调用者复用结果。例如设置可用性和实际渲染应复用同一设备能力判断 |
| 模型字段、语义、默认值、生命周期和兼容契约相同 | 合并模型，保留主类型名。多个紧密相关声明可同文件，文件用实际主题命名，如 `LibrarySort.kt` |
| 模型长得相似，但属于不同边界 | 按解析和映射后的实际用途判断，保留各自承担不同职责的模型。例如 GitHub 贡献者响应中的 `type` 用于过滤机器人，关于页只消费过滤后的结果 |
| 两套 UI 共享业务输入和交互规则，但布局不同 | 共享状态和事件契约。主题适配组织各自布局。仅在行为与样式语义一致时提取通用控件 |
| 文件很长，或一次改动常牵涉多个职责 | 排查是否混放了状态、IO、业务决策、布局和交互。沿数据流和职责拆分，页面专用代码留在邻近包。几百行可触发审查，拆分位置看职责与调用关系 |

参数有真实的变化点才提升为组件 API。仅供当前控件用的颜色、边距、阴影和资源作为本地实现或主题定义。包装层应承担主题适配、布局分发、安全区、状态生命周期、协议转换或兼容等职责之一。实现双主题接口的方法可以直接委托给现有控件，共用渲染实现。迁移内部组件时，核对所有调用方，清理没有用处的参数、槽位和分支。保留对外契约仍需要的参数，并注明兼容原因。

## 4. 代码风格

- 沿用 `gradle.properties` 声明的 Kotlin 官方风格。UTF-8、四空格缩进，使用 IDE 按项目风格整理 import，移除未使用和新增的通配 import。需要同时使用两套主题的同名 API 时用明确的 import 别名。
- 函数应表达一个完整动作。短表达式保持直接，多步骤读写和分支用普通语句写清楚。优先不可变数据和 `val`，只在明确的状态持有者内修改状态。跨层调用按已有的错误、取消与空值契约处理。
- Compose 可复用组件接收 `modifier: Modifier = Modifier` 时，把它用于根布局的入口。必填状态参数放前面，可选样式在后，`content` 等尾随 lambda 放最后。相同或易混的 Boolean、颜色、尺寸等值在调用处用具名参数。
- 页面层消费状态并发出事件。持久化、文件写入、跨页面任务交给已负责这些行为的状态持有者或仓库。组合内的副作用放在合适的 effect 中，手势取消与正常完成分开处理。
- 用户可见文案使用资源。注释说明约束、兼容理由和不变量。导入的上游实现保留来源与许可，将本项目的适配集中在边界。
- 文档描述当前有效的用法、接口与维护约定，保留执行前提、必要步骤和验证方式。变更经过与完成情况写入提交说明，同一规则只维护一处，其他文档通过链接引用。
- 格式化限于本次编辑的范围。规则能被工具稳定检查时，再考虑引入格式检查，现存差异分批处理。

## 5. 重构与验证

1. **先列边界。** 点明要整理的代码和全部调用点，确认哪些入口、默认值、UI 和行为需要保持。检查生成路由、Manifest、序列化字段、Room 结构、备份、JNI 或插件协议是否依赖旧名称。迁移更改与行为修复可独立审查时分开提交。
2. **按功能迁移。** 查找已有实现，迁移调用、资源和必要映射，清理迁移后的旧入口。在多个页面都使用的组件变动时，同时核对相关消费方。功能和 UI 保持现状，已确认的缺陷或不一致可单独修复并明确记录。
3. **按风险验证。** 文档或局部命名核对引用与差异。拆分 Compose 页面时，核对局部状态和副作用在组合树中的位置、状态键和触发条件，尤其是 `rememberSaveable` 的恢复边界。Kotlin 结构改动至少运行 `.\gradlew.bat :lyrico-app:compileReleaseKotlin`（Unix 用 `./gradlew`）和 `git diff --check`，涉及依赖或产物时运行相应构建。保留现有测试，按风险跑相关用例。
4. **说明证据。** 共用 UI 检查 Miuix、SaltUI 及实际相关的明暗主题、系统栏或交互分支。设置和数据改动核对保存、重读和旧数据。编译、静态检查和设备实测分别报告，设备验证按本次授权范围进行。

## 参考依据

下列文档提供通用原则。目录、角色后缀、双主题边界和提交粒度属于 Lyrico 的具体约定。

- [Kotlin 官方编码约定](https://kotlinlang.org/docs/coding-conventions.html) 和 [Kotlin API 一致性指南](https://kotlinlang.org/docs/api-guidelines-consistency.html)
- [Google Android Kotlin 风格指南](https://developer.android.com/kotlin/style-guide)
- [Jetpack Compose API 设计指南](https://developer.android.com/develop/ui/compose/api-guidelines)
- [Android 应用架构](https://developer.android.com/topic/architecture) 和 [常见模块化模式](https://developer.android.com/topic/modularization/patterns)
