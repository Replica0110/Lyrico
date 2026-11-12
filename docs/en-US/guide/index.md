# User Guide

Welcome to Lyrico. This guide is organized by common tasks to help you get started quickly.

## Getting Started

If you're new to Lyrico, read in this order:

1. [Getting Started](./getting-started.md) — Install, add your music folder, and set up plugins
2. [Browsing Your Library](./browsing.md) — Browse by song, artist, album; sort, search, and play
3. [Single Song Editing](./single-song.md) — Edit tags, search lyrics and covers
4. [Batch Operations](./batch.md) — Batch match, batch edit, batch rename, and more
5. [Using Plugins](./plugins.md) — Import and configure search source plugins

## Find By Task

| I want to… | See |
|------------|-----|
| Add music files for the first time | [Getting Started](./getting-started.md) |
| Manage music folders (add, remove, hide) | [Audio folder management](./library.md) |
| View songs for an album or artist | [Browsing Your Library](./browsing.md) |
| View songs by field | [Text Processing](./settings/editing.md#view-songs-by-field) |
| Edit a song's title, artist, cover, etc. | [Single Song Editing](./single-song.md#edit-tags-and-save) |
| Download lyrics or translations | [Single Song Editing](./single-song.md#search-lyrics-and-metadata) |
| Search and change cover art | [Single Song Editing](./single-song.md#cover-operations) |
| Convert or organize lyrics, or adjust timing | [Single Song Editing](./single-song.md#lyrics-operations) |
| Adjust the display order of original, romanization, and translation | [Lyrics Settings](./settings/editing.md#lyric-line-order) |
| Calculate ReplayGain for a song | [Single Song Editing](./single-song.md#replaygain) |
| Download lyrics/covers for multiple songs | [Batch Operations](./batch.md#tag-matching) |
| Batch-edit the same field across songs | [Batch Operations](./batch.md#edit-tags) |
| Batch-rename files by pattern | [Batch Operations](./batch.md#rename-files) |
| Install new search source plugins | [Using Plugins](./plugins.md) |
| Show or manage an artist poster | [Audio folder management](./library.md#artist-posters) |
| Switch interface style, app language, or bottom bar style | [Appearance](./settings/appearance.md#interface-style) |
| Change theme, scan, lyrics, or other settings | [Settings Overview](./settings/) |
| Troubleshoot a feature | [FAQ](./faq.md) |
| Integrate Lyrico editing into a player | [External Editing Integration](./external-edit.md) |

## Important Notes

### Saving and Writing

In the single-song editor, changes are written to the audio file only after you tap the **✓** button in the top-right. Going back without saving discards them.

In batch operations, tasks like **Edit Tags**, **Tag Matching**, **Rename Files**, **Convert lyrics format**, **Organize lyrics**, and **Calculate ReplayGain** directly process the selected files. Review previews, configuration, and selection before executing.

### Deletion and Removal

**Deleting a song** or batch **Delete** removes local audio files permanently. This cannot be undone.

**Removing a folder** only removes it from Lyrico's library index. Local files are not deleted.

### Plugins and Online Search

Lyrics, covers, and online metadata are provided by plugins. Plugins must be **installed and enabled** to participate in search and batch matching. Plugin order in the management list affects search and matching priority.
