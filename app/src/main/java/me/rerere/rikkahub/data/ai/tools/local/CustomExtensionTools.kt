package me.rerere.rikkahub.data.ai.tools.local

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.model.CustomExtension
import me.rerere.rikkahub.data.model.ExtensionPermission

private const val MAX_EXTENSION_NAME_LENGTH = 80
private const val MAX_EXTENSION_DESCRIPTION_LENGTH = 2_000
private const val MAX_EXTENSION_SKILL_LENGTH = 16_000
private const val MAX_EXTENSION_CODE_LENGTH = 64_000
private const val MAX_EXTENSION_INPUT_LENGTH = 64 * 1024

/**
 * Creates isolated JavaScript-backed tools from enabled user extensions.
 *
 * Extensions receive only their JSON input. QuickJS has no Android, filesystem,
 * network, DOM, or Node bindings; each invocation gets a fresh bounded runtime.
 */
internal fun createCustomExtensionTools(
    extensions: List<CustomExtension>,
): List<Tool> = extensions
    .filter { it.enabled && it.permission != ExtensionPermission.DISABLED }
    .filter { it.name.isNotBlank() && it.implementation.isNotBlank() }
    .filter { it.implementation.length <= MAX_EXTENSION_CODE_LENGTH }
    .mapNotNull { extension ->
        val safeId = extension.id.filter { it.isLetterOrDigit() }.take(32)
        if (safeId.isBlank()) return@mapNotNull null

        val toolName = "custom_extension_$safeId"
        val description = extension.description
            .ifBlank { "User-created extension: ${extension.name}" }
            .take(MAX_EXTENSION_DESCRIPTION_LENGTH)
        val skill = extension.skill.take(MAX_EXTENSION_SKILL_LENGTH)
        val implementation = extension.implementation

        Tool(
            name = toolName,
            description = description,
            parameters = {
                InputSchema.Obj(
                    properties = buildJsonObject {
                        put("input", buildJsonObject {
                            put("type", "object")
                            put("description", "JSON input for this extension. Follow the extension instructions.")
                            put("additionalProperties", true)
                        })
                    },
                    required = listOf("input"),
                )
            },
            systemPrompt = { _, _ ->
                buildString {
                    appendLine("**Custom extension: ${extension.name.take(MAX_EXTENSION_NAME_LENGTH)}**")
                    appendLine("Tool name: $toolName")
                    appendLine("Purpose: $description")
                    if (skill.isNotBlank()) {
                        appendLine("<extension_instructions>")
                        appendLine(skill.escapeExtensionXml())
                        appendLine("</extension_instructions>")
                    }
                    appendLine("Call this tool with an object in the input field. The implementation runs in an isolated JavaScript runtime.")
                }
            },
            needsApproval = {
                extension.permission == ExtensionPermission.ASK_EVERY_TIME ||
                    extension.permission == ExtensionPermission.THIS_SESSION
            },
            execute = { arguments ->
                val input = arguments.jsonObject["input"]
                    ?: error("The input object is required")
                require(input.toString().length <= MAX_EXTENSION_INPUT_LENGTH) { "Extension input exceeds the 64 KB limit" }
                val code = "const input = $input;\n" + implementation
                listOf(UIMessagePart.Text(evaluateJavascript(code)))
            },
        )
    }

private fun String.escapeExtensionXml(): String = buildString(length) {
    for (character in this@escapeExtensionXml) {
        when (character) {
            '&' -> append("&amp;")
            '<' -> append("&lt;")
            '>' -> append("&gt;")
            else -> append(character)
        }
    }
}
