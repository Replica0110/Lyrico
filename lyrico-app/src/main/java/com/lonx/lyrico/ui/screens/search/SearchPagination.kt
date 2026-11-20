package com.lonx.lyrico.ui.screens.search

import com.lonx.lyrico.data.model.search.SongSearchResult

internal data class SearchPageMergeResult(
    val results: List<SongSearchResult>,
    val addedCount: Int,
    val hasMore: Boolean
)

internal fun mergeSearchPage(
    existing: List<SongSearchResult>,
    incoming: List<SongSearchResult>,
    sourceMayHaveMore: Boolean
): SearchPageMergeResult {
    val seenKeys = existing
        .mapTo(mutableSetOf(), SongSearchResult::paginationKey)
    val uniqueIncoming = incoming.filter { seenKeys.add(it.paginationKey()) }

    return SearchPageMergeResult(
        results = existing + uniqueIncoming,
        addedCount = uniqueIncoming.size,
        hasMore = sourceMayHaveMore && (existing.isEmpty() || uniqueIncoming.isNotEmpty())
    )
}

private fun SongSearchResult.paginationKey(): String = "$pluginId\u0000$id"
