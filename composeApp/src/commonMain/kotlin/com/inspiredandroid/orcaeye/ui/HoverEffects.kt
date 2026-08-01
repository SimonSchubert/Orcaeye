package com.inspiredandroid.orcaeye.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Hand cursor on desktop for any clickable control.
 */
fun Modifier.hoverHand(): Modifier = pointerHoverIcon(PointerIcon.Hand)

/**
 * Row/list-item style: hand cursor + light hover wash.
 */
@Composable
fun Modifier.hoverClickable(
    enabled: Boolean = true,
    selected: Boolean = false,
    selectedColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    hoverColor: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f),
    cornerRadius: Dp = 6.dp,
    onClick: () -> Unit,
): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    val bg =
        when {
            selected -> selectedColor
            enabled && hovered -> hoverColor
            else -> Color.Transparent
        }
    return this
        .clip(RoundedCornerShape(cornerRadius))
        .background(bg)
        .hoverable(interactionSource = interactionSource, enabled = enabled)
        .then(if (enabled) Modifier.hoverHand() else Modifier)
        .clickable(
            interactionSource = interactionSource,
            indication = null,
            enabled = enabled,
            onClick = onClick,
        )
}
