package com.inspiredandroid.orcaeye.data

import com.inspiredandroid.orcaeye.model.AppSnapshot
import com.inspiredandroid.orcaeye.model.ToolKind

interface InventoryRepository {
    suspend fun loadSnapshot(): AppSnapshot

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
