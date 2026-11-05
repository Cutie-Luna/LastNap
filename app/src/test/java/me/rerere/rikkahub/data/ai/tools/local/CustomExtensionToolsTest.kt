package me.rerere.rikkahub.data.ai.tools.local

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import me.rerere.ai.core.InputSchema
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.model.CustomExtension
import me.rerere.rikkahub.data.model.ExtensionPermission
import me.rerere.rikkahub.data.model.testFingerprint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.uuid.Uuid

class CustomExtensionToolsTest {
    private val assistantId = Uuid.random()

    private fun enabledExtension(
        id: String,
        permission: ExtensionPermission = ExtensionPermission.ALWAYS_ALLOW,
        schema: String = """{"type":"object","properties":{"value":{"type":"integer"}},"required":["value"]}""",
        code: String = "input.value * 2",
    ): CustomExtension {
        val draft = CustomExtension(
            id = id,
            name = "Test custom tool",
            description = "A tested custom tool",
            inputSchema = schema,
            implementation = code,
            permission = permission,
            enabled = true,
        )
        return draft.copy(testedDefinitionHash = draft.testFingerprint())
    }

    private fun create(
        extensions: List<CustomExtension>,
        selected: Set<String> = extensions.mapTo(mutableSetOf()) { it.id },
        grants: Set<CustomExtensionSessionGrant> = emptySet(),
        reserved: Set<String> = emptySet(),
        onCollision: (String) -> Unit = {},
    ) = createCustomExtensionTools(
        extensions = extensions,
        selectedExtensionIds = selected,
        assistantId = assistantId,
        sessionApprovedExtensions = grants,
        reservedToolNames = reserved,
        onNameCollision = onCollision,
    )

    @Test
    fun onlySelectedEnabledAndCurrentlyTestedDefinitionsBecomeTools() {
        val selected = enabledExtension("selected")
        val unselected = enabledExtension("unselected")
        val disabled = enabledExtension("disabled").copy(enabled = false)
        val untested = enabledExtension("untested").copy(testedDefinitionHash = null)
        val editedAfterTest = enabledExtension("edited").copy(implementation = "input.value + 10")
        val invalidSchema = enabledExtension("invalid").copy(inputSchema = """{"type":"array"}""")

        val tools = create(
            listOf(selected, unselected, disabled, untested, editedAfterTest, invalidSchema),
            selected = setOf("selected", "disabled", "untested", "edited", "invalid"),
        )

        assertEquals(1, tools.size)
        assertEquals(customExtensionToolName(selected), tools.single().name)
    }

    @Test
    fun fullInputSchemaIsPreservedInsideRequiredInputProperty() {
        val schema = """{"type":"object","properties":{"city":{"type":"string"}},"required":["city"],"additionalProperties":false}"""
        val tool = create(listOf(enabledExtension("schema", schema = schema))).single()
        val parameters = tool.parameters() as InputSchema.Obj

        assertEquals(listOf("input"), parameters.required)
        assertEquals(Json.parseToJsonElement(schema), parameters.properties["input"])
    }

    @Test
    fun allPermissionModesHaveDistinctRuntimeBehavior() {
        val always = create(listOf(enabledExtension("always", ExtensionPermission.ALWAYS_ALLOW))).single()
        val ask = create(listOf(enabledExtension("ask", ExtensionPermission.ASK_EVERY_TIME))).single()
        val sessionExtension = enabledExtension("session", ExtensionPermission.THIS_SESSION)
        val session = create(listOf(sessionExtension)).single()
        val sessionGrant = sessionExtension.sessionGrant(assistantId)
        val sessionApproved = create(listOf(sessionExtension), grants = setOf(sessionGrant)).single()
        val wrongAssistantGrant = sessionExtension.sessionGrant(Uuid.random())
        val wrongAssistant = create(listOf(sessionExtension), grants = setOf(wrongAssistantGrant)).single()
        val disabled = create(
            listOf(enabledExtension("disabled", ExtensionPermission.DISABLED)),
        )

        assertFalse(always.needsApproval(JsonNull))
        assertTrue(ask.needsApproval(JsonNull))
        assertTrue(session.needsApproval(JsonNull))
        assertFalse(sessionApproved.needsApproval(JsonNull))
        assertTrue(wrongAssistant.needsApproval(JsonNull))
        assertTrue(disabled.isEmpty())
    }

    @Test
    fun providerToolNameIsSafeAndChangesWhenSchemaOrCodeChanges() {
        val original = enabledExtension("stable-id")
        val changedCode = original.copy(implementation = "input.value + 100")
        val changedSchema = original.copy(inputSchema = """{"type":"object","properties":{"other":{"type":"string"}}}""")
        val name = customExtensionToolName(original)

        assertTrue(name.length <= 64)
        assertTrue(name.matches(Regex("[a-z0-9_]+")))
        assertNotEquals(name, customExtensionToolName(changedCode))
        assertNotEquals(name, customExtensionToolName(changedSchema))
    }

    @Test
    fun collidingNamesAreSkippedInsteadOfShadowingExistingTools() {
        val extension = enabledExtension("collision")
        val collision = customExtensionToolName(extension)
        val reported = mutableListOf<String>()

        val tools = create(
            extensions = listOf(extension),
            reserved = setOf(collision),
            onCollision = reported::add,
        )

        assertTrue(tools.isEmpty())
        assertEquals(listOf(collision), reported)
    }

    @Test
    fun invalidRequiredSchemaShapeIsRejected() {
        val missingRequiredProperty = enabledExtension(
            "bad-required",
            schema = """{"type":"object","properties":{"value":{"type":"integer"}},"required":["missing"]}""",
        )
        val nonStringRequired = enabledExtension(
            "bad-required-type",
            schema = """{"type":"object","properties":{},"required":[1]}""",
        )

        assertTrue(create(listOf(missingRequiredProperty)).isEmpty())
        assertTrue(create(listOf(nonStringRequired)).isEmpty())
    }

    @Test
    fun executesInTheExistingIsolatedJavascriptRuntime() = runBlocking {
        val tool = create(listOf(enabledExtension("double"))).single()
        val input = buildJsonObject { put("value", 21) }
        val output = tool.execute(buildJsonObject { put("input", input) })

        assertEquals("""{"result":"42"}""", (output.single() as UIMessagePart.Text).text)
    }

    @Test
    fun invocationRequiresAnObjectAndEnforcesUtf8ByteLimit() = runBlocking {
        val tool = create(listOf(enabledExtension("bounded"))).single()
        val nonObjectInput = runCatching {
            tool.execute(buildJsonObject { put("input", JsonArray(emptyList())) })
        }
        val largeInput = buildJsonObject { put("value", "😀".repeat(17_000)) }
        val oversized = runCatching {
            tool.execute(buildJsonObject { put("input", largeInput) })
        }

        assertTrue(nonObjectInput.isFailure)
        assertTrue(oversized.isFailure)
        assertTrue(oversized.exceptionOrNull()?.message.orEmpty().contains("64 KiB"))
    }

    @Test
    fun duplicateDefinitionIdsDoNotProduceDuplicateTools() {
        val first = enabledExtension("duplicate")
        val second = enabledExtension("duplicate", code = "input.value + 1")

        assertEquals(1, create(listOf(first, second)).size)
    }
}
