# Maintenance

Day-to-day maintenance and troubleshooting features.

## Task History

Go to: `Settings` → `Other` → `Task History`.

Task history records results of all batch operations, including batch matching, batch editing, batch renaming, lyric formatting, ReplayGain calculation, and lyric/cover export.

### Viewing and Filtering

- Filter by **task type**: Batch Match, Format Lyrics, Export Lyrics, etc.
- Filter by **status**: Queued, Running, Succeeded, Failed, Skipped, Cancelled.

### Managing Tasks

- Running or queued tasks can be **cancelled**.
- Completed task records can be **deleted**.
- "Clear History" only removes completed task records matching the current filter. Running tasks are preserved.

::: warning Deleting task records does not undo changes
Deleting a task record does not revert tag modifications already written to files. It only cleans up the history log.
:::

### Viewing Task Details

Open a task to see per-song results in Succeeded, Failed, and Skipped tabs. A task may show "Succeeded" overall even if some individual songs failed or were skipped.

## App Logs

Go to: `Settings` → `Other` → `App Logs`.

App logs help diagnose issues across these categories:

- Crashes and exceptions
- Metadata read/write
- Batch operations
- Database
- Network requests
- Plugin execution

### Using Logs

- Filter by **level**: DEBUG, INFO, WARN, ERROR.
- Filter by **type**: Plugin, Network, Crash, Batch, etc.
- Tap a log entry to view details.
- **Copy** individual entries or **export** the log file.

### Log Retention

Options:
- No logging
- 7 days (default)
- 30 days
- 90 days
- Keep forever

The default 7 days is suitable for most troubleshooting. Longer retention uses more storage.

## Clear Cache

"Clear Cache" counts and clears:
- Image cache (cover art downloaded during search)
- Network cache
- External cache
- Other temporary files

Clearing cache does not delete music files or remove tags already written to audio files.

Cover image cache can grow quickly if you frequently search covers or have a high search limit. Regular cleanup is recommended.
