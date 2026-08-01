package com.inspiredandroid.orcaeye.data

import java.nio.file.Path
import java.util.concurrent.TimeUnit

/**
 * Opens a shell command in the platform's terminal application.
 * Shared by "Open tool" (Context) and "Run now" (Loops).
 */
object TerminalLauncher {
    fun openInTerminal(
        command: String,
        workingDirectory: String?,
    ) {
        val shell =
            buildString {
                if (workingDirectory != null) {
                    append("cd ")
                    append(shellQuote(workingDirectory))
                    append(" && ")
                }
                append(command)
            }
        val os = System.getProperty("os.name").orEmpty().lowercase()
        when {
            os.contains("mac") -> launchMacTerminal(shell)
            os.contains("win") -> launchWindowsTerminal(shell, workingDirectory)
            else -> launchLinuxTerminal(shell)
        }
    }

    fun shellQuote(value: String): String = if (value.isEmpty()) {
        "''"
    } else {
        "'" + value.replace("'", "'\\''") + "'"
    }

    private fun launchMacTerminal(shellCommand: String) {
        val script =
            """
            tell application "Terminal"
                activate
                do script ${appleScriptString(shellCommand)}
            end tell
            """.trimIndent()
        val proc =
            ProcessBuilder("osascript", "-e", script)
                .redirectErrorStream(true)
                .start()
        if (!proc.waitFor(5, TimeUnit.SECONDS)) {
            proc.destroyForcibly()
            error("Timed out opening Terminal")
        }
        if (proc.exitValue() != 0) {
            val out =
                proc.inputStream
                    .bufferedReader()
                    .readText()
                    .trim()
            error(out.ifBlank { "Failed to open Terminal (exit ${proc.exitValue()})" })
        }
    }

    private fun launchWindowsTerminal(
        shellCommand: String,
        workingDirectory: String?,
    ) {
        val args =
            mutableListOf(
                "cmd",
                "/c",
                "start",
                "cmd",
                "/k",
                shellCommand,
            )
        val pb = ProcessBuilder(args)
        if (workingDirectory != null) {
            pb.directory(Path.of(workingDirectory).toFile())
        }
        pb.start()
    }

    private fun launchLinuxTerminal(shellCommand: String) {
        val candidates =
            listOf(
                listOf("x-terminal-emulator", "-e", "bash", "-lc", "$shellCommand; exec bash"),
                listOf("gnome-terminal", "--", "bash", "-lc", "$shellCommand; exec bash"),
                listOf("konsole", "-e", "bash", "-lc", "$shellCommand; exec bash"),
                listOf("xterm", "-e", "bash", "-lc", "$shellCommand; exec bash"),
            )
        var lastError: Exception? = null
        for (cmd in candidates) {
            try {
                ProcessBuilder(cmd).start()
                return
            } catch (e: Exception) {
                lastError = e
            }
        }
        throw lastError ?: IllegalStateException("No terminal emulator found")
    }

    private fun appleScriptString(value: String): String = "\"" +
        value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"") +
        "\""
}
