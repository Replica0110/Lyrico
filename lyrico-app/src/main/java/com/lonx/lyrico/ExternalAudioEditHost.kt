package com.lonx.lyrico

/** Completes one external edit request without affecting ordinary in-app navigation. */
interface ExternalAudioEditHost {
    /**
     * Called when the external edit UI finishes.
     *
     * @param requestId must match the active external edit request id.
     * @param saved true if the user saved the changes.
     * @param extra optional [android.os.Bundle] of edited tag values (see [ExternalEditExtras]),
     *             returned to the calling app via the result [android.content.Intent] so it can
     *             update its own metadata without re-scanning the file. May be null.
     */
    fun finishExternalAudioEdit(
        requestId: Long,
        saved: Boolean,
        extra: android.os.Bundle? = null,
    )
}

/** Keys for the tag values returned in the result Intent's extras by [finishExternalAudioEdit]. */
object ExternalEditExtras {
    const val TITLE = "com.lonx.lyrico.extra.TITLE"
    const val ARTIST = "com.lonx.lyrico.extra.ARTIST"
    const val ALBUM_ARTIST = "com.lonx.lyrico.extra.ALBUM_ARTIST"
    const val ALBUM = "com.lonx.lyrico.extra.ALBUM"
    const val GENRE = "com.lonx.lyrico.extra.GENRE"
    const val LYRICS = "com.lonx.lyrico.extra.LYRICS"
    const val PIC_URL = "com.lonx.lyrico.extra.PIC_URL"
}
