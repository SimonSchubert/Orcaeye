package com.inspiredandroid.orcaeye.screenshots

import com.inspiredandroid.orcaeye.model.AgentFileItem
import com.inspiredandroid.orcaeye.model.AppSnapshot
import com.inspiredandroid.orcaeye.model.CronSchedule
import com.inspiredandroid.orcaeye.model.LoopJob
import com.inspiredandroid.orcaeye.model.LoopSnapshot
import com.inspiredandroid.orcaeye.model.LoopSource
import com.inspiredandroid.orcaeye.model.MemoryItem
import com.inspiredandroid.orcaeye.model.ProjectInventory
import com.inspiredandroid.orcaeye.model.RuleItem
import com.inspiredandroid.orcaeye.model.SchedulePreset
import com.inspiredandroid.orcaeye.model.SkillItem
import com.inspiredandroid.orcaeye.model.SkillOrigin
import com.inspiredandroid.orcaeye.model.ToolInstall
import com.inspiredandroid.orcaeye.model.ToolKind
import com.inspiredandroid.orcaeye.ui.InventoryUiState
import com.inspiredandroid.orcaeye.ui.LoopEditorState
import com.inspiredandroid.orcaeye.ui.LoopsUiState
import kotlinx.datetime.LocalDateTime

/**
 * Fixed inventory used by the screenshots so renders stay stable across machines
 * and never depend on whatever is installed on the host.
 */
object SampleData {
    private const val HOME = "/Users/simon"

    private fun skill(
        name: String,
        tool: ToolKind,
        origin: SkillOrigin,
        description: String,
        dir: String,
    ) = SkillItem(
        name = name,
        path = "$dir/$name",
        tool = tool,
        origin = origin,
        description = description,
        skillMdPath = "$dir/$name/SKILL.md",
    )

    private val systemSkills =
        listOf(
            skill(
                name = "check-updates",
                tool = ToolKind.Claude,
                origin = SkillOrigin.User,
                description = "Check Gradle version catalog and fastlane updates",
                dir = "$HOME/.claude/skills",
            ),
            skill(
                name = "dev-disk-cleanup",
                tool = ToolKind.Claude,
                origin = SkillOrigin.User,
                description = "Reclaim disk space from macOS dev tooling",
                dir = "$HOME/.claude/skills",
            ),
            skill(
                name = "monkey-test",
                tool = ToolKind.Claude,
                origin = SkillOrigin.User,
                description = "Run an Android UI monkey stress test",
                dir = "$HOME/.claude/skills",
            ),
            skill(
                name = "triage-reviews",
                tool = ToolKind.Claude,
                origin = SkillOrigin.User,
                description = "Triage Play Store reviews and draft replies",
                dir = "$HOME/.claude/skills",
            ),
            skill(
                name = "release-notes",
                tool = ToolKind.Grok,
                origin = SkillOrigin.User,
                description = "Summarize commits into release notes",
                dir = "$HOME/.grok/skills",
            ),
            skill(
                name = "check-work",
                tool = ToolKind.Grok,
                origin = SkillOrigin.User,
                description = "Verify changes with a review subagent",
                dir = "$HOME/.grok/skills",
            ),
            skill(
                name = "create-workflow",
                tool = ToolKind.Grok,
                origin = SkillOrigin.Bundled,
                description = "Author a Rhai orchestration workflow",
                dir = "$HOME/.grok/bundled/skills",
            ),
            skill(
                name = "design",
                tool = ToolKind.Grok,
                origin = SkillOrigin.Bundled,
                description = "Write a design doc with a PR plan",
                dir = "$HOME/.grok/bundled/skills",
            ),
            skill(
                name = "review",
                tool = ToolKind.Grok,
                origin = SkillOrigin.Bundled,
                description = "Code review uncommitted changes or a PR",
                dir = "$HOME/.grok/bundled/skills",
            ),
        )

    private val systemMemories =
        listOf(
            MemoryItem(
                title = "prefers-kotlin-multiplatform",
                path = "$HOME/.claude/memory/prefers-kotlin-multiplatform.md",
                tool = ToolKind.Claude,
            ),
            MemoryItem(
                title = "commit-style",
                path = "$HOME/.claude/memory/commit-style.md",
                tool = ToolKind.Claude,
            ),
            MemoryItem(
                title = "review-checklist",
                path = "$HOME/.grok/memory/review-checklist.md",
                tool = ToolKind.Grok,
            ),
        )

    private val systemRules =
        listOf(
            RuleItem(
                name = "code-style",
                path = "$HOME/.claude/rules/code-style.md",
                tool = ToolKind.Claude,
                description = "Formatting and naming conventions",
            ),
            RuleItem(
                name = "testing",
                path = "$HOME/.claude/rules/testing.md",
                tool = ToolKind.Claude,
                description = "Write a failing test first",
            ),
            RuleItem(
                name = "review-tone",
                path = "$HOME/.grok/rules/review-tone.md",
                tool = ToolKind.Grok,
                description = "Keep review comments short and concrete",
            ),
        )

    private val systemAgentFiles =
        listOf(
            AgentFileItem(name = "CLAUDE.md", path = "$HOME/.claude/CLAUDE.md", tool = ToolKind.Claude),
            AgentFileItem(name = "settings.json", path = "$HOME/.claude/settings.json", tool = ToolKind.Claude),
            AgentFileItem(name = "AGENTS.md", path = "$HOME/.grok/AGENTS.md", tool = ToolKind.Grok),
        )

    private val projects =
        listOf(
            ProjectInventory(
                path = "$HOME/Projects/Orcaeye",
                name = "Orcaeye",
                toolsPresent = setOf(ToolKind.Claude),
                agentFiles = listOf(AgentFileItem("AGENTS.md", "$HOME/Projects/Orcaeye/AGENTS.md", ToolKind.Claude)),
                skills =
                listOf(
                    skill(
                        name = "run",
                        tool = ToolKind.Claude,
                        origin = SkillOrigin.Project,
                        description = "Launch the desktop app and grab a screenshot",
                        dir = "$HOME/Projects/Orcaeye/.claude/skills",
                    ),
                ),
                memories =
                listOf(
                    MemoryItem(
                        title = "desktop-only-targets",
                        path = "$HOME/Projects/Orcaeye/.claude/memory/desktop-only-targets.md",
                        tool = ToolKind.Claude,
                        projectPath = "$HOME/Projects/Orcaeye",
                    ),
                ),
                rules =
                listOf(
                    RuleItem(
                        name = "compose-ui",
                        path = "$HOME/Projects/Orcaeye/.claude/rules/compose-ui.md",
                        tool = ToolKind.Claude,
                        description = "Keep composables stateless and preview-friendly",
                        globs = listOf("composeApp/src/**/ui/*.kt"),
                    ),
                ),
            ),
            ProjectInventory(
                path = "$HOME/Projects/Braincup",
                name = "Braincup",
                toolsPresent = setOf(ToolKind.Claude, ToolKind.Grok),
                agentFiles = emptyList(),
                skills = emptyList(),
                memories = emptyList(),
            ),
            ProjectInventory(
                path = "$HOME/Projects/LinuxCommandLibrary",
                name = "LinuxCommandLibrary",
                toolsPresent = setOf(ToolKind.Claude),
                agentFiles = emptyList(),
                skills = emptyList(),
                memories = emptyList(),
            ),
            ProjectInventory(
                path = "$HOME/Projects/Kai",
                name = "Kai",
                toolsPresent = setOf(ToolKind.OpenCode),
                agentFiles = emptyList(),
                skills = emptyList(),
                memories = emptyList(),
            ),
        )

    val snapshot =
        AppSnapshot(
            tools =
            listOf(
                ToolInstall(
                    kind = ToolKind.Claude,
                    installed = true,
                    binaryPath = "/opt/homebrew/bin/claude",
                    homeDir = "$HOME/.claude",
                    version = "2.1.4",
                ),
                ToolInstall(
                    kind = ToolKind.Grok,
                    installed = true,
                    binaryPath = "/opt/homebrew/bin/grok",
                    homeDir = "$HOME/.grok",
                    version = "0.9.0",
                ),
                ToolInstall(
                    kind = ToolKind.OpenCode,
                    installed = false,
                    binaryPath = null,
                    homeDir = null,
                ),
                ToolInstall(
                    kind = ToolKind.Codex,
                    installed = false,
                    binaryPath = null,
                    homeDir = null,
                ),
                ToolInstall(
                    kind = ToolKind.Cursor,
                    installed = true,
                    binaryPath = "/usr/local/bin/cursor-agent",
                    homeDir = "$HOME/.cursor",
                    version = "1.0.0",
                ),
                ToolInstall(
                    kind = ToolKind.Gemini,
                    installed = false,
                    binaryPath = null,
                    homeDir = null,
                ),
            ),
            systemSkills = systemSkills,
            systemMemories = systemMemories,
            systemAgentFiles = systemAgentFiles,
            systemRules = systemRules,
            projects = projects,
        )

    val state =
        InventoryUiState(
            loading = false,
            snapshot = snapshot,
        )

    private fun loop(
        id: String,
        name: String,
        expression: String,
        project: String,
        prompt: String,
        source: LoopSource = LoopSource.Managed,
        enabled: Boolean = true,
        tool: ToolKind = ToolKind.Grok,
    ) = LoopJob(
        id = id,
        name = name,
        source = source,
        enabled = enabled,
        schedule = CronSchedule.parse(expression) ?: error("bad fixture expression: $expression"),
        tool = tool,
        workingDirectory = "$HOME/Projects/$project",
        prompt = prompt,
        extraFlags = "--always-approve --permission-mode bypassPermissions",
        pathPrefix = "$HOME/.grok/bin:/opt/homebrew/bin:/usr/bin:/bin",
        logPath = "$HOME/Library/Logs/$name.log",
    )

    private val loopSnapshot =
        LoopSnapshot(
            jobs =
            listOf(
                loop(
                    id = "orcaeye:a41f7c02",
                    name = "find-missing-commands",
                    expression = "0 4,12,20 * * *",
                    project = "LinuxCommandLibrary",
                    prompt = "/find-missing-commands",
                ),
                loop(
                    id = "line:3",
                    name = "check-updates-linux",
                    expression = "0 6 * * *",
                    project = "LinuxCommandLibrary",
                    prompt = "/check-updates",
                    source = LoopSource.Adopted,
                ),
                loop(
                    id = "line:4",
                    name = "check-updates-braincup",
                    expression = "30 6 * * *",
                    project = "Braincup",
                    prompt = "/check-updates",
                    source = LoopSource.Adopted,
                ),
                loop(
                    id = "orcaeye:9b30de51",
                    name = "triage-reviews-kai",
                    expression = "0 9 * * 1",
                    project = "Kai",
                    prompt = "/triage-reviews",
                    tool = ToolKind.Claude,
                    enabled = false,
                ),
                LoopJob(
                    id = "line:9",
                    name = "backup hourly",
                    source = LoopSource.External,
                    enabled = true,
                    schedule = CronSchedule.parse("0 * * * *") ?: error("bad fixture expression"),
                    rawLine = "0 * * * * /opt/homebrew/bin/python3 $HOME/scripts/backup.py",
                ),
            ),
        )

    /**
     * Pinned wall-clock + next-run instants so relative "Next run in …" labels stay
     * identical on every machine (e.g. 20:00 from 18:00 → "in 2 hours").
     */
    private val loopsNow = LocalDateTime(2026, 8, 1, 18, 0)
    private val nextRuns =
        mapOf(
            "orcaeye:a41f7c02" to LocalDateTime(2026, 8, 1, 20, 0),
            "line:3" to LocalDateTime(2026, 8, 2, 6, 0),
            "line:4" to LocalDateTime(2026, 8, 2, 6, 30),
        )

    val loopsState =
        LoopsUiState(
            loading = false,
            snapshot = loopSnapshot,
            nextRuns = nextRuns,
            now = loopsNow,
        )

    /** Same screen with the create/edit dialog open. */
    val loopsEditorState =
        loopsState.copy(
            editor =
            LoopEditorState(
                name = "check-updates-orcaeye",
                tool = ToolKind.Grok,
                projectPath = "$HOME/Projects/Orcaeye",
                prompt = "/check-updates",
                preset = SchedulePreset.EveryNHours(hours = 8, startHour = 4, minute = 0),
                expression = "0 4,12,20 * * *",
                logPath = "$HOME/Library/Logs/check-updates-orcaeye.log",
                extraFlags = "--always-approve --permission-mode bypassPermissions",
                pathPrefix = "$HOME/.grok/bin:/opt/homebrew/bin:/usr/bin:/bin",
                nextRuns =
                listOf(
                    LocalDateTime(2026, 8, 1, 20, 0),
                    LocalDateTime(2026, 8, 2, 4, 0),
                    LocalDateTime(2026, 8, 2, 12, 0),
                ),
            ),
        )
}
