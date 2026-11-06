package me.rerere.rikkahub.data.files

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class ConversationMediaOwnershipTest {

    @Test
    fun sharedMediaIsRetainedAndUniqueMediaIsDeletedOnce() = runBlocking {
        val unique = "file:///conversation_media/chat-a/unique.png"
        val shared = "file:///conversation_media/chat-a/shared.png"

        val deletable = findUnreferencedChatFiles(
            files = listOf(unique, shared, unique),
            fileUrl = { it },
            hasFileReference = { it == shared },
        )

        assertEquals(listOf(unique), deletable)
    }

    @Test
    fun mediaIsRetainedWhenReferenceLookupFails() = runBlocking {
        val file = "file:///conversation_media/chat-a/unknown.png"

        val deletable = findUnreferencedChatFiles(
            files = listOf(file),
            fileUrl = { it },
            hasFileReference = { error("Database temporarily unavailable") },
        )

        assertEquals(emptyList<String>(), deletable)
    }
}
