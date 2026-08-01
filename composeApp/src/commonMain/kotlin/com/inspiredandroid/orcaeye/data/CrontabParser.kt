package com.inspiredandroid.orcaeye.data

import com.inspiredandroid.orcaeye.model.CronSchedule
import com.inspiredandroid.orcaeye.model.LoopJob
import com.inspiredandroid.orcaeye.model.LoopSnapshot
import com.inspiredandroid.orcaeye.model.LoopSource
import com.inspiredandroid.orcaeye.model.ToolKind

/**
 * Turns crontab text into [LoopJob]s and back.
 *
 * Three kinds of job come out of [parse]:
 *  - [LoopSource.Managed]  — preceded by an `# orcaeye id=… name=…` marker we wrote ourselves.
 *  - [LoopSource.Adopted]  — a hand-written agent-CLI line we understood well enough to edit.
 *  - [LoopSource.External] — a cron line we did not understand; listed read-only.
 *
 * Comments, blank lines and environment assignments are not jobs at all and simply pass through.
 *
 * [parse] and [render] share one [walk] over the text, so they can never disagree about which
 * line belongs to which job. That is what makes `render(previous, parse(previous).jobs) ==
 * previous` hold: nothing is rewritten until the user actually changes something.
 */
object CrontabParser {
    const val MARKER_PREFIX = "# orcaeye"
    const val DISABLED_PREFIX = "#DISABLED "

    private val MARKER = Regex("""^#\s*orcaeye\s+id=(\S+)(?:\s+name=(.*))?$""")
    private val CD_PREFIX = Regex("""^cd\s+('[^']*'|"[^"]*"|\S+)\s*&&\s*""")
    private val PATH_PREFIX = Regex("""^PATH=('[^']*'|"[^"]*"|\S+)\s+""")
    private val BINARY = Regex("""^\S*?\b(claude|grok|opencode)\s+""")
    private val PROMPT = Regex("""^(?:-p|--print|run)\s+('[^']*'|"[^"]*"|\S+)\s*""")
    private val REDIRECT = Regex("""\s*(?:\d?>>?)\s*('[^']*'|"[^"]*"|\S+)(?:\s*2>&1)?\s*$""")

    // region parse

    fun parse(lines: List<String>): LoopSnapshot {
        val warnings = mutableListOf<String>()
        val jobs =
            walk(lines).mapNotNull { entry ->
                when (entry) {
                    is Entry.Passthrough -> null
                    is Entry.Managed -> {
                        val job = parseCommand(entry.cronLine, entry.id, LoopSource.Managed)
                        if (job == null) {
                            warnings += "Could not parse the Orcaeye job \"${entry.name}\"; leaving it untouched."
                            null
                        } else {
                            job.copy(
                                name = entry.name.ifBlank { job.name },
                                enabled = entry.enabled,
                            )
                        }
                    }
                    is Entry.Plain ->
                        parseCommand(entry.line, entry.id, LoopSource.Adopted)
                            ?: LoopJob(
                                id = entry.id,
                                name = externalName(entry.line),
                                source = LoopSource.External,
                                enabled = true,
                                schedule = entry.schedule,
                                rawLine = entry.line,
                            )
                }
            }
        return LoopSnapshot(jobs = jobs, warnings = warnings)
    }

    // endregion

    // region render

    /**
     * Rewrites [previous] with [jobs]. A job that vanished from [jobs] has its lines dropped; an
     * adopted line the caller promoted to [LoopSource.Managed] is rewritten in place; everything
     * else is copied through byte-for-byte. New jobs are appended at the end.
     */
    fun render(
        previous: List<String>,
        jobs: List<LoopJob>,
    ): List<String> {
        val byId = jobs.associateBy { it.id }
        val seen = mutableSetOf<String>()
        val out = mutableListOf<String>()

        walk(previous).forEach { entry ->
            when (entry) {
                is Entry.Passthrough -> out += entry.lines
                is Entry.Managed -> {
                    seen += entry.id
                    byId[entry.id]?.let { out += renderJob(it) }
                }
                is Entry.Plain -> {
                    seen += entry.id
                    val job = byId[entry.id]
                    when {
                        job == null -> Unit // deleted
                        job.source == LoopSource.Managed -> out += renderJob(job) // adopted in place
                        else -> out += entry.line
                    }
                }
            }
        }

        jobs
            .filter { it.id !in seen && it.source == LoopSource.Managed }
            .forEach { out += renderJob(it) }
        return out
    }

    /** The two lines a managed job occupies: marker comment, then the cron entry. */
    fun renderJob(job: LoopJob): List<String> {
        val cronLine = "${job.schedule.expression} ${buildCommand(job)}"
        return listOf(
            "$MARKER_PREFIX id=${job.id} name=${job.name}",
            if (job.enabled) cronLine else "$DISABLED_PREFIX$cronLine",
        )
    }

    /** The shell command a job runs, without the schedule — also what "Run now" executes. */
    fun buildCommand(
        job: LoopJob,
        includeRedirect: Boolean = true,
    ): String = buildString {
        job.workingDirectory?.takeIf { it.isNotBlank() }?.let {
            append("cd ")
            append(quote(it))
            append(" && ")
        }
        job.pathPrefix?.takeIf { it.isNotBlank() }?.let {
            append("PATH=")
            append(it)
            append(' ')
        }
        val tool = job.tool ?: ToolKind.Grok
        append(binaryName(tool))
        append(' ')
        append(if (tool == ToolKind.OpenCode) "run" else "-p")
        append(' ')
        append(quote(job.prompt))
        job.extraFlags.trim().takeIf { it.isNotEmpty() }?.let {
            append(' ')
            append(it)
        }
        if (includeRedirect) {
            job.logPath?.takeIf { it.isNotBlank() }?.let {
                append(" >> ")
                append(quote(it))
                append(" 2>&1")
            }
        }
    }

    /** The flags a freshly created job gets, matching how these CLIs are run unattended. */
    fun defaultFlags(tool: ToolKind): String = when (tool) {
        ToolKind.Grok -> "--always-approve --permission-mode bypassPermissions"
        ToolKind.Claude -> "--dangerously-skip-permissions"
        ToolKind.OpenCode -> ""
    }

    fun binaryName(tool: ToolKind): String = when (tool) {
        ToolKind.Claude -> "claude"
        ToolKind.Grok -> "grok"
        ToolKind.OpenCode -> "opencode"
    }

    /** A short, stable job name from the prompt and project, e.g. `check-updates-kai`. */
    fun deriveName(
        prompt: String,
        workingDirectory: String?,
    ): String {
        val promptSlug = slug(prompt.removePrefix("/")).take(40).ifBlank { "job" }
        val projectSlug = workingDirectory?.trimEnd('/')?.substringAfterLast('/')?.let { slug(it) }
        return if (projectSlug.isNullOrBlank()) promptSlug else "$promptSlug-$projectSlug"
    }

    fun slug(value: String): String = value
        .trim()
        .lowercase()
        .replace(Regex("\\s+"), "-")
        .replace(Regex("[^a-z0-9._-]+"), "")
        .trim('-', '.', '_')

    // endregion

    // region walk

    private sealed interface Entry {
        /** Comments, blank lines, `MAILTO=…` — anything that is not a schedule. */
        data class Passthrough(
            val lines: List<String>,
        ) : Entry

        /** An `# orcaeye …` marker and the cron line under it. */
        data class Managed(
            val id: String,
            val name: String,
            val enabled: Boolean,
            val cronLine: String,
        ) : Entry

        /** A cron line without a marker: adopted if we can parse the command, else external. */
        data class Plain(
            val id: String,
            val line: String,
            val schedule: CronSchedule,
        ) : Entry
    }

    private fun walk(lines: List<String>): List<Entry> {
        val entries = mutableListOf<Entry>()
        val pending = mutableListOf<String>()
        fun flush() {
            if (pending.isNotEmpty()) {
                entries += Entry.Passthrough(pending.toList())
                pending.clear()
            }
        }

        var index = 0
        while (index < lines.size) {
            val line = lines[index]
            val marker = MARKER.find(line.trim())
            if (marker != null && index + 1 < lines.size) {
                flush()
                val cronLine = lines[index + 1]
                val disabled = cronLine.trimStart().startsWith(DISABLED_PREFIX.trim())
                entries +=
                    Entry.Managed(
                        id = marker.groupValues[1],
                        name = marker.groupValues[2].trim(),
                        enabled = !disabled,
                        cronLine = stripDisabled(cronLine),
                    )
                index += 2
                continue
            }

            val schedule = leadingSchedule(line)
            if (schedule == null) {
                pending += line
            } else {
                flush()
                entries += Entry.Plain(id = "line:$index", line = line, schedule = schedule)
            }
            index++
        }
        flush()
        return entries
    }

    /** The schedule at the start of [line], or null when the line is not a cron entry. */
    private fun leadingSchedule(line: String): CronSchedule? {
        val trimmed = line.trim()
        if (trimmed.isEmpty() || trimmed.startsWith("#")) return null
        val parts = trimmed.split(Regex("\\s+"))
        if (parts.size < 6) return null
        return CronSchedule.parse(parts.take(5).joinToString(" "))
    }

    private fun stripDisabled(line: String): String {
        val trimmed = line.trimStart()
        return if (trimmed.startsWith(DISABLED_PREFIX.trim())) {
            trimmed.removePrefix(DISABLED_PREFIX.trim()).trimStart()
        } else {
            line
        }
    }

    /**
     * Pulls a cron line apart into schedule + working directory + PATH + CLI + prompt + flags +
     * log. Returns null when the command is not a recognisable agent-CLI invocation.
     */
    private fun parseCommand(
        line: String,
        id: String,
        source: LoopSource,
    ): LoopJob? {
        val schedule = leadingSchedule(line) ?: return null
        var rest = line.trim().split(Regex("\\s+")).drop(5).joinToString(" ")

        var logPath: String? = null
        REDIRECT.find(rest)?.let { match ->
            logPath = unquote(match.groupValues[1])
            rest = rest.removeRange(match.range).trim()
        }

        var workingDirectory: String? = null
        CD_PREFIX.find(rest)?.let { match ->
            workingDirectory = unquote(match.groupValues[1])
            rest = rest.drop(match.value.length).trim()
        }

        var pathPrefix: String? = null
        PATH_PREFIX.find(rest)?.let { match ->
            pathPrefix = unquote(match.groupValues[1])
            rest = rest.drop(match.value.length).trim()
        }

        // Match against "$rest " so a command with no flags after it still ends in whitespace.
        val binaryMatch = BINARY.find("$rest ") ?: return null
        val tool =
            when (binaryMatch.groupValues[1]) {
                "claude" -> ToolKind.Claude
                "grok" -> ToolKind.Grok
                else -> ToolKind.OpenCode
            }
        rest = rest.drop(binaryMatch.value.length).trim()

        val promptMatch = PROMPT.find("$rest ") ?: return null
        val prompt = unquote(promptMatch.groupValues[1])
        if (prompt.isBlank()) return null
        val extraFlags = rest.drop(promptMatch.value.length).trim()

        return LoopJob(
            id = id,
            name = deriveName(prompt, workingDirectory),
            source = source,
            enabled = true,
            schedule = schedule,
            tool = tool,
            workingDirectory = workingDirectory,
            prompt = prompt,
            extraFlags = extraFlags,
            pathPrefix = pathPrefix,
            logPath = logPath,
            rawLine = line,
        )
    }

    private fun externalName(line: String): String {
        val command = line.trim().split(Regex("\\s+")).drop(5).joinToString(" ")
        return command.take(60).ifBlank { line.trim().take(60) }
    }

    private fun unquote(value: String): String = when {
        value.length >= 2 && value.startsWith("'") && value.endsWith("'") -> value.substring(1, value.length - 1)
        value.length >= 2 && value.startsWith("\"") && value.endsWith("\"") -> value.substring(1, value.length - 1)
        else -> value
    }

    private fun quote(value: String): String = if (value.isEmpty()) {
        "''"
    } else {
        "'" + value.replace("'", "'\\''") + "'"
    }

    // endregion
}
