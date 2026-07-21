# Plugin Package Structure

This page explains how a plugin package is organized on disk, including `manifest.json`, the entry script, helper scripts, icons, and ZIP packages that contain multiple plugins. Read this page before writing a plugin so the package layout rules are clear.

## File Structure

A complete Lyrico plugin contains these files:

```
<plugin-root>/
├── manifest.json       # Required: plugin manifest
├── source.js           # Required: entry script, or another file set by manifest.entry
├── lib/                # Optional: helper script directory declared in manifest.includeDirs
│   ├── 01_http.js      #   Concatenated one by one after sorting by filename
│   └── 02_parser.js
└── icon.png            # Optional: plugin icon declared in manifest.icon
```

## Entry File

### Selecting The Entry

Use the `entry` field in `manifest.json` to select the entry file:

```json
{
  "entry": "source.js"
}
```

- **Default**: `"source.js"`
- **Limit**: must use the `.js` extension and be relative to the plugin root
- **Validation rules**:
  - Must not contain `..`, `\`, or `\0`
  - Must not start with `/` or `\`
  - Must stay inside the plugin root and cannot escape through symlinks
  - File size must be ≤ 1 MB

### Entry Script Responsibilities

The entry script must define **global functions**. QuickJS does not support ES Modules in the plugin runtime:

```javascript
// Correct: global function declaration
function searchSongs(request) {
  return JSON.stringify([...]);
}

// Wrong: export is not supported
export function searchSongs(request) { ... }
```

## Include System

Plugins can split shared logic into helper files under directories such as `lib/`. At runtime, these files are **concatenated** into one complete script before execution.

### Declaring Include Directories

```json
{
  "includeDirs": ["lib"]
}
```

- **Type**: `string[]`
- **Default**: `[]`
- **Limits**:
  - Each directory must be a relative path under the plugin root
  - The directory cannot be `"."`
  - The directory cannot be outside the plugin root
  - The path must exist and be a directory

### Concatenation Rules

1. Traverse each directory in the order declared by `includeDirs`
2. Sort `.js` files in each directory by relative path
3. Concatenate all helper scripts before the entry script
4. Add a `sourceURL` comment before each script for debugging

**Final script layout:**

```
[bootstrap: include() implementation]
[semicolon separator]
// ===== Platform include: lib/01_http.js =====
[01_http.js contents]
//# sourceURL=lib/01_http.js
[semicolon separator]
// ===== Platform include: lib/02_parser.js =====
[02_parser.js contents]
//# sourceURL=lib/02_parser.js
[semicolon separator]
// ===== Platform entry: source.js =====
[source.js contents]
//# sourceURL=source.js
```

### include()

Helper scripts can call `include(path)` to declare dependencies on other helper scripts:

```javascript
// lib/02_parser.js
include("lib/01_http.js");  // Declared by includeDirs => OK

include("lib/secret.js");   // Not declared by includeDirs => throws Error
```

- `include()` is essentially **path validation**, because all helper scripts have already been concatenated
- Passing a path that is not declared by `includeDirs` throws an exception
- `include()` is injected by the bootstrap as `globalThis.include`

## Icon

```json
{
  "icon": "icon.png"
}
```

- **Type**: `string | null`
- **Default**: `null`
- **Supported formats**: `png`, `jpg`, `jpeg`, `webp`
- **Limit**: the icon file must exist and stay inside the plugin root

## Complete File Structure Examples

### Example Music Source Plugin

```
com.example.source/
├── manifest.json        # Plugin manifest
├── source.js            # Entry: searchSongs/getLyrics/searchCovers
└── lib/
    ├── 01_http.js       # API encryption, signing, request wrapper
    └── 02_lrc.js        # LRC parsing and line alignment
```

### Plugin With Configuration

```
com.example.source/
├── manifest.json        # Multiple config fields: API type, token, cover size
├── source.js            # Entry: search, lyrics, covers
└── lib/
    └── 01_api.js        # JWT token validation and request wrapper
```

## Multiple Plugins In One Package

A single ZIP can contain multiple plugins. Each plugin has its own `manifest.json`:

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
└── shared-lib/              # Not inside any plugin root, so it is not installed
    └── common.js
```

**Install exclusion rule**: if one plugin root is nested under another plugin root, the nested subdirectory is excluded during installation to prevent duplicate installs.

## File Limit Quick Reference

| Limit | Maximum |
|------|---------|
| `manifest.json` size | 128 KB |
| Entry script size | 1 MB |
| Single plugin total size | 5 MB |
| ZIP total size | 30 MB |
| Files per package | 1000 |
| Plugins per package | 20 |
| ZIP entry depth | 16 levels |
| Entry script extension | Must be `.js` |
| Icon extension | `.png` / `.jpg` / `.jpeg` / `.webp` |
