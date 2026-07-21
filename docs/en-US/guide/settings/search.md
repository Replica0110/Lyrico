# Search Settings

Controls online search and batch matching behavior.

## Plugin Management

Go to: `Settings` → `Search Settings` → `Plugin Management`.

Manage installed search source plugins: import, enable/disable, reorder, configure, and uninstall. See [Using Plugins](../plugins.md) for full details.

## Results per Load

Sets the number of search results loaded from each plugin source at a time. Use the **Load more** button at the bottom of the results to continue. Higher values load more candidates at once but may consume more bandwidth and increase image cache size.

## Prioritize Filename Matching

Affects batch matching behavior:

- **Disabled** (default): Uses the audio file's built-in title and artist tags as search keywords.
- **Enabled**: Ignores built-in tags and uses the filename as the search keyword. Useful for libraries with messy tags but well-structured filenames.

When enabled, it's recommended to set the title and artist field write rules to **Overwrite** mode so match results correctly overwrite the old tags.
