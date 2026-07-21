# Library & Folders

Manage music folders that Lyrico scans, and maintain your library.

## Folder Management

Go to: `Settings` → `Scan Settings` → `Folder Management`.

The folder management page has **Folders** and **Songs** tabs for viewing the folder tree and songs within a given scope.

### Add a Folder

Tap the add button, select a music folder in the system file picker, and grant access. Lyrico starts scanning automatically after adding.

If `Settings` → `Scan Settings` → `Skip audio under 60 seconds` is enabled, audio files 60 seconds or shorter will be skipped—useful for filtering out notification sounds, short clips, and samples.

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

## Scan Settings

`Settings` → `Scan Settings` includes:

- **Folder Management**: Manage added music folders (same as above).
- **Skip audio under 60 seconds**: When enabled, Lyrico skips audio files ≤ 60 seconds long.

## Folder Permission Issues

Lyrico automatically cleans up library records and refreshes indexes when:
- Folder permissions are lost (e.g., system revoked storage access)
- A folder is deleted or moved
- Songs in the database no longer exist on the device

If you see "some files cannot be accessed":
1. Go to `Folder Management` and remove the affected folder.
2. Re-add the folder and grant access again.
