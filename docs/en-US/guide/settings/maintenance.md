# Task History

Go to: `Settings` → `Task History`.

Task history records results of all batch operations, including batch matching, batch editing, batch renaming, lyrics processing, ReplayGain calculation, and lyric/cover export.

## Viewing and Filtering

- Filter by **task type**: Batch Match, Lyrics processing, Export Lyrics, etc.
- Filter by **status**: Queued, Running, Succeeded, Failed, Skipped, Cancelled.

## Managing Tasks

- Running or queued tasks can be **cancelled**.
- Completed task records can be **deleted**.
- "Clear History" only removes completed task records matching the current filter. Running tasks are preserved.

::: warning Deleting task records does not undo changes
Deleting a task record does not revert tag modifications already written to files. It only cleans up the history log.
:::

## Viewing Task Details

Open a task to see per-song results in Succeeded, Failed, and Skipped tabs. A task may show "Succeeded" overall even if some individual songs failed or were skipped.
