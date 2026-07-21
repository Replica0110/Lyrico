# Browsing Your Library

Lyrico provides three browsing dimensions—Songs, Artists, Albums—plus local search, to help you quickly find music.

## Three Main Views

The bottom navigation bar has three tabs:

### Songs

All scanned songs listed by title by default, with an alphabet sidebar for quick scrolling.

- Tap a song to [edit metadata](./single-song.md).
- Long-press or swipe a song to enter multi-select mode (for [batch operations](./batch.md#entering-selection-mode)).

### Artists

Artists grouped by name. If artist splitting is enabled (`Settings` → `Metadata Processing` → `Artist Split Rules`), songs with multiple artists are listed under each artist separately.

Tap an artist to see the artist detail page, which has **Songs** and **Albums** tabs showing all tracks and albums by that artist.

### Albums

Album covers displayed in a grid. Tap an album to see its detail page with all songs in that album.

Song lists in album detail pages also support tap-to-edit, long-press multi-select, and other operations.

## Sorting

Songs, artists, and albums all support sorting. Tap the sort button at the top of each list to change the sort order:

**Song sort options:**
- Title
- Artist
- Album
- Date modified
- Date added
- File size
- Duration
- Extension

**Artist sort options:**
- Name
- Song count
- Album count

**Album sort options:**
- Name
- Artist
- Song count
- Year

Album grid column count can also be adjusted on the albums page.

## Local Search

Tap the search icon in the navigation bar to open **Local Search**.

Local search matches **title, artist, album, and filename** simultaneously. Results are grouped into Artists, Albums, and Songs sections.

::: warning
Songs in hidden folders do not appear in normal lists or local search results. To restore visibility, go to `Settings` → `Scan Settings` → `Folder Management`.
:::

## Playing Songs

Lyrico is a tag editor, not a music player. Tapping a song opens the editor by default, not playback.

To play a song:
- In the song list, tap the **more menu** (⋮) and select **Play Music**.
- In the editor screen, tap the play button and choose an installed external player.

Lyrico remembers your last selected player.

## Song Menu Options

The more menu (⋮) on each song row provides:

| Option | Description |
|--------|-------------|
| Play Music | Open the audio file in an external player |
| Song Info | View title, artist, album, duration, bitrate, sample rate, channels, file path, size, etc. Content can be copied |
| Share Song | Send the audio file via the system share sheet |
| Rename File | Rename the file while keeping the original extension. **Does not modify the title tag** |
| Delete Song | Delete the local audio file. **Cannot be undone** |

In multi-select mode, the more menu is replaced by checkboxes. Tapping a song toggles its selection.
