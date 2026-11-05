package me.rerere.rikkahub.ui.pages.extensions

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import me.rerere.rikkahub.data.model.CustomExtension
import me.rerere.rikkahub.data.model.ExtensionPermission
import me.rerere.rikkahub.data.model.testFingerprint

class ExtensionBuilderStateTest {
    private fun testedExtension(
        schema: String = """{"type":"object","properties":{"value":{"type":"integer"}}}""",
        code: String = "input.value * 2",
        permission: ExtensionPermission = ExtensionPermission.ASK_EVERY_TIME,
    ): CustomExtension {
        val draft = CustomExtension(
            name = "Double a value",
            description = "Returns twice the supplied value",
            inputSchema = schema,
            implementation = code,
            permission = permission,
        )
        return draft.copy(testedDefinitionHash = draft.testFingerprint())
    }

    @Test
    fun testMarkerTracksBothSchemaAndImplementationButNotDescriptions() {
        val tested = testedExtension()

        assertTrue(tested.hasCurrentBuilderTest())
        assertFalse(tested.copy(inputSchema = """{"type":"object","properties":{"text":{"type":"string"}}}""").hasCurrentBuilderTest())
        assertFalse(tested.copy(implementation = "input.value + 1").hasCurrentBuilderTest())
        assertTrue(tested.copy(name = "Renamed", description = "Different purpose", skill = "Different guidance").hasCurrentBuilderTest())
    }

    @Test
    fun enablementRequiresCurrentTestAndCompleteValidation() {
        val tested = testedExtension()

        assertTrue(tested.canBeEnabledFromBuilder())
        assertFalse(tested.copy(testedDefinitionHash = null).canBeEnabledFromBuilder())
        assertFalse(tested.copy(name = "").canBeEnabledFromBuilder())
        assertFalse(tested.copy(permission = ExtensionPermission.DISABLED).canBeEnabledFromBuilder())
        assertFalse(tested.copy(implementation = "").canBeEnabledFromBuilder())
    }

    @Test
    fun testCodeUsesJsonParseAndPreservesSpecialJsonPropertyNames() {
        val code = buildCustomExtensionTestCode(
            testInput = """{"__proto__":{"safe":true},"value":21}""",
            implementation = "Object.prototype.hasOwnProperty.call(input, '__proto__')",
        )

        assertTrue(code.startsWith("""const input = JSON.parse("{\"__proto__\":{\"safe\":true},\"value\":21}");"""))
        assertTrue(code.endsWith("Object.prototype.hasOwnProperty.call(input, '__proto__')"))
    }

    @Test
    fun testInputMustBeAnObjectAndFitTheRuntimeArgumentLimit() {
        assertTrue(runCatching {
            buildCustomExtensionTestCode("""[]""", "1")
        }.isFailure)
        val oversizedValue = "😀".repeat(17_000)
        val oversizedInput = """{"value":"$oversizedValue"}"""
        assertTrue(runCatching {
            buildCustomExtensionTestCode(oversizedInput, "input.value.length")
        }.isFailure)
    }

    @Test
    fun validObjectTestInputBuildsCode() {
        val code = buildCustomExtensionTestCode("""{"value":21}""", "input.value * 2")

        assertTrue(code.contains("JSON.parse"))
        assertTrue(code.endsWith("input.value * 2"))
    }
}
