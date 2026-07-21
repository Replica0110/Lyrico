# Getting Started

Set up Lyrico for the first time and see your music library.

## Add A Music Folder

Lyrico does not scan your entire device automatically. You need to manually specify which folders to manage.

**Steps:**

1. Download and install the latest APK from [GitHub Releases](https://github.com/Replica0110/Lyrico/releases).
2. Open Lyrico and go to the Songs tab. If no folders have been added yet, you'll see "No folders added".
3. Tap the **Add Folder** button.
4. In the system file picker, find and select your music folder, then tap "Use this folder" to grant access.
5. Lyrico starts scanning audio files in the folder. Progress will show status like "Scanning folder…", "Reading tags 1 / 100", "Updating library…".

Once scanning completes, your songs appear in the list. Initial scanning may take a few minutes for large libraries.

::: tip
You can add more folders in `Settings` → `Scan Settings` → `Folder Management`. Lyrico only scans folders you explicitly add—it will not scan other directories on your device.
:::

## Browse Your Library

After scanning, the bottom navigation has three views:

- **Songs**: All scanned songs in a list. Tap a song to edit it.
- **Artists**: Grouped by artist. If artist splitting is enabled, multiple artists are listed separately.
- **Albums**: Grid view of album covers.

For more on sorting, local search, and detail pages, see [Browsing Your Library](./browsing.md).

## Edit Your First Song

Tap any song in the list to open the **Edit Metadata** screen—Lyrico's core editing interface.

**Quick try:**

1. Tap a song to open the editor.
2. Modify the title or artist field.
3. Tap the **Save** button at the top.
4. After seeing "Saved successfully", changes are written to the audio file.

For full editing operations (lyrics, covers, ReplayGain, etc.), see [Single Song Editing](./single-song.md).

## Install Search Source Plugins

Lyrico does not include built-in online search. Lyrics, cover, and metadata search require **plugins**.

**Steps:**

1. Download a plugin ZIP (e.g., from [Lyrico-Plugins](https://github.com/Replica0110/Lyrico-Plugins)).
2. Go to `Settings` → `Search Settings` → `Plugin Management`.
3. Tap **Import Plugin** and select the downloaded ZIP file.
4. In the "Plugin Package Found" dialog, check the plugins you want and tap **Install**.
5. Back in the plugin management list, turn on the plugin's **enable switch**.

Once enabled, enter a keyword in the search bar on the editor screen to search online metadata.

For more plugin details, see [Using Plugins](./plugins.md).

## Next Steps

- All editing features → [Single Song Editing](./single-song.md)
- Process multiple songs at once → [Batch Operations](./batch.md)
- Adjust app settings → [Settings Overview](./settings/)
