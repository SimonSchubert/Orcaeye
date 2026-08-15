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
 *
 * Every tool is detected the same way, from its [ToolLayout]: a config directory under home,
 * an install marker, or the binary itself is enough to call it installed.
 */
class ToolDetector(
    private val home: Path = Path.of(System.getProperty("user.home")),
) {
    /**
     * @param includeVersions when false, skips spawning each CLI with `--version`
     * (saves hundreds of ms on cold start; version is unused in the UI today).
     */
    fun detectTools(includeVersions: Boolean = true): List<ToolInstall> = ToolKind.entries.map { detect(it, includeVersions) }

    /** Absolute path to [tool]'s binary, falling back to its bare name when not found. */
    fun binaryFor(tool: ToolKind): String = findBinary(tool)?.absolutePathString() ?: tool.cliName

    private fun detect(
        tool: ToolKind,
        includeVersions: Boolean,
    ): ToolInstall {
        val layout = tool.layout
        val homeDir = layout.homeDirs.map { home.resolve(it) }.firstOrNull { it.exists() }
        val binary = findBinary(tool)
        val installed =
            homeDir != null ||
                binary != null ||
                layout.installMarkers.any { home.resolve(it).exists() }
        return ToolInstall(
            kind = tool,
            installed = installed,
            binaryPath = binary?.absolutePathString(),
            homeDir = homeDir?.absolutePathString(),
            version = if (includeVersions) binary?.let { runVersion(it, "--version") } else null,
        )
    }

    private fun findBinary(tool: ToolKind): Path? {
        tool.layout
            .binaryCandidates(home)
            .firstOrNull { it.exists() && Files.isExecutable(it) }
            ?.let { return it }
        return try {
            val proc =
                ProcessBuilder("which", tool.cliName)
                    .redirectErrorStream(true)
                    .start()
            if (!proc.waitFor(WHICH_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
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
        if (!proc.waitFor(VERSION_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            proc.destroyForcibly()
            null
        } else {
            proc.inputStream
                .bufferedReader()
                .readText()
                .lineSequence()
                .firstOrNull { it.isNotBlank() }
                ?.trim()
                ?.take(MAX_VERSION_CHARS)
        }
    } catch (_: Exception) {
        null
    }

    private companion object {
        const val WHICH_TIMEOUT_SECONDS = 2L
        const val VERSION_TIMEOUT_SECONDS = 3L
        const val MAX_VERSION_CHARS = 80
    }
}
