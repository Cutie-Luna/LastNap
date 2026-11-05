package me.rerere.rikkahub.data.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.security.MessageDigest
import kotlin.uuid.Uuid

/**
 * Size limits shared by persistence validation and the later Builder/runtime stages.
 *
 * The input limit applies to one invocation and is kept here so every stage uses the same value.
 */
object CustomExtensionLimits {
    const val MAX_NAME_LENGTH = 80
    const val MAX_DESCRIPTION_LENGTH = 2_000
    const val MAX_SKILL_LENGTH = 16_000
    const val MAX_INPUT_SCHEMA_LENGTH = 16_000
    const val MAX_IMPLEMENTATION_LENGTH = 64_000
    const val MAX_INPUT_LENGTH = 64 * 1024
}

/** Validation failures for a saved custom-tool definition. */
enum class CustomExtensionValidationError {
    EMPTY_ID,
    EMPTY_NAME,
    NAME_TOO_LONG,
    DESCRIPTION_TOO_LONG,
    SKILL_TOO_LONG,
    INPUT_SCHEMA_TOO_LONG,
    INVALID_INPUT_SCHEMA,
    IMPLEMENTATION_TOO_LONG,
    ENABLED_WITHOUT_CURRENT_TEST,
}

/**
 * User-authored custom-tool definition.
 *
 * The record is stored at the user level. Assistants opt in separately through
 * [Assistant.selectedCustomExtensionIds]. A new definition is a disabled draft.
 * Test input/output is transient Builder state and is deliberately not persisted here.
 */
@Serializable
data class CustomExtension(
    val id: String = Uuid.random().toString(),
    val name: String,
    val description: String,
    val skill: String = "",
    val inputSchema: String = """{"type":"object","properties":{},"additionalProperties":true}""",
    val implementation: String = "",
    val needsApproval: Boolean = true,
    val enabled: Boolean = false,
    val testedDefinitionHash: String? = null,
)

/**
 * Returns structural validation failures without discarding an incomplete draft.
 *
 * Empty implementation is allowed while editing. It can never be considered tested;
 * enabling requires a matching successful-test fingerprint.
 */
fun CustomExtension.validationErrors(): Set<CustomExtensionValidationError> = buildSet {
    if (id.isBlank()) add(CustomExtensionValidationError.EMPTY_ID)
    if (name.isBlank()) add(CustomExtensionValidationError.EMPTY_NAME)
    if (name.length > CustomExtensionLimits.MAX_NAME_LENGTH) {
        add(CustomExtensionValidationError.NAME_TOO_LONG)
    }
    if (description.length > CustomExtensionLimits.MAX_DESCRIPTION_LENGTH) {
        add(CustomExtensionValidationError.DESCRIPTION_TOO_LONG)
    }
    if (skill.length > CustomExtensionLimits.MAX_SKILL_LENGTH) {
        add(CustomExtensionValidationError.SKILL_TOO_LONG)
    }
    if (inputSchema.length > CustomExtensionLimits.MAX_INPUT_SCHEMA_LENGTH) {
        add(CustomExtensionValidationError.INPUT_SCHEMA_TOO_LONG)
    } else if (!inputSchema.isObjectInputSchema()) {
        add(CustomExtensionValidationError.INVALID_INPUT_SCHEMA)
    }
    if (implementation.length > CustomExtensionLimits.MAX_IMPLEMENTATION_LENGTH) {
        add(CustomExtensionValidationError.IMPLEMENTATION_TOO_LONG)
    }
    if (enabled && (implementation.isBlank() || testedDefinitionHash != testFingerprint())) {
        add(CustomExtensionValidationError.ENABLED_WITHOUT_CURRENT_TEST)
    }
}

private fun String.isObjectInputSchema(): Boolean = runCatching {
    val schema = Json.parseToJsonElement(this).jsonObject
    if (schema["type"]?.jsonPrimitive?.contentOrNull != "object") return@runCatching false
    if (schema["properties"] != null && schema["properties"] !is JsonObject) return@runCatching false
    if (schema["required"] != null) {
        val required = schema["required"] as? JsonArray ?: return@runCatching false
        if (required.any { (it as? JsonPrimitive)?.isString != true }) return@runCatching false
    }
    true
}.getOrDefault(false)

/**
 * Stable fingerprint used to ensure the enabled schema and implementation match the tested definition.
 *
 * The Builder must invalidate this value whenever either implementation or schema changes.
 */
fun CustomExtension.testFingerprint(): String {
    // Length prefixes make the two-part input unambiguous even if either value contains separators.
    val definition = "${inputSchema.length}:$inputSchema${implementation.length}:$implementation"
    return definition.extensionSha256()
}

/** SHA-256 for the exact schema/implementation definition being tested. */
fun String.extensionSha256(): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(toByteArray(Charsets.UTF_8))
    return digest.joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
}
