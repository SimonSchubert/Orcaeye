package com.inspiredandroid.orcaeye.data

import com.inspiredandroid.orcaeye.model.ToolKind
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertTrue

class DesktopInventoryRepositoryTest {
    @Test
    fun loadSnapshotFindsLocalToolsAndProjects() = runBlocking {
        val snapshot = DesktopInventoryRepository().loadSnapshot()

        val byKind = snapshot.tools.associateBy { it.kind }
        assertTrue(byKind[ToolKind.Claude]?.installed == true, "Claude should be installed")
        assertTrue(byKind[ToolKind.Grok]?.installed == true, "Grok should be installed")
        assertTrue(byKind[ToolKind.OpenCode]?.installed == true, "OpenCode should be installed")

        assertTrue(snapshot.systemSkills.isNotEmpty(), "expected system skills")
        assertTrue(
            snapshot.systemSkills.any { it.tool == ToolKind.Grok },
            "expected Grok system skills",
        )
        assertTrue(snapshot.projects.isNotEmpty(), "expected projects")
        assertTrue(
            snapshot.projects.any { it.name.contains("Braincup", ignoreCase = true) } ||
                snapshot.projects.any { it.skills.isNotEmpty() },
            "expected at least one known/non-empty project",
        )

        println(
            "tools=${snapshot.tools.map { "${it.kind}=${it.installed}" }} " +
                "skills=${snapshot.systemSkills.size} " +
                "projects=${snapshot.projects.size} " +
                "memories=${snapshot.systemMemories.size}",
        )
        snapshot.projects.take(8).forEach {
            println(
                "  ${it.name}: tools=${it.toolsPresent} " +
                    "skills=${it.skills.size} mem=${it.memories.size} agents=${it.agentFiles.size}",
            )
        }
    }
}
