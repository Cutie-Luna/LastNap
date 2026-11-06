package me.rerere.rikkahub.data.files

import android.net.Uri
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class ConversationMediaOwnershipTest {

    @Test
    fun sharedMediaIsRetainedAndUniqueMediaIsDeletedOnce() = runBlocking {
        val unique = Uri.parse("file:///conversation_media/chat-a/unique.png")
        val shared = Uri.parse("file:///conversation_media/chat-a/shared.png")

        val deletable = findUnreferencedChatFiles(
            files = listOf(unique, shared, unique),
            hasFileReference = { it == shared.toString() },
        )

        assertEquals(listOf(unique), deletable)
    }

    @Test
    fun mediaIsRetainedWhenReferenceLookupFails() = runBlocking {
        val file = Uri.parse("file:///conversation_media/chat-a/unknown.png")

        val deletable = findUnreferencedChatFiles(files = listOf(file)) {
            error("Database temporarily unavailable")
        }

        assertEquals(emptyList<Uri>(), deletable)
    }
}
