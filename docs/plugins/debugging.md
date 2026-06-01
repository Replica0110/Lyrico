# 本地调试插件

Lyrico 提供桌面端插件调试验证工具，可以在开发机上直接校验、运行和打包插件，避免每次修改后都手动导入到 Android 应用中验证。

当前协议不再校验 `requiredHostApis` 或 `metadataFields`。调试重点是 manifest 基础字段、入口文件、运行函数返回值、`fields` 标准 key 和 `internal` 大小限制。

工具位置：

```text
tools/plugin-devkit/
```

## 环境要求

- Node.js 20+
- 系统可用的 `curl` 命令

## 直接运行

在项目根目录执行：

```bash
node tools/plugin-devkit/src/cli.js validate ./my-plugin
node tools/plugin-devkit/src/cli.js inspect ./my-plugin
node tools/plugin-devkit/src/cli.js test ./my-plugin searchSongs --keyword "晴天"
node tools/plugin-devkit/src/cli.js pack ./my-plugin
```

也可以进入 `tools/plugin-devkit` 后用 `npm link` 注册命令：

```bash
npm link
lyrico-plugin validate ./my-plugin
```

## 校验插件

```bash
lyrico-plugin validate ./my-plugin
```

校验内容包括：

- `manifest.json` 是否存在且格式正确
- 插件 ID、版本号、API 版本是否合法
- `entry`、`includeDirs`、`icon` 是否存在且路径安全
- `capabilities` 是否受支持
- `configFields` 结构是否正确
- 运行结果中的 `fields` 是否只包含标准字段
- `internal` 是否满足数量和大小限制
- 插件目录大小是否超过应用限制

## 查看插件摘要

```bash
lyrico-plugin inspect ./my-plugin
```

会输出插件信息、能力声明、配置项和脚本加载顺序。

## 执行插件函数

测试歌曲搜索：

```bash
lyrico-plugin test ./my-plugin searchSongs --keyword "晴天" --page-size 5
```

测试封面搜索：

```bash
lyrico-plugin test ./my-plugin searchCovers --keyword "晴天"
```

测试歌词获取：

```bash
lyrico-plugin test ./my-plugin getLyrics --song ./song.json
```

工具会输出：

- 函数耗时
- 插件日志
- 原始返回值
- 按 Lyrico 解析规则转换后的结果
- 结构错误和警告

## 模拟配置项

如果插件依赖用户配置，可以传入配置文件：

```bash
lyrico-plugin test ./my-plugin searchSongs --keyword "晴天" --config ./config.json
```

`config.json` 可以直接写配置键值：

```json
{
  "api_key": "xxx",
  "region": "cn"
}
```

也可以写成：

```json
{
  "config": {
    "api_key": "xxx",
    "region": "cn"
  }
}
```

## 打包插件

```bash
lyrico-plugin pack ./my-plugin
```

默认输出：

```text
dist/<plugin-id>-<versionName>.zip
```

也可以指定输出路径：

```bash
lyrico-plugin pack ./my-plugin --out ./dist/my-plugin.zip
```

## 注意事项

调试工具运行在桌面端 Node.js 中，会尽量模拟 Lyrico 的插件宿主环境，但它不等同于 Android 端 QuickJS。网络、TLS、系统 User-Agent 和少量 JavaScript 运行时行为可能存在差异。

发布前仍建议在 Lyrico 中做一次真实导入验证。
