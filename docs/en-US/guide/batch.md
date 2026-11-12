# Batch Operations

Batch operations process multiple songs at once.

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
- Convert lyrics format
- Organize lyrics
- Export Lyrics
- Export Covers
- Rename
- Edit Tags
- Tag Matching
- Batch Match Lyrics
- Batch Match Covers
- Delete
- Share

---

## Tag Matching

**Tag Matching** uses enabled metadata sources to write metadata fields such as title, artist and album. Lyrics and covers are handled by separate **Batch Match Lyrics** and **Batch Match Covers** tasks.

Choose the corresponding task in the batch menu, then configure concurrency and write behavior before starting. Each task uses the enablement and priority of its own source type in Plugin Manager. Lyrics-only sources can return candidates directly without song search; cover candidates are scored against local song information.

**Full Steps:**

1. In the song list, **enter selection mode** by long-pressing or swiping.
2. Select the songs you want to match (you can use Select All or manually check each one).
3. Tap the FAB expand button and choose **Tag Matching**.
4. In the "Batch Match Config" page, review or adjust the **concurrency** setting.
5. Tap confirm to start the task.
6. Progress is shown during execution. You can monitor in the progress dialog or later in `Settings` → `Task History`.

**Before matching, verify:**

- At least one plugin is **enabled** in `Settings` → `Online search` → `Plugin Manager`.
- Plugin order matches your preference (earlier plugins are tried first).
- Plugins requiring Token, API Key, region, etc. have their configuration saved.
- The relevant field write rules are set properly (Complement or Overwrite, not Disabled).

**Concurrency:** Higher concurrency processes faster but is more likely to trigger network failures or rate limiting. If requests fail frequently, lower the concurrency and retry.

**Use filename for matching:** If filename matching is enabled in the batch-match configuration sheet, batch matching ignores existing title and artist tags and uses the filename as the search keyword. This is useful for libraries with messy tags but well-named files.

---

## Edit Tags

**Edit Tags** writes the same field values across multiple songs. The Config tab only lists fields enabled for batch editing in [Edit Fields](./settings/editing.md#edit-fields).

**Steps:**

1. In the song list, **enter selection mode** by long-pressing or swiping, then select songs.
2. Tap the FAB and choose **Edit Tags**.
3. Fill in the fields to write on the **Config** tab.
4. Switch to **Preview (N)** to review the changes. N is the number of songs that will change.
5. Tap **Confirm** (✓) in the top bar to start. A bottom sheet shows progress plus success, skipped, and failed counts; tap **Abort** to stop.
6. When it finishes, tap **Confirm** to close the sheet and go back. If any songs failed, you stay on the page and can check Task History.

**Three states for a field:**

| State | UI | Result |
|-------|----|--------|
| Keep original | The field shows `<keep>` and its label reads **"Title (Unchanged)"** | Nothing is written; the original value stays |
| Clear field | The field is empty | The tag is cleared |
| Write new value | The field contains your text | Written to every selected song |

"(Unchanged)" only means the field is skipped. It is **not** clearing. To clear a field, delete its text; an unchanged single-line field also has an **✕** button that clears it. After you edit a field, its icon becomes **Undo**; tap it to return to "(Unchanged)".

**Pick values from selected songs:** The list icon next to a field opens **Select from selected songs** with the non-empty values those songs already have for that field (duplicates removed). Tap one to fill the field. If none exist, it shows "No available values in selected songs".

**Other actions:**

- **Cover**: tap the cover area to **Choose Cover from This Device**, **Select cover from selected songs**, **Choose Cover from Same Album**, **Remove Cover**, or **Revert Cover**. Same-album covers require the selected songs to share album and artist, or fill in the album field on the Config tab first.
- **Custom tags**: choose **Add Custom Tag** in the Config tab's FAB menu, or add and enable tags in [Edit Fields](./settings/editing.md#edit-fields).
- **Edit Fields**: **Edit Fields** in the Config tab's FAB menu opens the field visibility and order settings.
- **How to use**: **How to use** in the Config tab's FAB menu summarizes these rules.

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

## Convert Lyrics Format

1. Enter selection mode, select songs, and choose **Convert lyrics format** from the FAB menu.
2. Choose the target format and concurrency.
3. Tap **Process** to start. Results are written directly to the files.

Songs without lyrics are skipped. Lyrics without word-level timing cannot be converted to word-timed formats.

## Organize Lyrics

1. Select songs, open **Organize lyrics** from the FAB menu, and choose an operation:
   - **Sort by lyric line**: Reorder lines sharing a timestamp or remove a line.
   - **Remove empty lines**: Delete blank or placeholder-only lines.
   - **Remove non-lyric content**: Remove credits and similar lines using [filtering rules](./settings/editing.md#non-lyric-content-rules).
2. Adjust the operation's settings and concurrency.
3. Tap **Process** to start. Results are written directly to the files.

View task records under **Settings → Task History → Lyrics processing**.

Sorting uses the same settings as single-song editing; see [Organize Lyrics](./single-song.md#organize-lyrics). Songs without lyrics or with no changes after processing are skipped.

---

## Calculate ReplayGain

**Calculate ReplayGain** analyzes selected songs and writes track gain, track peak, and reference loudness. A song is skipped only when all three fields are present and its reference loudness matches the current setting. If any field is missing or the reference loudness has changed, ReplayGain is recalculated and the track values are overwritten.

Decoding starts with the system decoder. If that fails, Lyrico automatically retries with its built-in FFmpeg decoder; this is common with ALAC and APE songs and needs no setup. The fallback can also fail—Lyrico does not guarantee every codec. A song that still fails is marked as failed without affecting the others; check the Failed tab in Task History.

**Full Steps:**

1. **Enter selection mode**, select songs, then choose **Calculate ReplayGain** from the FAB.
2. Review or adjust concurrency.
3. Tap confirm to start.

Higher concurrency means faster processing but more CPU and memory usage. If the device heats up, lags, or failures increase, lower the concurrency.

The target loudness used for the calculation comes from `Settings` → `Tag editing` → `Target loudness` (default -18 LUFS).

---

## Export Lyrics and Covers

**Export Lyrics** and **Export Covers** can save all files to one specified folder or save each file next to its corresponding audio file.

**Full Steps:**

1. **Enter selection mode**, select songs, then choose **Export Lyrics** or **Export Covers**.
2. Choose **Specified folder** or **Audio folder**. With a specified folder, select a writable destination in the system folder picker.
3. Lyrico begins exporting. Songs without lyrics/covers are skipped.

Export rules:
- TTML lyrics export as `.ttml`; others as `.lrc`.
- With **Audio folder**, each lyric or cover is written to the directory containing its corresponding audio file, even when the selected audio files are in different directories.
- Covers export as `.jpg` based on the audio filename.
- Existing files in the destination with the same name may be reused and overwritten.

---

## Delete and Share

**Delete** shows a "Delete selected files?" confirmation dialog. Confirming removes local audio files permanently.

**Share** invokes the Android system share sheet with the selected audio files.

Delete and Share run immediately without creating background tasks.

---

## Task History

All batch operations except Delete and Share create background tasks. Access them at `Settings` → `Task History`.

- Filter by **task type** and **status**.
- Statuses include: Queued, Running, Succeeded, Failed, Skipped, Cancelled.
- Running or queued tasks can be cancelled.
- Completed task records can be deleted—this does **not** undo changes already written to files.

Open a task to view per-song results in Succeeded, Failed, and Skipped tabs. A task may show "Succeeded" overall even if some individual songs failed or were skipped.
