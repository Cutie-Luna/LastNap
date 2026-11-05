package me.rerere.rikkahub.data.ai.tools.local

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.model.CustomExtension
import me.rerere.rikkahub.data.model.CustomExtensionLimits
import me.rerere.rikkahub.data.model.testFingerprint
import me.rerere.rikkahub.data.model.validationErrors
import me.rerere.rikkahub.data.model.extensionSha256

/**
 * The provider-visible name is bound to both identity and the tested definition.
 * Editing schema or code therefore makes an older pending tool call unresolvable.
 */
internal fun customExtensionToolName(extension: CustomExtension): String {
    val idHash = extension.id.extensionSha256().take(12)
    val definitionHash = extension.testFingerprint().take(16)
    return "custom_ext_${idHash}_${definitionHash}"
}

/**
 * Builds only assistant-selected, enabled, structurally valid, currently tested tools.
 *
 * The complete user schema is nested under the required "input" property because the
 * internal InputSchema.Obj represents only the outer properties/required pair.
 */
internal fun createCustomExtensionTools(
    extensions: List<CustomExtension>,
    selectedExtensionIds: Set<String>,
    reservedToolNames: Set<String> = emptySet(),
    onNameCollision: (String) -> Unit = {},
    httpRuntime: CustomToolHttpRuntime? = null,
    variablesByTool: Map<String, Map<String, String>> = emptyMap(),
): List<Tool> {
    val usedNames = reservedToolNames.toMutableSet()
    val seenIds = mutableSetOf<String>()

    return extensions.mapNotNull { extension ->
        if (!seenIds.add(extension.id)) return@mapNotNull null
        if (extension.id !in selectedExtensionIds) return@mapNotNull null
        if (!extension.enabled) return@mapNotNull null
        if (extension.validationErrors().isNotEmpty()) return@mapNotNull null
        // Capability-enabled tools must never silently fall back to the legacy evaluator.
        if ((extension.apiAccessEnabled || extension.storageEnabled || extension.variables.isNotEmpty()) &&
            httpRuntime == null
        ) {
            return@mapNotNull null
        }

        val inputSchema = parseCustomInputSchema(extension.inputSchema) ?: return@mapNotNull null
        val toolName = customExtensionToolName(extension)
        if (!usedNames.add(toolName)) {
            onNameCollision(toolName)
            return@mapNotNull null
        }

        val description = extension.description.ifBlank {
            "User-created custom tool: ${extension.name}"
        }.take(CustomExtensionLimits.MAX_DESCRIPTION_LENGTH)
        val skill = extension.skill.take(CustomExtensionLimits.MAX_SKILL_LENGTH)
        val implementation = extension.implementation

        Tool(
            name = toolName,
            description = description,
            parameters = {
                InputSchema.Obj(
                    properties = buildJsonObject { put("input", inputSchema) },
                    required = listOf("input"),
                )
            },
            systemPrompt = { _, _ ->
                buildString {
                    appendLine("**Custom tool: ${extension.name}**")
                    appendLine("Tool name: $toolName")
                    appendLine("Purpose: $description")
                    if (skill.isNotBlank()) {
                        appendLine("<custom_tool_instructions>")
                        appendLine(skill.escapeCustomToolXml())
                        appendLine("</custom_tool_instructions>")
                    }
                    appendLine("Call this tool with the user's JSON object inside the required input field.")
                    appendLine("The implementation runs in an isolated JavaScript runtime.")
                    if (extension.storageEnabled) {
                        appendLine("Private per-tool storage is available through storage.readText, storage.writeText, storage.delete, and storage.list.")
                    }
                    if (extension.apiAccessEnabled) {
                        appendLine("HTTP fetch is enabled only for the tool's configured allowed hosts.")
                        appendLine("Use fetch(url, { method, headers, body }) to make bounded API requests.")
                        appendLine("Non-sensitive configuration Variables are available as globalThis.variables.NAME.")
                        appendLine("Use {{secret:SECRET_NAME}} only inside an HTTP header value; Secret values are inserted by the native host and are not exposed to JavaScript.")
                    } else {
                        appendLine("Network access is not available to this tool.")
                    }
                }
            },
            needsApproval = { _ -> extension.needsApproval },
            execute = { arguments ->
                val args = arguments as? JsonObject ?: error("Custom tool arguments must be a JSON object")
                val serializedArguments = args.toString()
                val argumentBytes = serializedArguments.toByteArray(Charsets.UTF_8).size
                require(argumentBytes <= CustomExtensionLimits.MAX_INPUT_LENGTH) {
                    "Custom tool arguments exceed the 64 KiB limit"
                }
                val input = args["input"] as? JsonObject
                    ?: error("The input object is required")
                val serializedInput = input.toString()
                val serializedInputLiteral = JsonPrimitive(serializedInput).toString()
                val code = "const input = JSON.parse($serializedInputLiteral);\n$implementation"
                val output = if (extension.apiAccessEnabled || extension.storageEnabled || extension.variables.isNotEmpty()) {
                    requireNotNull(httpRuntime).evaluate(
                        extension = extension,
                        code = code,
                        variables = variablesByTool[extension.id].orEmpty(),
                    )
                } else {
                    evaluateJavascript(code)
                }
                listOf(UIMessagePart.Text(output))
            },
        )
    }
}

private fun parseCustomInputSchema(source: String): JsonObject? {
    if (source.length > CustomExtensionLimits.MAX_INPUT_SCHEMA_LENGTH) return null
    val schema = runCatching { Json.parseToJsonElement(source) as? JsonObject }.getOrNull() ?: return null
    if ((schema["type"] as? JsonPrimitive)?.contentOrNull != "object") return null

    if (schema["properties"] != null && schema["properties"] !is JsonObject) return null
    if (schema["required"] != null) {
        val required = schema["required"] as? JsonArray ?: return null
        if (required.any { (it as? JsonPrimitive)?.isString != true }) return null
    }
    return schema
}

private fun String.escapeCustomToolXml(): String = buildString(length) {
    for (character in this@escapeCustomToolXml) {
        when (character) {
            '&' -> append("&amp;")
            '<' -> append("&lt;")
            '>' -> append("&gt;")
            else -> append(character)
        }
    }
}
