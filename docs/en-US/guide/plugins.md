# Using Plugins

Lyrico does not include built-in online search. Lyric search, cover search, and metadata matching are provided by **plugins**—you can import third-party plugins to extend these capabilities.

## What Are Plugins

Plugins are JavaScript search sources that run inside Lyrico's built-in QuickJS engine. A plugin can declare these capabilities:

- **Search songs**: Query online databases for matching tracks by title and artist.
- **Get lyrics**: Fetch full lyrics (including translations and romanization).
- **Search covers**: Search for cover artwork online.

Each capability is optional—different plugins may support only some of them.

## Get Plugins

Official plugins are available at [Lyrico-Plugins](https://github.com/Replica0110/Lyrico-Plugins), covering QQ Music, NetEase Cloud Music, Kugou, Apple Music, and other platforms.

Plugins are typically distributed as **ZIP archives**. A single ZIP may contain multiple plugins.

## Import A Plugin

**Full Steps:**

1. Go to `Settings` → `Search Settings` → `Plugin Management`.
2. Tap the add button (or "Import Plugin") in the top-right.
3. In the system file picker, find and select the plugin ZIP file.
4. Lyrico scans the ZIP and shows a "Plugin Package Found" dialog:
   - **Installable**: Listed by version relationship (New Install / Update / Overwrite / Downgrade).
   - **Not Installable**: Plugins that don't meet requirements, with reasons shown.
5. Check the plugins you want and tap **Install**.

::: tip Version relationships
- **New Install**: Plugin not currently installed.
- **Update**: Package version is higher than installed—recommended.
- **Overwrite**: Package version equals installed version.
- **Downgrade**: Package version is lower—usually not recommended.
:::

## Enable and Disable

Plugins are **disabled by default** after installation and won't participate in search. Turn on the plugin's **enable switch** in the plugin management list to make it available for search and batch matching.

Disabling a plugin keeps its files and configuration. To temporarily exclude a plugin, just turn off the switch.

## Adjust Plugin Order

**Drag** plugin items up or down in the management list to reorder them. Plugin order affects:

- **Single song search**: Plugin source priority when searching lyrics.
- **Batch matching**: Plugin matching order.

Plugins higher in the list are tried first.

## Configure A Plugin

Some plugins require Token, API Key, region, cover size, or other settings to work.

**Steps:**

1. In the plugin management list, tap **Configure** on a plugin item.
2. Fill in the required configuration fields. Types may include text, password, number, toggle, dropdown, etc.
3. Tap **Save**.

Saved configuration takes effect on the next search. Existing search results are not auto-refreshed—you need to search again.

::: warning
Password fields are hidden after saving but are still passed to the plugin at runtime. Only fill in sensitive credentials for trusted plugins.
:::

## Field Write Rules

Plugin search results are candidate metadata. Lyrico decides how to handle each field based on **Field Write Rules**, configurable per plugin.

| Mode | Behavior |
|------|----------|
| Disabled | Do not write this field |
| Complement | Write only if the local field is empty |
| Overwrite | Replace the local field with the plugin result |

"Disable All", "Complement All", and "Overwrite All" buttons only change the write mode, not the write target (standard tag vs. custom tag).

Platform-internal data (song ID, lyric ID, hash, Token, etc.) returned by plugins is not written as visible tags.

## Uninstall A Plugin

Tap **Uninstall** on a plugin and confirm. Plugin files and configuration are permanently deleted. This cannot be undone.

If you only want to temporarily stop a plugin from participating in search, disable it instead of uninstalling.

## No Results or Execution Failures

Troubleshoot in this order:

1. Is the plugin **enabled**?
2. Does the plugin **support** the current operation? (Some only support song search, not lyrics or covers.)
3. Are the plugin's **required fields** filled and saved?
4. Is the network available?
5. Is batch match **concurrency** too high? (Try lowering it.)
6. Has the plugin or target platform API changed? (Check `Settings` → `Other` → `App Logs`, filter by "Plugin" or "Network".)

---

## Going Further: Writing Your Own Plugin

If you want to develop search source plugins, read the [Plugin Documentation](../plugins/). Recommended reading order:

1. [Build a Plugin](../plugins/examples.md)
2. [Plugin Package Structure](../plugins/composition.md)
3. [Plugin Functions](../plugins/plugin-functions.md)
4. [Manifest Reference](../plugins/manifest.md)
5. [Host API Reference](../plugins/host-api.md)
