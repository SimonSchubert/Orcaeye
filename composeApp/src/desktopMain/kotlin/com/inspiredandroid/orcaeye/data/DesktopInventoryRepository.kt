package com.inspiredandroid.orcaeye.data

import com.inspiredandroid.orcaeye.model.AgentFileItem
import com.inspiredandroid.orcaeye.model.AppSnapshot
import com.inspiredandroid.orcaeye.model.MemoryItem
import com.inspiredandroid.orcaeye.model.ProjectInventory
import com.inspiredandroid.orcaeye.model.RuleItem
import com.inspiredandroid.orcaeye.model.SkillItem
import com.inspiredandroid.orcaeye.model.SkillOrigin
import com.inspiredandroid.orcaeye.model.ToolInstall
import com.inspiredandroid.orcaeye.model.ToolKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.isSymbolicLink
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.nameWithoutExtension
import kotlin.io.path.readText

class DesktopInventoryRepository(
    private val home: Path = Path.of(System.getProperty("user.home")),
    private val toolDetector: ToolDetector = ToolDetector(home),
) : InventoryRepository {
    private val warnings = mutableListOf<String>()

    override suspend fun loadSnapshot(): AppSnapshot = withContext(Dispatchers.IO) {
        warnings.clear()
        val system = buildSystemSnapshot(includeVersions = true)
        val projects = discoverAndScanProjects(fullDetails = true)
        val linkedProjectPaths = projects.map { it.path }.toSet()
        val unlinked =
            collectGrokProjectMemories()
                .filter { it.projectPath == null || it.projectPath !in linkedProjectPaths }

        system.copy(
            projects = projects.sortedBy { it.name.lowercase() },
            unlinkedMemories = unlinked.sortedBy { it.title.lowercase() },
            warnings = (system.warnings + warnings).distinct(),
        )
    }

    override suspend fun loadSystemSnapshot(): AppSnapshot = withContext(Dispatchers.IO) {
        warnings.clear()
        buildSystemSnapshot(includeVersions = false)
    }

    override suspend fun discoverProjects(): DiscoveredProjects = withContext(Dispatchers.IO) {
        warnings.clear()
        val projects = discoverAndScanProjects(fullDetails = false)
        val linkedProjectPaths = projects.map { it.path }.toSet()
        val unlinked =
            collectGrokProjectMemories()
                .filter { it.projectPath == null || it.projectPath !in linkedProjectPaths }
                .sortedBy { it.title.lowercase() }
        DiscoveredProjects(
            projects = projects.sortedBy { it.name.lowercase() },
            unlinkedMemories = unlinked,
            warnings = warnings.toList(),
        )
    }

    override suspend fun loadProject(path: String): ProjectInventory? = withContext(Dispatchers.IO) {
        val normalized = normalizeExistingDir(path) ?: return@withContext null
        val claudeMemoryByProject = indexClaudeMemoriesByProject()
        val grokMemories = collectGrokProjectMemories()
        scanProject(
            projectPath = Path.of(normalized),
            claudeMemoryByProject = claudeMemoryByProject,
            grokMemories = grokMemories,
            fullDetails = true,
        )
    }

    private fun buildSystemSnapshot(includeVersions: Boolean): AppSnapshot {
        val tools = toolDetector.detectTools(includeVersions = includeVersions)
        val systemSkills = scanSystemSkills(tools)
        val systemMemories = scanSystemMemories(tools)
        val systemAgentFiles = scanSystemAgentFiles(tools)
        val systemRules = scanSystemRules(tools)
        return AppSnapshot(
            tools = tools,
            systemSkills = systemSkills.sortedWith(skillDisplayOrder),
            systemMemories = systemMemories.sortedBy { it.title.lowercase() },
            systemAgentFiles = systemAgentFiles.sortedBy { it.name.lowercase() },
            systemRules = systemRules.sortedBy { it.name.lowercase() },
            projects = emptyList(),
            unlinkedMemories = emptyList(),
            warnings = warnings.toList(),
        )
    }

    override suspend fun readFile(path: String): String = withContext(Dispatchers.IO) {
        val p = Path.of(path)
        if (!p.exists() || !p.isRegularFile()) {
            return@withContext "(file not found: $path)"
        }
        try {
            val bytes = Files.size(p)
            if (bytes > MAX_PREVIEW_BYTES) {
                p.readText().take(MAX_PREVIEW_CHARS) +
                    "\n\n… truncated ($bytes bytes total)"
            } else {
                p.readText()
            }
        } catch (e: Exception) {
            "(failed to read: ${e.message})"
        }
    }

    override suspend fun writeFile(
        path: String,
        content: String,
    ) = withContext(Dispatchers.IO) {
        val p = Path.of(path)
        p.parent?.let { Files.createDirectories(it) }
        Files.writeString(p, content)
        Unit
    }

    override suspend fun deleteEntry(path: String) = withContext(Dispatchers.IO) {
        val p = Path.of(path)
        if (!p.exists()) return@withContext
        if (p.isDirectory()) {
            p.toFile().deleteRecursively()
            return@withContext
        }
        // SKILL.md → delete parent skill folder when it only holds the skill
        if (p.name.equals("SKILL.md", ignoreCase = true) || p.name.equals("skill.md", ignoreCase = true)) {
            val parent = p.parent
            if (parent != null && parent.isDirectory()) {
                parent.toFile().deleteRecursively()
                return@withContext
            }
        }
        Files.deleteIfExists(p)
        Unit
    }

    override suspend fun createSkill(
        name: String,
        tool: ToolKind,
        projectPath: String?,
        description: String,
    ): String = withContext(Dispatchers.IO) {
        val slug = sanitizeName(name)
        require(slug.isNotBlank()) { "Skill name is empty" }
        val skillsRoot = skillsDirFor(tool, projectPath)
        Files.createDirectories(skillsRoot)
        val skillDir = skillsRoot.resolve(slug)
        if (skillDir.exists()) {
            error("Skill already exists: ${skillDir.absolutePathString()}")
        }
        Files.createDirectories(skillDir)
        val skillMd = skillDir.resolve("SKILL.md")
        val body =
            buildString {
                appendLine("---")
                appendLine("name: $slug")
                appendLine("description: ${description.ifBlank { slug }}")
                appendLine("---")
                appendLine()
                appendLine("# $name")
                appendLine()
                appendLine(description.ifBlank { "Describe what this skill does and when to use it." })
                appendLine()
            }
        Files.writeString(skillMd, body)
        skillMd.absolutePathString()
    }

    override suspend fun createMemory(
        name: String,
        tool: ToolKind,
        projectPath: String?,
        content: String,
    ): String = withContext(Dispatchers.IO) {
        val slug = sanitizeName(name)
        require(slug.isNotBlank()) { "Memory name is empty" }
        val target = memoryFileFor(tool, projectPath, slug)
        if (target.exists()) {
            error("Memory already exists: ${target.absolutePathString()}")
        }
        Files.createDirectories(target.parent)
        val body =
            content.ifBlank {
                buildString {
                    appendLine("---")
                    appendLine("name: $slug")
                    appendLine("description: $name")
                    appendLine("---")
                    appendLine()
                    appendLine("# $name")
                    appendLine()
                }
            }
        Files.writeString(target, body)
        target.absolutePathString()
    }

    override suspend fun createRule(
        name: String,
        tool: ToolKind,
        projectPath: String?,
        description: String,
    ): String = withContext(Dispatchers.IO) {
        val slug = sanitizeName(name)
        require(slug.isNotBlank()) { "Rule name is empty" }
        require(tool.supportsRules) { "${tool.displayName} does not read a rules directory" }
        val rulesRoot = rulesRootsFor(tool, projectPath).first()
        // Cursor reads .mdc; everyone else takes plain markdown.
        val extension = if (tool == ToolKind.Cursor) "mdc" else "md"
        val target = rulesRoot.resolve("$slug.$extension")
        if (target.exists()) {
            error("Rule already exists: ${target.absolutePathString()}")
        }
        Files.createDirectories(rulesRoot)
        val body =
            buildString {
                appendLine("---")
                appendLine("description: ${description.ifBlank { slug }}")
                if (tool == ToolKind.Cursor) {
                    // Empty globs + alwaysApply keeps the rule unconditional, matching
                    // how Claude/Grok treat a rule with no `paths`.
                    appendLine("globs:")
                    appendLine("alwaysApply: true")
                }
                appendLine("---")
                appendLine()
                appendLine("# $name")
                appendLine()
                appendLine(
                    description.ifBlank {
                        "Instructions the agent should follow in every session."
                    },
                )
                appendLine()
            }
        Files.writeString(target, body)
        target.absolutePathString()
    }

    override suspend fun launchTool(
        tool: ToolKind,
        workingDirectory: String?,
    ) = withContext(Dispatchers.IO) {
        val binary = toolDetector.binaryFor(tool)
        val cwd =
            workingDirectory
                ?.takeIf { it.isNotBlank() }
                ?.let { Path.of(it) }
                ?.takeIf { it.exists() && it.isDirectory() }
                ?.absolutePathString()
        TerminalLauncher.openInTerminal(
            command = TerminalLauncher.shellQuote(binary),
            workingDirectory = cwd,
        )
    }

    private fun skillsDirFor(
        tool: ToolKind,
        projectPath: String?,
    ): Path {
        if (projectPath != null) {
            val project = Path.of(projectPath)
            val dir =
                when (tool) {
                    ToolKind.Claude -> project.resolve(".claude/skills")
                    ToolKind.Grok -> project.resolve(".grok/skills")
                    ToolKind.OpenCode -> project.resolve(".opencode/skills")
                    ToolKind.Codex -> project.resolve(".agents/skills")
                    ToolKind.Cursor -> project.resolve(".cursor/skills")
                    ToolKind.Gemini -> project.resolve(".gemini/skills")
                }
            return dir
        }
        return when (tool) {
            ToolKind.Claude -> home.resolve(".claude/skills")
            ToolKind.Grok -> home.resolve(".grok/skills")
            ToolKind.OpenCode -> home.resolve(".opencode/skills")
            ToolKind.Codex -> home.resolve(".codex/skills")
            ToolKind.Cursor -> home.resolve(".cursor/skills")
            ToolKind.Gemini -> home.resolve(".gemini/skills")
        }
    }

    /**
     * Directories a tool loads always-on instruction files from, most canonical first
     * (the first entry is where [createRule] writes).
     *
     * Grok additionally reads `.claude/rules` and `.cursor/rules` for compatibility, and
     * Claude reads `.claude/rules` only — listing a file under the tool that owns the
     * directory keeps each rule in exactly one place.
     */
    private fun rulesRootsFor(
        tool: ToolKind,
        projectPath: String?,
    ): List<Path> {
        val base = projectPath?.let { Path.of(it) } ?: home
        return when (tool) {
            ToolKind.Claude -> listOf(base.resolve(".claude/rules"))
            ToolKind.Grok -> listOf(base.resolve(".grok/rules"))
            ToolKind.Cursor -> listOf(base.resolve(".cursor/rules"))
            ToolKind.Gemini ->
                if (projectPath != null) {
                    // Antigravity workspace rules live in .agent/rules.
                    listOf(base.resolve(".agent/rules"), base.resolve(".gemini/rules"))
                } else {
                    listOf(base.resolve(".gemini/rules"), base.resolve(".gemini/antigravity/rules"))
                }
            // AGENTS.md-only tools; already surfaced as agent files.
            ToolKind.OpenCode, ToolKind.Codex -> emptyList()
        }
    }

    private fun memoryFileFor(
        tool: ToolKind,
        projectPath: String?,
        slug: String,
    ): Path {
        if (projectPath == null) {
            return when (tool) {
                ToolKind.Grok -> home.resolve(".grok/memory").resolve("$slug.md")
                ToolKind.Claude -> home.resolve(".claude/memory").resolve("$slug.md")
                ToolKind.OpenCode -> home.resolve(".opencode/memory").resolve("$slug.md")
                ToolKind.Codex -> home.resolve(".codex/memory").resolve("$slug.md")
                ToolKind.Cursor -> home.resolve(".cursor/memory").resolve("$slug.md")
                ToolKind.Gemini -> home.resolve(".gemini/memory").resolve("$slug.md")
            }
        }
        val project = Path.of(projectPath)
        return when (tool) {
            ToolKind.Claude -> {
                val encoded = project.absolutePathString().replace("/", "-")
                home.resolve(".claude/projects").resolve(encoded).resolve("memory").resolve("$slug.md")
            }
            ToolKind.Grok -> {
                val base = project.name.lowercase().replace(Regex("[^a-z0-9-]+"), "-")
                home.resolve(".grok/memory").resolve(base).resolve("$slug.md")
            }
            ToolKind.OpenCode -> {
                project.resolve(".opencode/memory").resolve("$slug.md")
            }
            ToolKind.Codex -> {
                project.resolve(".agents/memory").resolve("$slug.md")
            }
            ToolKind.Cursor -> {
                project.resolve(".cursor/memory").resolve("$slug.md")
            }
            ToolKind.Gemini -> {
                project.resolve(".gemini/memory").resolve("$slug.md")
            }
        }
    }

    private fun sanitizeName(name: String): String = name
        .trim()
        .lowercase()
        .replace(Regex("\\s+"), "-")
        .replace(Regex("[^a-z0-9._-]+"), "")
        .trim('-', '.', '_')

    private fun scanSystemSkills(tools: List<ToolInstall>): List<SkillItem> {
        val result = mutableListOf<SkillItem>()
        if (tools.any { it.kind == ToolKind.Claude && it.installed }) {
            result += listSkillsIn(home.resolve(".claude/skills"), ToolKind.Claude, SkillOrigin.User)
        }
        if (tools.any { it.kind == ToolKind.Grok && it.installed }) {
            val user = listSkillsIn(home.resolve(".grok/skills"), ToolKind.Grok, SkillOrigin.User)
            val bundled = listSkillsIn(home.resolve(".grok/bundled/skills"), ToolKind.Grok, SkillOrigin.Bundled)
            // Match Grok: a same-named user skill overrides the bundled copy.
            val userNames = user.map { it.name.lowercase() }.toSet()
            result += user
            result += bundled.filter { it.name.lowercase() !in userNames }
        }
        if (tools.any { it.kind == ToolKind.OpenCode && it.installed }) {
            val openCode =
                listSkillsIn(home.resolve(".opencode/skills"), ToolKind.OpenCode, SkillOrigin.User) +
                    listSkillsIn(home.resolve(".config/opencode/skills"), ToolKind.OpenCode, SkillOrigin.User)
            // Two roots can both hold user skills; keep first occurrence of each name.
            result += openCode.distinctBy { it.name.lowercase() }
        }
        if (tools.any { it.kind == ToolKind.Codex && it.installed }) {
            result += listSkillsIn(home.resolve(".codex/skills"), ToolKind.Codex, SkillOrigin.User)
        }
        if (tools.any { it.kind == ToolKind.Cursor && it.installed }) {
            val user = listSkillsIn(home.resolve(".cursor/skills"), ToolKind.Cursor, SkillOrigin.User)
            val bundled = listSkillsIn(home.resolve(".cursor/skills-cursor"), ToolKind.Cursor, SkillOrigin.Bundled)
            val userNames = user.map { it.name.lowercase() }.toSet()
            result += user
            result += bundled.filter { it.name.lowercase() !in userNames }
        }
        if (tools.any { it.kind == ToolKind.Gemini && it.installed }) {
            val gemini =
                listSkillsIn(home.resolve(".gemini/skills"), ToolKind.Gemini, SkillOrigin.User) +
                    listSkillsIn(home.resolve(".gemini/antigravity/skills"), ToolKind.Gemini, SkillOrigin.User)
            result += gemini.distinctBy { it.name.lowercase() }
        }
        return result
    }

    private fun scanSystemMemories(tools: List<ToolInstall>): List<MemoryItem> {
        val result = mutableListOf<MemoryItem>()
        fun addMdFiles(
            dir: Path,
            tool: ToolKind,
        ) {
            if (!dir.exists() || !dir.isDirectory()) return
            try {
                dir.listDirectoryEntries().forEach { entry ->
                    if (entry.isRegularFile() && entry.name.endsWith(".md")) {
                        result +=
                            MemoryItem(
                                title =
                                if (entry.name.equals("MEMORY.md", ignoreCase = true)) {
                                    "Global MEMORY"
                                } else {
                                    entry.nameWithoutExtension
                                },
                                path = entry.absolutePathString(),
                                tool = tool,
                                projectPath = null,
                            )
                    }
                }
            } catch (_: Exception) {
                // skip
            }
        }
        if (tools.any { it.kind == ToolKind.Grok && it.installed }) {
            addMdFiles(home.resolve(".grok/memory"), ToolKind.Grok)
        }
        if (tools.any { it.kind == ToolKind.Claude && it.installed }) {
            addMdFiles(home.resolve(".claude/memory"), ToolKind.Claude)
        }
        if (tools.any { it.kind == ToolKind.OpenCode && it.installed }) {
            addMdFiles(home.resolve(".opencode/memory"), ToolKind.OpenCode)
        }
        if (tools.any { it.kind == ToolKind.Codex && it.installed }) {
            addMdFiles(home.resolve(".codex/memory"), ToolKind.Codex)
        }
        if (tools.any { it.kind == ToolKind.Cursor && it.installed }) {
            addMdFiles(home.resolve(".cursor/memory"), ToolKind.Cursor)
        }
        if (tools.any { it.kind == ToolKind.Gemini && it.installed }) {
            addMdFiles(home.resolve(".gemini/memory"), ToolKind.Gemini)
        }
        return result
    }

    private fun scanSystemRules(tools: List<ToolInstall>): List<RuleItem> = ToolKind.entries
        .filter { kind ->
            kind.supportsRules && tools.any { it.kind == kind && it.installed }
        }.flatMap { kind ->
            rulesRootsFor(kind, projectPath = null).flatMap { listRulesIn(it, kind) }
        }.distinctBy { it.path }

    private fun scanSystemAgentFiles(tools: List<ToolInstall>): List<AgentFileItem> {
        val result = mutableListOf<AgentFileItem>()
        if (tools.any { it.kind == ToolKind.Claude && it.installed }) {
            val settings = home.resolve(".claude/settings.json")
            if (settings.exists()) {
                result +=
                    AgentFileItem(
                        name = "settings.json",
                        path = settings.absolutePathString(),
                        tool = ToolKind.Claude,
                    )
            }
        }
        if (tools.any { it.kind == ToolKind.Grok && it.installed }) {
            val config = home.resolve(".grok/config.toml")
            if (config.exists()) {
                result +=
                    AgentFileItem(
                        name = "config.toml",
                        path = config.absolutePathString(),
                        tool = ToolKind.Grok,
                    )
            }
            listMarkdownIn(home.resolve(".grok/bundled/agents")).forEach { path ->
                result +=
                    AgentFileItem(
                        name = "agent: ${path.nameWithoutExtension}",
                        path = path.absolutePathString(),
                        tool = ToolKind.Grok,
                    )
            }
        }
        if (tools.any { it.kind == ToolKind.OpenCode && it.installed }) {
            listOf(
                home.resolve(".config/opencode/opencode.json"),
                home.resolve(".config/opencode/opencode.jsonc"),
            ).firstOrNull { it.exists() }?.let { cfg ->
                result +=
                    AgentFileItem(
                        name = cfg.name,
                        path = cfg.absolutePathString(),
                        tool = ToolKind.OpenCode,
                    )
            }
        }
        if (tools.any { it.kind == ToolKind.Codex && it.installed }) {
            listOf(
                home.resolve(".codex/config.toml"),
                home.resolve(".codex/config.json"),
            ).firstOrNull { it.exists() }?.let { cfg ->
                result +=
                    AgentFileItem(
                        name = cfg.name,
                        path = cfg.absolutePathString(),
                        tool = ToolKind.Codex,
                    )
            }
        }
        if (tools.any { it.kind == ToolKind.Cursor && it.installed }) {
            listOf(
                home.resolve(".cursor/cli-config.json"),
                home.resolve(".cursor/argv.json"),
            ).firstOrNull { it.exists() }?.let { cfg ->
                result +=
                    AgentFileItem(
                        name = cfg.name,
                        path = cfg.absolutePathString(),
                        tool = ToolKind.Cursor,
                    )
            }
        }
        if (tools.any { it.kind == ToolKind.Gemini && it.installed }) {
            listOf(
                home.resolve(".gemini/settings.json"),
                home.resolve(".gemini/config.json"),
            ).firstOrNull { it.exists() }?.let { cfg ->
                result +=
                    AgentFileItem(
                        name = cfg.name,
                        path = cfg.absolutePathString(),
                        tool = ToolKind.Gemini,
                    )
            }
        }
        return result
    }

    /**
     * @param fullDetails when true, list skills and attach Claude/Grok memories (slow).
     * When false, only markers + agent file presence (fast sidebar stubs).
     */
    private fun discoverAndScanProjects(fullDetails: Boolean): List<ProjectInventory> {
        val fromClaude = discoverFromClaudeJson() + discoverFromClaudeProjectsDirs()
        val fromGrok = discoverFromGrokTrusted() + discoverFromGrokSessions()
        val fromFolder = discoverFromProjectsFolder()

        val paths = LinkedHashSet<String>()
        paths.addAll(fromClaude)
        paths.addAll(fromGrok)
        paths.addAll(fromFolder)

        val claudeNormalized =
            fromClaude.mapNotNull { normalizeExistingDir(it) }.toCollection(LinkedHashSet())
        val grokNormalized =
            fromGrok.mapNotNull { normalizeExistingDir(it) }.toCollection(LinkedHashSet())

        // Normalize so /Users/... and realpath/symlink variants collapse to one project.
        val normalized =
            paths
                .mapNotNull { pathStr -> normalizeExistingDir(pathStr) }
                .toCollection(LinkedHashSet())

        // Memories live outside the project dir, so both passes need them: they are what
        // separates a registry-only project from a directory an agent was merely started in
        // once (a GUI launch or a cron line without `cd` registers `/` that way).
        val claudeMemoryByProject = indexClaudeMemoriesByProject()
        val grokMemories = collectGrokProjectMemories()
        val pathsWithMemories =
            claudeMemoryByProject.keys + grokMemories.mapNotNull { it.projectPath }

        val projects =
            normalized.mapNotNull { pathStr ->
                val projectPath = Path.of(pathStr)
                val inv =
                    scanProject(
                        projectPath = projectPath,
                        claudeMemoryByProject = claudeMemoryByProject,
                        grokMemories = grokMemories,
                        fullDetails = fullDetails,
                    )
                val hasContent =
                    inv.toolsPresent.isNotEmpty() ||
                        inv.skills.isNotEmpty() ||
                        inv.agentFiles.isNotEmpty() ||
                        inv.memories.isNotEmpty() ||
                        pathStr in pathsWithMemories
                if (!hasContent) return@mapNotNull null
                // The light pass skips memory attachment, so registry membership is all the
                // sidebar has to show a tool chip with.
                val fromRegistry =
                    pathStr in claudeNormalized || pathStr in grokNormalized
                if (!fullDetails && fromRegistry) {
                    val tools =
                        inv.toolsPresent.toMutableSet().apply {
                            if (pathStr in claudeNormalized) add(ToolKind.Claude)
                            if (pathStr in grokNormalized) add(ToolKind.Grok)
                        }
                    inv.copy(toolsPresent = tools)
                } else {
                    inv
                }
            }
        return projects
    }

    private fun normalizeExistingDir(pathStr: String): String? {
        val path = Path.of(pathStr)
        if (!path.exists() || !path.isDirectory()) return null
        // Skip home and other non-project roots that show up in Claude/Grok registries
        val abs = path.toAbsolutePath().normalize()
        // Filesystem root ("/" or "C:\"): registered whenever a CLI runs with no real cwd,
        // and its Path has no file name, which would render as a nameless sidebar row.
        if (abs.parent == null) return null
        if (abs == home.normalize() || abs == home.toAbsolutePath().normalize()) return null
        val isScanRoot =
            PROJECT_SCAN_ROOTS.any { abs.name.equals(it, ignoreCase = true) } &&
                abs.parent == home.toAbsolutePath().normalize()
        if (isScanRoot) return null
        return try {
            abs.toRealPath().absolutePathString()
        } catch (_: Exception) {
            abs.absolutePathString()
        }
    }

    private fun scanProject(
        projectPath: Path,
        claudeMemoryByProject: Map<String, List<MemoryItem>>,
        grokMemories: List<MemoryItem>,
        fullDetails: Boolean,
    ): ProjectInventory {
        val abs =
            try {
                projectPath.toRealPath().absolutePathString()
            } catch (_: Exception) {
                projectPath.toAbsolutePath().normalize().absolutePathString()
            }
        val toolsPresent = mutableSetOf<ToolKind>()
        val skills = mutableListOf<SkillItem>()
        val agentFiles = mutableListOf<AgentFileItem>()
        val memories = mutableListOf<MemoryItem>()
        val rules = mutableListOf<RuleItem>()

        val claudeDir = projectPath.resolve(".claude")
        if (claudeDir.exists()) {
            toolsPresent += ToolKind.Claude
            if (fullDetails) {
                skills += listSkillsIn(claudeDir.resolve("skills"), ToolKind.Claude, SkillOrigin.Project)
            }
        }
        val grokDir = projectPath.resolve(".grok")
        if (grokDir.exists()) {
            toolsPresent += ToolKind.Grok
            if (fullDetails) {
                skills += listSkillsIn(grokDir.resolve("skills"), ToolKind.Grok, SkillOrigin.Project)
            }
        }
        val opencodeDir = projectPath.resolve(".opencode")
        if (opencodeDir.exists()) {
            toolsPresent += ToolKind.OpenCode
            if (fullDetails) {
                skills += listSkillsIn(opencodeDir.resolve("skills"), ToolKind.OpenCode, SkillOrigin.Project)
            }
        }
        val codexDir = projectPath.resolve(".codex")
        val agentsDir = projectPath.resolve(".agents")
        if (codexDir.exists() || agentsDir.exists()) {
            toolsPresent += ToolKind.Codex
            if (fullDetails) {
                skills += listSkillsIn(agentsDir.resolve("skills"), ToolKind.Codex, SkillOrigin.Project)
                skills += listSkillsIn(codexDir.resolve("skills"), ToolKind.Codex, SkillOrigin.Project)
            }
        }
        val cursorDir = projectPath.resolve(".cursor")
        if (cursorDir.exists()) {
            toolsPresent += ToolKind.Cursor
            if (fullDetails) {
                skills += listSkillsIn(cursorDir.resolve("skills"), ToolKind.Cursor, SkillOrigin.Project)
            }
        }
        val geminiDir = projectPath.resolve(".gemini")
        val agentDir = projectPath.resolve(".agent")
        if (geminiDir.exists() || agentDir.exists()) {
            toolsPresent += ToolKind.Gemini
            if (fullDetails) {
                skills += listSkillsIn(geminiDir.resolve("skills"), ToolKind.Gemini, SkillOrigin.Project)
                // Antigravity project skills
                skills += listSkillsIn(agentDir.resolve("skills"), ToolKind.Gemini, SkillOrigin.Project)
            }
        }

        if (fullDetails) {
            // Rules dirs sit inside the tool folders above, plus Antigravity's .agent/rules.
            ToolKind.entries.filter { it.supportsRules }.forEach { kind ->
                val found =
                    rulesRootsFor(kind, abs)
                        .flatMap { listRulesIn(it, kind) }
                        .distinctBy { it.path }
                if (found.isNotEmpty()) {
                    rules += found
                    toolsPresent += kind
                }
            }
        }

        // Agent root files are cheap existence checks; include even on light scan.
        listOf(
            "CLAUDE.md" to ToolKind.Claude,
            "AGENTS.md" to ToolKind.Grok,
            "opencode.json" to ToolKind.OpenCode,
            ".opencode.json" to ToolKind.OpenCode,
            "GEMINI.md" to ToolKind.Gemini,
            // Cursor's pre-.cursor/rules format: a single root file, not a rules dir.
            ".cursorrules" to ToolKind.Cursor,
        ).forEach { (fileName, tool) ->
            val f = projectPath.resolve(fileName)
            if (f.exists() && f.isRegularFile()) {
                toolsPresent += tool
                agentFiles +=
                    AgentFileItem(
                        name = fileName,
                        path = f.absolutePathString(),
                        tool = tool,
                    )
            }
        }

        if (fullDetails) {
            // Match Claude memories by real path or by encode of any known alias
            claudeMemoryByProject[abs]?.let { memories += it }
            claudeMemoryByProject.forEach { (key, items) ->
                if (key != abs && normalizeExistingDir(key) == abs) {
                    items.forEach { item ->
                        if (memories.none { it.path == item.path }) {
                            memories += item.copy(projectPath = abs)
                        }
                    }
                }
            }

            // Grok: match by project path, or name heuristic on slug
            val projectName = projectPath.name
            val nameKey = projectName.lowercase()
            grokMemories
                .filter { mem ->
                    when {
                        mem.projectPath == abs -> true
                        mem.projectPath != null -> false
                        nameKey.isEmpty() -> false // an empty name prefix would match every memory
                        else -> {
                            val title = mem.title.lowercase()
                            val compact = nameKey.replace(" ", "").replace("_", "")
                            title.startsWith(nameKey) ||
                                title.startsWith(compact) ||
                                title.startsWith(nameKey.replace("-", ""))
                        }
                    }
                }.forEach { mem ->
                    if (memories.none { it.path == mem.path }) {
                        memories += mem.copy(projectPath = abs)
                        toolsPresent += ToolKind.Grok
                    }
                }

            if (memories.any { it.tool == ToolKind.Claude }) {
                toolsPresent += ToolKind.Claude
            }
        }

        return ProjectInventory(
            path = abs,
            name = projectPath.name,
            toolsPresent = toolsPresent,
            agentFiles = agentFiles.sortedBy { it.name.lowercase() },
            skills = skills.sortedBy { it.name.lowercase() },
            memories = memories.sortedBy { it.title.lowercase() },
            rules = rules.sortedBy { it.name.lowercase() },
            detailsLoaded = fullDetails,
        )
    }

    private fun discoverFromClaudeJson(): List<String> {
        val jsonPath = home.resolve(".claude.json")
        if (!jsonPath.exists()) return emptyList()
        return try {
            val text = jsonPath.readText()
            // Lightweight extract of projects object keys: "projects": { "/path": ...
            val projectsIdx = text.indexOf("\"projects\"")
            if (projectsIdx < 0) return emptyList()
            val braceStart = text.indexOf('{', projectsIdx)
            if (braceStart < 0) return emptyList()
            extractJsonObjectKeys(text, braceStart)
                .filter { it.startsWith("/") }
        } catch (e: Exception) {
            warnings += "Failed reading ~/.claude.json: ${e.message}"
            emptyList()
        }
    }

    private fun extractJsonObjectKeys(
        text: String,
        openBrace: Int,
    ): List<String> {
        val keys = mutableListOf<String>()
        var i = openBrace + 1
        var depth = 1
        while (i < text.length && depth > 0) {
            when (text[i]) {
                '{' -> depth++
                '}' -> depth--
                '"' -> {
                    if (depth == 1) {
                        val end = findStringEnd(text, i + 1) ?: break
                        val key = unescapeJson(text.substring(i + 1, end))
                        // next non-ws should be :
                        var j = end + 1
                        while (j < text.length && text[j].isWhitespace()) j++
                        if (j < text.length && text[j] == ':') {
                            keys += key
                        }
                        i = end
                    }
                }
            }
            i++
        }
        return keys
    }

    private fun findStringEnd(
        text: String,
        from: Int,
    ): Int? {
        var i = from
        while (i < text.length) {
            when (text[i]) {
                '\\' -> i += 2
                '"' -> return i
                else -> i++
            }
        }
        return null
    }

    private fun unescapeJson(s: String): String = s
        .replace("\\/", "/")
        .replace("\\\"", "\"")
        .replace("\\\\", "\\")
        .replace("\\n", "\n")

    private fun discoverFromClaudeProjectsDirs(): List<String> {
        val projectsDir = home.resolve(".claude/projects")
        if (!projectsDir.exists()) return emptyList()
        // Prefer mapping via claude.json; only decode when path exists after encode reverse
        val fromJson = discoverFromClaudeJson().toSet()
        return try {
            projectsDir
                .listDirectoryEntries()
                .filter { it.isDirectory() }
                .mapNotNull { dir ->
                    val decoded = decodeClaudeProjectDir(dir.name)
                    when {
                        decoded != null && Path.of(decoded).exists() -> decoded
                        else -> {
                            // match against known json paths by encode
                            fromJson.firstOrNull { encodeClaudeProjectDir(it) == dir.name }
                        }
                    }
                }
        } catch (e: Exception) {
            warnings += "Failed scanning ~/.claude/projects: ${e.message}"
            emptyList()
        }
    }

    private fun encodeClaudeProjectDir(absolutePath: String): String = absolutePath.replace("/", "-")

    private fun decodeClaudeProjectDir(encoded: String): String? {
        // Claude encoding: every / becomes -, including the leading one → "-Users-..."
        if (!encoded.startsWith("-")) return null
        // Ambiguous for hyphenated folder names; only accept if decoded path exists
        val candidate = encoded.replace("-", "/")
        return candidate.takeIf { Path.of(it).exists() }
    }

    private fun discoverFromGrokTrusted(): List<String> {
        val toml = home.resolve(".grok/trusted_folders.toml")
        if (!toml.exists()) return emptyList()
        return try {
            toml
                .readText()
                .lineSequence()
                .mapNotNull { line ->
                    val m = Regex("""^\[folders\."([^"]+)"\]""").find(line.trim())
                    m?.groupValues?.get(1)
                }.toList()
        } catch (e: Exception) {
            warnings += "Failed reading trusted_folders.toml: ${e.message}"
            emptyList()
        }
    }

    private fun discoverFromGrokSessions(): List<String> {
        val sessions = home.resolve(".grok/sessions")
        if (!sessions.exists()) return emptyList()
        return try {
            sessions
                .listDirectoryEntries()
                .filter { it.isDirectory() }
                .mapNotNull { dir ->
                    if (dir.name == "session_search.sqlite") return@mapNotNull null
                    try {
                        URLDecoder.decode(dir.name, StandardCharsets.UTF_8)
                    } catch (_: Exception) {
                        null
                    }
                }.filter { it.startsWith("/") && Path.of(it).exists() && Path.of(it).isDirectory() }
        } catch (e: Exception) {
            warnings += "Failed scanning grok sessions: ${e.message}"
            emptyList()
        }
    }

    private fun discoverFromProjectsFolder(): List<String> {
        val roots =
            PROJECT_SCAN_ROOTS
                .flatMap { listOf(home.resolve(it), home.resolve(it.lowercase())) }
                .distinct()
        val found = mutableListOf<String>()
        for (root in roots) {
            if (!root.exists() || !root.isDirectory()) continue
            try {
                root.listDirectoryEntries().forEach { child ->
                    if (child.isDirectory() && hasProjectMarkers(child)) {
                        found += child.absolutePathString()
                    }
                }
            } catch (e: Exception) {
                warnings += "Failed scanning ${root.name}: ${e.message}"
            }
        }
        return found
    }

    private fun hasProjectMarkers(dir: Path): Boolean {
        val markers =
            listOf(
                ".claude",
                ".grok",
                ".opencode",
                ".codex",
                ".agents",
                ".cursor",
                ".gemini",
                ".agent",
                "AGENTS.md",
                "CLAUDE.md",
                "GEMINI.md",
                "opencode.json",
                ".cursorrules",
            )
        return markers.any { dir.resolve(it).exists() }
    }

    private fun indexClaudeMemoriesByProject(): Map<String, List<MemoryItem>> {
        val projectsDir = home.resolve(".claude/projects")
        if (!projectsDir.exists()) return emptyMap()
        val jsonPaths = discoverFromClaudeJson()
        val encodeToPath = jsonPaths.associateBy { encodeClaudeProjectDir(it) }
        val result = mutableMapOf<String, MutableList<MemoryItem>>()
        try {
            projectsDir.listDirectoryEntries().forEach { projDir ->
                if (!projDir.isDirectory()) return@forEach
                val rawPath =
                    encodeToPath[projDir.name]
                        ?: decodeClaudeProjectDir(projDir.name)
                        ?: return@forEach
                val realPath = normalizeExistingDir(rawPath) ?: rawPath
                val memoryDir = projDir.resolve("memory")
                if (!memoryDir.exists()) return@forEach
                memoryDir
                    .listDirectoryEntries()
                    .filter { it.isRegularFile() && it.name.endsWith(".md") }
                    .forEach { md ->
                        val item =
                            MemoryItem(
                                title = md.nameWithoutExtension,
                                path = md.absolutePathString(),
                                tool = ToolKind.Claude,
                                projectPath = realPath,
                            )
                        result.getOrPut(realPath) { mutableListOf() } += item
                    }
            }
        } catch (e: Exception) {
            warnings += "Failed indexing Claude memories: ${e.message}"
        }
        return result
    }

    private fun collectGrokProjectMemories(): List<MemoryItem> {
        val memoryRoot = home.resolve(".grok/memory")
        if (!memoryRoot.exists()) return emptyList()
        val sessionPathBySlug = guessGrokSlugToProject()
        val items = mutableListOf<MemoryItem>()
        try {
            memoryRoot.listDirectoryEntries().forEach { entry ->
                if (!entry.isDirectory()) return@forEach
                val slug = entry.name
                val memoryMd = entry.resolve("MEMORY.md")
                val projectPath = sessionPathBySlug[slug]
                if (memoryMd.exists() && memoryMd.isRegularFile()) {
                    items +=
                        MemoryItem(
                            title = slug,
                            path = memoryMd.absolutePathString(),
                            tool = ToolKind.Grok,
                            projectPath = projectPath,
                        )
                }
                // session notes under memory/<slug>/sessions/*.md
                val sessionsDir = entry.resolve("sessions")
                if (sessionsDir.exists() && sessionsDir.isDirectory()) {
                    sessionsDir
                        .listDirectoryEntries()
                        .filter { it.isRegularFile() && it.name.endsWith(".md") }
                        .forEach { md ->
                            items +=
                                MemoryItem(
                                    title = "$slug / ${md.nameWithoutExtension}",
                                    path = md.absolutePathString(),
                                    tool = ToolKind.Grok,
                                    projectPath = projectPath,
                                )
                        }
                }
            }
        } catch (e: Exception) {
            warnings += "Failed scanning Grok memories: ${e.message}"
        }
        return items
    }

    /**
     * Best-effort map of grok memory slugs (e.g. braincup-2c75fe14) to project paths
     * using session folder names and project directory name heuristics.
     */
    private fun guessGrokSlugToProject(): Map<String, String> {
        val map = mutableMapOf<String, String>()
        val projects = discoverFromProjectsFolder() + discoverFromGrokTrusted() + discoverFromGrokSessions()
        val byName =
            projects
                .map { Path.of(it) }
                .filter { it.exists() }
                .associateBy { it.name.lowercase() }

        val memoryRoot = home.resolve(".grok/memory")
        if (!memoryRoot.exists()) return emptyMap()
        try {
            memoryRoot.listDirectoryEntries().filter { it.isDirectory() }.forEach { dir ->
                val slug = dir.name.lowercase()
                // strip trailing -hex hash if present
                val base = slug.replace(Regex("-[0-9a-f]{6,}$"), "")
                val match =
                    byName[base]
                        ?: byName.entries
                            .firstOrNull { (name, _) ->
                                base.startsWith(name) ||
                                    name.startsWith(base) ||
                                    slug.startsWith(name.replace(" ", ""))
                            }?.value
                if (match != null) {
                    map[dir.name] = match.absolutePathString()
                }
            }
        } catch (_: Exception) {
            // ignore
        }
        return map
    }

    private fun listSkillsIn(
        skillsDir: Path,
        tool: ToolKind,
        origin: SkillOrigin,
    ): List<SkillItem> {
        if (!skillsDir.exists() || !skillsDir.isDirectory()) return emptyList()
        return try {
            skillsDir
                .listDirectoryEntries()
                .filter { entry ->
                    val name = entry.name
                    !name.startsWith(".") && (entry.isDirectory() || entry.isSymbolicLink())
                }.mapNotNull { entry ->
                    val skillMd =
                        listOf(
                            entry.resolve("SKILL.md"),
                            entry.resolve("skill.md"),
                        ).firstOrNull { it.exists() && it.isRegularFile() }
                            ?: return@mapNotNull null
                    val symlinkTarget =
                        try {
                            if (entry.isSymbolicLink()) {
                                entry.parent.resolve(Files.readSymbolicLink(entry)).normalize().absolutePathString()
                            } else {
                                null
                            }
                        } catch (_: Exception) {
                            null
                        }
                    val meta = parseFrontmatter(skillMd)
                    SkillItem(
                        name = meta.scalar("name") ?: entry.name,
                        path = entry.absolutePathString(),
                        tool = tool,
                        origin = origin,
                        symlinkTarget = symlinkTarget,
                        description = meta.scalar("description"),
                        skillMdPath = skillMd.absolutePathString(),
                    )
                }
        } catch (e: Exception) {
            warnings += "Failed listing skills in $skillsDir: ${e.message}"
            emptyList()
        }
    }

    /**
     * Rule files anywhere under [rulesDir]. Claude and Cursor both discover rules
     * recursively, so a name keeps its subdirectory (`frontend/react`) to stay unique.
     *
     * Symlinked rule directories are a documented sharing pattern, so links are followed
     * with a visited set guarding against cycles.
     */
    private fun listRulesIn(
        rulesDir: Path,
        tool: ToolKind,
    ): List<RuleItem> {
        if (!rulesDir.exists() || !rulesDir.isDirectory()) return emptyList()
        val result = mutableListOf<RuleItem>()
        val visited = mutableSetOf<String>()

        fun walk(
            dir: Path,
            depth: Int,
        ) {
            if (depth > MAX_RULE_DEPTH) return
            val realDir =
                try {
                    dir.toRealPath().absolutePathString()
                } catch (_: Exception) {
                    dir.absolutePathString()
                }
            if (!visited.add(realDir)) return
            val entries =
                try {
                    dir.listDirectoryEntries()
                } catch (e: Exception) {
                    warnings += "Failed listing rules in $dir: ${e.message}"
                    return
                }
            entries.forEach { entry ->
                if (entry.name.startsWith(".")) return@forEach
                when {
                    entry.isDirectory() -> walk(entry, depth + 1)
                    entry.isRegularFile() && entry.name.substringAfterLast('.', "") in RULE_EXTENSIONS -> {
                        val meta = parseFrontmatter(entry)
                        result +=
                            RuleItem(
                                name = rulesDir.relativize(entry).toString().substringBeforeLast('.'),
                                path = entry.absolutePathString(),
                                tool = tool,
                                description = meta.scalar("description"),
                                globs = meta["paths"] ?: meta["globs"] ?: emptyList(),
                            )
                    }
                }
            }
        }

        walk(rulesDir, depth = 0)
        return result
    }

    /**
     * Minimal YAML frontmatter reader: scalars become one-element lists, and both inline
     * (`globs: ["a", "b"]`) and block (`paths:` + `- a`) sequences become multi-element ones.
     */
    private fun parseFrontmatter(file: Path): Map<String, List<String>> {
        return try {
            val lines = file.readText().lineSequence().iterator()
            if (!lines.hasNext()) return emptyMap()
            if (lines.next().trim() != "---") return emptyMap()
            val map = mutableMapOf<String, MutableList<String>>()
            var currentKey: String? = null
            while (lines.hasNext()) {
                val line = lines.next()
                if (line.trim() == "---") break
                val trimmed = line.trim()
                if (trimmed.startsWith("- ") || trimmed == "-") {
                    val key = currentKey ?: continue
                    val value = unquote(trimmed.removePrefix("-").trim())
                    if (value.isNotEmpty()) map.getOrPut(key) { mutableListOf() } += value
                    continue
                }
                val idx = line.indexOf(':')
                if (idx <= 0) continue
                val key = line.substring(0, idx).trim()
                currentKey = key
                val raw = line.substring(idx + 1).trim()
                val values =
                    when {
                        raw.isEmpty() -> emptyList()
                        raw.startsWith("[") ->
                            raw
                                .removePrefix("[")
                                .removeSuffix("]")
                                .split(',')
                                .map { unquote(it.trim()) }
                                .filter { it.isNotEmpty() }
                        else -> listOf(unquote(raw))
                    }
                map.getOrPut(key) { mutableListOf() } += values
            }
            map
        } catch (_: Exception) {
            emptyMap()
        }
    }

    private fun unquote(value: String): String {
        if (value.length >= 2) {
            if (value.startsWith("\"") && value.endsWith("\"")) return value.substring(1, value.length - 1)
            if (value.startsWith("'") && value.endsWith("'")) return value.substring(1, value.length - 1)
        }
        return value
    }

    private fun Map<String, List<String>>.scalar(key: String): String? = this[key]?.firstOrNull()?.takeIf { it.isNotBlank() }

    private fun listMarkdownIn(dir: Path): List<Path> {
        if (!dir.exists() || !dir.isDirectory()) return emptyList()
        return try {
            dir.listDirectoryEntries().filter { it.isRegularFile() && it.name.endsWith(".md") }
        } catch (_: Exception) {
            emptyList()
        }
    }

    companion object {
        private const val MAX_PREVIEW_BYTES = 512_000L
        private const val MAX_PREVIEW_CHARS = 200_000

        /** Folders under home that hold projects; the roots themselves are never projects. */
        private val PROJECT_SCAN_ROOTS = listOf("Projects", "Developer")

        /** `.mdc` is Cursor's rule format; everyone else uses plain markdown. */
        private val RULE_EXTENSIONS = setOf("md", "mdc")

        /** Rules nest a few levels at most (`rules/frontend/react.mdc`); cap runaway symlinks. */
        private const val MAX_RULE_DEPTH = 5

        /** User before bundled before project, then name. */
        private val skillDisplayOrder =
            compareBy<SkillItem> { it.origin.ordinal }
                .thenBy { it.name.lowercase() }
    }
}
