# Data management

Open **Settings → Data management**.

## Settings backup

### Export Settings

Export the current app configuration as a JSON file. Includes:

- Interface style (Miuix / SaltUI)
- Theme mode, Monet colors, theme color, navigation bar blur, floating navigation bar, and the floating bar effect
- Search settings (plugin enabled state, sort order, configuration, search limit, etc.)
- Lyrics settings (lyric format, romanization, translation, lyric line order)
- ReplayGain target loudness and peak type
- Artist split rules
- Edit field configuration
- Batch match configuration

**Not included:**
- Music files themselves
- Lyrics and cover data
- Music library database
- Installed plugin files

### Import Settings

Restore app configuration from a previously exported JSON file. Interface style, theme, and other appearance settings apply immediately.

Some index-related settings (like artist split rules) may require a library refresh or artist index rebuild to fully take effect.

::: warning
A settings backup is not a full data backup. It only contains app settings—it cannot restore music files, lyric data, or the database.
:::

## Cache management

### Clear Cache

Open **Clear Cache**, review category sizes and the total, then confirm.

Cleanup covers image, network, external and other temporary caches. It does not delete audio files or erase tags written to them.

To repair artist or album associations, use [Rebuild library indexes](./diagnostics.md#rebuild-library-indexes). Clearing cache does not rebuild the library.
