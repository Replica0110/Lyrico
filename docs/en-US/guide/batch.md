# Batch Operations

Batch operations let you process multiple songs at once. This page first explains how to enter selection mode and select songs, then covers each batch task in detail.

## Entering Selection Mode

To perform any batch operation, you must first enter **multi-select mode** and select songs.

### Method 1: Long-Press

**Steps:**

1. In a song list, **long-press** any song item.
2. The song is automatically selected, and the top bar shows "Selected: 1".
3. Tap other songs to add or remove them from the selection.

### Method 2: Swipe

**Steps:**

1. **Swipe left or right** on any song item.
2. Once the swipe reaches the threshold, selection mode activates with that song selected.

### Range Selection (Select Consecutive Songs)

Swipe selection can select a range of consecutive songs at once.

**Steps:**

1. Without being in selection mode, swipe left or right on the first song. It becomes selected as the **range start**.
2. Swipe another **unselected** song in the same list.
3. Lyrico automatically selects all visible items between the range start and the target song.
4. After the range is complete, you can continue tapping or swiping to add more songs.

The UI shows hints like "Enter Multi-Select", "Range Start", or "Range End" during swiping. Range selection is based on the current visible list order; if the list is sorted or filtered, the range reflects the current view.

### Operations in Selection Mode

Once in selection mode:

- Each song's more menu button is replaced by a **checkbox**. Tapping either the song or the checkbox toggles selection.
- The top bar provides three actions:
  - **Select All**: Select every song in the current list.
  - **Deselect All**: Clear the selection.
  - **Close**: Exit selection mode and clear selection.
- Pressing the system back button also exits selection mode.

### Opening the Batch Menu

After selecting at least one song, an **expand button (FAB)** appears at the bottom-right. Expanding it shows available batch operations:

- Calculate ReplayGain
- Format Lyrics
- Export Lyrics
- Export Covers
- Rename
- Edit Tags
- Tag Matching
- Delete
- Share

---

## Tag Matching

**Tag Matching** uses enabled plugins to search song metadata and writes title, artist, album, lyrics, cover, and other fields according to configuration.

**Full Steps:**

1. In the song list, **enter selection mode** by long-pressing or swiping.
2. Select the songs you want to match (you can use Select All or manually check each one).
3. Tap the FAB expand button and choose **Tag Matching**.
4. In the "Batch Match Config" page, review or adjust the **concurrency** setting.
5. Tap confirm to start the task.
6. Progress is shown during execution. You can monitor in the progress dialog or later in `Settings` → `Other` → `Task History`.

**Before matching, verify:**

- At least one plugin is **enabled** in `Settings` → `Search Settings` → `Plugin Management`.
- Plugin order matches your preference (earlier plugins are tried first).
- Plugins requiring Token, API Key, region, etc. have their configuration saved.
- The relevant field write rules are set properly (Complement or Overwrite, not Disabled).

**Concurrency:** Higher concurrency processes faster but is more likely to trigger network failures or rate limiting. If requests fail frequently, lower the concurrency and retry.

**Use filename for matching:** If "Prioritize filename matching" is enabled in `Settings` → `Search Settings`, batch matching ignores existing title and artist tags and uses the filename as the search keyword. This is useful for libraries with messy tags but well-named files.

---

## Edit Tags

**Edit Tags** writes the same field values across multiple songs at once.

**Full Steps:**

1. In the song list, **enter selection mode** by long-pressing or swiping.
2. Select the songs to modify.
3. Tap the FAB expand button and choose **Edit Tags**.
4. The batch edit page has **Configure** and **Preview** tabs.
5. In the Configure tab, add or modify fields to write. Leave fields as `<keep>` to preserve their original values.
6. Switch to the **Preview** tab to review the changes per song.
7. Verify everything looks correct, then tap **Save** to start writing.
8. After completion, success, skipped, and failed counts are shown. Failed items can be viewed in Task History.

`<keep>` means the field retains its original value. Only modified fields are written; unchanged fields are not cleared.

**Additional operations:**
- Pick field values from selected songs to use as batch input.
- Change, remove, or restore cover art in bulk.
- Pick cover from selected songs or same-album songs.
- Remove ReplayGain tags.
- Add custom tags (visible custom tags depend on Custom Tag Management and Field Visibility Settings).

---

## Rename Files

**Rename** changes audio filenames while keeping original extensions. It does not modify title, artist, or other tags.

**Full Steps:**

1. In the song list, **enter selection mode** and select songs.
2. Tap the FAB and choose **Rename**.
3. Enter a naming pattern using placeholders. For example, `@2 - @1` produces "Artist - Title".
4. If needed, configure character mapping to replace problematic characters.
5. Switch to the **Preview** tab to compare old and new filenames.
6. Tap **Execute Rename** to apply.

**Placeholders:**

| Placeholder | Field |
|-------------|-------|
| `@1` | Title |
| `@2` | Artist |
| `@3` | Album Artist |
| `@4` | Album |
| `@5` | Track Number |
| `@6` | Disc Number |
| `@7` | Year |
| `@8` | Genre |

If a placeholder's corresponding tag is empty, that part produces empty text. If the result is empty, the original filename is kept. When multiple files would get the same name, Lyrico automatically appends ` (1)`, ` (2)`, etc.

---

## Format Lyrics

**Format Lyrics** batch-processes or converts lyric content.

**Full Steps:**

1. **Enter selection mode**, select songs, then choose **Format Lyrics** from the FAB.
2. Configure options:
   - **Target format**: Keep current or convert to a specific lyric format.
   - **Sort by line**: Reorganize original-then-translation/romanization lyrics into line-by-line order.
   - **Remove non-lyric content**: Strip composer, lyricist, source lines using cleanup rules.
   - **Remove empty lines**: Delete lines with no content or only placeholders.
   - **Concurrency**: Control how many songs are processed simultaneously.
3. Tap confirm to start.

Songs without lyrics are skipped. Songs without word-level timing cannot be converted to word-timed formats.

Non-lyric content filtering rules are managed in `Settings` → `Metadata Processing`.

---

## Calculate ReplayGain

**Calculate ReplayGain** analyzes selected songs and writes ReplayGain tags. Songs that already have ReplayGain values are skipped (not overwritten).

**Full Steps:**

1. **Enter selection mode**, select songs, then choose **Calculate ReplayGain** from the FAB.
2. Review or adjust concurrency.
3. Tap confirm to start.

Higher concurrency means faster processing but more CPU and memory usage. If the device heats up, lags, or failures increase, lower the concurrency.

---

## Export Lyrics and Covers

**Export Lyrics** and **Export Covers** save lyrics or cover art to a target directory.

**Full Steps:**

1. **Enter selection mode**, select songs, then choose **Export Lyrics** or **Export Covers**.
2. In the system folder picker, select a writable destination directory.
3. Lyrico begins exporting. Songs without lyrics/covers are skipped.

Export rules:
- TTML lyrics export as `.ttml`; others as `.lrc`.
- Covers export as `.jpg` based on the audio filename.
- Existing files in the destination with the same name may be reused and overwritten.

---

## Delete and Share

**Delete** shows a "Delete selected files?" confirmation dialog. Confirming removes local audio files permanently.

**Share** invokes the Android system share sheet with the selected audio files.

These two operations run immediately without creating background tasks.

---

## Task History

All batch operations except Delete and Share create background tasks. Access them at `Settings` → `Other` → `Task History`.

- Filter by **task type** and **status**.
- Statuses include: Queued, Running, Succeeded, Failed, Skipped, Cancelled.
- Running or queued tasks can be cancelled.
- Completed task records can be deleted—this does **not** undo changes already written to files.

Open a task to view per-song results in Succeeded, Failed, and Skipped tabs. A task may show "Succeeded" overall even if some individual songs failed or were skipped.
