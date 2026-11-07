# Using Plugins

The current version supports API 1–5 plugins. API5 plugins require a compatible Lyrico version; update the app if installation reports an unsupported protocol. API1–4 plugins remain supported.

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

The plugin manager has only Metadata, Lyrics, and Covers tabs. Metadata enabled state and order control both single-song Main Search and batch metadata matching. Lyrics and Covers are shared by their corresponding single-song and batch entries. Declared source types are shown beneath each plugin name.

Disabling one type keeps the plugin files and settings and does not affect its other types. Use uninstall when you want to remove it completely.

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

## Online subscriptions and updates

Choose **Online subscriptions** from the import menu at the top right of plugin management. Add and refresh actions are at the top. Tap a subscription to open its plugin list, then tap **Back** to return to subscriptions. A subscription accepts an HTTP/HTTPS catalog URL and an optional name. Duplicate URLs are rejected. Each subscription menu supports refresh, edit, enable/disable, and deletion. **Refresh** on the subscription list checks all enabled subscriptions and installed plugins' update URLs; inside a subscription, it refreshes only that source.

The plugin list shows each source release and the version change when a different version is installed. Use the install, update, or downgrade button on the right to start the operation directly, without a changelog. Downgrades are labeled explicitly. Matching versions show **Installed**, and incompatible releases cannot be installed. The action button shows download percentage, verification, and installation status. You can request cancellation during a transfer and retry failed or cancelled operations. Cancelling a download does not uninstall an existing plugin. Only one installation per plugin ID runs at a time.

Opening plugin management or the subscription Sheet loads cached results and refreshes missing catalogs or results at least five minutes old in the background. Previously failed catalogs are retried too. Manual refresh bypasses this age limit. Concurrent requests for the same URL are shared. Refresh failures retain the last successful list with an error indication.

Update checks combine enabled subscriptions and plugin-declared `updateUrl` addresses, fetching each URL once. Plugin management offers the highest compatible update version. Tap **Update** to confirm the version, download size, and source; no changelog is shown. If that highest version has packages with different SHA-256 hashes, choose a source in the confirmation Sheet. Each subscription's plugin list compares its own release against the installed version. Installation origin and package hash are recorded without replacing the manifest update URL; the origin is visible in plugin configuration.

Disabling a subscription stops automatic checks through that subscription. The same URL is still checked if an installed plugin declares it as its own update URL. Deleting a subscription preserves installed plugins, their configuration, and their declared update URLs. Closing the Sheet leaves tasks running in the current application process. Reopen the subscription to view status or cancel a task; updates to installed plugins also show progress in plugin management. Downloads do not resume automatically after application process termination; start the installation or update again.
