package com.inspiredandroid.orcaeye.model

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.number
import kotlinx.datetime.plus
import kotlin.random.Random

/**
 * One field of a cron expression. Supports the forms that show up in real crontabs:
 * `*`, `5`, `4,12,20`, `1-5`, `*&#47;15` and `0-20/4`.
 */
data class CronField(
    val raw: String,
) {
    fun values(
        min: Int,
        max: Int,
    ): List<Int> = raw
        .split(',')
        .flatMap { part -> partValues(part.trim(), min, max) }
        .filter { it in min..max }
        .distinct()
        .sorted()

    fun matches(
        value: Int,
        min: Int,
        max: Int,
    ): Boolean = value in values(min, max)

    /** True when this field imposes no constraint at all. */
    val isWildcard: Boolean get() = raw.trim() == "*"

    private fun partValues(
        part: String,
        min: Int,
        max: Int,
    ): List<Int> {
        val step = part.substringAfter('/', "").toIntOrNull()
        val range = part.substringBefore('/')
        val base =
            when {
                range == "*" -> (min..max).toList()
                range.contains('-') -> {
                    val from = range.substringBefore('-').toIntOrNull() ?: return emptyList()
                    val to = range.substringAfter('-').toIntOrNull() ?: return emptyList()
                    if (from > to) return emptyList()
                    (from..to).toList()
                }
                else -> listOfNotNull(range.toIntOrNull())
            }
        if (step == null) return base
        if (step <= 0) return emptyList()
        // Steps count from the start of the range, matching cron semantics.
        return base.filterIndexed { index, _ -> index % step == 0 }
    }

    companion object {
        val WILDCARD = CronField("*")

        /** Rejects anything we cannot evaluate, so the editor can show a validation error. */
        fun parse(
            raw: String,
            min: Int,
            max: Int,
        ): CronField? {
            val trimmed = raw.trim()
            if (trimmed.isEmpty()) return null
            val field = CronField(trimmed)
            val valid =
                trimmed.split(',').all { part ->
                    val range = part.substringBefore('/')
                    val stepText = part.substringAfter('/', "")
                    val stepOk = stepText.isEmpty() || (stepText.toIntOrNull()?.let { it > 0 } == true)
                    val rangeOk =
                        when {
                            range == "*" -> true
                            range.contains('-') -> {
                                val from = range.substringBefore('-').toIntOrNull()
                                val to = range.substringAfter('-').toIntOrNull()
                                from != null && to != null && from <= to && from >= min && to <= max
                            }
                            else -> range.toIntOrNull()?.let { it in min..max } == true
                        }
                    stepOk && rangeOk
                }
            return if (valid && field.values(min, max).isNotEmpty()) field else null
        }
    }
}

/** A five-field cron expression: minute hour day-of-month month day-of-week. */
data class CronSchedule(
    val minute: CronField,
    val hour: CronField,
    val dayOfMonth: CronField,
    val month: CronField,
    val dayOfWeek: CronField,
) {
    val expression: String
        get() = listOf(minute, hour, dayOfMonth, month, dayOfWeek).joinToString(" ") { it.raw }

    val minutes: List<Int> get() = minute.values(MINUTE_MIN, MINUTE_MAX)
    val hours: List<Int> get() = hour.values(HOUR_MIN, HOUR_MAX)

    /**
     * The next [count] firing times at or after [from], walking day by day and only
     * enumerating the matching hours and minutes inside a matching day.
     */
    fun nextRuns(
        from: LocalDateTime,
        count: Int = 3,
    ): List<LocalDateTime> {
        val hourValues = hours
        val minuteValues = minutes
        if (hourValues.isEmpty() || minuteValues.isEmpty()) return emptyList()

        val result = mutableListOf<LocalDateTime>()
        var date = from.date
        var dayOffset = 0
        while (result.size < count && dayOffset <= MAX_LOOKAHEAD_DAYS) {
            if (matchesDate(date)) {
                for (h in hourValues) {
                    for (m in minuteValues) {
                        val candidate = LocalDateTime(date.year, date.month, date.day, h, m)
                        if (candidate > from) {
                            result += candidate
                            if (result.size == count) return result
                        }
                    }
                }
            }
            date = date.plus(1, DateTimeUnit.DAY)
            dayOffset++
        }
        return result
    }

    /**
     * Cron ORs day-of-month and day-of-week when both are restricted, and ANDs them
     * with the month.
     */
    private fun matchesDate(date: LocalDate): Boolean {
        if (!month.matches(date.month.number, MONTH_MIN, MONTH_MAX)) return false
        // Cron accepts both 0 and 7 for Sunday.
        val isoDay = date.dayOfWeek.isoDayNumber
        val cronDay = if (isoDay == 7) 0 else isoDay
        val domRestricted = !dayOfMonth.isWildcard
        val dowRestricted = !dayOfWeek.isWildcard
        val domMatch = dayOfMonth.matches(date.day, DOM_MIN, DOM_MAX)
        val dowMatch =
            dayOfWeek.matches(cronDay, DOW_MIN, DOW_MAX) ||
                dayOfWeek.matches(isoDay, DOW_MIN, DOW_MAX)
        return when {
            domRestricted && dowRestricted -> domMatch || dowMatch
            domRestricted -> domMatch
            dowRestricted -> dowMatch
            else -> true
        }
    }

    companion object {
        const val MINUTE_MIN = 0
        const val MINUTE_MAX = 59
        const val HOUR_MIN = 0
        const val HOUR_MAX = 23
        const val DOM_MIN = 1
        const val DOM_MAX = 31
        const val MONTH_MIN = 1
        const val MONTH_MAX = 12
        const val DOW_MIN = 0
        const val DOW_MAX = 7
        private const val MAX_LOOKAHEAD_DAYS = 366

        val DAILY_9AM = CronSchedule(CronField("0"), CronField("9"), CronField.WILDCARD, CronField.WILDCARD, CronField.WILDCARD)

        /** Returns null when [expression] is not a valid five-field cron expression. */
        fun parse(expression: String): CronSchedule? {
            val parts = expression.trim().split(Regex("\\s+"))
            if (parts.size != 5) return null
            val minute = CronField.parse(parts[0], MINUTE_MIN, MINUTE_MAX) ?: return null
            val hour = CronField.parse(parts[1], HOUR_MIN, HOUR_MAX) ?: return null
            val dayOfMonth = CronField.parse(parts[2], DOM_MIN, DOM_MAX) ?: return null
            val month = CronField.parse(parts[3], MONTH_MIN, MONTH_MAX) ?: return null
            val dayOfWeek = CronField.parse(parts[4], DOW_MIN, DOW_MAX) ?: return null
            return CronSchedule(minute, hour, dayOfMonth, month, dayOfWeek)
        }
    }
}

/** The interval shapes the picker offers. [Custom] means "only the raw expression fits". */
sealed interface SchedulePreset {
    data class Hourly(
        val minute: Int,
    ) : SchedulePreset

    data class EveryNHours(
        val hours: Int,
        val startHour: Int,
        val minute: Int,
    ) : SchedulePreset

    data class Daily(
        val hour: Int,
        val minute: Int,
    ) : SchedulePreset

    data class Weekly(
        val dayOfWeek: Int,
        val hour: Int,
        val minute: Int,
    ) : SchedulePreset

    data object Custom : SchedulePreset

    fun toSchedule(): CronSchedule? = when (this) {
        is Hourly ->
            CronSchedule.parse("$minute * * * *")
        is EveryNHours -> {
            val hoursList = generateSequence(startHour) { it + hours }.takeWhile { it <= 23 }.toList()
            if (hoursList.isEmpty()) null else CronSchedule.parse("$minute ${hoursList.joinToString(",")} * * *")
        }
        is Daily -> CronSchedule.parse("$minute $hour * * *")
        is Weekly -> CronSchedule.parse("$minute $hour * * $dayOfWeek")
        Custom -> null
    }

    val label: String
        get() = when (this) {
            is Hourly -> "Hourly"
            is EveryNHours -> "Every N hours"
            is Daily -> "Daily"
            is Weekly -> "Weekly"
            Custom -> "Custom"
        }

    companion object {
        val options: List<SchedulePreset> =
            listOf(
                Hourly(minute = 0),
                EveryNHours(hours = 8, startHour = 0, minute = 0),
                Daily(hour = 9, minute = 0),
                Weekly(dayOfWeek = 1, hour = 9, minute = 0),
                Custom,
            )

        /** Recognises a schedule as one of the presets, or [Custom] when it doesn't fit. */
        fun from(schedule: CronSchedule): SchedulePreset {
            if (!schedule.dayOfMonth.isWildcard || !schedule.month.isWildcard) return Custom
            val minutes = schedule.minutes
            if (minutes.size != 1) return Custom
            val minute = minutes.first()
            val hours = schedule.hours

            if (!schedule.dayOfWeek.isWildcard) {
                val days = schedule.dayOfWeek.values(CronSchedule.DOW_MIN, CronSchedule.DOW_MAX)
                if (days.size == 1 && hours.size == 1) {
                    return Weekly(dayOfWeek = days.first(), hour = hours.first(), minute = minute)
                }
                return Custom
            }

            if (schedule.hour.isWildcard) return Hourly(minute = minute)
            if (hours.size == 1) return Daily(hour = hours.first(), minute = minute)
            // Evenly spaced hours that run out before the next step would wrap past 23,
            // e.g. 4,12,20 → every 8 hours starting at 04:00.
            val step = hours[1] - hours[0]
            if (step <= 0) return Custom
            val evenlySpaced = hours.zipWithNext().all { (a, b) -> b - a == step }
            val coversRest = hours.last() + step > 23
            return if (evenlySpaced && coversRest) {
                EveryNHours(hours = step, startHour = hours.first(), minute = minute)
            } else {
                Custom
            }
        }
    }
}

/** Where a crontab line came from, which decides how much of it Orcaeye may rewrite. */
enum class LoopSource {
    /** Written by Orcaeye and carrying its marker comment. Fully editable. */
    Managed,

    /** A hand-written agent-CLI line Orcaeye understood. Editing adopts it. */
    Adopted,

    /** Anything else. Listed read-only and copied through untouched. */
    External,
}

/** One scheduled run: a cron expression plus the agent-CLI command it fires. */
data class LoopJob(
    val id: String,
    val name: String,
    val source: LoopSource,
    val enabled: Boolean,
    val schedule: CronSchedule,
    val tool: ToolKind? = null,
    val workingDirectory: String? = null,
    val prompt: String = "",
    val extraFlags: String = "",
    val pathPrefix: String? = null,
    val logPath: String? = null,
    val rawLine: String = "",
) {
    val projectName: String? get() = workingDirectory?.trimEnd('/')?.substringAfterLast('/')

    val editable: Boolean get() = source != LoopSource.External

    /**
     * Marks the job as Orcaeye-managed so [LoopSource.Adopted] lines get rewritten into a
     * managed block on the next write. Managed jobs keep their existing id.
     */
    fun asManaged(): LoopJob = if (source == LoopSource.Managed) {
        this
    } else {
        copy(id = newJobId(), source = LoopSource.Managed)
    }
}

/** A short, stable id for the `# orcaeye id=…` marker comment. */
fun newJobId(): String {
    val hex = (0 until 8).map { HEX[Random.nextInt(HEX.length)] }.joinToString("")
    return "orcaeye:$hex"
}

private const val HEX = "0123456789abcdef"

data class LoopSnapshot(
    val jobs: List<LoopJob> = emptyList(),
    val crontabAvailable: Boolean = true,
    val warnings: List<String> = emptyList(),
) {
    val scheduled: List<LoopJob> get() = jobs.filter { it.source != LoopSource.External }
    val external: List<LoopJob> get() = jobs.filter { it.source == LoopSource.External }
}
