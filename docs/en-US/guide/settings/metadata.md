# Editor configuration

Open **Settings → Tag editing** for editor fields and text-processing options.

## Chinese Text Conversion

"Chinese Text Conversion" applies to searched text metadata and lyrics. IDs, dates, numbers, URLs, and covers are not affected.

Options:
- None
- Simplified → Traditional
- Traditional → Simplified

## Remove Empty Lines

When enabled, empty lines are removed during lyric output and cleanup.

## Non-Lyric Content Filtering

Used by **Organize lyrics** → **Remove non-lyric content** to remove composer, lyricist, source, and similar lines.

Filtering rules are managed at `Settings` → `Tag editing` → `Non-Lyric Content Filtering Rules`. Matching is case-insensitive. A line is removed if it contains any configured keyword; word-timed lyrics are matched as complete lines.

Common filter examples: `Composer:`, `Lyricist:`, `Arranger:`, `Source: QQ Music`, etc.

## Artist Splitting

Configure [Artist Split Rules](./library.md#artist-split-rules) under **Settings → Library management**.

## Edit Fields

Open **Settings → Tag editing → Edit Fields** to manage built-in fields and custom tags in one list.

- Long-press and drag a field to reorder it. Use its switch to control visibility.
- For ReplayGain, choose **Manage Fields** from its menu to reorder or hide individual fields. Group and member switches are saved separately.
- Choose **View Songs** from a field's menu to see songs where the field is populated or missing.
- Use the reset button in the top bar to restore defaults. This does not modify song data.

The configuration applies to single-song editing, batch editing, and batch matching. Hiding a field does not clear its existing tag data.

### View Songs by Field

**View Songs** opens a page for that field. The top has two tabs: **Populated** and **Missing**.

- Tap a song to edit it. After you change a field or rename a file, the list updates with the library.
- Long-press or swipe to enter selection mode, then use the same batch actions available in song lists, such as editing tags, matching metadata, renaming, and deleting.
- The cover field reads the audio file to decide. Songs that cannot be read are not listed under **Missing**; a "Some songs could not be read" notice appears at the top with a **Retry** button.

### Custom Tags

Tap **+** in the top bar. Tap a suggested library tag to fill the input, or enter a key such as `SOURCE`, then confirm to add it. Keys are trimmed and converted to uppercase; duplicates, line breaks, and keys longer than 64 characters are not allowed.

Delete a custom tag through its menu to remove it from the configuration. Its data remains in the audio files.

## Character Mapping

Used during batch rename to replace specific characters in filenames.

Open Character Mapping from the batch rename screen. Tap a character to configure its replacement rule. Each character can map to a different replacement value. Common usage: replacing `/` with `-`, or `:` with `_` for characters unsuitable in filenames.
