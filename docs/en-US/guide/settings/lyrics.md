# Lyrics Settings

Open **Settings → Tag editing → Lyric content** to configure lyric retrieval and output.

<a id="lyric-mode"></a>

## Lyric format

Choose the preferred lyric format when searching:

- **Line-Timed Lyrics**: Traditional line-by-line LRC format.
- **Word-Timed Lyrics**: Lyrics with per-word timing.
- **Enhanced Word-Timed Lyrics**: Lyrics with per-word timing and additional metadata.
- **TTML**: TTML format (Timed Text Markup Language).

## Translation and Romanization

- **Romanization**: When enabled, requests romanized lyrics during search (useful for Japanese songs).
- **Translation**: When enabled, requests translated lyrics during search.
- **Translated lyrics only**: Requires Translation to be enabled. Uses translated text where available and keeps the original text for lines without a translation.

## Lyric Line Order

When several lyric parts share a timestamp, line-based lyrics write them in the order set here. Open **Settings** → **Lyrics** → **Lyric Line Order**. The lyrics settings panel on search screens shows the same list and changes the same setting.

Long-press **Original**, **Romanization**, or **Translation** in the list and drag to reorder. The default is Original → Romanization → Translation. Only LRC-style formats (line-timed, word-timed, and enhanced word-timed) follow this order; TTML keeps its own structure.

The list shows only the parts you have enabled. Turn off **Romanization** and it disappears from the list; turn it back on and its earlier position stays. With **Translated lyrics only** on, only **Translation** is listed.

Lyric Line Order is a global setting and does not rewrite existing lyrics. To reorder or remove the lines that share a timestamp in an existing file, use **Lyrics Options → Organize lyrics → Sort by lyric line**, which rewrites the text. See [Organize lyrics](../single-song.md#organize-lyrics).

## TTML Information Preservation

TTML output preserves supported performer references, paragraph timing, language tags, head metadata, word-timed romanization and multi-syllable Ruby annotations when present in the source. Ruby timing follows lyric offsets; missing boundaries are filled from neighboring syllables and word timing. Body duration does not change with the offset.

LRC cannot preserve TTML-only structures such as performers, paragraphs and Ruby. Word-timed LRC can retain romanization timestamps. Arbitrary XML structures are not guaranteed to survive parsing and rewriting; API5 plugins can return the supported extended fields.

## Notes

Changing the lyric format, romanization, or translation settings does **not auto-refresh** existing search results. Re-search to apply the new settings.

Lyric mode, romanization, and translation settings also do not affect lyrics already saved in audio files.
