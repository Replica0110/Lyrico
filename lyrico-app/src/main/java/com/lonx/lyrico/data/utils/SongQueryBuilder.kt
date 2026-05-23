package com.lonx.lyrico.data.utils

import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteQuery
import com.lonx.lyrico.viewmodel.SortBy
import com.lonx.lyrico.viewmodel.SortInfo
import com.lonx.lyrico.viewmodel.SortOrder

object SongQueryBuilder {

    fun build(sortInfo: SortInfo): SupportSQLiteQuery {
        return build(sortInfo, null)
    }

    fun build(sortInfo: SortInfo, folderId: Long?): SupportSQLiteQuery {

        val orderColumn = when (sortInfo.sortBy) {
            SortBy.TITLE -> "s.titleSortKey"
            SortBy.ARTISTS -> "s.artistSortKey"
            SortBy.DATE_MODIFIED -> "s.fileLastModified"
            SortBy.DATE_ADDED -> "s.fileAdded"
            SortBy.FILE_SIZE -> "s.fileSize"
            SortBy.DURATION -> "s.durationMilliseconds"
            SortBy.EXTENSION -> "s.fileExtension"
        }

        val direction = when (sortInfo.order) {
            SortOrder.ASC -> "ASC"
            SortOrder.DESC -> "DESC"
        }

        val whereClause = if (folderId != null) {
            "WHERE s.folderId = $folderId AND f.isIgnored = 0"
        } else {
            "WHERE f.isIgnored = 0"
        }

        val sql = """
            SELECT s.* FROM songs AS s
            INNER JOIN folders AS f ON s.folderId = f.id
            $whereClause
            ORDER BY $orderColumn $direction, s.titleSortKey ASC
        """.trimIndent()

        return SimpleSQLiteQuery(sql)
    }
}