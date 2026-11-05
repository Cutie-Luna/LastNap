package me.rerere.rikkahub.ui.pages.extensions

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import me.rerere.rikkahub.data.model.CustomExtension
import me.rerere.rikkahub.data.model.CustomExtensionLimits
import me.rerere.rikkahub.data.model.testFingerprint
import me.rerere.rikkahub.data.model.validationErrors

/** The saved test marker is valid only for the exact schema and implementation now being edited. */
internal fun CustomExtension.hasCurrentBuilderTest(): Boolean =
    testedDefinitionHash != null && testedDefinitionHash == testFingerprint()

/** Mirrors the model/runtime enable gate; saving an incomplete draft remains allowed. */
internal fun CustomExtension.canBeEnabledFromBuilder(): Boolean {
    val candidate = copy(enabled = true)
    return candidate.hasCurrentBuilderTest() && candidate.validationErrors().isEmpty()
}

/**
 * Builds the exact safe input prelude used by the custom-tool runtime.
 * JSON.parse is deliberate: object-literal interpolation would treat special JSON keys as JS syntax.
 */
internal fun buildCustomExtensionTestCode(testInput: String, implementation: String): String {
    val input = Json.parseToJsonElement(testInput) as? JsonObject
        ?: error("Test input must be a JSON object")
    val arguments = buildJsonObject { put("input", input) }
    val argumentBytes = arguments.toString().toByteArray(Charsets.UTF_8).size
    require(argumentBytes <= CustomExtensionLimits.MAX_INPUT_LENGTH) {
        "Test input exceeds the 64 KiB limit"
    }
    val inputLiteral = JsonPrimitive(input.toString()).toString()
    return "const input = JSON.parse($inputLiteral);\n$implementation"
}
