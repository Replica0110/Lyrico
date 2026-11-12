# Audio folder management

Manage music folders that Lyrico scans, and maintain your library.

## Folder Management

Go to: `Settings` → `Library management` → `Audio folders`.

The folder management page has **Folders** and **Songs** tabs for viewing the folder tree and songs within a given scope.

### Add a Folder

At the top level, tap the add-folder button in the top bar, select a music folder in the system file picker, and grant access. Lyrico starts scanning automatically after adding.

If `Settings` → `Library management` → `Ignore audio shorter than 60 seconds` is enabled, audio files shorter than 60 seconds will be skipped—useful for filtering out notification sounds, short clips, and samples.

### Open Subfolders

Folder Management opens at the top level. Tap a folder card to go one level down; the breadcrumb at the top shows the current path. Tap any level in the breadcrumb to jump back, and the back button or gesture goes up one level. The count under the breadcrumb shows the folders and songs in the current level.

### Refresh a Folder

"Refresh Folder" re-scans the corresponding folder. Useful when:
- You've added new audio files via a file manager
- You've deleted some audio files
- You've modified file tags with another tool

### Hide and Show Folders

**Hide Folder**: Songs in this folder and its subfolders are hidden from song lists, artist lists, album lists, and local search results.

**Show Folder**: Restore visibility of a previously hidden folder.

Hiding does not delete files or remove the folder record. It's useful for temporarily excluding folders like ringtones or podcasts.

### Remove a Folder

Removes the folder and its song records from Lyrico's library. Local audio files are **not deleted**. You can re-add the folder later.

### Operation Comparison

| Action | Local Files | Folder Record | Songs Visible |
|--------|-------------|---------------|---------------|
| Hide Folder | Kept | Kept | Hidden, can be shown again |
| Remove Folder | Kept | Removed | Hidden, needs re-adding |

## Artist Posters

The Artists list, artist detail page and single-song editor prefer **embedded artwork** that belongs to that artist. Ownership comes from the picture's **description**: the artist named there is the one it belongs to. Old pictures with no description are treated as belonging to the song's only artist. When an artist has no usable embedded picture, Lyrico looks up a poster by **file name** in the poster folder. Go to: `Settings` → `Library management` → `Artist poster folder`.

### Choose a Folder

Tap **Choose folder** on first use, select the folder holding your posters in the system file picker, and grant access. Lyrico reads the posters in it immediately.

Only files directly inside the selected folder are used; subfolders are not searched. Lyrico uses one poster folder at a time—changing the folder replaces the old one.

### Folder Actions

In the top bar of the Artist Posters page, tap ⋮ to:

- **File naming**: the rules that map file names to artists.
- **Folder details**: the folder path, the total number of posters and how many are **matched to library artists**. A count of 0 means the file names do not line up with the artist names; open the folder to check.
- **Refresh folder**: re-read the files. Use it after adding, removing or renaming posters in a file manager, or when the folder shows no usable posters.
- **Change folder**: pick another folder. Files in the old folder are not touched. If folder permission was revoked or the folder was deleted, re-grant access or change the folder as prompted.
- **Remove folder**: stop using the posters in this folder. Local files are not deleted; the confirmation says so.

### Poster Naming Rules

| File name | Artist it matches |
|-----------|-------------------|
| `Artist.jpg` | The artist whose name matches exactly |
| `Artist_live.jpg` | The same artist, with the name followed by `_` and a suffix |
| `AC_DC.jpg`, `AC-DC.jpg`, `AC／DC.jpg`, or `ACDC.jpg` | An artist whose name contains characters a file name cannot hold (`\ / : * ? " < > \|`), such as `AC/DC` |

JPG, JPEG, PNG, WebP, and MP4 (first video frame) are supported. Inside one folder, an exact artist name beats a suffixed variant. Each poster card shows the artist its file name matches, or **Not linked to an artist** when nothing matches.

### Working with One Poster File

Tap a poster card to open its menu:

- **View artist poster**: open the full-screen preview. See [Browsing your library](./browsing.md#image-preview) for the gestures.
- **Artist details**: open the artist's detail page. Unavailable when the file name does not match a single artist; the item shows **No unique artist match**.
- **Rename**: changes the file name only; the extension is fixed. The name decides the artist, so renaming changes the match. The confirm button stays disabled for an invalid or duplicate name.
- **Crop**: only offered for JPG, JPEG, PNG and WebP; the cropped image replaces this file. MP4 has no crop item.
- **Delete**: deletes this poster file. Cannot be undone.

### Three Actions That Are Easy to Confuse

| Action | Where | What it does |
|--------|-------|--------------|
| Remove folder | Artist posters page ⋮ → **Remove folder** | Stops using the folder; poster files stay |
| Remove artist posters | Artist row menu, the multi-select button, or **Remove artist posters** on the artist detail page | Removes these artists' embedded artist posters from their songs and deletes their poster files from the poster folder; covers and other pictures are left alone |
| Export embedded posters | **Export embedded posters** on the artist detail page | Saves embedded posters as image files named after the artist; matching posters are replaced, and artists without an embedded poster are skipped |

If no poster folder is set, exporting first asks you to choose one, and that folder becomes the poster folder. Removal and export report how many items succeeded, were unchanged, or failed.

When you add a poster from the artist detail page, **Add local poster to folder** saves it here under the artist's name without changing song files.

## Scan Settings

Use **Settings → Library management → File scanning** for audio folders, short-audio filtering and lyric indexing. See [Library management](./settings/library.md#file-scanning).

ReplayGain parameters are under [Tag editing → ReplayGain](./settings/editing.md#replaygain).

## Folder Permission Issues

Lyrico automatically cleans up library records and refreshes indexes when:
- Folder permissions are lost (e.g., system revoked storage access)
- A folder is deleted or moved
- Songs in the database no longer exist on the device

If you see "some files cannot be accessed":
1. Go to `Folder Management` and remove the affected folder.
2. Re-add the folder and grant access again.
