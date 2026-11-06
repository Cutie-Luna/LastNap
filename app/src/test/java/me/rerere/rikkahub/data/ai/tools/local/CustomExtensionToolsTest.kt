package me.rerere.rikkahub.data.ai.tools.local

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import me.rerere.ai.core.InputSchema
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.model.CustomExtension
import me.rerere.rikkahub.data.model.testFingerprint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomExtensionToolsTest {
    private fun enabledExtension(
        id: String,
        needsApproval: Boolean = true,
        schema: String = """{"type":"object","properties":{"value":{"type":"integer"}},"required":["value"]}""",
        code: String = "input.value * 2",
    ): CustomExtension {
        val draft = CustomExtension(
            id = id,
            name = "Test custom tool",
            description = "A tested custom tool",
            inputSchema = schema,
            implementation = code,
            needsApproval = needsApproval,
            enabled = true,
        )
        return draft.copy(testedDefinitionHash = draft.testFingerprint())
    }

    private fun create(
        extensions: List<CustomExtension>,
        selected: Set<String> = extensions.mapTo(mutableSetOf()) { it.id },
        reserved: Set<String> = emptySet(),
        onCollision: (String) -> Unit = {},
    ) = createCustomExtensionTools(
        extensions = extensions,
        selectedExtensionIds = selected,
        reservedToolNames = reserved,
        onNameCollision = onCollision,
    )


    @Test
    fun conversationImageMetadataBecomesATypedImagePartNotModelVisibleUriText() {
        val uri = "file:///conversation_media/12345678-1234-1234-1234-123456789abc/generated.png"
        val parts = customToolOutputParts(
            """{"result":"ready","mediaMimeType":"image/png","mediaUri":"$uri"}"""
        )

        assertEquals(2, parts.size)
        assertEquals("""{"result":"ready"}""", (parts[0] as UIMessagePart.Text).text)
        assertEquals(uri, (parts[1] as UIMessagePart.Image).url)
        assertFalse((parts[0] as UIMessagePart.Text).text.contains(uri))
    }

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
    fun approvalSwitchControlsTheExistingToolApprovalContract() {
        val approvalRequired = create(listOf(enabledExtension("approval-on", needsApproval = true))).single()
        val noApprovalRequired = create(listOf(enabledExtension("approval-off", needsApproval = false))).single()

        assertTrue(approvalRequired.needsApproval(JsonNull))
        assertFalse(noApprovalRequired.needsApproval(JsonNull))
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
    fun requiredSchemaMustBeAStringArrayButMayReferenceUnlistedProperties() {
        val missingRequiredProperty = enabledExtension(
            "bad-required",
            schema = """{"type":"object","properties":{"value":{"type":"integer"}},"required":["missing"]}""",
        )
        val nonStringRequired = enabledExtension(
            "bad-required-type",
            schema = """{"type":"object","properties":{},"required":[1]}""",
        )

        assertEquals(1, create(listOf(missingRequiredProperty)).size)
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
    fun inputWithProtoKeyRemainsAnOrdinaryJsonProperty() = runBlocking {
        val tool = create(
            listOf(
                enabledExtension(
                    "proto",
                    code = "Object.prototype.hasOwnProperty.call(input, '__proto__')",
                )
            )
        ).single()
        val input = buildJsonObject {
            put("__proto__", buildJsonObject { put("polluted", true) })
        }
        val output = tool.execute(buildJsonObject { put("input", input) })

        assertEquals("""{"result":"true"}""", (output.single() as UIMessagePart.Text).text)
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
        val oversizedExtraArgument = runCatching {
            tool.execute(
                buildJsonObject {
                    put("input", buildJsonObject { put("value", 1) })
                    put("extra", "😀".repeat(17_000))
                }
            )
        }

        assertTrue(nonObjectInput.isFailure)
        assertTrue(oversized.isFailure)
        assertTrue(oversizedExtraArgument.isFailure)
        assertTrue(oversized.exceptionOrNull()?.message.orEmpty().contains("64 KiB"))
        assertTrue(oversizedExtraArgument.exceptionOrNull()?.message.orEmpty().contains("64 KiB"))
    }

    @Test
    fun duplicateDefinitionIdsDoNotProduceDuplicateTools() {
        val first = enabledExtension("duplicate")
        val second = enabledExtension("duplicate", code = "input.value + 1")

        assertEquals(1, create(listOf(first, second)).size)
    }
}
