package me.rerere.rikkahub.data.files

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.model.localFileUrls
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
    fun nestedCustomToolImagePartsRemainConversationFileReferences() {
        val uri = "file:///conversation_media/chat-a/generated.png"
        val toolPart = UIMessagePart.Tool(
            toolCallId = "call-1",
            toolName = "custom_tool",
            input = "{}",
            output = listOf(UIMessagePart.Text("""{"result":"ready"}"""), UIMessagePart.Image(uri)),
        )

        assertEquals(setOf(uri), listOf(toolPart).localFileUrls())
    }

    @Test
    fun cancellationIsPropagatedInsteadOfBeingTreatedAsUnknownOwnership() = runBlocking {
        val failure = runCatching {
            findUnreferencedChatFiles(
                files = listOf("file:///conversation_media/chat-a/image.png"),
                fileUrl = { it },
                hasFileReference = { throw CancellationException("cancelled") },
            )
        }.exceptionOrNull()

        assertTrue(failure is CancellationException)
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
