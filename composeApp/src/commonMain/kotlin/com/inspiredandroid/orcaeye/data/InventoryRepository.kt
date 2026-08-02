package com.inspiredandroid.orcaeye.data

import com.inspiredandroid.orcaeye.model.AppSnapshot
import com.inspiredandroid.orcaeye.model.MemoryItem
import com.inspiredandroid.orcaeye.model.ProjectInventory
import com.inspiredandroid.orcaeye.model.ToolKind

/** Result of the fast project-path discovery pass (details not fully scanned). */
data class DiscoveredProjects(
    val projects: List<ProjectInventory>,
    val unlinkedMemories: List<MemoryItem> = emptyList(),
    val warnings: List<String> = emptyList(),
)

interface InventoryRepository {
    /**
     * Full inventory: system + every project fully scanned.
     * Prefer [loadSystemSnapshot] + [discoverProjects] + [loadProject] for interactive startup.
     */
    suspend fun loadSnapshot(): AppSnapshot

    /** Tools and system skills/memories/config only (no project walk). Fast first paint. */
    suspend fun loadSystemSnapshot(): AppSnapshot

    /**
     * Discover project paths with marker-level info only.
     * Each [ProjectInventory.detailsLoaded] is false until [loadProject] runs.
     */
    suspend fun discoverProjects(): DiscoveredProjects

    /** Full scan of one project (skills, agents, memories). Null if the path is gone. */
    suspend fun loadProject(path: String): ProjectInventory?

    suspend fun readFile(path: String): String

    suspend fun writeFile(
        path: String,
        content: String,
    )

    /**
     * Deletes a file, or a skill directory (when [path] is a skill folder or SKILL.md inside it).
     */
    suspend fun deleteEntry(path: String)

    suspend fun createSkill(
        name: String,
        tool: ToolKind,
        projectPath: String?,
        description: String,
    ): String

    suspend fun createMemory(
        name: String,
        tool: ToolKind,
        projectPath: String?,
        content: String,
    ): String

    /**
     * Opens [tool] in a new terminal window.
     * When [workingDirectory] is set (e.g. a project path), the shell cds there first.
     */
    suspend fun launchTool(
        tool: ToolKind,
        workingDirectory: String? = null,
    )
}
