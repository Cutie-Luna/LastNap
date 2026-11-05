package me.rerere.rikkahub.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomExtensionTest {

    @Test
    fun `new definitions are valid disabled drafts by default`() {
        val extension = CustomExtension(name = "Weather", description = "Returns a forecast")

        assertFalseEnabled(extension)
        assertEquals(ExtensionPermission.ASK_EVERY_TIME, extension.permission)
        assertEquals(null, extension.testedImplementationHash)
        assertEquals(emptySet<CustomExtensionValidationError>(), extension.validationErrors())
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
    fun `enabled definition needs a matching implementation test hash`() {
        val code = "input.value * 2"
        val untested = CustomExtension(
            name = "Double",
            description = "",
            implementation = code,
            enabled = true,
        )
        val tested = untested.copy(testedImplementationHash = code.extensionSha256())

        assertTrue(CustomExtensionValidationError.ENABLED_WITHOUT_CURRENT_TEST in untested.validationErrors())
        assertEquals(emptySet<CustomExtensionValidationError>(), tested.validationErrors())
        assertTrue(
            CustomExtensionValidationError.ENABLED_WITHOUT_CURRENT_TEST in
                tested.copy(implementation = "input.value + 2").validationErrors()
        )
        assertTrue(
            CustomExtensionValidationError.ENABLED_WITH_DISABLED_PERMISSION in
                tested.copy(permission = ExtensionPermission.DISABLED).validationErrors()
        )
    }

    @Test
    fun `implementation fingerprint changes when code changes`() {
        assertTrue("return 1".extensionSha256() != "return 2".extensionSha256())
    }

    private fun assertFalseEnabled(extension: CustomExtension) {
        assertEquals(false, extension.enabled)
    }
}
