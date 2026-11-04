package me.rerere.rikkahub.data.ai.tools.local

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.model.CustomExtension
import me.rerere.rikkahub.data.model.ExtensionPermission
import me.rerere.rikkahub.data.model.extensionSha256
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomExtensionToolsTest {
    private fun enabledExtension(
        id: String,
        permission: ExtensionPermission = ExtensionPermission.ALWAYS_ALLOW,
        code: String = "input.value * 2",
    ) = CustomExtension(
        id = id,
        name = "Test extension",
        description = "A tested extension",
        implementation = code,
        enabled = true,
        permission = permission,
        testedImplementationHash = code.extensionSha256(),
    )

    @Test
    fun onlyEnabledTestedNonDisabledExtensionsBecomeTools() {
        val tools = createCustomExtensionTools(
            listOf(
                enabledExtension("enabled-1"),
                CustomExtension(
                    id = "draft-2",
                    name = "Draft",
                    description = "Not ready",
                    implementation = "1 + 1",
                    enabled = false,
                ),
                enabledExtension("disabled-3", ExtensionPermission.DISABLED),
                enabledExtension("changed-4").copy(implementation = "input.value + 100"),
                enabledExtension("untested-5").copy(testedImplementationHash = null),
            )
        )

        assertEquals(listOf("custom_extension_enabled1"), tools.map { it.name })
        assertFalse(tools.single().needsApproval(Json.parseToJsonElement("""{"input":{"value":4}}""")))
    }

    @Test
    fun askPermissionIsRequestedBeforeEveryInvocation() {
        val tool = createCustomExtensionTools(
            listOf(enabledExtension("ask-me", ExtensionPermission.ASK_EVERY_TIME))
        ).single()

        assertTrue(tool.needsApproval(Json.parseToJsonElement("""{"input":{"value":4}}""")))
    }

    @Test
    fun runsExtensionJavascriptWithIsolatedInput() = runBlocking {
        val tool = createCustomExtensionTools(
            listOf(enabledExtension("double"))
        ).single()

        val output = tool.execute(
            buildJsonObject {
                put("input", buildJsonObject { put("value", 21) })
            }
        )
        assertEquals("{\"result\":\"42\"}", (output.single() as UIMessagePart.Text).text)
    }
}
