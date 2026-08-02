package com.inspiredandroid.orcaeye.data

import com.inspiredandroid.orcaeye.model.SkillOrigin
import com.inspiredandroid.orcaeye.model.ToolKind
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
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

    @Test
    fun grokSystemSkillsSkipNonSkillsDedupeAndOrderUserFirst() = runBlocking {
        val snapshot = DesktopInventoryRepository().loadSnapshot()
        val grok = snapshot.systemSkills.filter { it.tool == ToolKind.Grok }

        assertTrue(grok.isNotEmpty(), "expected Grok system skills")
        assertTrue(
            grok.all { it.skillMdPath != null },
            "every skill should have a SKILL.md (skip shared / empty dirs)",
        )
        assertTrue(
            grok.none { it.name.equals("shared", ignoreCase = true) },
            "shared helper dir must not appear as a skill",
        )

        val names = grok.map { it.name.lowercase() }
        assertEquals(names.size, names.toSet().size, "user skill should override same-named bundled skill")

        val origins = grok.map { it.origin }
        val firstBundled = origins.indexOfFirst { it == SkillOrigin.Bundled }
        val lastUser = origins.indexOfLast { it == SkillOrigin.User }
        if (firstBundled >= 0 && lastUser >= 0) {
            assertTrue(
                lastUser < firstBundled,
                "all user skills should sort before bundled skills",
            )
        }

        val userNames = grok.filter { it.origin == SkillOrigin.User }.map { it.name.lowercase() }
        assertEquals(userNames, userNames.sorted(), "user skills should be alphabetical")
        val bundledNames = grok.filter { it.origin == SkillOrigin.Bundled }.map { it.name.lowercase() }
        assertEquals(bundledNames, bundledNames.sorted(), "bundled skills should be alphabetical")
    }
}
