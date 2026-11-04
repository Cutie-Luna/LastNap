package me.rerere.rikkahub.data.model

import kotlinx.serialization.Serializable
import java.security.MessageDigest
import kotlin.uuid.Uuid

/**
 * User-authored extension definition.
 *
 * The description explains what the extension does, the skill teaches the assistant
 * how to use it, and the implementation contains its executable definition.
 * New extensions are drafts and require approval before they can be enabled.
 */
@Serializable
data class CustomExtension(
    val id: String = Uuid.random().toString(),
    val name: String,
    val description: String,
    val skill: String = "",
    val inputSchema: String = """{"type":"object","properties":{},"additionalProperties":true}""",
    val implementation: String = "",
    val permission: ExtensionPermission = ExtensionPermission.ASK_EVERY_TIME,
    val enabled: Boolean = false,
    val testedImplementationHash: String? = null,
)

@Serializable
enum class ExtensionPermission {
    ALWAYS_ALLOW,
    ASK_EVERY_TIME,
    THIS_SESSION,
    DISABLED,
}

/**
 * Stable fingerprint used to ensure that enabled code is exactly the code
 * that was successfully tested in the extension workshop.
 */
fun String.extensionSha256(): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(toByteArray(Charsets.UTF_8))
    return digest.joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
}
