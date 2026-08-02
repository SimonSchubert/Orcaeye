package com.inspiredandroid.orcaeye.data

import com.inspiredandroid.orcaeye.model.ToolInstall
import com.inspiredandroid.orcaeye.model.ToolKind
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.io.path.absolutePathString
import kotlin.io.path.exists

/**
 * Locates the agent CLIs on this machine. Shared by the inventory scan and by the
 * crontab code, which needs the same binary paths to build scheduled commands.
 */
class ToolDetector(
    private val home: Path = Path.of(System.getProperty("user.home")),
) {
    /**
     * @param includeVersions when false, skips spawning each CLI with `--version`
     * (saves hundreds of ms on cold start; version is unused in the UI today).
     */
    fun detectTools(includeVersions: Boolean = true): List<ToolInstall> = listOf(
        detectClaude(includeVersions),
        detectGrok(includeVersions),
        detectOpenCode(includeVersions),
        detectCodex(includeVersions),
        detectCursor(includeVersions),
        detectGemini(includeVersions),
    )

    /** Absolute path to [tool]'s binary, falling back to its bare name when not found. */
    fun binaryFor(tool: ToolKind): String = detectTools(includeVersions = false)
        .firstOrNull { it.kind == tool }
        ?.binaryPath
        ?: findBinary(tool.cliName, knownPathsFor(tool))?.absolutePathString()
        ?: tool.cliName

    private fun knownPathsFor(tool: ToolKind): List<Path> = when (tool) {
        ToolKind.Claude ->
            listOf(
                home.resolve(".local/bin/claude"),
                Path.of("/opt/homebrew/bin/claude"),
                Path.of("/usr/local/bin/claude"),
            )
        ToolKind.Grok ->
            listOf(
                home.resolve(".grok/bin/grok"),
                home.resolve(".local/bin/grok"),
                Path.of("/opt/homebrew/bin/grok"),
            )
        ToolKind.OpenCode ->
            listOf(
                home.resolve(".opencode/bin/opencode"),
                home.resolve(".local/bin/opencode"),
                Path.of("/opt/homebrew/bin/opencode"),
            )
        ToolKind.Codex ->
            listOf(
                home.resolve(".codex/bin/codex"),
                home.resolve(".local/bin/codex"),
                Path.of("/opt/homebrew/bin/codex"),
                Path.of("/usr/local/bin/codex"),
            )
        ToolKind.Cursor ->
            listOf(
                home.resolve(".cursor/bin/cursor-agent"),
                home.resolve(".local/bin/cursor-agent"),
                Path.of("/opt/homebrew/bin/cursor-agent"),
                Path.of("/usr/local/bin/cursor-agent"),
            )
        ToolKind.Gemini ->
            listOf(
                home.resolve(".gemini/bin/gemini"),
                home.resolve(".local/bin/gemini"),
                Path.of("/opt/homebrew/bin/gemini"),
                Path.of("/usr/local/bin/gemini"),
            )
    }

    private fun detectClaude(includeVersions: Boolean): ToolInstall {
        val homeDir = home.resolve(".claude")
        val binary = findBinary("claude", knownPathsFor(ToolKind.Claude))
        val exists = homeDir.exists() || binary != null || home.resolve(".claude.json").exists()
        return ToolInstall(
            kind = ToolKind.Claude,
            installed = exists,
            binaryPath = binary?.absolutePathString(),
            homeDir = homeDir.takeIf { it.exists() }?.absolutePathString(),
            version = if (includeVersions) binary?.let { runVersion(it, "--version") } else null,
        )
    }

    private fun detectGrok(includeVersions: Boolean): ToolInstall {
        val homeDir = home.resolve(".grok")
        val binary = findBinary("grok", knownPathsFor(ToolKind.Grok))
        val exists = homeDir.exists() || binary != null
        return ToolInstall(
            kind = ToolKind.Grok,
            installed = exists,
            binaryPath = binary?.absolutePathString(),
            homeDir = homeDir.takeIf { it.exists() }?.absolutePathString(),
            version = if (includeVersions) binary?.let { runVersion(it, "--version") } else null,
        )
    }

    private fun detectOpenCode(includeVersions: Boolean): ToolInstall {
        val homeDir = home.resolve(".opencode")
        val configDir = home.resolve(".config/opencode")
        val binary = findBinary("opencode", knownPathsFor(ToolKind.OpenCode))
        val exists = homeDir.exists() || configDir.exists() || binary != null
        return ToolInstall(
            kind = ToolKind.OpenCode,
            installed = exists,
            binaryPath = binary?.absolutePathString(),
            homeDir =
            when {
                homeDir.exists() -> homeDir.absolutePathString()
                configDir.exists() -> configDir.absolutePathString()
                else -> null
            },
            version = if (includeVersions) binary?.let { runVersion(it, "--version") } else null,
        )
    }

    private fun detectCodex(includeVersions: Boolean): ToolInstall {
        val homeDir = home.resolve(".codex")
        val binary = findBinary("codex", knownPathsFor(ToolKind.Codex))
        val exists = homeDir.exists() || binary != null
        return ToolInstall(
            kind = ToolKind.Codex,
            installed = exists,
            binaryPath = binary?.absolutePathString(),
            homeDir = homeDir.takeIf { it.exists() }?.absolutePathString(),
            version = if (includeVersions) binary?.let { runVersion(it, "--version") } else null,
        )
    }

    private fun detectCursor(includeVersions: Boolean): ToolInstall {
        val homeDir = home.resolve(".cursor")
        // Prefer cursor-agent; bare "agent" collides with other CLIs (e.g. Grok's agent).
        val binary = findBinary("cursor-agent", knownPathsFor(ToolKind.Cursor))
        val exists = homeDir.exists() || binary != null
        return ToolInstall(
            kind = ToolKind.Cursor,
            installed = exists,
            binaryPath = binary?.absolutePathString(),
            homeDir = homeDir.takeIf { it.exists() }?.absolutePathString(),
            version = if (includeVersions) binary?.let { runVersion(it, "--version") } else null,
        )
    }

    private fun detectGemini(includeVersions: Boolean): ToolInstall {
        val homeDir = home.resolve(".gemini")
        val binary = findBinary("gemini", knownPathsFor(ToolKind.Gemini))
        val exists = homeDir.exists() || binary != null
        return ToolInstall(
            kind = ToolKind.Gemini,
            installed = exists,
            binaryPath = binary?.absolutePathString(),
            homeDir = homeDir.takeIf { it.exists() }?.absolutePathString(),
            version = if (includeVersions) binary?.let { runVersion(it, "--version") } else null,
        )
    }

    private fun findBinary(
        name: String,
        knownPaths: List<Path>,
    ): Path? {
        knownPaths.firstOrNull { it.exists() && Files.isExecutable(it) }?.let { return it }
        return try {
            val proc =
                ProcessBuilder("which", name)
                    .redirectErrorStream(true)
                    .start()
            if (!proc.waitFor(2, TimeUnit.SECONDS)) {
                proc.destroyForcibly()
                return null
            }
            val out =
                proc.inputStream
                    .bufferedReader()
                    .readText()
                    .trim()
            if (proc.exitValue() == 0 && out.isNotBlank()) Path.of(out) else null
        } catch (_: Exception) {
            null
        }
    }

    private fun runVersion(
        binary: Path,
        arg: String,
    ): String? = try {
        val proc =
            ProcessBuilder(binary.absolutePathString(), arg)
                .redirectErrorStream(true)
                .start()
        if (!proc.waitFor(3, TimeUnit.SECONDS)) {
            proc.destroyForcibly()
            null
        } else {
            proc.inputStream
                .bufferedReader()
                .readText()
                .lineSequence()
                .firstOrNull { it.isNotBlank() }
                ?.trim()
                ?.take(80)
        }
    } catch (_: Exception) {
        null
    }
}

internal val ToolKind.cliName: String
    get() =
        when (this) {
            ToolKind.Claude -> "claude"
            ToolKind.Grok -> "grok"
            ToolKind.OpenCode -> "opencode"
            ToolKind.Codex -> "codex"
            ToolKind.Cursor -> "cursor-agent"
            ToolKind.Gemini -> "gemini"
        }
