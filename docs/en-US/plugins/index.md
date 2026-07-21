# Lyrico Plugins

Lyrico uses plugins to provide online music information. Plugins can extend song search, lyrics lookup, cover search, and source-specific configuration.

These pages are for two audiences:

- **Users** who want to install, enable, configure, disable, or remove plugins.
- **Developers** who want to build or maintain source plugins.

## I Want To Use Plugins

| Page | Use it for |
|------|------------|
| [Using Plugins](./using.md) | Importing, enabling, configuring, disabling, and uninstalling plugins |
| [Configuration and Result Fields](./config-metadata.md) | Understanding `configFields`, `fields`, and `internal` |

## I Want To Build Plugins

| Order | Page | Contents |
|------|------|----------|
| 1 | [Build a Plugin](./examples.md) | A complete plugin from manifest to packaged ZIP |
| 2 | [Plugin Package Structure](./composition.md) | File layout, entry scripts, helper folders, and ZIP rules |
| 3 | [Plugin Functions](./plugin-functions.md) | Implement `searchSongs`, `getLyrics`, and `searchCovers` |
| 4 | [Manifest Reference](./manifest.md) | Manifest fields, values, and validation rules |
| 5 | [Host API Reference](./host-api.md) | HTTP, crypto, encoding, compression, XML, and logging APIs |

## I Want To Understand The Runtime

Read [Architecture and Lifecycle](./overview.md) to learn how Lyrico imports, validates, installs, loads, executes, and unloads plugins.

## Minimal Plugin Shape

```text
com.example.source/
├── manifest.json
└── source.js
```

`manifest.json` declares plugin identity, capabilities, entry file, and configuration fields. `source.js` implements the plugin functions.

Minimal `manifest.json`:

```json
{
  "id": "com.example.source",
  "name": "Example Plugin",
  "versionCode": 1,
  "versionName": "1.0.0",
  "apiVersion": 3
}
```

Minimal `source.js`:

```javascript
function searchSongs(request) {
  return JSON.stringify([
    {
      id: "12345",
      title: "Example Song",
      artist: "Example Artist"
    }
  ]);
}
```

## Runtime

- Plugins are written in JavaScript and run in Android's embedded QuickJS runtime.
- Plugin functions are global functions. ES modules are not supported.
- Plugins access host APIs through `globalThis.Platform`.
- Plugins have execution time and memory limits, so they should focus on search, parsing, and lightweight conversion.
