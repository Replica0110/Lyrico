# FAQ

## Basic Usage

### Why are notification and audio permissions requested together on first launch?

On Android 13 and later, Lyrico submits both ungranted permissions as one startup request:

- Audio access is used to scan, read, and manage local music.
- Notification access is used to show progress for batch jobs and other background processing.

Audio access is required to build and manage the music library. If notification access is denied, you can still browse and edit, but background task notifications may not appear. Android 12 and earlier request only audio read access. If you denied a permission by mistake, restore it from system `Settings` → `Apps` → `Lyrico` → `Permissions`.

### Why doesn't tapping a song play music?

Lyrico is a tag editor, not a music player. Tapping a song opens the metadata editor by default. To play music, use "Play Music" from the song's more menu.

### I saved changes but the song info didn't change?

Saving writes changes to the audio file itself, not just Lyrico's database. Verify:
1. The app showed "Saved successfully".
2. The file's directory has write permissions. Some Android versions may require additional authorization.
3. Check if other music players show the updated tags.

### How do I remove songs from the library without deleting files?

Use the **Hide Folder** feature (`Settings` → `Library management` → `Audio folders`) to hide songs from the library without deleting files.

For individual songs, move them to a separate folder and hide that folder.

### Embedded artist posters vs. posters in the poster folder—which wins?

Priority is decided **per artist**. Lyrico prefers the **embedded poster** that belongs to that artist - the picture whose description names them, or an old picture with no description. Only when that artist has no usable embedded poster does it look up a poster by file name in your **poster folder**. So a same-named poster file has no effect for an artist that already carries an embedded poster, but it does not affect the other artists on the same song.

If an embedded poster's description matches no artist (usually after the artist field was edited), it gets its own page in the editor, labeled **"Untagged"** or with its original description, and it can be assigned to an artist from that page's menu.

See [Artist posters](./library.md#artist-posters).

### What is the difference between removing an artist's posters and removing a poster folder?

**Remove artist posters** deletes that artist's embedded posters and deletes the poster files named after that artist from the poster folder. You can run it from the artist row menu, the multi-select button, or the artist detail page. It cannot be undone.

**Remove folder** is in `Settings` → `Library management` → **Artist poster folder**. It only stops using the posters in that folder; no files are deleted. Re-add the folder whenever you need it.

### I added a poster folder but artists still show no poster?

Check in order:

1. Are the poster files **directly inside** the selected folder? (Lyrico does not search subfolders.)
2. Does the file name match the artist name (e.g., `周杰伦.jpg`)? Names containing `\ / : * ? " < > |` need a substitute such as an underscore—`AC/DC` becomes `AC_DC.jpg`.
3. Does the artist already have an embedded poster? Embedded posters take priority and override the folder file.
4. Is the "matched to library artists" count in **Folder details** 0? A count of 0 means the file names do not line up with the artist names.

See [Artist posters](./library.md#artist-posters).

### One artist got split into several in the artist list?

Artist splitting breaks a multi-artist tag apart by separator; the tags in the audio files themselves are not modified. To keep an artist intact, add its name to the **No-Split Whitelist** under `Settings` → `Library management` → `Artist Split Rules`, then rebuild the artist index.

## Interface

### How do I switch the app language?

Go to `Settings` → `Appearance` → `App Language` and choose Follow System, English, 简体中文, 繁體中文（台灣）, or 繁體中文（香港）. The interface switches immediately; no restart is needed.

### The bottom bar turned into a floating style—how do I undo that?

Turn off **Floating Navigation Bar** in `Settings` → `Appearance` to get the normal bottom bar back. While it is on, you can also choose the **Floating Bar Effect** right below it (None / Frosted glass / Liquid glass). See [Appearance](./settings/appearance.md#floating-navigation-bar).

### Blur and the floating navigation bar disappeared after I changed the interface style?

**Bar blur** and the **floating navigation bar** are only available with the **Miuix** interface style. After you switch to **SaltUI** under `Settings` → `Appearance` → **Interface style**, both options are hidden; their previous switch values are kept. Switch back to **Miuix** to set them again. See [Interface style](./settings/appearance.md#interface-style).

### What is the number on the left of each song row?

The number is the **track number**, taken from the song's "track number" tag. When the tags include a disc number it is shown as `track (disc)`; songs without a track number show `—`. Only the song lists in album detail and artist detail show it.

## Plugin Issues

### Plugin installed but no search results?

Check in order:
1. Is the plugin **enabled**?
2. Does the plugin **support** the current operation? (Some only support song search, not lyrics or covers.)
3. Does the plugin need a **Token or API Key**? If so, fill it in the plugin configuration.
4. Is the network available?
5. Check `Settings` → `Troubleshooting` → `App Logs`, filter by "Plugin" to see error details.

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

### Batch tasks stall in the background but continue when I return to the app?

Battery optimization or background activity restrictions may pause the app. Check Lyrico's background settings first. Systems such as ColorOS may have a separate background activity control.

1. Long-press the Lyrico icon, open **App info**, and find **Battery usage**, **Battery management**, or a similar setting.
2. Set Lyrico to **Unrestricted / No restrictions**, or enable **Allow background activity / Allow background running**.
3. If your system has a separate **Battery optimization** setting, select **Don't optimize / Ignore battery optimization** for Lyrico. Also check whether a power-saving mode is still restricting it.

Names and locations vary by device and system version. Background activity and battery optimization may need to be configured separately. Allowing continuous background activity may increase battery use.

If tasks still stall, filter **App Logs** by **Batch** under `Settings` → `Troubleshooting` and include the logs in your report. You can also keep Lyrico in the foreground until the task finishes.

References: [Android background optimization](https://developer.android.com/topic/performance/background-optimization), [World Run's OPPO background settings guide](https://donate.wingsforlifeworldrun.com/en/wflwr-app-settings/android/oppo).

### Batch matching fails halfway through?

Possible reasons:
- Concurrency is too high, triggering rate limiting. Lower concurrency and retry.
- Some songs have empty title/artist, making search keywords invalid.
- Plugin network requests timing out.

Check per-song failure reasons in `Settings` → `Task History`.

### Batch rename didn't change filenames?

Check:
1. Whether placeholder tags are empty (e.g., `@1` for title—if title is empty, produces empty text).
2. Whether the preview shows the expected filenames.
3. Whether multiple files would get the same name (Lyrico auto-appends numeric suffixes).

### What if a ReplayGain calculation fails?

Failures show a reason; for batch tasks, check the **Failed** tab under `Settings` → `Task History` → **Scan ReplayGain**. One failed song does not stop the rest.

If the file is damaged, the codec is unrecognized, or decoded data is empty, check that the file plays in a player; if it still fails, check **App Logs** filtered to **Batch**.

## Lyrics

### Why don't word-timed lyrics display?

- Make sure the lyric mode is set to "Word-Timed" or "Enhanced Word-Timed".
- The plugin must return lyrics with word-level timing data. Plain LRC cannot be converted to word-timed format.

### How do I convert Traditional Chinese lyrics to Simplified?

Use the "Simplify / Traditionalize" option in the editor's lyrics menu. Or set "Chinese Text Conversion" to "Traditional → Simplified" in `Settings` → `Tag editing` to auto-convert search results.

## Other

### The app is using too much storage?

Mainly cover image cache. Go to `Settings` → `Data management` → `Clear Cache` to clean up.

### Can't save when opening a file shared from another app?

If you can't save after editing an audio file opened via "Share" or "Open with" from another app, Lyrico likely lacks audio access permission.

Fix: Grant Lyrico audio access permission in system settings (Settings → Apps → Lyrico → Permissions → Music and audio). Once authorized, you can save normally.

### How do I report issues or suggest features?

- GitHub Issues: [https://github.com/Replica0110/Lyrico/issues](https://github.com/Replica0110/Lyrico/issues)
- Telegram group: [https://t.me/lyrico_app](https://t.me/lyrico_app)

### Will my settings be lost after updating?

Not normally. But it's good practice to back up via `Settings` → `Data management` → `Export Settings` before updating.
