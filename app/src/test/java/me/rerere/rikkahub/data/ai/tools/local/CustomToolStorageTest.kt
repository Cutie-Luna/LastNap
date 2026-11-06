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
    fun globalQuotaIsSharedAcrossDifferentTools() {
        val parent = Files.createTempDirectory("lastnap-global-storage-test").toFile()
        val storage = CustomToolStorage(File(parent, "private-tool-data"), maxGlobalBytes = 1_024)
        try {
            storage.writeBytes("tool-a", "first.bin", ByteArray(700))

            assertTrue(runCatching {
                storage.writeBytes("tool-b", "second.bin", ByteArray(400))
            }.isFailure)
            assertNull(storage.readBytes("tool-b", "second.bin"))
            assertEquals(700, storage.readBytes("tool-a", "first.bin")!!.size)
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
    fun reconciliationRemovesOnlyStorageForMissingToolIds() {
        val (parent, storage) = newStorage()
        try {
            storage.writeBytes("tool-a", "data.txt", "first".toByteArray())
            storage.writeBytes("tool-b", "data.txt", "second".toByteArray())

            assertEquals(1, storage.reconcile(setOf("tool-b")))

            assertNull(storage.readBytes("tool-a", "data.txt"))
            assertEquals("second", storage.readBytes("tool-b", "data.txt")!!.decodeToString())
        } finally {
            parent.deleteRecursively()
        }
    }

    @Test
    fun reconciliationRemovesInterruptedTemporaryWritesForLiveTools() {
        val (parent, storage) = newStorage()
        try {
            storage.writeBytes("tool-a", "saved.txt", "safe".toByteArray())
            val toolDirectory = File(File(parent, "private-tool-data").listFiles()!!.single(), ".crash.tmp")
            toolDirectory.writeBytes(byteArrayOf(1, 2, 3))

            assertEquals(1, storage.reconcile(setOf("tool-a")))
            assertFalse(toolDirectory.exists())
            assertEquals("safe", storage.readBytes("tool-a", "saved.txt")!!.decodeToString())
        } finally {
            parent.deleteRecursively()
        }
    }

    @Test
    fun reconciliationRemovesInterruptedTemporaryWritesButKeepsOwnedData() {
        val (parent, storage) = newStorage()
        try {
            storage.writeBytes("tool-a", "cache.txt", "complete".toByteArray())
            val toolDirectory = File(parent, "private-tool-data").listFiles()!!.single()
            val temporary = File(toolDirectory, ".cache.txt.123.tmp")
            temporary.writeText("partial")

            assertEquals(1, storage.reconcile(setOf("tool-a")))

            assertFalse(temporary.exists())
            assertEquals("complete", storage.readBytes("tool-a", "cache.txt")!!.decodeToString())
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
