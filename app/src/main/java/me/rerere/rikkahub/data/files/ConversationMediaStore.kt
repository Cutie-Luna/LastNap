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
