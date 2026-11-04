package me.rerere.rikkahub.data.model

import kotlinx.serialization.Serializable
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
    val implementation: String = "",
    val permission: ExtensionPermission = ExtensionPermission.ASK_EVERY_TIME,
    val enabled: Boolean = false,
)

@Serializable
enum class ExtensionPermission {
    ALWAYS_ALLOW,
    ASK_EVERY_TIME,
    THIS_SESSION,
    DISABLED,
}
