package me.rerere.rikkahub.data.ai.tools.local

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.model.CustomExtension
import me.rerere.rikkahub.data.model.ExtensionPermission
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomExtensionToolsTest {
    @Test
    fun onlyEnabledNonDisabledExtensionsBecomeTools() {
        val tools = createCustomExtensionTools(
            listOf(
                CustomExtension(
                    id = "enabled-1",
                    name = "Double",
                    description = "Doubles a number",
                    implementation = "input.value * 2",
                    enabled = true,
                    permission = ExtensionPermission.ALWAYS_ALLOW,
                ),
                CustomExtension(
                    id = "draft-2",
                    name = "Draft",
                    description = "Not ready",
                    implementation = "1 + 1",
                    enabled = false,
                ),
                CustomExtension(
                    id = "disabled-3",
                    name = "Disabled",
                    description = "Turned off",
                    implementation = "1 + 1",
                    enabled = true,
                    permission = ExtensionPermission.DISABLED,
                ),
            )
        )

        assertEquals(listOf("custom_extension_enabled1"), tools.map { it.name })
        assertFalse(tools.single().needsApproval(Json.parseToJsonElement("""{"input":{"value":4}}""")))
    }

    @Test
    fun askPermissionIsRequestedBeforeEveryInvocation() {
        val tool = createCustomExtensionTools(
            listOf(
                CustomExtension(
                    id = "ask-me",
                    name = "Careful tool",
                    description = "Needs confirmation",
                    implementation = "input.value",
                    enabled = true,
                    permission = ExtensionPermission.ASK_EVERY_TIME,
                )
            )
        ).single()

        assertTrue(tool.needsApproval(Json.parseToJsonElement("""{"input":{"value":4}}""")))
    }

    @Test
    fun runsExtensionJavascriptWithIsolatedInput() = runBlocking {
        val tool = createCustomExtensionTools(
            listOf(
                CustomExtension(
                    id = "double",
                    name = "Double",
                    description = "Doubles a number",
                    implementation = "input.value * 2",
                    enabled = true,
                    permission = ExtensionPermission.ALWAYS_ALLOW,
                )
            )
        ).single()

        val output = tool.execute(
            buildJsonObject {
                put("input", buildJsonObject { put("value", 21) })
            }
        )
        assertEquals("{\"result\":\"42\"}", (output.single() as UIMessagePart.Text).text)
    }
}
