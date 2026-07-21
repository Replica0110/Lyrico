# Debug Plugins Locally

Lyrico provides a desktop plugin devkit that can validate, run, and package plugins on your development machine. This avoids manually importing the plugin into the Android app after every change.

The devkit focuses on manifest basics, entry files, function return values, standard `fields` keys, and `internal` size limits.

The devkit lives in the [Replica0110/Lyrico-Plugins](https://github.com/Replica0110/Lyrico-Plugins) plugin repository. Tool location:

```text
tools/plugin-devkit/
```

## Requirements

- Node.js 20+
- A system `curl` command available on `PATH`

## Run Directly

From the [Replica0110/Lyrico-Plugins](https://github.com/Replica0110/Lyrico-Plugins) repository root:

```bash
node tools/plugin-devkit/src/cli.js validate ./my-plugin
node tools/plugin-devkit/src/cli.js inspect ./my-plugin
node tools/plugin-devkit/src/cli.js test ./my-plugin searchSongs --keyword "晴天"
node tools/plugin-devkit/src/cli.js pack ./my-plugin
```

You can also enter `tools/plugin-devkit` and register the command with `npm link`:

```bash
npm link
lyrico-plugin validate ./my-plugin
```

## Validate A Plugin

```bash
lyrico-plugin validate ./my-plugin
```

Validation checks include:

- Whether `manifest.json` exists and is valid JSON
- Whether plugin ID, version, and API version are valid
- Whether `entry`, `includeDirs`, and `icon` exist and use safe paths
- Whether `capabilities` are supported
- Whether `configFields` has a valid structure
- Whether returned `fields` only use standard keys
- Whether `internal` satisfies count and size limits
- Whether the plugin directory exceeds app limits

## Inspect A Plugin

```bash
lyrico-plugin inspect ./my-plugin
```

This prints plugin information, declared capabilities, config fields, and script loading order.

## Run Plugin Functions

Test song search:

```bash
lyrico-plugin test ./my-plugin searchSongs --keyword "晴天" --page-size 5
```

Test cover search:

```bash
lyrico-plugin test ./my-plugin searchCovers --keyword "晴天"
```

Test lyrics retrieval:

```bash
lyrico-plugin test ./my-plugin getLyrics --song ./song.json
```

The tool prints:

- Function duration
- Plugin logs
- Raw return value
- Result parsed using Lyrico rules
- Structure errors and warnings

## Simulate Config Values

If a plugin depends on user configuration, pass a config file:

```bash
lyrico-plugin test ./my-plugin searchSongs --keyword "晴天" --config ./config.json
```

`config.json` can contain config keys directly:

```json
{
  "api_key": "xxx",
  "region": "cn"
}
```

It can also wrap them in `config`:

```json
{
  "config": {
    "api_key": "xxx",
    "region": "cn"
  }
}
```

## Package A Plugin

```bash
lyrico-plugin pack ./my-plugin
```

Default output:

```text
dist/<plugin-id>-<versionName>.zip
```

You can choose an output path:

```bash
lyrico-plugin pack ./my-plugin --out ./dist/my-plugin.zip
```

## Notes

The devkit runs in desktop Node.js and tries to simulate Lyrico's plugin host environment, but it is not identical to Android QuickJS. Network behavior, TLS, system User-Agent, and a few JavaScript runtime details may differ.

Before publishing, still verify the plugin once by importing it into Lyrico.
