package com.lonx.lyrico.ui.screens.edit

/** Page identity and independent image edits; tickets never become valid again after A → B → A. */
internal class PictureEditSession {
    data class Ticket internal constructor(
        internal val epoch: Long,
        internal val key: String? = null,
        internal val revision: Long = 0,
        internal val artistRevision: Long = 0,
    )

    private var uri: String? = null
    private var epoch = 0L
    private var artistRevision = 0L
    private val revisions = mutableMapOf<String, Long>()

    @Synchronized
    fun changeSong(nextUri: String) {
        if (uri == nextUri) return
        uri = nextUri
        epoch++
        revisions.clear()
        artistRevision++
    }

    @Synchronized
    fun ticket(): Ticket = Ticket(epoch)

    @Synchronized
    fun begin(key: String): Ticket {
        val revision = (revisions[key] ?: 0) + 1
        revisions[key] = revision
        return Ticket(epoch, key, revision, artistRevision)
    }

    @Synchronized
    fun invalidateArtists() { artistRevision++ }

    @Synchronized
    fun isCurrent(ticket: Ticket): Boolean = ticket.epoch == epoch &&
        (ticket.key == null || revisions[ticket.key] == ticket.revision) &&
        (ticket.key?.startsWith("artist:") != true || ticket.artistRevision == artistRevision)
}
