# Using Plugins

This page explains how plugins work in Lyrico. You do not need to understand JavaScript, manifests, or host APIs to use plugins.

Plugins provide online search capabilities. Once enabled, Lyrico can use them to search song metadata, lyrics, and cover artwork.

## Plugin Packages

Lyrico plugins are usually distributed as ZIP archives. A package contains at least:

```text
plugin.zip
└── com.example.source/
    ├── manifest.json
    └── source.js
```

Some ZIP files may contain multiple plugins. During import, Lyrico scans for `manifest.json` files and lists installable plugins.

## Import A Plugin

1. Open the plugin management page in Lyrico.
2. Select a plugin ZIP archive.
3. Review detected plugins and import results.
4. Confirm the plugins you want to install.

If a package does not match the required structure, Lyrico rejects it and shows the failure reason.

## Enable And Disable

Installed plugins do not necessarily participate in search immediately. A plugin must be enabled before it appears in lyrics, cover, or batch matching flows.

Disabling a plugin keeps its files and settings but removes it from search. Use uninstall when you want to remove it completely.

## Configure Plugins

Plugins can declare source-specific settings such as:

- API URL
- Token or API key
- Region
- Cover size
- Search preference

Lyrico generates the settings UI from `configFields` in the plugin manifest. Saved settings are passed to the plugin the next time it searches songs, fetches lyrics, or searches covers.

If a plugin has no settings entry, it usually means the plugin does not declare configurable fields.

## Metadata Application

Plugin search results can return standard metadata fields such as release date, album, cover URL, composer, lyricist, and comment. Lyrico decides which fields can be applied in single-song and batch flows, and whether each field should supplement or overwrite existing tags.

Platform IDs, hashes, lyrics IDs, and other private values are only used by the same plugin for later requests. They are not shown as writable tag fields.

## Update And Uninstall

When importing another package with the same plugin ID, Lyrico compares version numbers and treats it as an update, overwrite, or downgrade.

Uninstalling a plugin removes plugin files and clears its settings.

## FAQ

### A Plugin Is Enabled But Returns No Results

Possible reasons:

- The plugin does not implement the requested capability.
- The plugin requires a token, API key, cookie, or other setting.
- Network requests failed.
- The remote service changed its response format and the plugin needs an update.

### Saved Settings Do Not Affect Existing Results

Settings are passed on the next plugin call. Existing search results are not refreshed automatically; search again to use new settings.

### Plugin Import Failed

Common causes:

- No valid `manifest.json` exists in the archive.
- The plugin ID format is invalid.
- The plugin API version is incompatible.
- The entry script is missing or has an unsafe path.
- The archive or plugin directory exceeds size limits.
