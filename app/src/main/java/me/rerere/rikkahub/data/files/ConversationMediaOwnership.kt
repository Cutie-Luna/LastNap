package me.rerere.rikkahub.data.files

import kotlinx.coroutines.CancellationException

/**
 * Select only media references that no persisted message still owns.
 *
 * The check is intentionally global rather than scoped to the conversation being edited:
 * a fork, copied message, or another conversation may still own the same managed file.
 * If ownership cannot be checked, retain the file; a later reconciliation can retry.
 */
internal suspend fun <T> findUnreferencedChatFiles(
    files: List<T>,
    fileUrl: (T) -> String,
    hasFileReference: suspend (String) -> Boolean,
): List<T> = files.distinct().filter { file ->
    try {
        !hasFileReference(fileUrl(file))
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        false
    }
}
