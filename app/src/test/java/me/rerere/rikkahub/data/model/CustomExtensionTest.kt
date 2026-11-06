package me.rerere.rikkahub.data.model

import kotlinx.serialization.decodeFromString
import me.rerere.rikkahub.utils.JsonInstant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomExtensionTest {

    @Test
    fun `new definitions are valid disabled drafts by default`() {
        val extension = CustomExtension(name = "Weather", description = "Returns a forecast")

        assertFalseEnabled(extension)
        assertEquals(true, extension.needsApproval)
        assertEquals(null, extension.testedDefinitionHash)
        assertEquals(emptySet<CustomExtensionValidationError>(), extension.validationErrors())
    }

    @Test
    fun `Secrets require the API access capability`() {
        val extension = CustomExtension(
            name = "API tool",
            description = "",
            secrets = listOf(CustomToolValueDeclaration("API_KEY")),
        )

        assertTrue(CustomExtensionValidationError.SECRETS_WITHOUT_API_ACCESS in extension.validationErrors())
    }

    @Test
    fun `legacy permission values migrate to approval required`() {
        val legacy = """{"id":"legacy","name":"Legacy","description":"","permission":"ALWAYS_ALLOW"}"""
        val decoded = JsonInstant.decodeFromString<CustomExtension>(legacy)

        assertTrue(decoded.needsApproval)
    }

    @Test
    fun `object input schema is required`() {
        val invalid = CustomExtension(
            name = "Lookup",
            description = "",
            inputSchema = """{"type":"array","items":{"type":"string"}}""",
        )

        assertEquals(
            setOf(CustomExtensionValidationError.INVALID_INPUT_SCHEMA),
            invalid.validationErrors(),
        )
    }

    @Test
    fun `malformed input schema is rejected`() {
        val invalid = CustomExtension(
            name = "Lookup",
            description = "",
            inputSchema = "{",
        )

        assertTrue(CustomExtensionValidationError.INVALID_INPUT_SCHEMA in invalid.validationErrors())
    }

    @Test
    fun `object schema properties and required shapes are validated`() {
        val invalidProperties = CustomExtension(
            name = "Lookup",
            description = "",
            inputSchema = """{"type":"object","properties":[] }""",
        )
        val invalidRequired = CustomExtension(
            name = "Lookup",
            description = "",
            inputSchema = """{"type":"object","properties":{},"required":"value"}""",
        )
        val nonStringRequired = CustomExtension(
            name = "Lookup",
            description = "",
            inputSchema = """{"type":"object","properties":{},"required":["value",1]}""",
        )

        assertTrue(CustomExtensionValidationError.INVALID_INPUT_SCHEMA in invalidProperties.validationErrors())
        assertTrue(CustomExtensionValidationError.INVALID_INPUT_SCHEMA in invalidRequired.validationErrors())
        assertTrue(CustomExtensionValidationError.INVALID_INPUT_SCHEMA in nonStringRequired.validationErrors())
    }

    @Test
    fun `all persisted text limits are enforced`() {
        val invalid = CustomExtension(
            id = "",
            name = "n".repeat(CustomExtensionLimits.MAX_NAME_LENGTH + 1),
            description = "d".repeat(CustomExtensionLimits.MAX_DESCRIPTION_LENGTH + 1),
            skill = "s".repeat(CustomExtensionLimits.MAX_SKILL_LENGTH + 1),
            inputSchema = " ".repeat(CustomExtensionLimits.MAX_INPUT_SCHEMA_LENGTH + 1),
            implementation = "c".repeat(CustomExtensionLimits.MAX_IMPLEMENTATION_LENGTH + 1),
        )

        val errors = invalid.validationErrors()
        assertTrue(CustomExtensionValidationError.EMPTY_ID in errors)
        assertTrue(CustomExtensionValidationError.NAME_TOO_LONG in errors)
        assertTrue(CustomExtensionValidationError.DESCRIPTION_TOO_LONG in errors)
        assertTrue(CustomExtensionValidationError.SKILL_TOO_LONG in errors)
        assertTrue(CustomExtensionValidationError.INPUT_SCHEMA_TOO_LONG in errors)
        assertTrue(CustomExtensionValidationError.IMPLEMENTATION_TOO_LONG in errors)
    }

    @Test
    fun `blank name is rejected but incomplete disabled implementation is allowed`() {
        val invalidName = CustomExtension(name = " ", description = "")
        val draft = CustomExtension(name = "Draft", description = "")

        assertEquals(
            setOf(CustomExtensionValidationError.EMPTY_NAME),
            invalidName.validationErrors(),
        )
        assertEquals(emptySet<CustomExtensionValidationError>(), draft.validationErrors())
    }

    @Test
    fun `enabled definition needs a matching schema and implementation test hash`() {
        val code = "input.value * 2"
        val untested = CustomExtension(
            name = "Double",
            description = "",
            implementation = code,
            enabled = true,
        )
        val tested = untested.copy(testedDefinitionHash = untested.testFingerprint())

        assertTrue(CustomExtensionValidationError.ENABLED_WITHOUT_CURRENT_TEST in untested.validationErrors())
        assertEquals(emptySet<CustomExtensionValidationError>(), tested.validationErrors())
        assertTrue(
            CustomExtensionValidationError.ENABLED_WITHOUT_CURRENT_TEST in
                tested.copy(implementation = "input.value + 2").validationErrors()
        )
        assertTrue(
            CustomExtensionValidationError.ENABLED_WITHOUT_CURRENT_TEST in
                tested.copy(inputSchema = """{"type":"object","properties":{"value":{"type":"string"}}}""")
                    .validationErrors()
        )
    }

    @Test
    fun `approval preference is independent from validation and enablement`() {
        val extension = CustomExtension(name = "Lookup", description = "", needsApproval = false)
        assertEquals(false, extension.needsApproval)
        assertEquals(emptySet<CustomExtensionValidationError>(), extension.validationErrors())
    }

    @Test
    fun `test fingerprint changes when schema or implementation changes`() {
        val original = CustomExtension(
            name = "Lookup",
            description = "",
            inputSchema = """{"type":"object","properties":{"value":{"type":"string"}}}""",
            implementation = "return input.value",
        )

        assertTrue(
            original.testFingerprint() != original.copy(implementation = "return input.value.trim()").testFingerprint()
        )
        assertTrue(
            original.testFingerprint() != original.copy(inputSchema = """{"type":"object","properties":{}}""").testFingerprint()
        )
    }

    @Test
    fun `legacy definitions keep their original test fingerprint`() {
        val extension = CustomExtension(
            name = "Legacy",
            description = "",
            inputSchema = """{"type":"object","properties":{"value":{"type":"string"}}}""",
            implementation = "return input.value",
        )
        val legacyDefinition =
            "${extension.inputSchema.length}:${extension.inputSchema}${extension.implementation.length}:${extension.implementation}"

        assertEquals(legacyDefinition.extensionSha256(), extension.testFingerprint())
    }

    @Test
    fun `API access is off by default and requires an explicit host allowlist`() {
        val legacy = CustomExtension(name = "Legacy", description = "")
        val missingHosts = CustomExtension(
            name = "API tool",
            description = "",
            apiAccessEnabled = true,
        )

        assertFalse(legacy.apiAccessEnabled)
        assertTrue(legacy.allowedApiHosts.isEmpty())
        assertTrue(missingHosts.validationErrors().contains(CustomExtensionValidationError.API_HOSTS_REQUIRED))
    }

    @Test
    fun `host allowlist accepts exact and bounded wildcard hostnames`() {
        val extension = CustomExtension(
            name = "API tool",
            description = "",
            apiAccessEnabled = true,
            allowedApiHosts = listOf("api.example.com", "*.images.example.com"),
        )

        assertEquals(emptySet<CustomExtensionValidationError>(), extension.validationErrors())
        assertTrue(extension.allowsApiHost("api.example.com"))
        assertTrue(extension.allowsApiHost("cdn.images.example.com"))
        assertFalse(extension.allowsApiHost("a.b.images.example.com"))
        assertFalse(extension.allowsApiHost("example.com"))
        assertFalse(extension.allowsApiHost("images.example.com"))
        assertFalse(extension.allowsApiHost("notexample.com"))
        assertFalse(extension.allowsApiHost("api.other.com"))
    }

    @Test
    fun `host allowlist rejects URL syntax ports credentials and IP literals without local access`() {
        val invalidHosts = listOf(
            "https://api.example.com",
            "api.example.com:443",
            "user@api.example.com",
            "api.example.com/path",
            "127.0.0.1",
            "*.example.com/path",
            "*api.example.com",
            "api..example.com",
        )

        invalidHosts.forEach { host ->
            val extension = CustomExtension(
                name = "API tool",
                description = "",
                apiAccessEnabled = true,
                allowedApiHosts = listOf(host),
            )
            assertTrue(
                "Expected invalid host pattern: $host",
                CustomExtensionValidationError.INVALID_API_HOST_PATTERN in extension.validationErrors(),
            )
        }
    }

    @Test
    fun `private IPv4 hosts require the local network capability`() {
        val withoutLocalAccess = CustomExtension(
            name = "Local API",
            description = "",
            apiAccessEnabled = true,
            allowedApiHosts = listOf("127.0.0.1"),
        )
        val withLocalAccess = withoutLocalAccess.copy(allowLocalNetwork = true)
        val publicIp = withLocalAccess.copy(allowedApiHosts = listOf("8.8.8.8"))

        assertTrue(CustomExtensionValidationError.INVALID_API_HOST_PATTERN in withoutLocalAccess.validationErrors())
        assertEquals(emptySet<CustomExtensionValidationError>(), withLocalAccess.validationErrors())
        assertTrue(withLocalAccess.allowsApiHost("127.0.0.1"))
        // Local-network permission is separate from the exact host allowlist.
        assertFalse(withLocalAccess.allowsApiHost("192.168.1.40"))
        val explicitlyAllowedLanHost = withLocalAccess.copy(allowedApiHosts = listOf("192.168.1.40"))
        assertTrue(explicitlyAllowedLanHost.allowsApiHost("192.168.1.40"))
        assertFalse(publicIp.allowsApiHost("8.8.8.8"))
        assertTrue(CustomExtensionValidationError.INVALID_API_HOST_PATTERN in publicIp.validationErrors())
    }

    @Test
    fun `local network host patterns require a separate capability`() {
        val invalid = CustomExtension(
            name = "Local API",
            description = "",
            apiAccessEnabled = true,
            allowedApiHosts = listOf("localhost", "homeassistant.local"),
        )
        val enabled = invalid.copy(allowLocalNetwork = true)

        assertTrue(CustomExtensionValidationError.INVALID_API_HOST_PATTERN in invalid.validationErrors())
        assertEquals(emptySet<CustomExtensionValidationError>(), enabled.validationErrors())
        assertTrue(enabled.allowsApiHost("localhost"))
        assertTrue(enabled.allowsApiHost("homeassistant.local"))
    }

    @Test
    fun `API capability and host changes invalidate the successful test hash`() {
        val base = CustomExtension(
            name = "API tool",
            description = "",
            inputSchema = """{"type":"object","properties":{}}""",
            implementation = "return 1",
        )
        val api = base.copy(
            apiAccessEnabled = true,
            allowedApiHosts = listOf("api.example.com"),
        )
        val anotherHost = api.copy(allowedApiHosts = listOf("other.example.com"))
        val localNetwork = api.copy(allowLocalNetwork = true)
        val storage = base.copy(storageEnabled = true)

        assertNotEquals(base.testFingerprint(), api.testFingerprint())
        assertNotEquals(base.testFingerprint(), storage.testFingerprint())
        assertNotEquals(api.testFingerprint(), anotherHost.testFingerprint())
        assertNotEquals(api.testFingerprint(), localNetwork.testFingerprint())
    }

    @Test
    fun `value declarations affect the test fingerprint`() {
        val base = CustomExtension(
            name = "Configured API",
            description = "",
            implementation = "return globalThis.variables.API_BASE",
            apiAccessEnabled = true,
            allowedApiHosts = listOf("api.example.com"),
        )
        val declared = base.copy(
            secrets = listOf(CustomToolValueDeclaration("API_KEY")),
            variables = listOf(CustomToolValueDeclaration("API_BASE")),
        )

        assertNotEquals(base.testFingerprint(), declared.testFingerprint())
        assertEquals(emptySet<CustomExtensionValidationError>(), base.validationErrors())
        assertEquals(emptySet<CustomExtensionValidationError>(), declared.validationErrors())
    }

    @Test
    fun `duplicate value declaration names are rejected`() {
        val invalid = CustomExtension(
            name = "Configured API",
            description = "",
            secrets = listOf(CustomToolValueDeclaration("API_KEY")),
            variables = listOf(CustomToolValueDeclaration("API_KEY")),
        )

        assertTrue(CustomExtensionValidationError.DUPLICATE_VALUE_NAME in invalid.validationErrors())
    }

    private fun assertFalseEnabled(extension: CustomExtension) {
        assertEquals(false, extension.enabled)
    }
}
