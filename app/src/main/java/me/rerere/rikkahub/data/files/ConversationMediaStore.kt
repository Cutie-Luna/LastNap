package me.rerere.rikkahub.data.files

/**
 * Narrow boundary for publishing tool-produced media into durable conversation-owned storage.
 * Tool-private storage is deliberately not exposed through this interface.
 */
interface ConversationMediaStore {
    suspend fun saveConversationMedia(
        conversationId: String,
        bytes: ByteArray,
        displayName: String,
        mimeType: String,
    ): String

    fun discardConversationMedia(uri: String)
}


/**
 * Covers the cancellation window where durable storage commits a file but withContext cannot
 * deliver its URI back to the cancelled caller. The commit callback lets the owner clean it up.
 */
internal suspend fun <T> withConversationMediaCancellationCleanup(
    persist: suspend (onCommitted: (String) -> Unit) -> T,
    discard: (String) -> Unit,
): T {
    var committedUri: String? = null
    return try {
        persist { committedUri = it }
    } catch (cancelled: kotlinx.coroutines.CancellationException) {
        committedUri?.let { uri -> runCatching { discard(uri) } }
        throw cancelled
    }
}
