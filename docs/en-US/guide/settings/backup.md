# Backup & Restore

## Export Settings

Export the current app configuration as a JSON file. Includes:

- Theme and appearance settings
- Search settings (plugin enabled state, sort order, configuration, search limit, etc.)
- Lyrics settings (lyric mode, romanization, translation)
- Artist split rules
- Field visibility settings
- Batch match configuration

**Not included:**
- Music files themselves
- Lyrics and cover data
- Music library database
- Installed plugin files

## Import Settings

Restore app configuration from a previously exported JSON file.

After importing, some index-related settings (like artist split rules) may require a library refresh or artist index rebuild to fully take effect.

::: warning
A settings backup is not a full data backup. It only contains app settings—it cannot restore music files, lyric data, or the database.
:::
