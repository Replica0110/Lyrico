# Architecture And Lifecycle

This page is for maintainers and advanced plugin developers. It explains how Lyrico imports, validates, installs, loads, executes, and uninstalls plugins. You do not need to read this before writing your first plugin.

In the current protocol, the manifest only declares identity, version, entry, capabilities, and `configFields`. Plugin results return standard metadata through `fields` and plugin-private context through `internal`; field application policy is managed by the Lyrico host.

## System Architecture

The Lyrico plugin system is a source-plugin framework based on the **QuickJS embedded JavaScript engine** and runs on Android. Plugins are written in JavaScript and executed in the native QuickJS runtime through a JNI bridge.

### Layers

```
┌─────────────────────────────────────────────┐
│  Plugin JS files (manifest.json + source.js) │  ← Written by developers
├─────────────────────────────────────────────┤
│  Plugin runtime layer                         │
│  QuickJsRuntime  /  PluginJsRuntime          │  ← JS engine
│  QuickJsHostApi                              │  ← Host capability injection
│  HostApiRegistry                             │  ← API registry
├─────────────────────────────────────────────┤
│  Plugin management layer                      │
│  SourcePluginInstaller                       │  ← Import/install/uninstall
│  PluginSearchSourceManager                   │  ← Cache/activate
│  ScriptSearchSourceFactory                   │  ← Build script source
├─────────────────────────────────────────────┤
│  Data layer                                   │
│  PluginManifest (data model)                  │
│  SourcePluginEntity (Room DB)                 │
│  SourcePluginRepository (DAO)                 │
├─────────────────────────────────────────────┤
│  App layer                                    │
│  PluginViewModel                             │  ← UI state management
│  SearchSourceProvider                        │  ← Search source exposure
└─────────────────────────────────────────────┘
```

### Core Component Responsibilities

| Component | Responsibility |
|----------|----------------|
| `PluginManifest` | Plugin manifest data model defining basic information, capabilities, and config |
| `SourcePluginInstaller` | Imports, validates, and installs plugins from ZIP files |
| `ScriptSearchSourceFactory` | Reads manifest + JS files and concatenates them into a complete script |
| `PluginSearchSourceManager` | Caches all started `ScriptSearchSource` instances |
| `ScriptSearchSource` | Wraps a single plugin search source and manages its JS runtime lifecycle |
| `QuickJsRuntime` | QuickJS engine wrapper that executes JS scripts and calls global functions |
| `QuickJsHostApi` | Implements host APIs such as HTTP, crypto, encoding, compression, and XML |
| `PluginJsonParser` | Parses plugin JSON returns into app-internal data models |

## Complete Flow

### Stage 1: Import And Validation

1. The user selects a `.zip` file from the file manager
2. `SourcePluginInstaller.prepareImport()` extracts the ZIP to a temporary directory
3. Lyrico recursively finds every `manifest.json` file in the package
4. Each manifest is validated:

| Validation item | Rule |
|-----------------|------|
| ID format | Must match `^[a-zA-Z][a-zA-Z0-9_]*(\.[a-zA-Z][a-zA-Z0-9_]*)+$` reverse-domain format |
| API version | `apiVersion` must be in the host-supported range **1..3**; `minHostApiVersion` cannot exceed the current host API **3** |
| Capabilities | If `capabilities` is declared, it must include `searchSongs` |
| Entry file | Must exist, use `.js`, stay inside the plugin root, and be ≤ 1 MB |
| Include directories | Directories in `includeDirs` must exist and stay inside the plugin root |
| Icon | If specified, it must exist and use `png`/`jpg`/`jpeg`/`webp` |

5. Version conflicts are checked against installed plugins:

| Scenario | Conflict type |
|----------|---------------|
| Plugin does not exist | `NONE` |
| New versionCode > old versionCode | `UPDATE` |
| New versionCode == old versionCode | `OVERWRITE` |
| New versionCode < old versionCode | `DOWNGRADE`, rejected by default |

6. A `PluginImportSession` is returned with candidate and failure lists

### Stage 2: Install

1. `installPrepared()` processes each candidate
2. Installation uses a **staging directory** named `.staging-<id>-<timestamp>` for atomic replacement:
   - Copy all files under the plugin root into the staging directory
   - Automatically exclude nested child plugin directories
   - Verify total size ≤ **5 MB** (`maxSinglePluginBytes`)
3. After staging succeeds, replace the previous plugin directory if it exists

### Stage 3: Store In Database

After installation, plugin metadata is written to the Room `source_plugins` table:

| Field | Description |
|------|-------------|
| `id` | Unique plugin ID |
| `name` | Display name |
| `versionCode` / `versionName` | Version information |
| `author` / `description` | Author and description |
| `apiVersion` | Plugin API version |
| `pluginDir` | Absolute install directory |
| `entryFile` | Entry filename |
| `includeDirsJson` | JSON serialization of include directories |
| `iconPath` | Absolute icon path, optional |
| `enabled` | Enabled state, default `false` on first install |
| `sortOrder` | Ordering value |
| `installedAt` / `updatedAt` | Timestamps |

### Stage 4: Load And Activate

1. `PluginSearchSourceManager.buildSourcesLocked()` iterates over all plugins with `enabled = true`
2. For each plugin, it calls `ScriptSearchSourceFactory.create()`:
   - Read `manifest.json`
   - Concatenate JS scripts in order: first every `.js` file in `includeDirs` sorted by path, then the entry file
   - Inject the bootstrap that implements `include()` at the top of the script
3. A `ScriptSearchSource` instance is created and cached by plugin ID
4. The JS runtime is **lazy initialized**: `QuickJsRuntime` is created and the complete script is executed only when `searchSongs`/`getLyrics`/`searchCovers` is first called

### Stage 5: Runtime Calls

1. The user enters a keyword in the search UI
2. `SearchSourceProvider` obtains all enabled search sources from `PluginSearchSourceManager`
3. Each source receives `searchSongs(keyword, page, separator, pageSize)`
4. `ScriptSearchSource` serializes the request as JSON and invokes the plugin global function `searchSongs(requestJson)` through JNI
5. The plugin returns a JSON string, and `PluginJsonParser` parses it into `SongSearchResult` values

### Stage 6: Enable / Disable

- `PluginViewModel.setEnabled(id, enabled)` updates the `enabled` column in the database
- `PluginSearchSourceManager.invalidate(pluginId)` removes the cached source and closes its runtime
- Only plugins with `enabled = true` appear in the `observeEnabledSources()` Flow

### Stage 7: Uninstall

1. Delete the record from the Room database
2. Call `PluginSearchSourceManager.invalidate(pluginId)` to close the runtime
3. Remove user configuration for the plugin
4. Recursively delete `plugins/sources/<pluginId>/`

### Stage 8: Close / Release

- `PluginSearchSourceManager.close()` closes all cached `ScriptSearchSource` instances
- `ScriptSearchSource.close()` closes the QuickJS runtime and stops the dedicated executor
- Import temporary directories are removed by `SourcePluginInstaller.discardImport()`

## Import Limits

| Limit | Default |
|------|---------|
| Total ZIP size after extraction | 30 MB |
| Single plugin directory size | 5 MB |
| Manifest file size | 128 KB |
| Entry script size | 1 MB |
| Maximum plugins per package | 20 |
| Maximum files per package | 1000 |
| ZIP entry path depth | 16 |

### ZIP Entry Safety Rules

- Empty names are not allowed
- `\0` NUL bytes are not allowed
- Absolute paths are not allowed, including paths starting with `/` or `\`
- Backslashes are not allowed; ZIP paths must use `/`
- `..` is not allowed, preventing directory traversal
- Every file must extract inside the target directory

## Runtime Constraints

| Limit | Value |
|------|-------|
| Memory limit | 64 MB |
| Stack size | 2 MB |
| Default execution timeout | 15 seconds |
| Plugin operation timeout from UI | 30 seconds |
| Dedicated single-thread executor per plugin | `QuickJS-<pluginId>` |

## Host Capability Overview

Plugins access host capabilities through `globalThis.Platform`. There are **41 APIs**:

| Category | API count | Purpose |
|----------|-----------|---------|
| `app` | 2 | Host app information and User-Agent |
| `runtime` | 1 | Runtime information |
| `cache` | 4 | Plugin-private string cache with expiry and deletion |
| `crypto` | 4 | MD5 and AES-ECB encryption/decryption |
| `base64` | 11 | Base64/Base64URL encode, decode, truncate, and byte conversion |
| `bytes` | 2 | XOR byte operations |
| `compression` | 2 | zlib inflate decompression |
| `http` | 8 | GET/POST requests for text and binary responses, old and new APIs |
| `xml` | 4 | XML/TTML lookup and rewriting |
| `log` | 3 | debug/warn/error logging |

See [Host API Reference](./host-api.md) for details.

## Search Source Interface

Each enabled plugin is exposed to upper layers as a `SearchSource`:

```kotlin
interface SearchSource {
    val id: String           // Unique plugin ID
    val name: String         // Display name
    val capabilities: Set<SearchSourceCapability>  // SEARCH_SONGS, GET_LYRICS, SEARCH_COVERS
    val configFields: List<PluginConfigField>      // Configurable fields

    suspend fun searchSongs(keyword, page, separator, pageSize): List<SongSearchResult>
    suspend fun getLyrics(song): LyricsResult?
    suspend fun searchCovers(keyword, pageSize): List<SongSearchResult>
}
```
