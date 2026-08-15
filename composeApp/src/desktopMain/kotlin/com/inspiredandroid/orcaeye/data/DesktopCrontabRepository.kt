package com.inspiredandroid.orcaeye.data

import com.inspiredandroid.orcaeye.model.LoopJob
import com.inspiredandroid.orcaeye.model.LoopSnapshot
import com.inspiredandroid.orcaeye.model.ToolKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.io.path.absolutePathString
import kotlin.io.path.exists
import kotlin.io.path.isDirectory

/**
 * Talks to the `crontab` binary. Reads with `crontab -l`, writes by handing `crontab` a whole
 * file — there is no partial-update mode, so every write goes through [replaceAll], which
 * re-reads first so concurrent edits from a terminal are never clobbered silently.
 */
class DesktopCrontabRepository(
    private val home: Path = Path.of(System.getProperty("user.home")),
    private val toolDetector: ToolDetector = ToolDetector(home),
) : CrontabRepository {
    override suspend fun loadJobs(): LoopSnapshot = withContext(Dispatchers.IO) {
        val read = readCrontab()
        CrontabParser.parse(read.lines).copy(
            crontabAvailable = read.available,
            warnings = read.warnings,
        )
    }

    override suspend fun saveJob(job: LoopJob) = replaceAll { jobs ->
        if (jobs.any { it.id == job.id }) {
            jobs.map { if (it.id == job.id) job else it }
        } else {
            jobs + job
        }
    }

    override suspend fun deleteJob(id: String) = replaceAll { jobs ->
        jobs.filterNot { it.id == id }
    }

    override suspend fun runNow(job: LoopJob) = withContext(Dispatchers.IO) {
        job.workingDirectory?.takeIf { it.isNotBlank() }?.let { dir ->
            val path = Path.of(dir)
            check(path.exists() && path.isDirectory()) { "Working directory does not exist: $dir" }
        }
        // The command already carries its own `cd`, so don't prepend a second one. Skip the log
        // redirect so the output lands in the terminal where the user can watch it.
        TerminalLauncher.openInTerminal(
            command = CrontabParser.buildCommand(job, includeRedirect = false),
            workingDirectory = null,
        )
    }

    override fun defaultLogPathFor(name: String): String {
        val fileName = slug(name).ifBlank { "orcaeye-job" }
        val os = System.getProperty("os.name").orEmpty().lowercase()
        val dir =
            if (os.contains("mac")) {
                home.resolve("Library/Logs")
            } else {
                home.resolve(".local/state/orcaeye")
            }
        return dir.resolve("$fileName.log").absolutePathString()
    }

    /**
     * cron runs with a near-empty PATH, so scheduled jobs need one spelled out — both for the
     * CLI itself and for the tools it shells out to (git, gradle, …).
     */
    override fun defaultPathPrefix(): String = listOf(
        home.resolve(".grok/bin"),
        home.resolve(".local/bin"),
        Path.of("/opt/homebrew/bin"),
        Path.of("/usr/local/bin"),
        Path.of("/usr/bin"),
        Path.of("/bin"),
    ).filter { it.exists() }
        .joinToString(":") { it.absolutePathString() }
        .ifBlank { "/usr/bin:/bin" }

    override fun binaryFor(tool: ToolKind): String = toolDetector.binaryFor(tool)

    /**
     * Re-reads the crontab, applies [transform] to the jobs it holds, and writes the result back
     * through [CrontabParser.render] so untouched lines survive verbatim.
     */
    private suspend fun replaceAll(transform: (List<LoopJob>) -> List<LoopJob>) = withContext(Dispatchers.IO) {
        val read = readCrontab()
        check(read.available) { "The `crontab` command is not available on this system." }
        val current = CrontabParser.parse(read.lines).jobs
        val rendered = CrontabParser.render(read.lines, transform(current))
        writeCrontab(previous = read.lines, lines = rendered)
    }

    private fun readCrontab(): CrontabRead = try {
        val proc =
            ProcessBuilder("crontab", "-l")
                .redirectErrorStream(false)
                .start()
        val stdout = proc.inputStream.bufferedReader().readText()
        val stderr = proc.errorStream.bufferedReader().readText()
        if (!proc.waitFor(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            proc.destroyForcibly()
            CrontabRead(emptyList(), available = false, warnings = listOf("Timed out reading the crontab."))
        } else {
            when {
                proc.exitValue() == 0 -> CrontabRead(stdout.lines().dropLastWhile { it.isEmpty() })
                // An empty crontab is not an error.
                stderr.contains("no crontab", ignoreCase = true) -> CrontabRead(emptyList())
                else ->
                    CrontabRead(
                        emptyList(),
                        available = false,
                        warnings = listOf(stderr.trim().ifBlank { "crontab -l failed (exit ${proc.exitValue()})" }),
                    )
            }
        }
    } catch (e: Exception) {
        CrontabRead(
            emptyList(),
            available = false,
            warnings = listOf("Could not run `crontab`: ${e.message}"),
        )
    }

    private fun writeCrontab(
        previous: List<String>,
        lines: List<String>,
    ) {
        backup(previous)
        val temp = Files.createTempFile("orcaeye-crontab", ".cron")
        try {
            // crontab requires a trailing newline or it drops the last entry.
            Files.writeString(temp, lines.joinToString("\n").trimEnd('\n') + "\n")
            val proc =
                ProcessBuilder("crontab", temp.absolutePathString())
                    .redirectErrorStream(true)
                    .start()
            val output = proc.inputStream.bufferedReader().readText()
            if (!proc.waitFor(WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                proc.destroyForcibly()
                error("Timed out writing the crontab. Previous version saved in $BACKUP_DIR_NAME.")
            }
            if (proc.exitValue() != 0) {
                error(
                    output.trim().ifBlank { "crontab failed (exit ${proc.exitValue()})" } +
                        " — previous version saved in ${backupDir().absolutePathString()}",
                )
            }
        } finally {
            Files.deleteIfExists(temp)
        }
    }

    /** Keeps the last [MAX_BACKUPS] crontabs so a bad write is always recoverable. */
    private fun backup(previous: List<String>) {
        if (previous.isEmpty()) return
        try {
            val dir = backupDir()
            Files.createDirectories(dir)
            val stamp = System.currentTimeMillis() / 1000
            Files.writeString(dir.resolve("$stamp.cron"), previous.joinToString("\n") + "\n")
            dir
                .toFile()
                .listFiles { file -> file.name.endsWith(".cron") }
                .orEmpty()
                .sortedByDescending { it.name }
                .drop(MAX_BACKUPS)
                .forEach { it.delete() }
        } catch (_: Exception) {
            // A failed backup must not block the write the user asked for.
        }
    }

    private fun backupDir(): Path = home.resolve(BACKUP_DIR_NAME)

    private data class CrontabRead(
        val lines: List<String>,
        val available: Boolean = true,
        val warnings: List<String> = emptyList(),
    )

    companion object {
        private const val READ_TIMEOUT_SECONDS = 5L
        private const val WRITE_TIMEOUT_SECONDS = 5L
        private const val MAX_BACKUPS = 20
        private const val BACKUP_DIR_NAME = ".orcaeye/crontab-backups"
    }
}
