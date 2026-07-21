# FAQ

## Basic Usage

### Why doesn't tapping a song play music?

Lyrico is a tag editor, not a music player. Tapping a song opens the metadata editor by default. To play music, use "Play Music" from the song's more menu.

### I saved changes but the song info didn't change?

Saving writes changes to the audio file itself, not just Lyrico's database. Verify:
1. The app showed "Saved successfully".
2. The file's directory has write permissions. Some Android versions may require additional authorization.
3. Check if other music players show the updated tags.

### How do I remove songs from the library without deleting files?

Use the **Hide Folder** feature (`Settings` → `Scan Settings` → `Folder Management`) to hide songs from the library without deleting files.

For individual songs, move them to a separate folder and hide that folder.

## Plugin Issues

### Plugin installed but no search results?

Check in order:
1. Is the plugin **enabled**?
2. Does the plugin **support** the current operation? (Some only support song search, not lyrics or covers.)
3. Does the plugin need a **Token or API Key**? If so, fill it in the plugin configuration.
4. Is the network available?
5. Check `Settings` → `Other` → `App Logs`, filter by "Plugin" to see error details.

### Plugin ZIP import fails?

Common reasons:
- No valid `manifest.json` in the ZIP.
- Plugin API version is incompatible with Lyrico.
- Plugin package structure or entry script doesn't meet requirements.
- Package exceeds size limits (5 MB per plugin, 30 MB unpacked ZIP).

Specific reasons are shown in the "Not Installable" section of the import dialog.

### Where can I get more plugins?

Official plugin repository: [Lyrico-Plugins](https://github.com/Replica0110/Lyrico-Plugins), covering QQ Music, NetEase Cloud Music, Kugou, Apple Music, and more.

## Batch Operations

### How do I select multiple songs at once?

Enter multi-select mode by:
- **Long-pressing** any song.
- **Swiping** left or right on a song.
- Swiping one song as the range start, then swiping another to select everything in between.

See [Batch Operations](./batch.md#entering-selection-mode) for details.

### Batch matching fails halfway through?

Possible reasons:
- Concurrency is too high, triggering rate limiting. Lower concurrency and retry.
- Some songs have empty title/artist, making search keywords invalid.
- Plugin network requests timing out.

Check per-song failure reasons in `Settings` → `Other` → `Task History`.

### Batch rename didn't change filenames?

Check:
1. Whether placeholder tags are empty (e.g., `@1` for title—if title is empty, produces empty text).
2. Whether the preview shows the expected filenames.
3. Whether multiple files would get the same name (Lyrico auto-appends numeric suffixes).

## Lyrics

### Why don't word-timed lyrics display?

- Make sure the lyric mode is set to "Word-Timed" or "Enhanced Word-Timed".
- The plugin must return lyrics with word-level timing data. Plain LRC cannot be converted to word-timed format.

### How do I convert Traditional Chinese lyrics to Simplified?

Use the "Simplify / Traditionalize" option in the editor's lyrics menu. Or set "Chinese Text Conversion" to "Traditional → Simplified" in `Settings` → `Metadata Processing` to auto-convert search results.

## Other

### The app is using too much storage?

Mainly cover image cache. Go to `Settings` → `Other` → `Clear Cache` to clean up.

### Can't save when opening a file shared from another app?

If you can't save after editing an audio file opened via "Share" or "Open with" from another app, Lyrico likely lacks audio access permission.

Fix: Grant Lyrico audio access permission in system settings (Settings → Apps → Lyrico → Permissions → Music and audio). Once authorized, you can save normally.

### How do I report issues or suggest features?

- GitHub Issues: [https://github.com/Replica0110/Lyrico/issues](https://github.com/Replica0110/Lyrico/issues)
- Telegram group: [https://t.me/lyrico_app](https://t.me/lyrico_app)

### Will my settings be lost after updating?

Not normally. But it's good practice to back up via `Settings` → `Backup & Restore` → `Export Settings` before updating.
