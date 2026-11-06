package me.rerere.rikkahub.data.ai.tools.local

import me.rerere.rikkahub.data.model.CustomExtension
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

class CustomToolSecretStoreTest {

    private class TestKeyProvider : CustomToolSecretKeyProvider {
        private val key: SecretKey = KeyGenerator.getInstance("AES").apply {
            init(256)
        }.generateKey()

        override fun getOrCreateKey(): SecretKey = key
    }

    private fun temporaryStore(): Pair<File, CustomToolSecretStore> {
        val root = Files.createTempDirectory("lastnap-custom-tool-secrets").toFile()
        return root to CustomToolSecretStore(root, TestKeyProvider())
    }

    @Test
    fun encryptedSecretsPersistWithoutWritingPlaintextOrLeavingTemporaryFiles() {
        val root = Files.createTempDirectory("lastnap-secret-persistence").toFile()
        try {
            val keyProvider = TestKeyProvider()
            val firstStore = CustomToolSecretStore(root, keyProvider)
            val secret = "sensitive-value-that-must-not-be-serialized"

            firstStore.putSecret("tool-a", "API_KEY", secret)
            val storeDirectory = File(root, "custom_tool_secrets")
            val storedFile = storeDirectory.listFiles()!!.single { it.extension == "json" }

            assertFalse(storedFile.readText().contains(secret))
            assertTrue(storeDirectory.listFiles().orEmpty().none { it.name.endsWith(".tmp") })

            val reopenedStore = CustomToolSecretStore(root, keyProvider)
            assertEquals(secret, reopenedStore.getSecret("tool-a", "API_KEY"))

            reopenedStore.putSecret("tool-a", "API_KEY", "rotated-value")
            assertEquals("rotated-value", CustomToolSecretStore(root, keyProvider).getSecret("tool-a", "API_KEY"))
            assertTrue(storeDirectory.listFiles().orEmpty().none { it.name.endsWith(".tmp") })
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun ciphertextIsAuthenticatedToBothToolIdAndSecretName() {
        val cipher = CustomToolSecretCipher(TestKeyProvider())
        val encrypted = cipher.encrypt("tool-a", "API_KEY", "bound-secret")

        assertEquals("bound-secret", cipher.decrypt("tool-a", "API_KEY", encrypted))
        assertTrue(runCatching { cipher.decrypt("tool-b", "API_KEY", encrypted) }.isFailure)
        assertTrue(runCatching { cipher.decrypt("tool-a", "OTHER_KEY", encrypted) }.isFailure)
    }

    @Test
    fun requiredSecretReportingIgnoresMissingOptionalValues() {
        val (root, store) = temporaryStore()
        try {
            val extension = CustomExtension(
                id = "tool-a",
                name = "Configured API",
                description = "",
                secrets = listOf(
                    me.rerere.rikkahub.data.model.CustomToolValueDeclaration("API_KEY", required = true),
                    me.rerere.rikkahub.data.model.CustomToolValueDeclaration("OPTIONAL_TOKEN", required = false),
                ),
            )

            assertEquals(listOf("API_KEY"), store.missingRequiredSecrets(extension))
            store.putSecret("tool-a", "API_KEY", "configured")
            assertEquals(emptyList<String>(), store.missingRequiredSecrets(extension))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun declarationCleanupAndToolDeletionNeverTouchAnotherToolsSecrets() {
        val (root, store) = temporaryStore()
        try {
            store.putSecret("tool-a", "KEEP_KEY", "keep-a")
            store.putSecret("tool-a", "REMOVED_KEY", "remove-a")
            store.putSecret("tool-b", "KEEP_KEY", "keep-b")

            store.retainDeclaredSecrets("tool-a", setOf("KEEP_KEY"))

            assertEquals("keep-a", store.getSecret("tool-a", "KEEP_KEY"))
            assertNull(store.getSecret("tool-a", "REMOVED_KEY"))
            assertEquals("keep-b", store.getSecret("tool-b", "KEEP_KEY"))

            store.deleteToolSecrets("tool-a")
            assertNull(store.getSecret("tool-a", "KEEP_KEY"))
            assertEquals("keep-b", store.getSecret("tool-b", "KEEP_KEY"))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun startupReconciliationRemovesOrphanedStoresAndInterruptedTempFiles() {
        val (root, store) = temporaryStore()
        try {
            store.putSecret("tool-a", "API_KEY", "orphan")
            store.putSecret("tool-b", "API_KEY", "retained")
            val storeDirectory = File(root, "custom_tool_secrets")
            File(storeDirectory, "interrupted-write.tmp").writeText("partial")

            assertEquals(2, store.reconcile(mapOf("tool-b" to setOf("API_KEY"))))
            assertNull(store.getSecret("tool-a", "API_KEY"))
            assertEquals("retained", store.getSecret("tool-b", "API_KEY"))
            assertTrue(storeDirectory.listFiles().orEmpty().none { it.name.endsWith(".tmp") })
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun aSecretEncryptedWithAnotherDeviceKeyRequiresReentry() {
        val root = Files.createTempDirectory("lastnap-secret-key-rotation").toFile()
        try {
            CustomToolSecretStore(root, TestKeyProvider()).putSecret("tool-a", "API_KEY", "device-secret")
            val restoredOnAnotherDevice = CustomToolSecretStore(root, TestKeyProvider())
            val failure = runCatching { restoredOnAnotherDevice.getSecret("tool-a", "API_KEY") }.exceptionOrNull()

            assertTrue(failure is CustomToolSecretUnavailableException)
            assertFalse(failure?.message.orEmpty().contains("device-secret"))
        } finally {
            root.deleteRecursively()
        }
    }
}
