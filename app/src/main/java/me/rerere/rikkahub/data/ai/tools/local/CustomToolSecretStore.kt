package me.rerere.rikkahub.data.ai.tools.local

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import me.rerere.rikkahub.data.model.CustomExtension
import me.rerere.rikkahub.data.model.CustomExtensionLimits
import java.io.File
import java.io.FileOutputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.security.MessageDigest
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Stores custom-tool Secrets encrypted under a dedicated Android Keystore key.
 *
 * Ciphertext lives only under noBackupFilesDir and is never part of Settings, Room or exports.
 * The cipher binds every value to its immutable tool ID and declared name using GCM AAD.
 */
class CustomToolSecretStore(context: Context) {
    private val root = File(context.noBackupFilesDir, DIRECTORY_NAME)
    private val lock = Any()

    fun putSecret(toolId: String, name: String, value: String) = synchronized(lock) {
        validateReference(toolId, name)
        require(value.isNotEmpty()) { "Secret value cannot be empty" }
        require(value.toByteArray(Charsets.UTF_8).size <= CustomExtensionLimits.MAX_SECRET_VALUE_LENGTH) {
            "Secret value exceeds the 8 KiB limit"
        }
        val secrets = readEncryptedValues(toolId).toMutableMap()
        secrets[name] = CustomToolSecretCipher.encrypt(toolId, name, value)
        writeEncryptedValues(toolId, secrets)
    }

    fun getSecret(toolId: String, name: String): String? = synchronized(lock) {
        validateReference(toolId, name)
        readEncryptedValues(toolId)[name]?.let { encrypted ->
            try {
                CustomToolSecretCipher.decrypt(toolId, name, encrypted)
            } catch (_: Exception) {
                throw CustomToolSecretUnavailableException(
                    "The saved Secret '$name' is unavailable on this device. Re-enter it in the tool settings."
                )
            }
        }
    }

    fun hasSecret(toolId: String, name: String): Boolean =
        getSecret(toolId, name)?.isNotEmpty() == true

    fun missingRequiredSecrets(extension: CustomExtension): List<String> =
        extension.secrets.filter { it.required && !hasSecret(extension.id, it.name) }.map { it.name }

    fun deleteSecret(toolId: String, name: String) = synchronized(lock) {
        validateReference(toolId, name)
        val secrets = readEncryptedValues(toolId).toMutableMap()
        secrets.remove(name)
        if (secrets.isEmpty()) {
            deleteFile(toolId)
        } else {
            writeEncryptedValues(toolId, secrets)
        }
    }

    fun deleteToolSecrets(toolId: String) = synchronized(lock) {
        validateToolId(toolId)
        deleteFile(toolId)
    }

    /** Deletes values whose declarations were removed, after the new tool definition is saved. */
    fun retainDeclaredSecrets(toolId: String, declaredNames: Set<String>) = synchronized(lock) {
        validateToolId(toolId)
        val secrets = readEncryptedValues(toolId).filterKeys { it in declaredNames }
        if (secrets.isEmpty()) deleteFile(toolId) else writeEncryptedValues(toolId, secrets)
    }

    /**
     * Removes orphaned stores after interrupted tool deletion. Corrupt/unreadable records are
     * retained for safety; cleanup never guesses ownership from an unverified filename.
     */
    fun reconcile(validToolIds: Set<String>): Int = synchronized(lock) {
        if (!root.isDirectory) return@synchronized 0
        var removed = 0
        root.listFiles().orEmpty().filter { it.isFile && it.extension == "json" }.forEach { file ->
            val owner = runCatching {
                Json.parseToJsonElement(file.readText()).jsonObject["toolId"]?.jsonPrimitive?.contentOrNull
            }.getOrNull() ?: return@forEach
            if (owner !in validToolIds && file.delete()) removed += 1
        }
        removed
    }

    private fun readEncryptedValues(toolId: String): Map<String, String> {
        val file = storeFile(toolId)
        if (!file.exists()) return emptyMap()
        return runCatching {
            val rootObject = Json.parseToJsonElement(file.readText()).jsonObject
            require(rootObject["toolId"]?.jsonPrimitive?.contentOrNull == toolId) {
                "Custom tool Secret store owner mismatch"
            }
            val values = rootObject["secrets"]?.jsonObject ?: JsonObject(emptyMap())
            values.mapValues { (_, value) -> value.jsonPrimitive.content }
        }.getOrElse {
            throw CustomToolSecretUnavailableException(
                "Saved Secrets for this tool could not be read. The encrypted data was left untouched."
            )
        }
    }

    private fun writeEncryptedValues(toolId: String, secrets: Map<String, String>) {
        check(root.isDirectory || root.mkdirs()) { "Cannot create the private Secret store" }
        val destination = storeFile(toolId)
        val temporary = File(root, "${destination.name}.${System.nanoTime()}.tmp")
        val payload = buildJsonObject {
            put("toolId", toolId)
            put("secrets", buildJsonObject {
                secrets.toSortedMap().forEach { (name, encrypted) -> put(name, encrypted) }
            })
        }.toString().toByteArray(Charsets.UTF_8)

        try {
            FileOutputStream(temporary).use { output ->
                output.write(payload)
                output.fd.sync()
            }
            try {
                Files.move(
                    temporary.toPath(),
                    destination.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING,
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temporary.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            temporary.delete()
        }
    }

    private fun deleteFile(toolId: String) {
        val file = storeFile(toolId)
        if (file.exists() && !file.delete()) {
            throw IllegalStateException("Could not remove this tool's private Secrets")
        }
    }

    private fun storeFile(toolId: String): File =
        File(root, toolId.sha256() + ".json")

    private fun validateReference(toolId: String, name: String) {
        validateToolId(toolId)
        require(name.matches(SECRET_NAME_PATTERN)) { "Secret name is invalid" }
    }

    private fun validateToolId(toolId: String) {
        require(toolId.isNotBlank() && toolId.length <= MAX_TOOL_ID_LENGTH) {
            "Custom tool ID is invalid"
        }
    }

    private fun String.sha256(): String = MessageDigest.getInstance("SHA-256")
        .digest(toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it.toInt() and 0xff) }

    companion object {
        private const val DIRECTORY_NAME = "custom_tool_secrets"
        private const val MAX_TOOL_ID_LENGTH = 128
        private val SECRET_NAME_PATTERN = Regex("[A-Z][A-Z0-9_]{0,63}")
    }
}

/** A local device secret could not be read; the user must re-enter it rather than use a fallback. */
class CustomToolSecretUnavailableException(message: String) : IllegalStateException(message)

private object CustomToolSecretCipher {
    private const val KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "lastnap_custom_tool_secrets_v1"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val VERSION_PREFIX = "v1:"
    private const val IV_SIZE = 12
    private const val TAG_BITS = 128

    fun encrypt(toolId: String, name: String, plaintext: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        cipher.updateAAD(associatedData(toolId, name))
        val encrypted = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        return VERSION_PREFIX + Base64.getEncoder().encodeToString(cipher.iv + encrypted)
    }

    fun decrypt(toolId: String, name: String, value: String): String {
        require(value.startsWith(VERSION_PREFIX)) { "Unsupported custom tool Secret version" }
        val bytes = Base64.getDecoder().decode(value.removePrefix(VERSION_PREFIX))
        require(bytes.size > IV_SIZE + TAG_BITS / 8) { "Custom tool Secret is incomplete" }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            secretKey(),
            GCMParameterSpec(TAG_BITS, bytes, 0, IV_SIZE),
        )
        cipher.updateAAD(associatedData(toolId, name))
        return cipher.doFinal(bytes, IV_SIZE, bytes.size - IV_SIZE).toString(Charsets.UTF_8)
    }

    @Synchronized
    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE).apply {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build()
            )
        }.generateKey()
    }

    private fun associatedData(toolId: String, name: String): ByteArray =
        "LastNap/custom-tool-secret/v1\u0000$toolId\u0000$name".toByteArray(Charsets.UTF_8)
}
