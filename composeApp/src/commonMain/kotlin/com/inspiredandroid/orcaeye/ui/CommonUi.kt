package com.inspiredandroid.orcaeye.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.inspiredandroid.orcaeye.model.ToolKind
import com.inspiredandroid.orcaeye.ui.icons.ToolIcon

/**
 * Chrome shared by the Context and Loops screens so both look like one app.
 */

@Composable
internal fun DetailToolbar(
    title: String,
    subtitle: String?,
    loading: Boolean,
    onRefresh: () -> Unit,
    actions: @Composable () -> Unit = {},
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineSmall)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (loading) {
                InlineSpinner(modifier = Modifier.padding(end = 8.dp))
            }
            actions()
            TextButton(
                onClick = onRefresh,
                enabled = !loading,
                modifier = Modifier.hoverHand(),
            ) {
                Text("Refresh")
            }
        }
    }
}

@Composable
internal fun SectionCard(
    title: String,
    subtitle: String,
    tool: ToolKind? = null,
    onTitleClick: (() -> Unit)? = null,
    trailing: @Composable () -> Unit = {},
    content: @Composable () -> Unit,
) {
    PlainCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Only the title itself opens the tool, so [trailing] stays independently clickable.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier =
                if (onTitleClick != null) {
                    Modifier
                        .hoverClickable(onClick = onTitleClick)
                        .padding(vertical = 2.dp)
                } else {
                    Modifier
                },
            ) {
                if (tool != null) {
                    ToolIcon(tool = tool, size = 22.dp)
                }
                Text(title, style = MaterialTheme.typography.titleMedium)
                if (onTitleClick != null) {
                    Text(
                        "Open",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            trailing()
        }
        if (subtitle.isNotBlank()) {
            Text(
                subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        content()
    }
}

/** The card shell used by [SectionCard] and by any screen that wants its own header. */
@Composable
internal fun PlainCard(content: @Composable ColumnScope.() -> Unit) {
    CardShell(padding = 16.dp, spacing = 6.dp, content = content)
}

/** Tighter [PlainCard] for list rows, so more of them fit on screen. */
@Composable
internal fun CompactCard(content: @Composable ColumnScope.() -> Unit) {
    CardShell(padding = 12.dp, spacing = 3.dp, content = content)
}

@Composable
private fun CardShell(
    padding: Dp,
    spacing: Dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(
            Modifier.padding(padding),
            verticalArrangement = Arrangement.spacedBy(spacing),
            content = content,
        )
    }
}

/** A card that says why a list is empty, or why a feature is unavailable. */
@Composable
internal fun MessageCard(
    title: String,
    message: String,
) {
    PlainCard {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(
            message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * One entry in a card list. Skills, memories, rules and agent files all read the same way —
 * tool icon, name, optional badge, then dimmer lines of detail — so they share one row.
 *
 * [muted] is for entries the user did not write (a CLI's bundled skills): same layout,
 * less contrast.
 */
@Composable
internal fun ItemRow(
    title: String,
    details: List<String>,
    tool: ToolKind? = null,
    description: String? = null,
    muted: Boolean = false,
    badge: @Composable (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    val secondaryColor =
        if (muted) {
            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        }
    Column(
        modifier =
        Modifier
            .fillMaxWidth()
            .hoverClickable(onClick = onClick)
            .padding(vertical = 6.dp, horizontal = 4.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            tool?.let { ToolIcon(tool = it, size = 14.dp) }
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (muted) FontWeight.Normal else FontWeight.Medium,
                color =
                if (muted) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
            badge?.invoke()
        }
        description?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = secondaryColor,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        details.forEach { detail ->
            Text(
                detail,
                style = MaterialTheme.typography.labelSmall,
                color = secondaryColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * Create affordance for a scope (a tool, or a project). Empty sections are hidden, so the
 * per-section "Add" buttons would disappear along with them — this keeps one entry point
 * that is visible whether or not the scope already holds anything.
 */
@Composable
internal fun AddMenu(
    options: List<Pair<String, () -> Unit>>,
    label: String = "New",
) {
    if (options.isEmpty()) return
    MenuButton(
        value = label,
        options = options,
        onSelect = { select -> select() },
    )
}

/** A labelled picker: the label above, the current value as the button that opens the menu. */
@Composable
internal fun <T> DropdownField(
    label: String,
    value: String,
    options: List<Pair<String, T>>,
    onSelect: (T) -> Unit,
) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium)
        MenuButton(value = value, options = options, onSelect = onSelect, maxMenuHeight = 320.dp)
    }
}

/** The button-plus-dropdown shared by [AddMenu] and [DropdownField]. */
@Composable
private fun <T> MenuButton(
    value: String,
    options: List<Pair<String, T>>,
    onSelect: (T) -> Unit,
    // Unspecified leaves the menu unconstrained, the way a short menu wants it.
    maxMenuHeight: Dp = Dp.Unspecified,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(
            onClick = { expanded = true },
            modifier = Modifier.hoverHand(),
        ) {
            Text(value, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.heightIn(max = maxMenuHeight),
        ) {
            options.forEach { (text, item) ->
                DropdownMenuItem(
                    text = { Text(text) },
                    onClick = {
                        expanded = false
                        onSelect(item)
                    },
                    modifier = Modifier.hoverHand(),
                )
            }
        }
    }
}

/** Which CLI a new skill, rule, memory or loop belongs to. */
@Composable
internal fun ToolPicker(
    label: String,
    tools: List<ToolKind>,
    selected: ToolKind,
    onSelect: (ToolKind) -> Unit,
) {
    Text(label, style = MaterialTheme.typography.labelMedium)
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        tools.forEach { option ->
            FilterChip(
                selected = selected == option,
                onClick = { onSelect(option) },
                label = { Text(option.displayName) },
                leadingIcon = { ToolIcon(tool = option, size = 14.dp) },
                modifier = Modifier.hoverHand(),
            )
        }
    }
}

/** Last stop before something is deleted from disk or from the crontab. */
@Composable
internal fun ConfirmDeleteDialog(
    title: String,
    message: String,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onConfirm, modifier = Modifier.hoverHand()) {
                Text("Delete", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel, modifier = Modifier.hoverHand()) {
                Text("Cancel")
            }
        },
    )
}

@Composable
internal fun SubHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
    )
}

@Composable
internal fun EmptyHint(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * Small pill used for skill origins and for the crontab/managed badges on Loops.
 * [outlined] marks the entries a CLI ships with, which sit one step back from the rest.
 */
@Composable
internal fun Badge(
    label: String,
    outlined: Boolean = false,
) {
    Surface(
        color =
        if (outlined) {
            MaterialTheme.colorScheme.surfaceContainerHigh
        } else {
            MaterialTheme.colorScheme.surfaceContainerHighest
        },
        shape = RoundedCornerShape(8.dp),
        border = if (outlined) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant) else null,
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (outlined) 0.85f else 1f),
        )
    }
}

/** Progress spinner sized to sit inside a row of text or buttons. */
@Composable
internal fun InlineSpinner(
    modifier: Modifier = Modifier,
    size: Dp = 18.dp,
    strokeWidth: Dp = 2.dp,
) {
    CircularProgressIndicator(
        modifier =
        modifier
            .height(size)
            .width(size),
        strokeWidth = strokeWidth,
    )
}

/** Full-size spinner centred in whatever space [modifier] gives it. */
@Composable
internal fun LoadingBox(modifier: Modifier = Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}
