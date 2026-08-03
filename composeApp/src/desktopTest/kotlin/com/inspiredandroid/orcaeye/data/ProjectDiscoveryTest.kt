package com.inspiredandroid.orcaeye.data

import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Discovery against a synthetic home, so the assertions do not depend on whatever the
 * machine's real Claude/Grok registries happen to contain.
 */
class ProjectDiscoveryTest {
    private fun fakeHome(): Path = Files.createTempDirectory("orcaeye-home").toRealPath()

    @Test
    fun dropsRegistryPathsThatAreNotProjects() = runBlocking {
        val home = fakeHome()
        val withMarker = home.resolve("Projects/withmarker").also { it.resolve(".claude").createDirectories() }
        val memoryOnly = home.resolve("Projects/memoryonly").also { it.createDirectories() }
        val documents = home.resolve("Documents").also { it.createDirectories() }
        home.resolve("Downloads").createDirectories()

        // Claude registers every cwd it was ever started in, including `/` and stray folders.
        writeClaudeJson(
            home,
            listOf(
                "/",
                documents.absolutePathString(),
                home.resolve("Downloads").absolutePathString(),
                home.absolutePathString(),
                home.resolve("Projects").absolutePathString(),
                withMarker.absolutePathString(),
                memoryOnly.absolutePathString(),
            ),
        )
        // memoryonly has no local markers, only memories written by Claude.
        claudeMemoryDir(home, memoryOnly).resolve("note.md").writeText("# note")

        val discovered = DesktopInventoryRepository(home = home).discoverProjects()
        val names = discovered.projects.map { it.name }

        assertTrue(discovered.projects.none { it.path == "/" }, "filesystem root is not a project")
        assertTrue(discovered.projects.none { it.name.isEmpty() }, "no nameless rows: $names")
        assertEquals(listOf("memoryonly", "withmarker"), names.sorted(), "unexpected projects: $names")
    }

    @Test
    fun keepsRegistryProjectWhoseOnlyContentIsMemories() = runBlocking {
        val home = fakeHome()
        val project = home.resolve("Projects/memoryonly").also { it.createDirectories() }
        writeClaudeJson(home, listOf(project.absolutePathString()))
        claudeMemoryDir(home, project).resolve("note.md").writeText("# note")

        val stubs = DesktopInventoryRepository(home = home).discoverProjects().projects

        assertEquals(listOf("memoryonly"), stubs.map { it.name })
        assertTrue(stubs.single().toolsPresent.isNotEmpty(), "registry membership should show a tool chip")
    }

    private fun writeClaudeJson(
        home: Path,
        projectPaths: List<String>,
    ) {
        val entries = projectPaths.joinToString(",") { "\"${it.replace("\\", "\\\\")}\":{}" }
        home.resolve(".claude.json").writeText("{\"projects\":{$entries}}")
    }

    private fun claudeMemoryDir(
        home: Path,
        project: Path,
    ): Path = home
        .resolve(".claude/projects")
        .resolve(project.absolutePathString().replace("/", "-"))
        .resolve("memory")
        .also { it.createDirectories() }
}
