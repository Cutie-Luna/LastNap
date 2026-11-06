package me.rerere.rikkahub.data.files

import android.net.Uri
import kotlinx.coroutines.CancellationException

/**
 * Select only media URLs that no persisted message still references.
 *
 * The check is intentionally global rather than scoped to the conversation being edited:
 * a fork, copied message, or another conversation may still own the same managed file.
 * If ownership cannot be checked, retain the file; a later reconciliation can retry.
 */
internal suspend fun findUnreferencedChatFiles(
    files: List<Uri>,
    hasFileReference: suspend (String) -> Boolean,
): List<Uri> = files.distinct().filter { uri ->
    try {
        !hasFileReference(uri.toString())
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        false
    }
}
