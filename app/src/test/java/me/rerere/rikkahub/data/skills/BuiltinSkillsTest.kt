package me.rerere.rikkahub.data.skills

import java.io.File
import me.rerere.rikkahub.data.model.Assistant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BuiltinSkillsTest {
    @Test
    fun `local skill overrides builtin skill with same name`() {
        val local = SkillMetadata(name = "shared", description = "local", skillDir = File("skills/shared"))
        val merged = mergeWithBuiltinSkills(
            local = listOf(local),
            builtin = listOf(
                SkillMetadata(name = "shared", description = "builtin", skillDir = File("builtin/shared"), builtin = true),
                SkillMetadata(name = "only-builtin", description = "builtin", skillDir = File("builtin/only"), builtin = true),
            ),
        )

        assertEquals(listOf("shared", "only-builtin"), merged.map { it.name })
        assertEquals(local, merged.first())
    }

    @Test
    fun `tool creator is bundled but not enabled for new assistants`() {
        val skillFile = File("src/main/assets/builtin_skills/tool-creator/SKILL.md")

        assertTrue("Tool Creator skill is missing", skillFile.isFile)
        assertTrue("New assistants must opt in to skills", Assistant().enabledSkills.isEmpty())
    }

    @Test
    fun `tool creator covers every Builder field and the tested image handoff`() {
        val skill = File("src/main/assets/builtin_skills/tool-creator/SKILL.md").readText()
        listOf(
            "API access and allowed hosts",
            "Local-network permission",
            "Secrets",
            "Variables",
            "Private storage",
            "Need approval",
            "media.image(data, mimeType, fileName)",
            "media.imageFromResponse(response, fileName)",
            "media.imageFromUrl(url, options, fileName)",
            "one image per invocation",
            "Never ask for Secret values",
        ).forEach { required ->
            assertTrue("Tool Creator skill must document $required", skill.contains(required))
        }

        val strings = File("src/main/res/values/strings.xml").readText()
        assertTrue(strings.contains("API access and host allowlist"))
        assertTrue(strings.contains("Secret and Variable declarations"))
        assertTrue(strings.contains("enter Secrets only in this Builder"))
    }

    @Test
    fun `builtin skills in assets are valid`() {
        // Android 单元测试的工作目录是模块根目录
        val root = File("src/main/assets/builtin_skills")
        val skillDirs = root.listFiles()?.filter { it.isDirectory }.orEmpty()
        assertTrue("No builtin skills found in ${root.absolutePath}", skillDirs.isNotEmpty())

        skillDirs.forEach { dir ->
            val skillFile = dir.resolve("SKILL.md")
            assertTrue("${dir.name}: missing SKILL.md", skillFile.exists())
            val content = skillFile.readText()
            val frontmatter = SkillFrontmatterParser.parse(content)
            assertEquals("${dir.name}: name must match directory name", dir.name, frontmatter["name"])
            val description = frontmatter["description"]
            assertTrue("${dir.name}: description is blank", !description.isNullOrBlank())
            assertTrue("${dir.name}: description exceeds 1024 chars", description!!.length <= 1024)

            // 正文中的相对链接必须指向技能目录内存在的文件（忽略代码块和行内代码中的示例）
            val body = SkillFrontmatterParser.extractBody(content)
                .replace(Regex("(?s)```.*?```"), "")
                .replace(Regex("`[^`]*`"), "")
            Regex("""\]\(([^)\s]+)\)""").findAll(body)
                .map { it.groupValues[1].substringBefore('#') }
                .filter { it.isNotBlank() && !it.contains("://") && !it.startsWith("/") }
                .forEach { link ->
                    assertTrue("${dir.name}: broken link $link", dir.resolve(link).exists())
                }
        }
    }
}
