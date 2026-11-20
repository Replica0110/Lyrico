package com.lonx.lyrico.data.song.library

import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteQuery

object SongQueryBuilder {

    fun build(sortInfo: SongSortInfo): SupportSQLiteQuery {
        return build(sortInfo, null)
    }

    fun build(sortInfo: SongSortInfo, folderId: Long?): SupportSQLiteQuery {

        val direction = when (sortInfo.order) {
            SortOrder.ASC -> "ASC"
            SortOrder.DESC -> "DESC"
        }

        val orderClause = when (sortInfo.sortBy) {
            SongSortBy.TITLE -> "s.titleSortKey $direction, s.titleSortKey ASC"
            SongSortBy.ARTISTS -> "s.artistSortKey $direction, s.titleSortKey ASC"
            SongSortBy.ALBUM -> """
                s.albumSortKey $direction,
                COALESCE(s.discNumber, 1) ASC,
                CAST(NULLIF(s.trackerNumber, '') AS INTEGER) ASC
            """.trimIndent()
            SongSortBy.DATE_MODIFIED -> "s.fileLastModified $direction, s.titleSortKey ASC"
            SongSortBy.DATE_ADDED -> "s.fileAdded $direction, s.titleSortKey ASC"
            SongSortBy.FILE_SIZE -> "s.fileSize $direction, s.titleSortKey ASC"
            SongSortBy.DURATION -> "s.durationMilliseconds $direction, s.titleSortKey ASC"
            SongSortBy.EXTENSION -> "s.fileExtension $direction, s.titleSortKey ASC"
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
