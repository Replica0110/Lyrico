# Single Song Editing

The single-song editor is Lyrico's core screen for viewing and editing all metadata for one track.

## Opening the Editor

There are several ways to open the editor:

- In the Songs tab, artist detail, album detail, or local search results, **tap any song**.
- **Open or share** an audio file from another app (like a file manager) to Lyrico to jump directly to the editor.

::: warning Tapping does not play
Tapping a song opens the metadata editor by default, not playback. To play, use "Play Music" from the more menu or tap the play button in the editor.
:::

## Editor Layout

The editor is divided into the following areas, top to bottom:

| Area | Purpose |
|------|---------|
| Top bar | Save button, play button, more options |
| Search bar | Enter keywords to search online metadata and lyrics via plugins |
| Cover area | View, change, search, or remove cover art |
| Tag fields | Title, artist, album, album artist, year, genre, track number, disc number, composer, lyricist, comment, etc. |
| Lyrics area | View, import, export, format lyrics; simplify/traditionalize; adjust timing |
| ReplayGain area | Calculate track gain and peak |
| Custom tags | Manage custom tags (requires adding visible custom tag keys in settings first) |

If some fields are missing from the editor, they may be hidden by **Field Visibility Settings**. Hidden fields are preserved in the file and not cleared on save. See [Metadata Processing](./settings/metadata.md#field-visibility-settings).

## Edit Tags and Save

**Steps:**

1. Open the editor, modify any fields you want to change. Empty fields can be filled; filled fields can be edited or cleared.
2. Tap the **Save** button in the top toolbar.
3. "Saved successfully" confirms changes were written. If saving fails, the error reason is shown.

**Important:**
- Search results, cover selections, lyrics processing, and ReplayGain calculations only update the editor's temporary content.
- Changes are written to the audio file only when you tap **Save**.
- Some Android versions or specific directories may prompt additional permission requests—follow the on-screen instructions.

## Search Lyrics and Metadata

Use plugins to search online song metadata to fill in title, artist, lyrics, and other fields.

**Steps:**

1. Open the editor. The search bar is pre-filled with the current song's **title + artist** as keywords. If the song has no title or artist tags, the filename is used instead.
2. Tap the search button (or press Enter). Lyrico queries enabled plugins for matching songs.
3. Search results are shown under "All" and per-plugin-source tabs.
   - Tap **Load more** at the bottom of the results to load the next page. The “All” tab continues loading every source that still has more results.
4. Tap a search result to view its metadata and lyrics.
   - By default, only fields containing values are shown. Enable **Show All Search Result Fields** under `Settings` → `Search Settings` to display missing fields as “(empty)”.
5. Choose an action:
   - **Apply**: Apply both the selected metadata fields and lyrics to the editor.
   - **Lyrics Only**: Apply only the lyrics to the editor.
6. Back in the editor, review the content, then tap **Save**.

::: tip No search results?
Check that at least one plugin supporting song search is installed and enabled in `Settings` → `Search Settings` → `Plugin Management`, and that plugin configuration is complete. See [Using Plugins](./plugins.md).
:::

## Cover Operations

Tap the cover area or "Cover Options" in the editor to:

| Action | Description |
|--------|-------------|
| Change Cover | Pick a local image from the system picker |
| Search Cover Online | Search covers via enabled plugins |
| Use Same-Album Cover | Use the first readable cover found from songs in the same album |
| Remove Cover | Delete the current cover |
| Save Cover | Export the current cover to the system pictures directory |
| Crop Image | Crop the current cover before applying |

Online cover search requires a plugin that supports cover search.

## Lyrics Operations

Tap "Lyrics Options" to:

| Action | Description |
|--------|-------------|
| Import | Read lyrics from a text file as UTF-8 |
| Export | Export current lyrics. TTML exports as `.ttml`, others as `.lrc`; empty lyrics are not exported |
| Simplify / Traditionalize | Convert lyrics between Simplified and Traditional Chinese |
| Lyric Offset | Adjust timing with `-500`, `-100`, `+100`, `+500` ms buttons |
| Format Lyrics | Convert format, sort by line, remove empty lines, or strip non-lyric content |
| View Lyrics Text | Render word-level lyrics as plain text, with options to toggle romanization and translation |

::: warning Format limitations
Formatting lyrics cannot generate word-level timings from scratch. If the source lacks word-level timing data, it cannot be converted to a word-timed format.
:::

Enter lyric offsets directly in milliseconds. Positive values shift everything later and negative values shift everything earlier. Only complete integers are applied; clearing the field leaves it empty and does not reset the timeline. Use the reset button on the right to restore an offset of `0`.

## ReplayGain

ReplayGain normalizes playback volume across different tracks.

**Steps:**

1. Find the ReplayGain section in the editor.
2. Tap **Calculate ReplayGain**. Lyrico analyzes the current audio.
3. Once complete, track gain, track peak, and reference loudness fields are populated in the editor.
4. Tap **Save** to write these values to the audio file.

You can cancel the calculation at any time. Existing ReplayGain values can be recalculated to override.

## More Menu

The editor's more menu includes:

- **Song Info**: View detailed file info (duration, bitrate, sample rate, channels, path, size, etc.), copy content.
- **Share Song**: Send the audio file via the system share sheet.
- **Rename File**: Rename the file while keeping the extension (does not modify the title tag).
- **Delete Song**: Delete the local audio file. Cannot be undone.
