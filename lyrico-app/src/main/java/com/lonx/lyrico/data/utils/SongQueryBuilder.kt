package com.lonx.lyrico.data.utils

import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteQuery
import com.lonx.lyrico.data.model.SortBy
import com.lonx.lyrico.data.model.SortInfo
import com.lonx.lyrico.data.model.SortOrder

object SongQueryBuilder {

    fun build(sortInfo: SortInfo): SupportSQLiteQuery {
        return build(sortInfo, null)
    }

    fun build(sortInfo: SortInfo, folderId: Long?): SupportSQLiteQuery {

        val direction = when (sortInfo.order) {
            SortOrder.ASC -> "ASC"
            SortOrder.DESC -> "DESC"
        }

        val orderClause = when (sortInfo.sortBy) {
            SortBy.TITLE -> "s.titleSortKey $direction, s.titleSortKey ASC"
            SortBy.ARTISTS -> "s.artistSortKey $direction, s.titleSortKey ASC"
            SortBy.ALBUM -> """
                s.albumSortKey $direction,
                COALESCE(s.discNumber, 1) ASC,
                CAST(NULLIF(s.trackerNumber, '') AS INTEGER) ASC
            """.trimIndent()
            SortBy.DATE_MODIFIED -> "s.fileLastModified $direction, s.titleSortKey ASC"
            SortBy.DATE_ADDED -> "s.fileAdded $direction, s.titleSortKey ASC"
            SortBy.FILE_SIZE -> "s.fileSize $direction, s.titleSortKey ASC"
            SortBy.DURATION -> "s.durationMilliseconds $direction, s.titleSortKey ASC"
            SortBy.EXTENSION -> "s.fileExtension $direction, s.titleSortKey ASC"
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
            ORDER BY $orderClause
        """.trimIndent()

        return SimpleSQLiteQuery(sql)
    }
}
