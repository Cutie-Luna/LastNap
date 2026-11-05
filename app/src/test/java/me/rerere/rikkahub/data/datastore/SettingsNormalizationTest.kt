package me.rerere.rikkahub.data.datastore

import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.CustomExtension
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.uuid.Uuid

class SettingsNormalizationTest {

    @Test
    fun `unassigned tools do not become assigned to default assistants`() {
        val settings = Settings()

        assertTrue(settings.assistants.isNotEmpty())
        assertTrue(settings.assistants.all { it.selectedCustomExtensionIds.isEmpty() })
    }

    @Test
    fun `normalization removes duplicate definitions and dangling assistant selections`() {
        val first = extension("tool-1", "First").copy(
            variables = listOf(me.rerere.rikkahub.data.model.CustomToolValueDeclaration("API_BASE"))
        )
        val duplicate = extension("tool-1", "Duplicate")
        val settings = Settings(
            customExtensions = listOf(first, duplicate, extension("", "No ID")),
            customToolVariables = mapOf(
                "tool-1" to mapOf("API_BASE" to "https://api.example.com", "UNKNOWN" to "discard"),
                "deleted-tool" to mapOf("API_BASE" to "discard"),
            ),
            assistants = listOf(
                Assistant(
                    id = id(1),
                    selectedCustomExtensionIds = setOf("tool-1", "deleted-tool", ""),
                )
            ),
        )

        val normalized = settings.withoutInvalidReferences()

        assertEquals(listOf(first), normalized.customExtensions)
        assertEquals(mapOf("tool-1" to mapOf("API_BASE" to "https://api.example.com")), normalized.customToolVariables)
        assertEquals(setOf("tool-1"), normalized.assistants.single().selectedCustomExtensionIds)
    }

    @Test
    fun `deleting a custom tool removes every assistant assignment atomically`() {
        val first = extension("tool-1", "First")
        val second = extension("tool-2", "Second")
        val settings = Settings(
            customExtensions = listOf(
                first.copy(variables = listOf(me.rerere.rikkahub.data.model.CustomToolValueDeclaration("API_BASE"))),
                second,
            ),
            customToolVariables = mapOf(
                "tool-1" to mapOf("API_BASE" to "https://first.example"),
                "tool-2" to mapOf("TOKEN" to "second"),
            ),
            assistants = listOf(
                Assistant(id = id(1), selectedCustomExtensionIds = setOf("tool-1", "tool-2")),
                Assistant(id = id(2), selectedCustomExtensionIds = setOf("tool-1")),
                Assistant(id = id(3), selectedCustomExtensionIds = emptySet()),
            ),
        )

        val updated = settings.removeCustomExtension("tool-1")

        assertEquals(listOf(second), updated.customExtensions)
        assertEquals(mapOf("tool-2" to mapOf("TOKEN" to "second")), updated.customToolVariables)
        assertEquals(listOf(setOf("tool-2"), emptySet(), emptySet()), updated.assistants.map { it.selectedCustomExtensionIds })
    }

    private fun extension(id: String, name: String) = CustomExtension(
        id = id,
        name = name,
        description = "",
    )

    private fun id(value: Int) = Uuid.parse("00000000-0000-0000-0000-%012d".format(value))
}
