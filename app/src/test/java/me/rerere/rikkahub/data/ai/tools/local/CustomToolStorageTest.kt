package me.rerere.rikkahub.data.ai.tools.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class CustomToolStorageTest {

    private fun newStorage(): Pair<File, CustomToolStorage> {
        val parent = Files.createTempDirectory("lastnap-storage-test").toFile()
        return parent to CustomToolStorage(File(parent, "private-tool-data"))
    }

    @Test
    fun storageIsCreatedLazilyAndIsolatedByToolId() {
        val (parent, storage) = newStorage()
        try {
            assertFalse(File(parent, "private-tool-data").exists())

            storage.writeBytes("tool-a", "settings.json", """{"theme":"dark"}""".toByteArray())

            assertTrue(File(parent, "private-tool-data").exists())
            assertEquals("""{"theme":"dark"}""", storage.readBytes("tool-a", "settings.json")!!.decodeToString())
            assertNull(storage.readBytes("tool-b", "settings.json"))
        } finally {
            parent.deleteRecursively()
        }
    }

    @Test
    fun deletingOneToolDoesNotTouchAnotherToolsData() {
        val (parent, storage) = newStorage()
        try {
            storage.writeBytes("tool-a", "data.txt", "first".toByteArray())
            storage.writeBytes("tool-b", "data.txt", "second".toByteArray())

            storage.deleteToolStorage("tool-a")

            assertNull(storage.readBytes("tool-a", "data.txt"))
            assertEquals("second", storage.readBytes("tool-b", "data.txt")!!.decodeToString())
        } finally {
            parent.deleteRecursively()
        }
    }

    @Test
    fun unsafeNamesAndOversizedFilesAreRejected() {
        val (parent, storage) = newStorage()
        try {
            assertTrue(runCatching { storage.writeBytes("tool-a", "../escape.txt", byteArrayOf(1)) }.isFailure)
            assertTrue(runCatching { storage.writeBytes("tool-a", "/absolute.txt", byteArrayOf(1)) }.isFailure)
            assertTrue(runCatching {
                storage.writeBytes("tool-a", "large.bin", ByteArray(1024 * 1024 + 1))
            }.isFailure)
        } finally {
            parent.deleteRecursively()
        }
    }

    @Test
    fun explicitToolDeletionRemovesItsPrivateStorage() {
        val (parent, storage) = newStorage()
        try {
            storage.writeBytes("tool-a", "cache.txt", "keep".toByteArray())
            // Capability flags control runtime access; only explicit tool deletion removes data.
            assertEquals("keep", storage.readBytes("tool-a", "cache.txt")!!.decodeToString())
            storage.deleteToolStorage("tool-a")
            assertNull(storage.readBytes("tool-a", "cache.txt"))
        } finally {
            parent.deleteRecursively()
        }
    }
}
