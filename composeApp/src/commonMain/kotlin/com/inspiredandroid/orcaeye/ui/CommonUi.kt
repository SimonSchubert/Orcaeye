package com.inspiredandroid.orcaeye.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
                CircularProgressIndicator(
                    modifier =
                    Modifier
                        .padding(end = 8.dp)
                        .height(18.dp)
                        .width(18.dp),
                    strokeWidth = 2.dp,
                )
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
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(
            onClick = { expanded = true },
            modifier = Modifier.hoverHand(),
        ) {
            Text(label)
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            options.forEach { (text, onSelect) ->
                DropdownMenuItem(
                    text = { Text(text) },
                    onClick = {
                        expanded = false
                        onSelect()
                    },
                    modifier = Modifier.hoverHand(),
                )
            }
        }
    }
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

/** Small pill used for skill origins and for the crontab/managed badges on Loops. */
@Composable
internal fun Badge(label: String) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        shape = RoundedCornerShape(8.dp),
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
