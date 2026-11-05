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
    const val MAX_VALUE_DECLARATIONS = 20
    const val MAX_VALUE_NAME_LENGTH = 64
    const val MAX_VALUE_DESCRIPTION_LENGTH = 512
    const val MAX_SECRET_VALUE_LENGTH = 8 * 1024
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
    TOO_MANY_SECRETS,
    TOO_MANY_VARIABLES,
    INVALID_SECRET_DECLARATION,
    INVALID_VARIABLE_DECLARATION,
    DUPLICATE_VALUE_NAME,
    ENABLED_WITHOUT_CURRENT_TEST,
}

/**
 * A name-only declaration. Secret/Variable values are stored separately from tool definitions.
 * Names use an uppercase environment-variable convention so they are easy to reference safely.
 */
@Serializable
data class CustomToolValueDeclaration(
    val name: String,
    val description: String = "",
    val required: Boolean = true,
)

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
    val secrets: List<CustomToolValueDeclaration> = emptyList(),
    val variables: List<CustomToolValueDeclaration> = emptyList(),
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

    if (secrets.size > CustomExtensionLimits.MAX_VALUE_DECLARATIONS) {
        add(CustomExtensionValidationError.TOO_MANY_SECRETS)
    }
    if (variables.size > CustomExtensionLimits.MAX_VALUE_DECLARATIONS) {
        add(CustomExtensionValidationError.TOO_MANY_VARIABLES)
    }
    if (secrets.any { !it.isValidToolValueDeclaration() }) {
        add(CustomExtensionValidationError.INVALID_SECRET_DECLARATION)
    }
    if (variables.any { !it.isValidToolValueDeclaration() }) {
        add(CustomExtensionValidationError.INVALID_VARIABLE_DECLARATION)
    }
    val valueNames = secrets.map { it.name } + variables.map { it.name }
    if (valueNames.distinct().size != valueNames.size) {
        add(CustomExtensionValidationError.DUPLICATE_VALUE_NAME)
    }

    if (enabled && (implementation.isBlank() || testedDefinitionHash != testFingerprint())) {
        add(CustomExtensionValidationError.ENABLED_WITHOUT_CURRENT_TEST)
    }
}

private fun CustomToolValueDeclaration.isValidToolValueDeclaration(): Boolean =
    name.length <= CustomExtensionLimits.MAX_VALUE_NAME_LENGTH &&
        name.matches(Regex("[A-Z][A-Z0-9_]{0,63}")) &&
        description.length <= CustomExtensionLimits.MAX_VALUE_DESCRIPTION_LENGTH

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
 * schemes, ports, paths, credentials, and query strings are rejected. Private IPv4 literals
 * are accepted only when the separate local-network capability is enabled.
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
    if (wildcard && labels.size < 2) return null
    if (labels.any { label ->
            label.isEmpty() || label.length > 63 ||
                !label.first().isLetterOrDigit() || !label.last().isLetterOrDigit() ||
                label.any { !it.isLetterOrDigit() && it != '-' }
        }
    ) return null

    val looksLikeIpv4 = ascii.matches(Regex("""\d{1,3}(\.\d{1,3}){3}"""))
    if (looksLikeIpv4) {
        val octets = ascii.split('.').map { it.toIntOrNull() ?: return null }
        if (octets.any { it !in 0..255 } || wildcard) return null
        val first = octets[0]
        val second = octets[1]
        val isPrivateOrLoopback = first == 10 || first == 127 ||
            (first == 172 && second in 16..31) ||
            (first == 192 && second == 168)
        return ascii.takeIf { allowLocalNetwork && isPrivateOrLoopback }
    }

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
            val matchedPrefix = normalizedHost.removeSuffix(suffix).removeSuffix(".")
            matchedPrefix.isNotEmpty() && '.' !in matchedPrefix && normalizedHost.endsWith(suffix)
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
    // Preserve the exact pre-capability fingerprint for legacy tools. Their existing
    // successful-test hashes remain valid because all new fields default to disabled/empty.
    val legacyDefinition = "${inputSchema.length}:$inputSchema${implementation.length}:$implementation"
    if (!apiAccessEnabled && !allowLocalNetwork && secrets.isEmpty() && variables.isEmpty()) {
        return legacyDefinition.extensionSha256()
    }

    val sortedHosts = allowedApiHosts
        .mapNotNull { normalizeCustomApiHostPattern(it, allowLocalNetwork) }
        .distinct()
        .sorted()
    val hostDefinition = sortedHosts.joinToString("") { "${it.length}:$it" }
    fun declarationFingerprint(values: List<CustomToolValueDeclaration>): String =
        values.sortedBy { it.name }.joinToString("") { value ->
            "${value.name.length}:${value.name}${value.description.length}:${value.description}${value.required};"
        }
    val definition = buildString {
        append(legacyDefinition)
        append("|api=").append(apiAccessEnabled)
        append("|local=").append(allowLocalNetwork)
        append("|hosts=").append(hostDefinition)
        append("|secrets=").append(declarationFingerprint(secrets))
        append("|variables=").append(declarationFingerprint(variables))
    }
    return definition.extensionSha256()
}

/** SHA-256 for the exact schema/implementation/capability definition being tested. */
fun String.extensionSha256(): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(toByteArray(Charsets.UTF_8))
    return digest.joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
}
