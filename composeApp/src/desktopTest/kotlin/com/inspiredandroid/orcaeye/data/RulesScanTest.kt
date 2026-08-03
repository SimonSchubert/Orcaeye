package com.inspiredandroid.orcaeye.data

import com.inspiredandroid.orcaeye.model.ToolKind
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Rules scanning against a synthetic home, so assertions do not depend on whatever
 * rules the machine's real tool directories happen to contain.
 */
class RulesScanTest {
    private fun fakeHome(): Path = Files.createTempDirectory("orcaeye-rules-home").toRealPath()

    private fun Path.write(content: String): Path = also {
        it.parent.createDirectories()
        it.writeText(content)
    }

    @Test
    fun systemRulesAreListedPerToolWithFrontmatter() = runBlocking {
        val home = fakeHome()
        home
            .resolve(".claude/rules/testing.md")
            .write(
                """
                ---
                description: How we write tests
                ---

                Use kotlin.test.
                """.trimIndent(),
            )
        // Recursive discovery keeps the subdirectory in the name.
        home.resolve(".claude/rules/frontend/compose.md").write("# Compose")
        home.resolve(".grok/rules/style.md").write("# Style")
        home
            .resolve(".cursor/rules/api.mdc")
            .write(
                """
                ---
                description: API conventions
                globs: ["src/api/**/*.ts", "lib/**/*.ts"]
                alwaysApply: false
                ---

                Validate input.
                """.trimIndent(),
            )

        val snapshot = DesktopInventoryRepository(home = home).loadSystemSnapshot()
        val byName = snapshot.systemRules.associateBy { it.name }

        assertEquals(
            listOf("api", "frontend/compose", "style", "testing"),
            snapshot.systemRules.map { it.name }.sorted(),
        )
        assertEquals(ToolKind.Claude, byName.getValue("testing").tool)
        assertEquals("How we write tests", byName.getValue("testing").description)
        assertTrue(byName.getValue("testing").globs.isEmpty(), "no paths frontmatter means always-on")
        assertEquals(ToolKind.Grok, byName.getValue("style").tool)

        val api = byName.getValue("api")
        assertEquals(ToolKind.Cursor, api.tool)
        assertEquals("API conventions", api.description)
        assertEquals(listOf("src/api/**/*.ts", "lib/**/*.ts"), api.globs)
    }

    @Test
    fun claudeBlockSequencePathsAreParsed() = runBlocking {
        val home = fakeHome()
        home
            .resolve(".claude/rules/api.md")
            .write(
                """
                ---
                description: API rules
                paths:
                  - "src/api/**/*.ts"
                  - 'tests/**/*.test.ts'
                ---

                Body.
                """.trimIndent(),
            )

        val rule = DesktopInventoryRepository(home = home).loadSystemSnapshot().systemRules.single()

        assertEquals(listOf("src/api/**/*.ts", "tests/**/*.test.ts"), rule.globs)
        assertEquals("API rules", rule.description)
    }

    @Test
    fun toolsWithoutARulesDirectoryAreSkipped() = runBlocking {
        val home = fakeHome()
        // Neither CLI reads a rules folder — they only follow the AGENTS.md chain.
        home.resolve(".opencode/rules/ignored.md").write("# nope")
        home.resolve(".codex/rules/ignored.md").write("# nope")

        val snapshot = DesktopInventoryRepository(home = home).loadSystemSnapshot()

        assertTrue(snapshot.systemRules.isEmpty(), "found: ${snapshot.systemRules.map { it.path }}")
        assertFalse(ToolKind.OpenCode.supportsRules)
        assertFalse(ToolKind.Codex.supportsRules)
    }

    @Test
    fun projectRulesCoverClaudeAndAntigravityDirs() = runBlocking {
        val home = fakeHome()
        val project = home.resolve("Projects/demo").also { it.createDirectories() }
        project.resolve(".claude/rules/security.md").write("# Security")
        project.resolve(".agent/rules/ui.md").write("# UI")
        project.resolve(".cursor/rules/nested/deep.mdc").write("# Deep")

        val inventory = DesktopInventoryRepository(home = home).loadProject(project.toString())

        assertNotNull(inventory)
        assertEquals(
            listOf("nested/deep", "security", "ui"),
            inventory.rules.map { it.name }.sorted(),
        )
        assertEquals(
            setOf(ToolKind.Claude, ToolKind.Gemini, ToolKind.Cursor),
            inventory.rules.map { it.tool }.toSet(),
        )
        assertTrue(
            inventory.toolsPresent.containsAll(listOf(ToolKind.Claude, ToolKind.Gemini, ToolKind.Cursor)),
            "a rules dir should mark its tool present: ${inventory.toolsPresent}",
        )
    }

    @Test
    fun legacyCursorRulesFileIsListedAsAnAgentFile() = runBlocking {
        val home = fakeHome()
        val project = home.resolve("Projects/legacy").also { it.createDirectories() }
        project.resolve(".cursorrules").write("Old style rules")

        val inventory = DesktopInventoryRepository(home = home).loadProject(project.toString())

        assertNotNull(inventory)
        assertTrue(inventory.rules.isEmpty(), "a root file is not a rules-directory entry")
        val agentFile = inventory.agentFiles.single { it.name == ".cursorrules" }
        assertEquals(ToolKind.Cursor, agentFile.tool)
    }

    @Test
    fun createRuleWritesMarkdownForClaudeAndMdcForCursor() = runBlocking {
        val home = fakeHome()
        val project = home.resolve("Projects/demo").also { it.createDirectories() }
        val repo = DesktopInventoryRepository(home = home)

        val claudePath = repo.createRule("Test Style", ToolKind.Claude, project.toString(), "How we test")
        val cursorPath = repo.createRule("Api Rules", ToolKind.Cursor, project.toString(), "API conventions")
        val geminiPath = repo.createRule("Ui", ToolKind.Gemini, project.toString(), "")

        assertEquals(project.resolve(".claude/rules/test-style.md").toString(), claudePath)
        assertEquals(project.resolve(".cursor/rules/api-rules.mdc").toString(), cursorPath)
        // Antigravity workspace rules live in .agent/rules, not .gemini.
        assertEquals(project.resolve(".agent/rules/ui.md").toString(), geminiPath)
        assertTrue(Path.of(cursorPath).readText().contains("alwaysApply: true"))

        val rules = assertNotNull(repo.loadProject(project.toString())).rules
        assertEquals(listOf("api-rules", "test-style", "ui"), rules.map { it.name }.sorted())
        assertEquals("How we test", rules.single { it.name == "test-style" }.description)
    }

    @Test
    fun createSystemRuleRejectsToolsWithoutRulesSupport() = runBlocking {
        val home = fakeHome()
        val repo = DesktopInventoryRepository(home = home)

        val failed =
            try {
                repo.createRule("nope", ToolKind.Codex, null, "")
                false
            } catch (_: IllegalArgumentException) {
                true
            }

        assertTrue(failed, "Codex has no rules directory to write to")
        assertFalse(home.resolve(".codex/rules").exists())
    }
}
