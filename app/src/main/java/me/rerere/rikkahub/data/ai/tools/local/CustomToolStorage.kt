package me.rerere.rikkahub.data.ai.tools.local

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

/** Narrow storage surface exposed to the custom-tool runtime. */
interface CustomToolStorageProvider {
    fun readBytes(toolId: String, name: String): ByteArray?
    fun writeBytes(toolId: String, name: String, bytes: ByteArray)
    fun delete(toolId: String, name: String): Boolean
    fun list(toolId: String): List<String>
    fun deleteToolStorage(toolId: String)
}

/** Ephemeral storage used by Builder tests so tests never mutate a tool's saved data. */
class InMemoryCustomToolStorage : CustomToolStorageProvider {
    private val values = mutableMapOf<Pair<String, String>, ByteArray>()

    @Synchronized
    override fun readBytes(toolId: String, name: String): ByteArray? {
        validateName(name)
        return values[toolId to name]?.copyOf()
    }

    @Synchronized
    override fun writeBytes(toolId: String, name: String, bytes: ByteArray) {
        validateName(name)
        require(bytes.size <= MAX_FILE_BYTES) { "Stored item exceeds the 1 MiB per-file limit" }
        val key = toolId to name
        val oldSize = values[key]?.size ?: 0
        val total = values.filterKeys { it.first == toolId }.values.sumOf { it.size } - oldSize + bytes.size
        require(total <= MAX_TOTAL_BYTES) { "Tool storage exceeds the 10 MiB total limit" }
        if (key !in values) {
            require(values.keys.count { it.first == toolId } < MAX_FILES_PER_TOOL) {
                "Tool storage is limited to 100 files"
            }
        }
        values[key] = bytes.copyOf()
    }

    @Synchronized
    override fun delete(toolId: String, name: String): Boolean {
        validateName(name)
        return values.remove(toolId to name) != null
    }

    @Synchronized
    override fun list(toolId: String): List<String> =
        values.keys.filter { it.first == toolId }.map { it.second }.sorted()

    @Synchronized
    override fun deleteToolStorage(toolId: String) {
        values.keys.filter { it.first == toolId }.forEach(values::remove)
    }

    private fun validateName(name: String) {
        require(name.length <= MAX_NAME_LENGTH && SAFE_NAME.matches(name)) {
            "Storage item name is invalid"
        }
    }

    companion object {
        private const val MAX_NAME_LENGTH = 128
        private const val MAX_FILE_BYTES = 1024 * 1024
        private const val MAX_TOTAL_BYTES = 10 * 1024 * 1024
        private const val MAX_FILES_PER_TOOL = 100
        private val SAFE_NAME = Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,127}")
    }
}

/**
 * Private, no-backup storage owned by one immutable custom-tool ID.
 *
 * The API accepts one safe filename, never a path. It is intentionally separate from managed
 * conversation media: chat outputs survive tool deletion, while this private working data does not.
 */
class CustomToolStorage(private val root: File) : CustomToolStorageProvider {

    override fun readBytes(toolId: String, name: String): ByteArray? = synchronized(lock) {
        val file = fileFor(toolId, name) ?: return@synchronized null
        if (!file.exists()) return@synchronized null
        requireOwnedDirectory(toolId, file.parentFile ?: error("Storage path is invalid"))
        require(file.isFile && !Files.isSymbolicLink(file.toPath())) { "Stored item is not a regular file" }
        require(file.length() <= MAX_FILE_BYTES) { "Stored item exceeds the size limit" }
        file.readBytes().also {
            require(it.size <= MAX_FILE_BYTES) { "Stored item exceeds the size limit" }
        }
    }

    override fun writeBytes(toolId: String, name: String, bytes: ByteArray) = synchronized(lock) {
        validateToolId(toolId)
        validateName(name)
        require(bytes.size <= MAX_FILE_BYTES) { "Stored item exceeds the 1 MiB per-file limit" }

        val directory = toolDirectory(toolId)
        if (directory.exists()) {
            requireOwnedDirectory(toolId, directory)
        }
        val existingFiles = if (directory.isDirectory) listOwnedFiles(toolId, directory) else emptyList()
        val oldFile = File(directory, name)
        val oldSize = existingFiles.firstOrNull { it.name == name }?.length() ?: 0L
        if (oldFile.exists() && existingFiles.none { it.name == name }) {
            throw IllegalStateException("Storage contains an unrecognized item")
        }
        if (oldFile.exists() && oldFile.isDirectory) {
            throw IllegalArgumentException("Storage item name is already a directory")
        }
        if (!oldFile.exists()) {
            require(existingFiles.size < MAX_FILES_PER_TOOL) { "Tool storage is limited to 100 files" }
        }
        val totalSize = existingFiles.sumOf { it.length() } - oldSize + bytes.size
        require(totalSize <= MAX_TOTAL_BYTES) { "Tool storage exceeds the 10 MiB total limit" }

        if (!directory.exists()) {
            check(directory.mkdirs()) { "Could not create private tool storage" }
            writeOwnerMarker(toolId, directory)
        }
        requireOwnedDirectory(toolId, directory)
        val destination = File(directory, name)
        val temporary = File(directory, ".$name.${System.nanoTime()}.tmp")
        try {
            FileOutputStream(temporary).use { output ->
                output.write(bytes)
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

    override fun delete(toolId: String, name: String): Boolean = synchronized(lock) {
        val file = fileFor(toolId, name) ?: return@synchronized false
        if (!file.exists()) return@synchronized false
        requireOwnedDirectory(toolId, file.parentFile ?: error("Storage path is invalid"))
        require(file.isFile && !Files.isSymbolicLink(file.toPath())) { "Stored item is not a regular file" }
        check(file.delete()) { "Could not delete the stored item" }
    }

    override fun list(toolId: String): List<String> = synchronized(lock) {
        validateToolId(toolId)
        val directory = toolDirectory(toolId)
        if (!directory.exists()) return@synchronized emptyList()
        requireOwnedDirectory(toolId, directory)
        listOwnedFiles(toolId, directory).map { it.name }.filter { SAFE_NAME.matches(it) }.sorted()
    }

    override fun deleteToolStorage(toolId: String) = synchronized(lock) {
        validateToolId(toolId)
        val directory = toolDirectory(toolId)
        if (!directory.exists()) return@synchronized
        requireOwnedDirectory(toolId, directory)
        directory.listFiles().orEmpty().forEach { file ->
            require(file.canonicalFile.parentFile == directory.canonicalFile &&
                !Files.isSymbolicLink(file.toPath()) && file.isFile
            ) { "Tool storage contains an unsafe item" }
            check(file.delete()) { "Could not remove private tool storage item" }
        }
        check(directory.delete()) { "Could not remove private tool storage directory" }
    }

    /**
     * Idempotent recovery for a crash between tool-definition deletion and private-data cleanup.
     * Unreadable ownership markers are retained rather than guessed.
     */
    fun reconcile(validToolIds: Set<String>): Int = synchronized(lock) {
        if (!root.isDirectory) return@synchronized 0
        var removed = 0
        root.listFiles().orEmpty().filter { it.isDirectory && !Files.isSymbolicLink(it.toPath()) }.forEach { directory ->
            val owner = runCatching { File(directory, OWNER_FILE).readText() }.getOrNull()
            if (owner != null && owner !in validToolIds) {
                val toolId = owner
                runCatching { deleteToolStorage(toolId) }.onSuccess { removed += 1 }
            } else if (owner == null && directory.listFiles().isNullOrEmpty()) {
                if (directory.delete()) removed += 1
            }
        }
        removed
    }

    private fun fileFor(toolId: String, name: String): File? {
        validateToolId(toolId)
        validateName(name)
        val directory = toolDirectory(toolId)
        if (!directory.exists()) return null
        requireOwnedDirectory(toolId, directory)
        val file = File(directory, name)
        require(file.canonicalFile.parentFile == directory.canonicalFile) { "Storage path escaped its tool directory" }
        return file
    }

    private fun listOwnedFiles(toolId: String, directory: File): List<File> {
        requireOwnedDirectory(toolId, directory)
        return directory.listFiles().orEmpty().filter { file ->
            file.name != OWNER_FILE && file.isFile && !Files.isSymbolicLink(file.toPath()) &&
                file.canonicalFile.parentFile == directory.canonicalFile
        }
    }

    private fun requireOwnedDirectory(toolId: String, directory: File) {
        require(directory.canonicalFile.parentFile == root.canonicalFile &&
            directory.name == toolId.sha256()
        ) {
            "Tool storage path escaped the private storage root"
        }
        require(!Files.isSymbolicLink(directory.toPath())) { "Tool storage directory cannot be a symbolic link" }
        val ownerFile = File(directory, OWNER_FILE)
        require(ownerFile.isFile && !Files.isSymbolicLink(ownerFile.toPath())) {
            "Tool storage ownership metadata is missing"
        }
        require(ownerFile.readText() == toolId) { "Tool storage belongs to a different tool" }
    }

    private fun writeOwnerMarker(toolId: String, directory: File) {
        val marker = File(directory, OWNER_FILE)
        FileOutputStream(marker).use { output ->
            output.write(toolId.toByteArray(Charsets.UTF_8))
            output.fd.sync()
        }
    }

    private fun toolDirectory(toolId: String): File =
        File(root, toolId.sha256())

    private fun validateToolId(toolId: String) {
        require(toolId.isNotBlank() && toolId.length <= MAX_TOOL_ID_LENGTH) { "Custom tool ID is invalid" }
    }

    private fun validateName(name: String) {
        require(name.length <= MAX_NAME_LENGTH && SAFE_NAME.matches(name) && name != "." && name != "..") {
            "Storage item name is invalid"
        }
    }

    private fun String.sha256(): String =
        MessageDigest.getInstance("SHA-256").digest(toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }

    companion object {
        private const val OWNER_FILE = ".owner"
        private const val MAX_TOOL_ID_LENGTH = 128
        private const val MAX_NAME_LENGTH = 128
        private const val MAX_FILE_BYTES = 1024 * 1024
        private const val MAX_TOTAL_BYTES = 10 * 1024 * 1024
        private const val MAX_FILES_PER_TOOL = 100
        private val SAFE_NAME = Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,127}")

        fun fromContext(context: Context): CustomToolStorage =
            CustomToolStorage(File(context.noBackupFilesDir, "custom_tool_storage"))
    }
}
