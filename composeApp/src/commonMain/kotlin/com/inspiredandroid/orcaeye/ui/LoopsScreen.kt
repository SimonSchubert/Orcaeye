package com.inspiredandroid.orcaeye.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.inspiredandroid.orcaeye.data.CrontabParser
import com.inspiredandroid.orcaeye.data.slug
import com.inspiredandroid.orcaeye.model.CronSchedule
import com.inspiredandroid.orcaeye.model.LoopJob
import com.inspiredandroid.orcaeye.model.LoopSource
import com.inspiredandroid.orcaeye.model.ProjectInventory
import com.inspiredandroid.orcaeye.model.SchedulePreset
import com.inspiredandroid.orcaeye.model.SkillItem
import com.inspiredandroid.orcaeye.model.ToolKind
import com.inspiredandroid.orcaeye.ui.icons.ToolIcon
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.daysUntil

/**
 * Scheduled agent-CLI runs, backed by the user's crontab.
 */
@Composable
internal fun LoopsScreen(
    state: LoopsUiState,
    projects: List<ProjectInventory>,
    systemSkills: List<SkillItem>,
    installedTools: List<ToolKind>,
    actions: LoopsActions,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize()) {
        ScrollableLazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                DetailToolbar(
                    title = "Loops",
                    subtitle = loopsSubtitle(state),
                    loading = state.loading,
                    onRefresh = actions.onRefresh,
                    actions = {
                        TextButton(
                            onClick = actions.onCreate,
                            enabled = state.crontabAvailable,
                            modifier = Modifier.hoverHand(),
                        ) {
                            Text("Add")
                        }
                    },
                )
            }

            if (!state.crontabAvailable) {
                item {
                    MessageCard(
                        title = "crontab unavailable",
                        message = "Orcaeye could not run `crontab -l`, so scheduling is read-only here.",
                    )
                }
            }
            state.snapshot?.warnings?.forEach { warning ->
                item {
                    Text(
                        warning,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            if (state.loading && state.snapshot == null) {
                item {
                    LoadingBox(Modifier.fillMaxWidth().padding(32.dp))
                }
            }

            if (state.snapshot != null && state.jobs.isEmpty()) {
                item {
                    MessageCard(
                        title = "No scheduled runs yet",
                        message = "Add one to run a skill or prompt on a schedule — it lands in your crontab.",
                    )
                }
            }

            items(state.jobs, key = { it.id }) { job ->
                JobCard(job = job, nextRun = state.nextRuns[job.id], now = state.now, actions = actions)
            }

            if (state.externalJobs.isNotEmpty()) {
                item {
                    SubHeader("Other crontab entries (${state.externalJobs.size})")
                }
                items(state.externalJobs, key = { it.id }) { job ->
                    ExternalJobCard(job)
                }
            }
        }
    }

    state.editor?.let { editor ->
        LoopEditorDialog(
            editor = editor,
            projects = projects,
            systemSkills = systemSkills,
            installedTools = installedTools,
            onChange = actions.onEditorChange,
            onSave = actions.onEditorSave,
            onCancel = actions.onEditorCancel,
        )
    }

    state.deleteConfirm?.let { job ->
        ConfirmDeleteDialog(
            title = "Delete loop?",
            message = "Remove \"${job.name}\" from your crontab? This cannot be undone.",
            onConfirm = actions.onConfirmDelete,
            onCancel = actions.onCancelDelete,
        )
    }
}

private fun loopsSubtitle(state: LoopsUiState): String {
    val snapshot = state.snapshot ?: return "Reading crontab…"
    val enabled = state.jobs.count { it.enabled }
    val total = state.jobs.size
    val external = snapshot.external.size
    val base =
        when {
            total == 0 -> "No scheduled runs in your crontab"
            enabled == total -> "$total scheduled run${plural(total)} in your crontab"
            else -> "$enabled of $total scheduled run${plural(total)} enabled"
        }
    return if (external > 0) "$base · $external other entr${if (external == 1) "y" else "ies"}" else base
}

private fun plural(count: Int) = if (count == 1) "" else "s"

@Composable
private fun JobCard(
    job: LoopJob,
    nextRun: LocalDateTime?,
    now: LocalDateTime?,
    actions: LoopsActions,
) {
    CompactCard {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            job.tool?.let { ToolIcon(tool = it, size = 18.dp) }
            Text(
                job.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (job.source == LoopSource.Adopted) {
                Badge("crontab")
            }
            Box(Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                CompactButton("Run now") { actions.onRunNow(job) }
                CompactButton("Log", enabled = !job.logPath.isNullOrBlank()) { actions.onViewLog(job) }
                CompactButton("Edit") { actions.onEdit(job) }
                CompactButton("Delete", color = MaterialTheme.colorScheme.error) { actions.onRequestDelete(job) }
                Switch(
                    checked = job.enabled,
                    onCheckedChange = { actions.onToggleEnabled(job) },
                    modifier = Modifier.padding(start = 4.dp).hoverHand(),
                )
            }
        }

        Text(
            buildString {
                append(describe(job.schedule))
                job.projectName?.let {
                    append(" · ")
                    append(it)
                }
                append(" · ")
                append(job.prompt)
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            if (!job.enabled) {
                "Disabled"
            } else {
                nextRunLabel(nextRun, now) ?: job.schedule.expression
            },
            style = MaterialTheme.typography.labelSmall,
            color =
            if (job.enabled) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            maxLines = 1,
        )
    }
}

@Composable
private fun CompactButton(
    label: String,
    enabled: Boolean = true,
    color: Color = MaterialTheme.colorScheme.primary,
    onClick: () -> Unit,
) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        color = if (enabled) color else color.copy(alpha = 0.38f),
        modifier =
        Modifier
            .hoverClickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

@Composable
private fun ExternalJobCard(job: LoopJob) {
    CompactCard {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(describe(job.schedule), style = MaterialTheme.typography.bodySmall)
            Badge("not an agent job")
        }
        Text(
            job.rawLine,
            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// region editor dialog

@Composable
private fun LoopEditorDialog(
    editor: LoopEditorState,
    projects: List<ProjectInventory>,
    systemSkills: List<SkillItem>,
    installedTools: List<ToolKind>,
    onChange: (LoopEditorState) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
) {
    val tools = installedTools.ifEmpty { ToolKind.entries }
    val project = projects.firstOrNull { it.path == editor.projectPath }
    val skillNames =
        remember(editor.tool, editor.projectPath, systemSkills, project) {
            (systemSkills.filter { it.tool == editor.tool } + project?.skills?.filter { it.tool == editor.tool }.orEmpty())
                .map { "/${it.name}" }
                .distinct()
                .sorted()
        }

    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(if (editor.isNew) "New loop" else "Edit loop") },
        text = {
            Column(
                modifier = Modifier.widthIn(min = 420.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ToolPicker(
                    label = "CLI",
                    tools = tools,
                    selected = editor.tool,
                    onSelect = { onChange(editor.copy(tool = it)) },
                )

                DropdownField(
                    label = "Project",
                    value = editor.projectPath?.substringAfterLast('/') ?: "(no working directory)",
                    options =
                    listOf<Pair<String, String?>>("(no working directory)" to null) +
                        projects.map { it.name to it.path },
                    onSelect = { onChange(editor.copy(projectPath = it)) },
                )

                OutlinedTextField(
                    value = editor.prompt,
                    onValueChange = { onChange(editor.copy(prompt = it)) },
                    label = { Text("Skill or prompt") },
                    placeholder = { Text("/check-updates") },
                    singleLine = true,
                    isError = editor.prompt.isBlank(),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (skillNames.isNotEmpty()) {
                    DropdownField(
                        label = "Pick a skill",
                        value = skillNames.firstOrNull { it == editor.prompt } ?: "Choose…",
                        options = skillNames.map { it to it },
                        onSelect = { onChange(editor.copy(prompt = it)) },
                    )
                }

                ScheduleControls(editor = editor, onChange = onChange)

                OutlinedTextField(
                    value = editor.expression,
                    onValueChange = { onChange(editor.copy(expression = it)) },
                    label = { Text("Cron expression") },
                    singleLine = true,
                    isError = !editor.expressionValid,
                    supportingText = {
                        Text(
                            if (editor.expressionValid) describe(editor.schedule) else "Not a valid cron expression",
                            style = MaterialTheme.typography.labelSmall,
                        )
                    },
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (editor.nextRuns.isNotEmpty()) {
                    Text(
                        "Next runs: " + editor.nextRuns.joinToString(" · ") { formatRun(it) },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                OutlinedTextField(
                    value = editor.name,
                    onValueChange = { onChange(editor.copy(name = slug(it))) },
                    label = { Text("Name") },
                    singleLine = true,
                    isError = editor.name.isBlank(),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = editor.logPath,
                    onValueChange = { onChange(editor.copy(logPath = it)) },
                    label = { Text("Log file") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = editor.extraFlags,
                    onValueChange = { onChange(editor.copy(extraFlags = it)) },
                    label = { Text("Extra CLI flags") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                SubHeader("Command")
                Text(
                    previewCommand(editor),
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onSave,
                enabled = editor.canSave,
                modifier = Modifier.hoverHand(),
            ) {
                Text(if (editor.isNew) "Create" else "Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel, modifier = Modifier.hoverHand()) {
                Text("Cancel")
            }
        },
    )
}

/** Preset picker plus whichever fields that preset needs. */
@Composable
private fun ScheduleControls(
    editor: LoopEditorState,
    onChange: (LoopEditorState) -> Unit,
) {
    fun setPreset(preset: SchedulePreset) = onChange(editor.copy(preset = preset))

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        DropdownField(
            label = "Schedule",
            value = editor.preset.label,
            options = SchedulePreset.options.map { it.label to it },
            onSelect = ::setPreset,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            when (val preset = editor.preset) {
                is SchedulePreset.Hourly ->
                    NumberField("Minute", preset.minute, 0..59) { setPreset(preset.copy(minute = it)) }
                is SchedulePreset.EveryNHours -> {
                    NumberField("Every (h)", preset.hours, 1..23) { setPreset(preset.copy(hours = it)) }
                    NumberField("From (h)", preset.startHour, 0..23) { setPreset(preset.copy(startHour = it)) }
                    NumberField("Minute", preset.minute, 0..59) { setPreset(preset.copy(minute = it)) }
                }
                is SchedulePreset.Daily -> {
                    NumberField("Hour", preset.hour, 0..23) { setPreset(preset.copy(hour = it)) }
                    NumberField("Minute", preset.minute, 0..59) { setPreset(preset.copy(minute = it)) }
                }
                is SchedulePreset.Weekly -> {
                    DropdownField(
                        label = "Day",
                        value = weekdayName(preset.dayOfWeek),
                        options = (0..6).map { weekdayName(it) to it },
                        onSelect = { setPreset(preset.copy(dayOfWeek = it)) },
                    )
                    NumberField("Hour", preset.hour, 0..23) { setPreset(preset.copy(hour = it)) }
                    NumberField("Minute", preset.minute, 0..59) { setPreset(preset.copy(minute = it)) }
                }
                SchedulePreset.Custom ->
                    Text(
                        "Edit the cron expression below.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
            }
        }
    }
}

@Composable
private fun NumberField(
    label: String,
    value: Int,
    range: IntRange,
    onChange: (Int) -> Unit,
) {
    OutlinedTextField(
        value = value.toString(),
        onValueChange = { text ->
            text.filter { it.isDigit() }.take(2).toIntOrNull()?.takeIf { it in range }?.let(onChange)
        },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.width(96.dp),
    )
}

// endregion

// region formatting

/** A plain-English reading of a cron expression, e.g. "Every 8 hours from 04:00". */
internal fun describe(schedule: CronSchedule?): String {
    if (schedule == null) return "Invalid schedule"
    return when (val preset = SchedulePreset.from(schedule)) {
        is SchedulePreset.Hourly -> "Every hour at :${two(preset.minute)}"
        is SchedulePreset.EveryNHours ->
            "Every ${preset.hours} hours from ${time(preset.startHour, preset.minute)}"
        is SchedulePreset.Daily -> "Daily at ${time(preset.hour, preset.minute)}"
        is SchedulePreset.Weekly ->
            "Every ${weekdayName(preset.dayOfWeek)} at ${time(preset.hour, preset.minute)}"
        SchedulePreset.Custom -> schedule.expression
    }
}

internal fun formatRun(value: LocalDateTime): String = "${weekdayName(value.dayOfWeek.ordinal.plus(1) % 7)} ${two(value.day)} ${monthName(value.month.ordinal + 1)} " +
    time(value.hour, value.minute)

/** Relative label for a job card, e.g. "Next run in 2 hours". */
internal fun nextRunLabel(
    nextRun: LocalDateTime?,
    now: LocalDateTime?,
): String? {
    if (nextRun == null || now == null) return null
    return "Next run in ${formatRunIn(nextRun, from = now)}"
}

/**
 * Human-readable duration until [value], e.g. "2 hours", "5 minutes", "1 day".
 * Floors to the largest unit under the next threshold (minutes < 1h, hours < 1d, else days).
 */
internal fun formatRunIn(
    value: LocalDateTime,
    from: LocalDateTime,
): String {
    val totalMinutes = minutesBetween(from, value)
    if (totalMinutes <= 0) return "less than a minute"
    return when {
        totalMinutes < 60 -> unitLabel(totalMinutes.toInt(), "minute")
        totalMinutes < 60 * 24 -> unitLabel((totalMinutes / 60).toInt(), "hour")
        else -> unitLabel((totalMinutes / (60 * 24)).toInt(), "day")
    }
}

private fun unitLabel(
    count: Int,
    unit: String,
) = if (count == 1) "1 $unit" else "$count ${unit}s"

/** Whole minutes from [from] to [to] using local wall time (cron is minute-resolution). */
internal fun minutesBetween(
    from: LocalDateTime,
    to: LocalDateTime,
): Long {
    val days = from.date.daysUntil(to.date)
    val fromMins = from.hour * 60L + from.minute
    val toMins = to.hour * 60L + to.minute
    return days * 24L * 60 + (toMins - fromMins)
}

private fun time(
    hour: Int,
    minute: Int,
) = "${two(hour)}:${two(minute)}"

private fun two(value: Int) = value.toString().padStart(2, '0')

private fun weekdayName(dayOfWeek: Int) = listOf(
    "Sun",
    "Mon",
    "Tue",
    "Wed",
    "Thu",
    "Fri",
    "Sat",
)[dayOfWeek.coerceIn(0, 6)]

private fun monthName(month: Int) = listOf(
    "Jan",
    "Feb",
    "Mar",
    "Apr",
    "May",
    "Jun",
    "Jul",
    "Aug",
    "Sep",
    "Oct",
    "Nov",
    "Dec",
)[(month - 1).coerceIn(0, 11)]

private fun previewCommand(editor: LoopEditorState): String = CrontabParser.buildCommand(editor.toJob())

// endregion
