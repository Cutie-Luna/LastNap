package me.rerere.rikkahub.data.files

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.core.net.toFile
import androidx.core.net.toUri
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.common.android.Logging
import me.rerere.rikkahub.AppScope
import me.rerere.rikkahub.data.db.entity.ManagedFileEntity
import me.rerere.rikkahub.data.repository.FilesRepository
import me.rerere.rikkahub.utils.exportImage
import me.rerere.rikkahub.utils.exportImageFile
import me.rerere.rikkahub.utils.getActivity
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

class FilesManager(
    private val context: Context,
    private val repository: FilesRepository,
    private val appScope: AppScope,
) : ConversationMediaStore {
    companion object {
        private const val TAG = "FilesManager"
        private const val MAX_CONVERSATION_MEDIA_BYTES = 8 * 1024 * 1024
        private val CONVERSATION_ID_PATTERN = Regex("[0-9a-fA-F-]{36}")
        private val CONVERSATION_IMAGE_MIMES = setOf("image/png", "image/jpeg", "image/webp", "image/gif")
    }

    suspend fun saveManagedFromUri(
        folder: String,
        uri: Uri,
        displayName: String? = null,
        mimeType: String? = null,
    ): ManagedFileEntity = withContext(Dispatchers.IO) {
        val resolvedName = displayName ?: getFileNameFromUri(uri) ?: "file"
        val resolvedMime = mimeType ?: getFileMimeType(uri) ?: "application/octet-stream"
        val target = createTargetFile(folder, resolvedName, resolvedMime)
        context.contentResolver.openInputStream(uri)?.use { input ->
            target.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        createManagedFileEntity(
            folder = folder,
            file = target,
            displayName = resolvedName,
            mimeType = resolvedMime,
        )
    }

    suspend fun saveManagedFromBytes(
        folder: String,
        bytes: ByteArray,
        displayName: String,
        mimeType: String = "application/octet-stream",
    ): ManagedFileEntity = withContext(Dispatchers.IO) {
        val target = createTargetFile(folder, displayName, mimeType)
        target.writeBytes(bytes)
        createManagedFileEntity(
            folder = folder,
            file = target,
            displayName = displayName,
            mimeType = mimeType,
        )
    }

    /**
     * Persist an image produced by a tool as conversation-owned media, never in tool storage.
     * The returned URI is safe to attach to a message and is included in chat backups.
     */
    override suspend fun saveConversationMedia(
        conversationId: String,
        bytes: ByteArray,
        displayName: String,
        mimeType: String,
    ): String = withConversationMediaCancellationCleanup(
        persist = { onCommitted ->
            withContext(NonCancellable + Dispatchers.IO) {
                require(conversationId.matches(CONVERSATION_ID_PATTERN)) { "Conversation ID is invalid" }
        require(bytes.isNotEmpty() && bytes.size <= MAX_CONVERSATION_MEDIA_BYTES) {
            "Conversation image exceeds the 8 MiB size limit"
        }
        val normalizedMime = mimeType.substringBefore(';').trim().lowercase()
        require(normalizedMime in CONVERSATION_IMAGE_MIMES) { "Unsupported conversation image type" }

        val filesRoot = context.filesDir.canonicalFile
        val root = File(context.filesDir, FileFolders.CONVERSATION_MEDIA)
        require(!Files.isSymbolicLink(root.toPath())) { "Conversation media root cannot be a symbolic link" }
        if (!root.exists()) check(root.mkdirs()) { "Could not create conversation media storage" }
        require(root.canonicalFile.parentFile == filesRoot) { "Conversation media root escaped app storage" }

        val directory = File(root, conversationId)
        require(!Files.isSymbolicLink(directory.toPath())) { "Conversation media directory cannot be a symbolic link" }
        if (!directory.exists()) check(directory.mkdir()) { "Could not create conversation media directory" }
        require(directory.canonicalFile.parentFile == root.canonicalFile) {
            "Conversation media directory escaped its owner"
        }

        val file = File(directory, FileUtils.buildUuidFileName(displayName, normalizedMime))
        val temporary = File(directory, ".${file.name}.${System.nanoTime()}.tmp")
        try {
            FileOutputStream(temporary).use { output ->
                output.write(bytes)
                output.fd.sync()
            }
            try {
                Files.move(
                    temporary.toPath(),
                    file.toPath(),
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                )
            } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
                Files.move(temporary.toPath(), file.toPath())
            }
            val relativePath = FileUtils.getRelativePathInFilesDir(context.filesDir, file)
                ?: error("Conversation media path is outside app storage")
            val now = System.currentTimeMillis()
            repository.insert(
                ManagedFileEntity(
                    folder = FileFolders.CONVERSATION_MEDIA,
                    relativePath = relativePath,
                    displayName = displayName,
                    mimeType = normalizedMime,
                    sizeBytes = file.length(),
                    createdAt = now,
                    updatedAt = now,
                )
            )
            val uri = file.toUri().toString()
            onCommitted(uri)
            uri
            } catch (error: Exception) {
                file.delete()
                throw error
            } finally {
                temporary.delete()
            }
        }
    ) { uri ->
        discardConversationMedia(uri)
    }

    /**
     * Delete one managed conversation-media file only after its owning messages are gone.
     * A failed disk delete keeps its database row so startup reconciliation can retry.
     */
    suspend fun deleteConversationMedia(entity: ManagedFileEntity): Boolean = withContext(Dispatchers.IO) {
        require(entity.folder == FileFolders.CONVERSATION_MEDIA) {
            "Only conversation-owned media can be deleted by this operation"
        }
        val root = File(context.filesDir, FileFolders.CONVERSATION_MEDIA)
        require(!Files.isSymbolicLink(root.toPath()) && root.canonicalFile.parentFile == context.filesDir.canonicalFile) {
            "Conversation media root is invalid"
        }
        val file = getFile(entity)
        val parent = file.canonicalFile.parentFile
        require(parent != null && parent.parentFile == root.canonicalFile &&
            parent.name.matches(CONVERSATION_ID_PATTERN) &&
            !Files.isSymbolicLink(parent.toPath()) && !Files.isSymbolicLink(file.toPath())
        ) { "Conversation media path is invalid" }
        if (file.exists()) {
            require(file.isFile) { "Conversation media is not a regular file" }
            if (!file.delete()) return@withContext false
        }
        repository.deleteByPath(entity.relativePath)
        parent.delete()
        true
    }

    /**
     * Remove only conversation media that has no persisted message owner. Unknown references
     * and failed database checks are retained; this also repairs crashes before message commit.
     */
    suspend fun reconcileConversationMedia(
        hasFileReference: suspend (String) -> Boolean,
    ): Int = withContext(Dispatchers.IO) {
        val entities = list(FileFolders.CONVERSATION_MEDIA)
        val trackedPaths = entities.mapTo(mutableSetOf()) { it.relativePath }
        var removed = 0
        entities.forEach { entity ->
            val referenced = try {
                hasFileReference(getFile(entity).toUri().toString())
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                true
            }
            if (!referenced) {
                val deleted = try {
                    deleteConversationMedia(entity)
                } catch (cancelled: kotlinx.coroutines.CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    false
                }
                if (deleted) removed += 1
            }
        }

        val root = File(context.filesDir, FileFolders.CONVERSATION_MEDIA)
        if (root.isDirectory && !Files.isSymbolicLink(root.toPath())) {
            root.listFiles().orEmpty()
                .filter { it.isDirectory && it.name.matches(CONVERSATION_ID_PATTERN) && !Files.isSymbolicLink(it.toPath()) }
                .forEach { directory ->
                    directory.listFiles().orEmpty()
                        .filter { it.isFile && !Files.isSymbolicLink(it.toPath()) }
                        .forEach fileLoop@ { file ->
                        val relativePath = FileUtils.getRelativePathInFilesDir(context.filesDir, file) ?: return@fileLoop
                        if (relativePath in trackedPaths) return@fileLoop
                        val referenced = try {
                            hasFileReference(file.toUri().toString())
                        } catch (cancelled: kotlinx.coroutines.CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            true
                        }
                        if (!referenced && file.delete()) removed += 1
                    }
                    directory.delete()
                }
        }
        removed
    }

    suspend fun saveManagedText(
        folder: String,
        text: String,
        displayName: String = "pasted_text.txt",
        mimeType: String = "text/plain",
    ): ManagedFileEntity = withContext(Dispatchers.IO) {
        val target = createTargetFile(folder, displayName, mimeType)
        target.writeText(text)
        createManagedFileEntity(
            folder = folder,
            file = target,
            displayName = displayName,
            mimeType = mimeType,
        )
    }

    fun observe(folder: String = FileFolders.UPLOAD): Flow<List<ManagedFileEntity>> =
        repository.listByFolder(folder)

    suspend fun list(folder: String = FileFolders.UPLOAD): List<ManagedFileEntity> =
        repository.listByFolder(folder).first()

    suspend fun get(id: Long): ManagedFileEntity? = repository.getById(id)

    suspend fun getByRelativePath(relativePath: String): ManagedFileEntity? = repository.getByPath(relativePath)

    fun getFile(entity: ManagedFileEntity): File =
        File(context.filesDir, entity.relativePath)

    fun createChatFilesByContents(uris: List<Uri>): List<Uri> {
        val newUris = mutableListOf<Uri>()
        val dir = context.filesDir.resolve(FileFolders.UPLOAD)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        uris.forEach { uri ->
            runCatching {
                val sourceName = getFileNameFromUri(uri) ?: uri.lastPathSegment ?: "file"
                val sourceMime = getFileMimeType(uri)
                val fileName = buildUuidFileName(displayName = sourceName, mimeType = sourceMime)
                val file = dir.resolve(fileName)
                if (!file.exists()) {
                    file.createNewFile()
                }
                val inputStream = context.contentResolver.openInputStream(uri)
                    ?: error("Failed to open input stream for $uri")
                inputStream.use { input ->
                    file.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                val guessedMime = sourceMime ?: guessMimeType(file, sourceName)
                trackManagedFile(
                    folder = FileFolders.UPLOAD,
                    file = file,
                    displayName = sourceName,
                    mimeType = guessedMime
                )
                newUris.add(file.toUri())
            }.onFailure {
                it.printStackTrace()
                Log.e(TAG, "createChatFilesByContents: Failed to save file from $uri", it)
                Logging.log(
                    TAG,
                    "createChatFilesByContents: Failed to save file from $uri ${it.message} | ${it.stackTraceToString()}"
                )
            }
        }
        return newUris
    }

    fun createChatFilesByByteArrays(
        byteArrays: List<ByteArray>,
        displayName: String = "image.png",
        mimeType: String = "image/png",
    ): List<Uri> {
        val newUris = mutableListOf<Uri>()
        val dir = context.filesDir.resolve(FileFolders.UPLOAD)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        byteArrays.forEach { byteArray ->
            val fileName = buildUuidFileName(displayName = displayName, mimeType = mimeType)
            val file = dir.resolve(fileName)
            if (!file.exists()) {
                file.createNewFile()
            }
            val newUri = file.toUri()
            file.outputStream().use { outputStream ->
                outputStream.write(byteArray)
            }
            trackManagedFile(
                folder = FileFolders.UPLOAD,
                file = file,
                displayName = displayName,
                mimeType = mimeType
            )
            newUris.add(newUri)
        }
        return newUris
    }

    @OptIn(ExperimentalEncodingApi::class)
    suspend fun convertBase64ImagePartToLocalFile(message: UIMessage): UIMessage =
        withContext(Dispatchers.IO) {
            message.copy(
                parts = message.parts.map { part ->
                    when (part) {
                        is UIMessagePart.Image -> {
                            if (part.url.startsWith("data:image")) {
                                val sourceByteArray = Base64.decode(part.url.substringAfter("base64,").toByteArray())
                                val bitmap = BitmapFactory.decodeByteArray(sourceByteArray, 0, sourceByteArray.size)
                                val byteArray = FileUtils.compressBitmapToPng(bitmap)
                                val urls = createChatFilesByByteArrays(listOf(byteArray))
                                Log.i(
                                    TAG,
                                    "convertBase64ImagePartToLocalFile: convert base64 img to ${urls.joinToString(", ")}"
                                )
                                part.copy(
                                    url = urls.first().toString(),
                                )
                            } else {
                                part
                            }
                        }

                        else -> part
                    }
                }
            )
        }

    override fun discardConversationMedia(uri: String) {
        deleteChatFiles(listOf(Uri.parse(uri)))
    }

    fun deleteChatFiles(uris: List<Uri>) {
        // A conversation may own uploaded attachments or generated media. Never delete an
        // arbitrary file:// URL from a workspace, skill, or unrelated app-owned folder.
        val relativePaths = uris.mapNotNull { uri ->
            if (uri.scheme != "file") return@mapNotNull null
            val file = runCatching { uri.toFile() }.getOrNull() ?: return@mapNotNull null
            val path = getRelativePathInFilesDir(file) ?: return@mapNotNull null
            if (Files.isSymbolicLink(file.toPath())) return@mapNotNull null

            val segments = path.split('/')
            val isUpload = segments.size == 2 && segments[0] == FileFolders.UPLOAD
            val isConversationMedia = segments.size == 3 &&
                segments[0] == FileFolders.CONVERSATION_MEDIA &&
                segments[1].matches(CONVERSATION_ID_PATTERN)
            if (!isUpload && !isConversationMedia) return@mapNotNull null

            if (isConversationMedia) {
                val mediaRoot = File(context.filesDir, FileFolders.CONVERSATION_MEDIA)
                val ownerDirectory = file.canonicalFile.parentFile
                if (Files.isSymbolicLink(mediaRoot.toPath()) ||
                    ownerDirectory == null ||
                    Files.isSymbolicLink(ownerDirectory.toPath()) ||
                    ownerDirectory.parentFile != mediaRoot.canonicalFile
                ) {
                    return@mapNotNull null
                }
            }

            val isMissing = !file.exists()
            if (!isMissing && (!file.isFile || !file.delete())) return@mapNotNull null
            if (isConversationMedia) file.parentFile?.delete()
            path
        }.toSet()
        if (relativePaths.isEmpty()) return

        // Keep metadata when disk deletion fails; startup reconciliation can retry safely.
        appScope.launch(Dispatchers.IO) {
            relativePaths.forEach { path -> repository.deleteByPath(path) }
        }
    }

    suspend fun countChatFiles(): Pair<Int, Long> = withContext(Dispatchers.IO) {
        val dir = context.filesDir.resolve(FileFolders.UPLOAD)
        if (!dir.exists()) {
            return@withContext Pair(0, 0)
        }
        val files = dir.listFiles() ?: return@withContext Pair(0, 0)
        val count = files.size
        val size = files.sumOf { it.length() }
        Pair(count, size)
    }

    fun createChatTextFile(text: String): UIMessagePart.Document {
        val dir = context.filesDir.resolve(FileFolders.UPLOAD)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        val fileName = buildUuidFileName(displayName = "pasted_text.txt", mimeType = "text/plain")
        val file = dir.resolve(fileName)
        file.writeText(text)
        trackManagedFile(
            folder = FileFolders.UPLOAD,
            file = file,
            displayName = "pasted_text.txt",
            mimeType = "text/plain"
        )
        return UIMessagePart.Document(
            url = file.toUri().toString(),
            fileName = "pasted_text.txt",
            mime = "text/plain"
        )
    }

    fun getImagesDir(): File {
        val dir = context.filesDir.resolve("images")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    @OptIn(ExperimentalEncodingApi::class)
    fun createImageFileFromBase64(base64Data: String, filePath: String): File {
        val data = if (base64Data.startsWith("data:image")) {
            base64Data.substringAfter("base64,")
        } else {
            base64Data
        }

        val byteArray = Base64.decode(data.toByteArray())
        val file = File(filePath)
        file.parentFile?.mkdirs()
        file.writeBytes(byteArray)
        return file
    }

    fun listImageFiles(): List<File> {
        val imagesDir = getImagesDir()
        return imagesDir.listFiles()
            ?.filter { it.isFile && it.extension.lowercase() in listOf("png", "jpg", "jpeg", "webp") }
            ?.toList()
            ?: emptyList()
    }

    @OptIn(ExperimentalEncodingApi::class)
    suspend fun saveMessageImage(activityContext: Context, image: String) = withContext(Dispatchers.IO) {
        val activity = requireNotNull(activityContext.getActivity()) { "Activity not found" }
        when {
            image.startsWith("data:image") -> {
                val byteArray = Base64.decode(image.substringAfter("base64,").toByteArray())
                val bitmap = BitmapFactory.decodeByteArray(byteArray, 0, byteArray.size)
                activityContext.exportImage(activity, bitmap)
            }

            image.startsWith("file:") -> {
                val file = image.toUri().toFile()
                activityContext.exportImageFile(activity, file)
            }

            image.startsWith("/") -> {
                activityContext.exportImageFile(activity, File(image))
            }

            image.startsWith("http") -> {
                runCatching {
                    val url = URL(image)
                    val connection = url.openConnection() as HttpURLConnection
                    connection.connect()

                    if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                        val bitmap = BitmapFactory.decodeStream(connection.inputStream)
                        activityContext.exportImage(activity, bitmap)
                    } else {
                        Log.e(
                            TAG,
                            "saveMessageImage: Failed to download image from $image, response code: ${connection.responseCode}"
                        )
                    }
                }.getOrNull()
            }

            else -> error("Invalid image format")
        }
    }

    suspend fun syncFolder(folder: String = FileFolders.UPLOAD): SyncResult = withContext(Dispatchers.IO) {
        val dir = File(context.filesDir, folder)
        val diskFiles = if (dir.exists()) {
            dir.listFiles()?.filter { it.isFile }
                ?: return@withContext SyncResult(inserted = 0, removed = 0)
        } else {
            emptyList()
        }

        // 磁盘 -> 数据库：补录尚未登记的文件
        var inserted = 0
        val diskRelativePaths = HashSet<String>()
        diskFiles.forEach { file ->
            val relativePath = "${folder}/${file.name}"
            diskRelativePaths.add(relativePath)
            val existing = repository.getByPath(relativePath)
            if (existing == null) {
                val now = System.currentTimeMillis()
                val displayName = file.name
                val mimeType = guessMimeType(file, displayName)
                repository.insert(
                    ManagedFileEntity(
                        folder = folder,
                        relativePath = relativePath,
                        displayName = displayName,
                        mimeType = mimeType,
                        sizeBytes = file.length(),
                        createdAt = file.lastModified().takeIf { it > 0 } ?: now,
                        updatedAt = now,
                    )
                )
                inserted += 1
            }
        }

        // 数据库 -> 磁盘：清理文件已不存在的孤儿记录
        var removed = 0
        repository.listByFolder(folder).first().forEach { entity ->
            if (entity.relativePath !in diskRelativePaths && !getFile(entity).isFile) {
                removed += repository.deleteByPath(entity.relativePath)
            }
        }

        SyncResult(inserted = inserted, removed = removed)
    }

    suspend fun delete(id: Long, deleteFromDisk: Boolean = true): Boolean = withContext(Dispatchers.IO) {
        val entity = repository.getById(id) ?: return@withContext false
        if (deleteFromDisk) {
            runCatching { getFile(entity).delete() }
        }
        repository.deleteById(id) > 0
    }

    suspend fun deleteAll(folder: String = FileFolders.UPLOAD): Boolean = withContext(Dispatchers.IO) {
        val dir = File(context.filesDir, folder)
        val entries = dir.listFiles()
        if (dir.exists() && entries == null) {
            return@withContext false
        }

        var allDeletedFromDisk = true
        entries.orEmpty().forEach { entry ->
            if (!runCatching { entry.deleteRecursively() }.getOrDefault(false)) {
                allDeletedFromDisk = false
            }
        }

        if (allDeletedFromDisk) {
            repository.deleteByFolder(folder)
            return@withContext true
        }

        repository.listByFolder(folder).first().forEach { entity ->
            if (!getFile(entity).exists()) {
                repository.deleteById(entity.id)
            }
        }
        false
    }

    suspend fun deleteOlderThan(
        folder: String = FileFolders.UPLOAD,
        cutoffMillis: Long,
    ): Boolean = withContext(Dispatchers.IO) {
        var allDeleted = true
        repository.listByFolder(folder).first()
            .filter { it.createdAt < cutoffMillis }
            .forEach { entity ->
                val file = getFile(entity)
                val deletedFromDisk = !file.exists() || runCatching {
                    file.deleteRecursively()
                }.getOrDefault(false)

                if (deletedFromDisk) {
                    if (repository.deleteById(entity.id) == 0) {
                        allDeleted = false
                    }
                } else {
                    allDeleted = false
                }
            }
        allDeleted
    }

    private fun createTargetFile(folder: String, displayName: String, mimeType: String?): File {
        val dir = File(context.filesDir, folder)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return File(dir, FileUtils.buildUuidFileName(displayName = displayName, mimeType = mimeType))
    }

    private fun buildUuidFileName(displayName: String?, mimeType: String?): String =
        FileUtils.buildUuidFileName(displayName, mimeType)

    private suspend fun createManagedFileEntity(
        folder: String,
        file: File,
        displayName: String,
        mimeType: String,
    ): ManagedFileEntity {
        val now = System.currentTimeMillis()
        return repository.insert(
            ManagedFileEntity(
                folder = folder,
                relativePath = buildRelativePath(folder, file),
                displayName = displayName,
                mimeType = mimeType,
                sizeBytes = file.length(),
                createdAt = now,
                updatedAt = now,
            )
        )
    }

    private fun trackManagedFile(folder: String, file: File, displayName: String, mimeType: String) {
        val relativePath = buildRelativePath(folder, file)
        appScope.launch(Dispatchers.IO) {
            runCatching {
                val existing = repository.getByPath(relativePath)
                if (existing != null) {
                    return@runCatching
                }
                val now = System.currentTimeMillis()
                repository.insert(
                    ManagedFileEntity(
                        folder = folder,
                        relativePath = relativePath,
                        displayName = displayName,
                        mimeType = mimeType,
                        sizeBytes = file.length(),
                        createdAt = now,
                        updatedAt = now,
                    )
                )
            }.onFailure {
                Log.e(TAG, "trackManagedFile: Failed to track file ${file.absolutePath}", it)
                Logging.log(
                    TAG,
                    "trackManagedFile: Failed to track file ${file.absolutePath} ${it.message} | ${it.stackTraceToString()}"
                )
            }
        }
    }

    private fun buildRelativePath(folder: String, file: File): String =
        FileUtils.buildRelativePath(folder, file)

    private fun getRelativePathInFilesDir(file: File): String? =
        FileUtils.getRelativePathInFilesDir(context.filesDir, file)

    fun getFileNameFromUri(uri: Uri): String? =
        FileUtils.getFileNameFromUri(context, uri)

    fun getFileMimeType(uri: Uri): String? =
        FileUtils.getFileMimeType(context, uri)

    private fun guessMimeType(file: File, fileName: String): String =
        FileUtils.guessMimeType(file, fileName)
}

data class SyncResult(
    val inserted: Int,
    val removed: Int,
)

object FileFolders {
    const val UPLOAD = "upload"
    const val SKILLS = "skills"
    const val BUILTIN_SKILLS = "builtin_skills"
    const val FONTS = "fonts"
    const val TOOL_OUTPUTS = "tool_outputs"
    const val MEDIA_CREATION = "media_creation"
    const val CONVERSATION_MEDIA = "conversation_media"
}

suspend fun FilesManager.saveUploadFromUri(
    uri: Uri,
    displayName: String? = null,
    mimeType: String? = null,
): ManagedFileEntity = saveManagedFromUri(
    folder = FileFolders.UPLOAD,
    uri = uri,
    displayName = displayName,
    mimeType = mimeType,
)

suspend fun FilesManager.saveUploadFromBytes(
    bytes: ByteArray,
    displayName: String,
    mimeType: String = "application/octet-stream",
): ManagedFileEntity = saveManagedFromBytes(
    folder = FileFolders.UPLOAD,
    bytes = bytes,
    displayName = displayName,
    mimeType = mimeType,
)

suspend fun FilesManager.saveUploadText(
    text: String,
    displayName: String = "pasted_text.txt",
    mimeType: String = "text/plain",
): ManagedFileEntity = saveManagedText(
    folder = FileFolders.UPLOAD,
    text = text,
    displayName = displayName,
    mimeType = mimeType,
)
