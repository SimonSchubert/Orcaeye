package com.inspiredandroid.orcaeye.data

import com.inspiredandroid.orcaeye.model.CronSchedule
import com.inspiredandroid.orcaeye.model.LoopSource
import com.inspiredandroid.orcaeye.model.SchedulePreset
import com.inspiredandroid.orcaeye.model.ToolKind
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Fixture is the real crontab this feature was built against, verbatim.
 */
class CrontabParserTest {
    private val pathValue = "/Users/simon/.grok/bin:/Users/simon/.local/bin:/opt/homebrew/bin:/usr/bin:/bin"
    private val cronPath = "PATH=$pathValue"
    private val grokFlags = "--always-approve --permission-mode bypassPermissions"

    private val crontab =
        listOf(
            "0 * * * * source /Users/simon/.zshrc && /opt/homebrew/bin/python3 " +
                "/Users/simon/Projects/agent1/main.py > /Users/simon/Projects/agent1/main.log 2>&1",
            "# Find missing Linux Command Library commands every 8 hours (offset 4h)",
            "0 4,12,20 * * * cd /Users/simon/Projects/LinuxCommandLibrary && $cronPath " +
                "grok -p \"/find-missing-commands\" $grokFlags " +
                ">> /Users/simon/Library/Logs/find-missing-commands.log 2>&1",
            "# Check dependency updates once daily (staggered so runs don't overlap)",
            "0 6 * * * cd /Users/simon/Projects/LinuxCommandLibrary && $cronPath " +
                "grok -p \"/check-updates\" $grokFlags " +
                ">> /Users/simon/Library/Logs/check-updates-linux.log 2>&1",
            "30 6 * * * cd /Users/simon/Projects/Braincup && $cronPath " +
                "grok -p \"/check-updates\" $grokFlags " +
                ">> /Users/simon/Library/Logs/check-updates-braincup.log 2>&1",
            "0 7 * * * cd /Users/simon/Projects/Kai && $cronPath " +
                "grok -p \"/check-updates\" $grokFlags " +
                ">> /Users/simon/Library/Logs/check-updates-kai.log 2>&1",
            "30 7 * * * cd /Users/simon/Projects/2-player-quiz && $cronPath " +
                "grok -p \"/check-updates\" $grokFlags " +
                ">> /Users/simon/Library/Logs/check-updates-2-player-quiz.log 2>&1",
        )

    @Test
    fun parsesTheFiveAgentJobsAndLeavesThePythonJobExternal() {
        val snapshot = CrontabParser.parse(crontab)

        assertEquals(6, snapshot.jobs.size, "every cron line should become a job")
        assertEquals(5, snapshot.scheduled.size, "the five grok lines are adoptable")
        assertEquals(1, snapshot.external.size, "the python line is external")
        assertTrue(snapshot.external.single().rawLine.contains("main.py"))
        assertTrue(snapshot.scheduled.all { it.source == LoopSource.Adopted })
    }

    @Test
    fun pullsApartEveryPieceOfAnAdoptedLine() {
        val job = CrontabParser.parse(crontab).scheduled.first { it.prompt == "/find-missing-commands" }

        assertEquals(ToolKind.Grok, job.tool)
        assertEquals("/Users/simon/Projects/LinuxCommandLibrary", job.workingDirectory)
        assertEquals("LinuxCommandLibrary", job.projectName)
        assertEquals(grokFlags, job.extraFlags)
        assertEquals("/Users/simon/Library/Logs/find-missing-commands.log", job.logPath)
        assertEquals("0 4,12,20 * * *", job.schedule.expression)
        assertEquals(
            "/Users/simon/.grok/bin:/Users/simon/.local/bin:/opt/homebrew/bin:/usr/bin:/bin",
            job.pathPrefix,
        )
        assertEquals("find-missing-commands-linuxcommandlibrary", job.name)
    }

    @Test
    fun renderingAnUnchangedSnapshotChangesNothing() {
        val snapshot = CrontabParser.parse(crontab)

        assertEquals(crontab, CrontabParser.render(crontab, snapshot.jobs))
    }

    @Test
    fun editingAnAdoptedJobRewritesOnlyThatLine() {
        val snapshot = CrontabParser.parse(crontab)
        val target = snapshot.scheduled.first { it.workingDirectory?.endsWith("/Kai") == true }
        val edited =
            target.copy(
                id = "orcaeye:a1b2c3d4",
                source = LoopSource.Managed,
                schedule = assertNotNull(CronSchedule.parse("15 9 * * *")),
            )
        val jobs = snapshot.jobs.map { if (it.id == target.id) edited else it }

        val rendered = CrontabParser.render(crontab, jobs)

        assertTrue(rendered.contains("# orcaeye id=orcaeye:a1b2c3d4 name=check-updates-kai"))
        assertTrue(
            rendered.any { it.startsWith("15 9 * * * cd '/Users/simon/Projects/Kai'") },
            "expected the rewritten line, got:\n${rendered.joinToString("\n")}",
        )
        // Every other original line survives untouched.
        crontab.filterNot { it.contains("/Projects/Kai") }.forEach { original ->
            assertTrue(rendered.contains(original), "lost line: $original")
        }
    }

    @Test
    fun deletingAJobRemovesOnlyItsLine() {
        val snapshot = CrontabParser.parse(crontab)
        val target = snapshot.scheduled.first { it.workingDirectory?.endsWith("/Braincup") == true }

        val rendered = CrontabParser.render(crontab, snapshot.jobs - target)

        assertEquals(crontab.size - 1, rendered.size)
        assertTrue(rendered.none { it.contains("/Projects/Braincup") })
        assertTrue(rendered.contains(crontab.first()), "the external python line must survive")
    }

    @Test
    fun disablingAJobCommentsItOutAndSurvivesAReparse() {
        val snapshot = CrontabParser.parse(crontab)
        val target = snapshot.scheduled.first { it.workingDirectory?.endsWith("/Kai") == true }
        val disabled = target.copy(id = "orcaeye:deadbeef", source = LoopSource.Managed, enabled = false)

        val rendered = CrontabParser.render(crontab, snapshot.jobs.map { if (it.id == target.id) disabled else it })
        assertTrue(rendered.any { it.startsWith("#DISABLED 0 7 * * *") })

        val reparsed = CrontabParser.parse(rendered).jobs.first { it.id == "orcaeye:deadbeef" }
        assertEquals(false, reparsed.enabled)
        assertEquals(LoopSource.Managed, reparsed.source)
        assertEquals("/check-updates", reparsed.prompt)
        assertEquals("/Users/simon/Projects/Kai", reparsed.workingDirectory)
        assertEquals(grokFlags, reparsed.extraFlags)
    }

    @Test
    fun appendsNewJobsAtTheEndWithoutDisturbingExistingLines() {
        val snapshot = CrontabParser.parse(crontab)
        val created =
            snapshot.scheduled
                .first()
                .copy(
                    id = "orcaeye:11112222",
                    name = "check-updates-orcaeye",
                    source = LoopSource.Managed,
                    workingDirectory = "/Users/simon/Projects/Orcaeye",
                    prompt = "/check-updates",
                    schedule = assertNotNull(CronSchedule.parse("45 8 * * *")),
                    logPath = "/Users/simon/Library/Logs/check-updates-orcaeye.log",
                )

        val rendered = CrontabParser.render(crontab, snapshot.jobs + created)

        assertEquals(crontab, rendered.dropLast(2), "existing lines must be identical")
        assertEquals("# orcaeye id=orcaeye:11112222 name=check-updates-orcaeye", rendered[rendered.size - 2])
        assertEquals(
            "45 8 * * * cd '/Users/simon/Projects/Orcaeye' && $cronPath " +
                "grok -p '/check-updates' $grokFlags " +
                ">> '/Users/simon/Library/Logs/check-updates-orcaeye.log' 2>&1",
            rendered.last(),
        )
    }

    @Test
    fun everyEightHoursRoundTripsThroughThePreset() {
        val schedule = assertNotNull(CronSchedule.parse("0 4,12,20 * * *"))

        val preset = SchedulePreset.from(schedule)

        assertEquals(SchedulePreset.EveryNHours(hours = 8, startHour = 4, minute = 0), preset)
        assertEquals("0 4,12,20 * * *", preset.toSchedule()?.expression)
    }

    @Test
    fun recognisesTheOtherPresets() {
        fun preset(expression: String) = SchedulePreset.from(assertNotNull(CronSchedule.parse(expression)))

        assertEquals(SchedulePreset.Hourly(minute = 0), preset("0 * * * *"))
        assertEquals(SchedulePreset.Daily(hour = 6, minute = 30), preset("30 6 * * *"))
        assertEquals(SchedulePreset.Weekly(dayOfWeek = 1, hour = 9, minute = 0), preset("0 9 * * 1"))
        assertEquals(SchedulePreset.Custom, preset("0 9 1 * *"))
        assertEquals(SchedulePreset.Custom, preset("0 5,6,13 * * *"))
    }

    @Test
    fun rejectsInvalidExpressions() {
        assertNull(CronSchedule.parse("0 9 * *"), "four fields is not a schedule")
        assertNull(CronSchedule.parse("0 99 * * *"), "hour out of range")
        assertNull(CronSchedule.parse("every day"), "not cron at all")
    }

    @Test
    fun computesTheNextRuns() {
        val schedule = assertNotNull(CronSchedule.parse("30 6 * * *"))
        val from = LocalDateTime(2026, 8, 1, 12, 0)

        assertEquals(
            listOf(
                LocalDateTime(2026, 8, 2, 6, 30),
                LocalDateTime(2026, 8, 3, 6, 30),
                LocalDateTime(2026, 8, 4, 6, 30),
            ),
            schedule.nextRuns(from, count = 3),
        )
    }

    @Test
    fun computesTheNextRunsForAMultiHourSchedule() {
        val schedule = assertNotNull(CronSchedule.parse("0 4,12,20 * * *"))
        val from = LocalDateTime(2026, 8, 1, 13, 0)

        assertEquals(
            listOf(
                LocalDateTime(2026, 8, 1, 20, 0),
                LocalDateTime(2026, 8, 2, 4, 0),
                LocalDateTime(2026, 8, 2, 12, 0),
            ),
            schedule.nextRuns(from, count = 3),
        )
    }

    @Test
    fun weeklyScheduleLandsOnTheRightWeekday() {
        // 2026-08-01 is a Saturday, so the next Monday is the 3rd.
        val schedule = assertNotNull(CronSchedule.parse("0 9 * * 1"))

        assertEquals(
            listOf(LocalDateTime(2026, 8, 3, 9, 0), LocalDateTime(2026, 8, 10, 9, 0)),
            schedule.nextRuns(LocalDateTime(2026, 8, 1, 12, 0), count = 2),
        )
    }

    @Test
    fun commentsBlankLinesAndEnvironmentAssignmentsPassThrough() {
        val lines =
            listOf(
                "MAILTO=\"\"",
                "",
                "# a note",
                "0 9 * * * cd /tmp && grok -p '/x' >> /tmp/x.log 2>&1",
            )
        val snapshot = CrontabParser.parse(lines)

        assertEquals(1, snapshot.jobs.size, "only the cron line is a job")
        assertEquals(lines, CrontabParser.render(lines, snapshot.jobs))
    }

    @Test
    fun parsesClaudeAndOpencodeInvocations() {
        val lines =
            listOf(
                "0 9 * * * cd /tmp && claude -p '/check-updates' --dangerously-skip-permissions",
                "0 10 * * * cd /tmp && opencode run \"summarise the diff\"",
            )
        val jobs = CrontabParser.parse(lines).jobs

        assertEquals(ToolKind.Claude, jobs[0].tool)
        assertEquals("/check-updates", jobs[0].prompt)
        assertEquals("--dangerously-skip-permissions", jobs[0].extraFlags)
        assertNull(jobs[0].logPath)

        assertEquals(ToolKind.OpenCode, jobs[1].tool)
        assertEquals("summarise the diff", jobs[1].prompt)
    }
}
