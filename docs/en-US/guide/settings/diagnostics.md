# Troubleshooting

Open **Settings → Troubleshooting**. App logs and problem diagnosis are followed by the Library maintenance group.

## Log recording

### App Logs

Go to: `Settings` → `Troubleshooting` → `App Logs`.

App logs help diagnose issues across these categories:

- Crashes and exceptions
- Metadata read/write
- Batch operations
- Database
- Network requests
- Plugin execution

#### Using Logs

- Filter by **level**: DEBUG, INFO, WARN, ERROR.
- Filter by **type**: Plugin, Network, Crash, Batch, etc.
- Tap a log entry to view details.
- **Copy** individual entries or **export** the log file.

#### Log Retention

Options:
- No logging
- 7 days (default)
- 30 days
- 90 days
- Keep forever

The default 7 days is suitable for most troubleshooting. Longer retention uses more storage.

### Problem diagnosis

Open **Settings → Troubleshooting → Problem diagnosis**.

1. Start recording before reproducing the issue.
2. Return to the affected feature and perform the actions that trigger the problem.
3. Return to the diagnosis page and stop recording.
4. Preview the report, then share or export it for troubleshooting.

This records a temporary diagnostic session; it does not automatically fix the issue. Review report contents before sharing.

## Library maintenance

Confirm an operation before starting it. Wait for it to finish before running another library maintenance action.

### Rebuild library indexes

Rebuild artist and album associations from existing song records. Use this when grouping or associations are inconsistent.

This does not rescan audio files or rewrite their tags. To discover added files or reload externally edited tags, refresh the audio folder instead.

### Reset music library

Clears song records, scan folders and library indexes. Audio files remain on the device.

Afterward, add audio folders again to rebuild the library. Exporting app settings does not back up these library records.
