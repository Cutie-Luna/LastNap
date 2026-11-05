package me.rerere.rikkahub.data.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.IDN
import java.util.Locale
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
    const val MAX_ALLOWED_API_HOSTS = 20
    const val MAX_API_HOST_PATTERN_LENGTH = 253
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
    API_HOSTS_REQUIRED,
    TOO_MANY_API_HOSTS,
    INVALID_API_HOST_PATTERN,
    LOCAL_NETWORK_WITHOUT_API_ACCESS,
    ENABLED_WITHOUT_CURRENT_TEST,
}

/**
 * User-authored custom-tool definition.
 *
 * The record is stored at the user level. Assistants opt in separately through
 * [Assistant.selectedCustomExtensionIds]. A new definition is a disabled draft.
 * Test input/output is transient Builder state and is deliberately not persisted here.
 *
 * Network access is a separate opt-in capability. Existing serialized definitions decode
 * with API access disabled and no allowed hosts.
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
    val apiAccessEnabled: Boolean = false,
    val allowedApiHosts: List<String> = emptyList(),
    val allowLocalNetwork: Boolean = false,
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

    if (!apiAccessEnabled && allowLocalNetwork) {
        add(CustomExtensionValidationError.LOCAL_NETWORK_WITHOUT_API_ACCESS)
    }
    if (apiAccessEnabled) {
        if (allowedApiHosts.isEmpty()) add(CustomExtensionValidationError.API_HOSTS_REQUIRED)
        if (allowedApiHosts.size > CustomExtensionLimits.MAX_ALLOWED_API_HOSTS) {
            add(CustomExtensionValidationError.TOO_MANY_API_HOSTS)
        }
        if (allowedApiHosts.any { normalizeCustomApiHostPattern(it, allowLocalNetwork) == null }) {
            add(CustomExtensionValidationError.INVALID_API_HOST_PATTERN)
        }
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
 * Validates and canonicalizes a host allowlist entry. Entries are hostnames only:
 * schemes, ports, paths, credentials, query strings, and IP literals are rejected.
 *
 * A wildcard is permitted only as the complete left-most label (for example
 * "*.api.example.com"). Local names such as localhost and .local require the separate
 * local-network capability.
 */
fun normalizeCustomApiHostPattern(
    value: String,
    allowLocalNetwork: Boolean,
): String? {
    val raw = value.trim().lowercase(Locale.ROOT)
    if (raw.isEmpty() || raw.length > CustomExtensionLimits.MAX_API_HOST_PATTERN_LENGTH) return null

    val wildcard = raw.startsWith("*.")
    val host = if (wildcard) raw.substring(2) else raw
    if (host.isEmpty() || '*' in host || host.endsWith('.') || ':' in host ||
        '/' in host || '@' in host || '?' in host || '#' in host || '%' in host
    ) return null

    val ascii = runCatching { IDN.toASCII(host, IDN.USE_STD3_ASCII_RULES).lowercase(Locale.ROOT) }
        .getOrNull() ?: return null
    if (ascii.length > CustomExtensionLimits.MAX_API_HOST_PATTERN_LENGTH) return null

    val labels = ascii.split('.')
    if (labels.any { label ->
            label.isEmpty() || label.length > 63 ||
                !label.first().isLetterOrDigit() || !label.last().isLetterOrDigit() ||
                label.any { !it.isLetterOrDigit() && it != '-' }
        }
    ) return null

    val isIpv4Literal = ascii.matches(Regex("""\d{1,3}(\.\d{1,3}){3}""")) &&
        ascii.split('.').all { it.toIntOrNull() in 0..255 }
    if (isIpv4Literal) return null

    val isLocalName = ascii == "localhost" || ascii.endsWith(".localhost") ||
        ascii.endsWith(".local") || !ascii.contains('.')
    if (isLocalName && !allowLocalNetwork) return null

    return if (wildcard) "*.$ascii" else ascii
}

/** True only when [host] matches one of the extension's validated allowlist entries. */
fun CustomExtension.allowsApiHost(host: String): Boolean {
    if (!apiAccessEnabled) return false
    val normalizedHost = runCatching {
        IDN.toASCII(host.trim().trimEnd('.'), IDN.USE_STD3_ASCII_RULES).lowercase(Locale.ROOT)
    }.getOrNull() ?: return false
    if (normalizedHost.isBlank()) return false

    return allowedApiHosts.any { entry ->
        val pattern = normalizeCustomApiHostPattern(entry, allowLocalNetwork) ?: return@any false
        if (pattern.startsWith("*.")) {
            val suffix = pattern.removePrefix("*")
            normalizedHost.length > suffix.length && normalizedHost.endsWith(suffix)
        } else {
            normalizedHost == pattern
        }
    }
}

/**
 * Stable fingerprint used to ensure the enabled schema, implementation, and network policy
 * match the definition that passed the real Builder test.
 *
 * Secret values and ordinary Variable values are intentionally never part of this fingerprint.
 */
fun CustomExtension.testFingerprint(): String {
    val sortedHosts = allowedApiHosts
        .mapNotNull { normalizeCustomApiHostPattern(it, allowLocalNetwork) }
        .distinct()
        .sorted()
    val hostDefinition = sortedHosts.joinToString("") { "${it.length}:$it" }
    val definition = buildString {
        append("${inputSchema.length}:$inputSchema")
        append("${implementation.length}:$implementation")
        append("|api=").append(apiAccessEnabled)
        append("|local=").append(allowLocalNetwork)
        append("|hosts=").append(hostDefinition)
    }
    return definition.extensionSha256()
}

/** SHA-256 for the exact schema/implementation/capability definition being tested. */
fun String.extensionSha256(): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(toByteArray(Charsets.UTF_8))
    return digest.joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
}
