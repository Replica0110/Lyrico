# Manifest Reference

`manifest.json` describes plugin identity, version, entry file, capabilities, and settings. Plugins should not declare which metadata fields they may return, which host APIs they need, or how fields should be written to audio tags.

Field application policy belongs to Lyrico. Plugins return data at runtime.

## Field Overview

| Field | Type | Required | Default | Description |
|------|------|----------|---------|-------------|
| `id` | `string` | Yes | - | Unique plugin ID in reverse-domain format |
| `name` | `string` | Yes | - | Display name |
| `versionCode` | `int` | Yes | - | Version code, at least 1 |
| `versionName` | `string` | Yes | - | Version name |
| `apiVersion` | `int` | Yes | - | Plugin API version |
| `minHostApiVersion` | `int` | No | `1` | Minimum host API version |
| `author` | `string` | No | `""` | Author |
| `description` | `string` | No | `""` | Description |
| `entry` | `string` | No | `"source.js"` | Entry JavaScript file |
| `includeDirs` | `string[]` | No | `[]` | Local helper script directories |
| `icon` | `string \| null` | No | `null` | Relative icon path |
| `capabilities` | `string[]` | No | `[]` | Plugin capabilities |
| `configFields` | `ConfigField[]` | No | `[]` | User-configurable fields |

Fields used by older protocols to declare host APIs, returned fields, or write policies are no longer needed. New plugins should not include them.

## Example

```json
{
  "id": "com.example.source",
  "name": "Example Source",
  "versionCode": 1,
  "versionName": "1.0.0",
  "author": "Plugin Author",
  "description": "Example source plugin",
  "apiVersion": 3,
  "minHostApiVersion": 1,
  "entry": "source.js",
  "includeDirs": ["lib"],
  "capabilities": ["searchSongs", "getLyrics", "searchCovers"],
  "configFields": [
    {
      "key": "lyrics_source",
      "title": "Lyrics source",
      "summary": "Choose which lyrics source the plugin should prefer",
      "type": "dropdown",
      "required": true,
      "defaultValue": "official",
      "options": [
        { "value": "official", "label": "Official lyrics" },
        { "value": "user", "label": "User-uploaded lyrics" }
      ]
    }
  ]
}
```

## Field Details

`id` must use reverse-domain format, such as `com.example.music_source`.

`apiVersion` is used for plugin protocol compatibility. Host APIs are backward compatible: a host at version 3 can load plugins whose `apiVersion` is 1, 2, or 3, while plugins declaring a newer version are rejected.

`minHostApiVersion` declares the minimum host API version actually required by the plugin. It must be at least 1 and cannot exceed the current host version. Plugins can still inspect `Platform.runtime.getInfo().supportedHostApis` for individual capabilities; missing capabilities are returned as runtime errors.

`capabilities` supports:

| Capability | Function |
|------------|----------|
| `searchSongs` | `searchSongs(request)` |
| `getLyrics` | `getLyrics(request)` |
| `searchCovers` | `searchCovers(request)` |

If `capabilities` is declared, source plugins must include `searchSongs`.

`includeDirs` can only reference relative directories inside the plugin package. Absolute paths, `..`, network URLs, and cross-plugin files are not allowed.

## configFields

`configFields` declares user-configurable options. Saved values are passed into every plugin function call through `request.config`.

Config field JSON structure:

| Field | Type | Required | Default | Description |
|------|------|----------|---------|-------------|
| `key` | `string` | Yes | - | Config key, read via `request.config[key]` in JS |
| `title` | `string` | Yes | - | Label shown in the config UI |
| `summary` | `string` | No | `null` | Descriptive text shown below the input |
| `group` | `string` | No | `""` | Group name; fields with the same group are rendered together in a card. Defaults to "Basic" |
| `type` | `string` | Yes | - | Input control type, determines UI rendering |
| `required` | `boolean` | No | `false` | Whether the field must be filled before saving |
| `defaultValue` | `string` | No | `""` | Initial value when the plugin is first loaded |
| `options` | `Option[]` | No | `[]` | Option list, required only for `dropdown` type |
| `dependency` | `Dependency` | No | `null` | Conditional visibility rule, see [Dependency System](#dependency-system-dependency) |

`Option` structure:

| Field | Type | Description |
|------|------|-------------|
| `value` | `string` | Actual value stored in config |
| `label` | `string` | Display text in the dropdown list |
| `summary` | `string` | Extra description shown in grey in the dropdown |

### Type Reference

Each `type` controls how the field appears and behaves in the UI.

#### text — Single-line text

```json
{ "key": "server_url", "title": "Server URL", "summary": "Base URL of the API", "type": "text", "required": true, "defaultValue": "https://api.example.com" }
```

#### password — Secret / token

Input is masked. Suitable for API keys, tokens, cookies, and other sensitive data.

```json
{ "key": "api_token", "title": "API Token", "summary": "Get from your service provider dashboard", "type": "password", "required": true, "defaultValue": "" }
```

#### number — Numeric input

Restricts input to digit characters. Useful for timeout seconds, page size, etc.

```json
{ "key": "timeout", "title": "Timeout (seconds)", "summary": "HTTP request timeout", "type": "number", "defaultValue": "15" }
```

#### switch — Toggle

Boolean-like control stored as the string `"true"` or `"false"`. Convert explicitly in JS.

```json
{ "key": "use_proxy", "title": "Use proxy", "summary": "Route requests through a proxy server", "type": "switch", "defaultValue": "false" }
```

JS: `var useProxy = request.config.use_proxy === "true";`

#### dropdown — Dropdown selector

```json
{
  "key": "lyrics_source",
  "title": "Lyrics source",
  "summary": "Which lyrics source the plugin should prefer",
  "type": "dropdown",
  "required": true,
  "defaultValue": "official",
  "options": [
    { "value": "official", "label": "Official lyrics", "summary": "LRC provided by the copyright holder" },
    { "value": "user", "label": "User-uploaded lyrics", "summary": "Translation submitted by users" }
  ]
}
```

#### textarea — Multi-line text

Suitable for long JSON config, custom scripts, multi-line notes.

```json
{ "key": "custom_headers", "title": "Custom headers", "summary": "One header per line, format: Key: Value", "type": "textarea", "defaultValue": "" }
```

#### markdown — Explanatory text

Display-only field. Not included in `request.config`. Use for usage instructions, notes, sponsorship info. `defaultValue` is rendered as Markdown body; `title` as heading.

```json
{
  "key": "help_note",
  "title": "Usage instructions",
  "type": "markdown",
  "defaultValue": "### How to get an API Key\n\n1. Register an account\n2. Go to API Management\n3. Click Generate Key\n\n> Free tier: 1000 requests/day"
}
```

### Grouping

Fields sharing the same `group` value are rendered together in a single card, with the group name as the card title. Empty values default to "Basic".

```json
"configFields": [
  { "key": "api_key", "title": "API Key", "group": "Authentication", "type": "password", "required": true },
  { "key": "api_secret", "title": "API Secret", "group": "Authentication", "type": "password" },
  { "key": "region", "title": "Region", "group": "Request", "type": "dropdown", "defaultValue": "cn", "options": [...] },
  { "key": "timeout", "title": "Timeout (s)", "group": "Request", "type": "number", "defaultValue": "15" },
  { "key": "cover_size", "title": "Cover size", "group": "Cover", "type": "dropdown", "defaultValue": "800", "options": [...] }
]
```

The UI renders three cards: **Authentication** (2 items), **Request** (2 items), **Cover** (1 item).

### Dependency System (`dependency`)

`dependency` controls field visibility: the field only appears in the UI when its condition is satisfied. Four sub-types are supported:

| Type | Description |
|------|-------------|
| `match` | Visible when a specified field equals a given value |
| `and` | Visible when all sub-conditions are met |
| `or` | Visible when any sub-condition is met |
| `not` | Visible when the condition is NOT met |

#### match — Simple equality

```json
{
  "key": "proxy_url",
  "title": "Proxy URL",
  "type": "text",
  "dependency": { "match": { "key": "use_proxy", "value": "true" } }
}
```

#### and — All conditions

```json
{
  "key": "token_url",
  "title": "Token endpoint",
  "type": "text",
  "dependency": {
    "and": {
      "conditions": [
        { "match": { "key": "auth_type", "value": "oauth" } },
        { "match": { "key": "custom_server", "value": "true" } }
      ]
    }
  }
}
```

#### or — Any condition

```json
{
  "key": "proxy_exclude",
  "title": "Proxy exclusion domains",
  "type": "text",
  "dependency": {
    "or": {
      "conditions": [
        { "match": { "key": "use_proxy", "value": "true" } },
        { "match": { "key": "use_vpn", "value": "true" } }
      ]
    }
  }
}
```

#### not — Negation

```json
{
  "key": "custom_host",
  "title": "Custom host",
  "type": "text",
  "dependency": {
    "not": {
      "condition": { "match": { "key": "server_mode", "value": "auto" } }
    }
  }
}
```

### Complete Config Example

Combining all features into a realistic `configFields` array:

```json
"configFields": [
  {
    "key": "help_intro",
    "title": "Welcome",
    "type": "markdown",
    "defaultValue": "This plugin connects to the **MusicApi** service.\n\nEnter your API Key below to get started."
  },
  {
    "key": "api_key",
    "title": "API Key",
    "summary": "Get one at https://example.com/console",
    "group": "Authentication",
    "type": "password",
    "required": true
  },
  {
    "key": "use_custom_host",
    "title": "Custom server",
    "group": "Authentication",
    "type": "switch",
    "defaultValue": "false"
  },
  {
    "key": "custom_host",
    "title": "Server host",
    "group": "Authentication",
    "type": "text",
    "dependency": { "match": { "key": "use_custom_host", "value": "true" } }
  },
  {
    "key": "lyrics_source",
    "title": "Lyrics source",
    "group": "Lyrics",
    "type": "dropdown",
    "defaultValue": "official",
    "options": [
      { "value": "official", "label": "Official lyrics" },
      { "value": "translated", "label": "Translated lyrics" }
    ]
  },
  {
    "key": "cover_size",
    "title": "Cover size",
    "group": "Cover",
    "type": "dropdown",
    "defaultValue": "800",
    "options": [
      { "value": "300", "label": "300 × 300" },
      { "value": "800", "label": "800 × 800" },
      { "value": "1200", "label": "1200 × 1200" }
    ]
  },
  {
    "key": "timeout",
    "title": "Timeout (s)",
    "group": "Request",
    "type": "number",
    "defaultValue": "15"
  },
  {
    "key": "help_footer",
    "title": "Notes",
    "type": "markdown",
    "defaultValue": "> Free tier: 1000 requests/day\n> Open an issue on GitHub for support"
  }
]
```

## Runtime Data Return

Plugin functions return search data through two JSON objects: `fields` (host-standard metadata fields like title, artist) and `internal` (plugin-private context like platform IDs, tokens).

See [Configuration And Result Fields](./config-metadata.md) for the full standard field list, `internal` size constraints, and host write policies.
