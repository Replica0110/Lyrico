# Lyrico Plugin Devkit

桌面端插件调试验证工具，用于在开发机上验证 Lyrico 搜索源插件。

## 使用方式

直接通过 Node 运行：

```bash
node tools/plugin-devkit/src/cli.js validate ./my-plugin
node tools/plugin-devkit/src/cli.js inspect ./my-plugin
node tools/plugin-devkit/src/cli.js test ./my-plugin searchSongs --keyword "晴天"
node tools/plugin-devkit/src/cli.js pack ./my-plugin
```

也可以在 `tools/plugin-devkit` 目录下链接为命令：

```bash
npm link
lyrico-plugin validate ./my-plugin
```

要求：

- Node.js 20+
- 系统可用的 `curl` 命令，用于同步模拟 Lyrico 宿主 HTTP API

## 命令

### validate

校验插件目录：

```bash
lyrico-plugin validate ./my-plugin
```

会检查：

- `manifest.json`
- 插件 ID、版本号、API 版本
- `entry`、`includeDirs`、`icon`
- `capabilities`
- `configFields`
- 插件目录大小
- 运行结果中的 `fields` 是否只使用宿主标准字段
- `internal` 是否满足数量和大小限制

`configFields` 支持 `text`、`password`、`number`、`switch`、`dropdown`、`textarea` 和只展示说明、不写入运行时配置的 `markdown` 类型。

### inspect

输出插件摘要：

```bash
lyrico-plugin inspect ./my-plugin
```

包括插件信息、能力、配置项和脚本加载顺序。

### test

执行插件函数：

```bash
lyrico-plugin test ./my-plugin searchSongs --keyword "晴天" --page-size 5
lyrico-plugin test ./my-plugin searchCovers --keyword "晴天"
lyrico-plugin test ./my-plugin getLyrics --song ./song.json
```

支持配置文件：

```bash
lyrico-plugin test ./my-plugin searchSongs --keyword "晴天" --config ./config.json
```

`config.json` 可以是配置对象：

```json
{
  "api_key": "xxx",
  "region": "cn"
}
```

也可以是：

```json
{
  "config": {
    "api_key": "xxx",
    "region": "cn"
  }
}
```

### pack

打包插件目录：

```bash
lyrico-plugin pack ./my-plugin
```

默认输出到插件目录同级的 `dist/<plugin-id>-<versionName>.zip`。

指定输出路径：

```bash
lyrico-plugin pack ./my-plugin --out ./dist/my-plugin.zip
```

## 注意

Devkit 会尽量模拟 Lyrico 的插件运行环境，但它运行在桌面端 Node.js 中，不等同于 Android 端 QuickJS。网络、TLS、系统 UA 和少量 JavaScript 运行时行为可能存在差异。最终发布前仍建议在 Lyrico 中做一次真实导入验证。
