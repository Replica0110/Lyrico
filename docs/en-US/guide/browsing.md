# Browsing Your Library

Lyrico provides three browsing dimensions—Songs, Artists, Albums—plus local search, to help you quickly find music.

## Three Main Views

The bottom navigation bar has three tabs:

### Songs

All scanned songs listed by title by default, with an alphabet sidebar for quick scrolling.

- Tap a song to [edit metadata](./single-song.md).
- Long-press or swipe a song to enter multi-select mode (for [batch operations](./batch.md#entering-selection-mode)).

### Artists

Artists grouped by name. If artist splitting is enabled (`Settings` → `Library management` → `Artist Split Rules`), songs with multiple artists are listed under each artist separately.

Tap an artist to see the artist detail page, which has **Songs** and **Albums** tabs showing all tracks and albums by that artist.

Long-press an artist or swipe left or right to enter multi-select mode; the button at the bottom right removes artist posters for the selected artists in one go. The round cover at the top of the detail page is the poster entry—it opens **Artist poster options**. See [Artist posters](#artist-posters).

### Albums

Album covers displayed in a grid. Tap an album to see its detail page with all songs in that album.

Song lists in album detail pages also support tap-to-edit, long-press multi-select, and other operations.

**Track numbers**: In the album detail and artist detail song lists, each song shows its track number on the left; when the tags include a disc number, it is shown as `track (disc)`. Songs without a track number show `—`.

::: tip Floating navigation bar
If the bottom bar takes up too much room, enable **Floating Navigation Bar** in `Settings` → `Appearance` and pick a frosted-glass or liquid-glass effect. The floating bar, its effects and blur are available only with the **Miuix** interface style; with **SaltUI** the bar is fixed and these settings no longer appear. See [Appearance](./settings/appearance.md#floating-navigation-bar).
:::

## Artist Posters

The more menu (⋮) on each artist row offers **View artist poster**, **Crop artist poster** and **Remove artist posters**. For video posters the crop item says **Video posters cannot be cropped**. If the artist has no usable poster, the menu shows **No artist poster** with a **Select Artist Poster** entry.

On the artist detail page, tap the round cover at the top to open **Artist poster options**:

- **View artist poster**: open the current poster full screen.
- **Choose Artist Poster from This Device**: after picking an image, choose **Embed in songs** or **Add local poster to folder**. The latter does not change song files.
- **Remove artist posters**: removes this artist's embedded posters from its songs and deletes this artist's files from the poster folder. Asks for confirmation.
- **Export embedded posters**: saves the embedded poster as an image file named after the artist. A poster with the same name is replaced; nothing is written when the artist has no embedded poster.

If no poster folder is set, exporting first asks you to choose one, and that folder becomes the poster folder. Removal and export report how many items succeeded, were unchanged, or failed.

When you select several artists in the artist list, the bottom-right button only offers **Remove artist posters**, with the same result as removing one artist. Exporting is done from each artist detail page.

### Image Preview

All poster previews share the same screen, and actions apply to the poster currently shown:

- Pinch to zoom; drag to pan when zoomed in.
- Swipe left or right to move between posters; with more than one, the thumbnail strip at the bottom also works.
- Tap the image to show or hide the top and bottom bars.
- Drag up on the image to open **Image info**; drag down to close it. You can also tap **Image info** at the bottom.
- Bottom buttons: **Share**, **Crop**, **Image info**.

Image info shows dimensions, file size and format, plus whether the source is **Embedded** or **Poster file**, along with its file name or path.

MP4 posters play as video with playback controls. Videos cannot be cropped, so the crop button is not shown.

Cropping changes only the current poster: an embedded poster rewrites the song file it comes from, while a poster file is replaced in place.

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
Songs in hidden folders do not appear in normal lists or local search results. To restore visibility, go to `Settings` → `Library management` → `Audio folders`.
:::

## Playing Songs

Lyrico is a tag editor, not a music player. Tapping a song opens the editor by default, not playback.

To play a song:
- In the song list, tap the **more menu** (⋮) and select **Play Music**.
- In the editor screen, tap the play button.

Both entry points open the **Play with** sheet, where you can choose an installed player or the system picker.

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
