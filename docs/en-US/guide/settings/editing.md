# Tag editing

Open **Settings → Tag editing**.

## Tag writing

### Edit Fields

Open **Settings → Tag editing → Edit Fields** to manage built-in fields and custom tags in one list.

- Long-press and drag a field to reorder it. Use its switch to control visibility.
- For ReplayGain, choose **Manage Fields** from its menu to reorder or hide individual fields. Group and member switches are saved separately.
- Choose **View Songs** from a field's menu to see songs where the field is populated or missing.
- Use the reset button in the top bar to restore defaults. This does not modify song data.

The configuration applies to single-song editing, batch editing, and batch matching. Hiding a field does not clear its existing tag data.

#### View Songs by Field

**View Songs** opens a page for that field. The top has two tabs: **Populated** and **Missing**.

- Tap a song to edit it. After you change a field or rename a file, the list updates with the library.
- Long-press or swipe to enter selection mode, then use the same batch actions available in song lists, such as editing tags, matching metadata, renaming, and deleting.
- The cover field reads the audio file to decide. Songs that cannot be read are not listed under **Missing**; a "Some songs could not be read" notice appears at the top with a **Retry** button.

#### Custom Tags

Tap **+** in the top bar. Tap a suggested library tag to fill the input, or enter a key such as `SOURCE`, then confirm to add it. Keys are trimmed and converted to uppercase; duplicates, line breaks, and keys longer than 64 characters are not allowed.

Delete a custom tag through its menu to remove it from the configuration. Its data remains in the audio files.

### Multi-artist separator

Choose the separator used to join multiple artist names: enumeration comma, slash, comma or semicolon.

This is separate from the library's [Artist Split Rules](./library.md#artist-split-rules).

## Lyric content

### Lyric format

Choose the preferred lyric format when searching:

- **Line-Timed Lyrics**: Traditional line-by-line LRC format.
- **Word-Timed Lyrics**: Lyrics with per-word timing.
- **Enhanced Word-Timed Lyrics**: Lyrics with per-word timing and additional metadata.
- **TTML**: TTML format (Timed Text Markup Language).

#### TTML Information Preservation

TTML output preserves supported performer references, paragraph timing, language tags, head metadata, word-timed romanization and multi-syllable Ruby annotations when present in the source. Ruby timing follows lyric offsets; missing boundaries are filled from neighboring syllables and word timing. Body duration does not change with the offset.

LRC cannot preserve TTML-only structures such as performers, paragraphs and Ruby. Word-timed LRC can retain romanization timestamps. Arbitrary XML structures are not guaranteed to survive parsing and rewriting; API5 plugins can return the supported extended fields.

### Romanization

Include romanized text when retrieving lyrics.

### Translation

Include translated lyrics when retrieving lyrics.

### Translated lyrics only

Requires Translation. Use translated lines when available; keep the original for lines without translations.

### Lyric Line Order

When several lyric parts share a timestamp, line-based lyrics write them in the order set here. Open **Settings** → **Lyrics** → **Lyric Line Order**. The lyrics settings panel on search screens shows the same list and changes the same setting.

Long-press **Original**, **Romanization**, or **Translation** in the list and drag to reorder. The default is Original → Romanization → Translation. Only LRC-style formats (line-timed, word-timed, and enhanced word-timed) follow this order; TTML keeps its own structure.

The list shows only the parts you have enabled. Turn off **Romanization** and it disappears from the list; turn it back on and its earlier position stays. With **Translated lyrics only** on, only **Translation** is listed.

Lyric Line Order is a global setting and does not rewrite existing lyrics. To reorder or remove the lines that share a timestamp in an existing file, use **Lyrics Options → Organize lyrics → Sort by lyric line**, which rewrites the text. See [Organize lyrics](../single-song.md#organize-lyrics).

Search again to apply changes to existing results. Lyrics saved in audio files are unchanged.

## Text Processing

### Chinese Text Conversion

"Chinese Text Conversion" applies to searched text metadata and lyrics. IDs, dates, numbers, URLs, and covers are not affected.

Options:
- None
- Simplified → Traditional
- Traditional → Simplified

### Non-Lyric Content Rules

Used by **Organize lyrics** → **Remove non-lyric content** to remove composer, lyricist, source, and similar lines.

Filtering rules are managed at `Settings` → `Tag editing` → `Non-Lyric Content Filtering Rules`. Matching is case-insensitive. A line is removed if it contains any configured keyword; word-timed lyrics are matched as complete lines.

Common filter examples: `Composer:`, `Lyricist:`, `Arranger:`, `Source: QQ Music`, etc.

### Remove Empty Lines

When enabled, empty lines are removed during lyric output and cleanup.

## ReplayGain

### Target loudness

The target for ReplayGain calculation defaults to **-18 LUFS**.

Presets are **-23 LUFS (EBU R128)**, **-18 LUFS (default)** and **-14 LUFS (streaming)**. Custom values range from **-60 to 0 LUFS**.

### ReplayGain peak type

- **Sample peak** is the default and follows traditional ReplayGain peak semantics.
- **True peak** estimates the reconstructed peak through oversampling interpolation.

Both use 1.0 for digital full scale. Decoded compressed audio can exceed 1.0 even with sample-peak measurement.

Changes affect future calculations. Run [ReplayGain calculation](../single-song.md#replaygain) again to update existing tags.
