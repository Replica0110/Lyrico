# Getting Started

Set up Lyrico for the first time and see your music library.

## Download and Install

Download the latest APK from [GitHub Releases](https://github.com/Replica0110/Lyrico/releases). Lyrico publishes separate packages for each device ABI instead of a universal APK:

- `arm64-v8a`: For most modern Android devices; choose this one in most cases.
- `armeabi-v7a`: For older devices that still use a 32-bit ARM architecture.

APK names follow `Lyrico-<version>-<ABI>.apk`, for example `Lyrico-1.4.0-a1b2c3d-arm64-v8a.apk`. The short suffix in the version is the Git commit hash used to identify the exact source revision of the build. Debug builds also include `-debug`.

## First Launch and Permissions

On first launch, Lyrico requests any required permissions that have not already been granted:

- **Music and audio**: Used to scan, read, and manage local audio files. If denied, Lyrico cannot build the library normally or process audio that requires direct access.
- **Notifications** (Android 13 and later): Used to show progress for batch jobs and other background processing. Denying it does not prevent normal browsing, but related task notifications may not appear.

On Android 13 and later, notification and audio permissions are submitted as one startup permission request. Android 12 and earlier request only the applicable audio read permission. Permissions already granted are not requested again. To restore a denied permission, open system `Settings` → `Apps` → `Lyrico` → `Permissions`.

## Add A Music Folder

Lyrico does not scan your entire device automatically. You need to manually specify which folders to manage.

**Steps:**

1. Open Lyrico, complete the first-launch permission request, and go to the Songs tab. If no folders have been added yet, you'll see "No folders added".
2. Tap the **Add Folder** button.
3. In the system file picker, find and select your music folder, then tap "Use this folder" to grant access.
4. Lyrico starts scanning audio files in the folder. Progress will show status like "Scanning folder…", "Reading tags 1 / 100", "Updating library…".

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
