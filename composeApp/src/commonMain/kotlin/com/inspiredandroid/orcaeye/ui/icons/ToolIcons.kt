package com.inspiredandroid.orcaeye.ui.icons

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.inspiredandroid.orcaeye.model.ToolKind

/** Claude-inspired starburst (warm coral). */
val ClaudeIcon: ImageVector
    get() {
        if (_claude != null) return _claude!!
        _claude =
            ImageVector
                .Builder(
                    name = "Claude",
                    defaultWidth = 24.dp,
                    defaultHeight = 24.dp,
                    viewportWidth = 24f,
                    viewportHeight = 24f,
                ).apply {
                    path(
                        fill = SolidColor(Color(0xFFD97757)),
                        pathFillType = PathFillType.EvenOdd,
                    ) {
                        moveTo(12f, 2.2f)
                        lineTo(14.1f, 8.4f)
                        lineTo(20.6f, 9.1f)
                        lineTo(15.7f, 13.4f)
                        lineTo(17.2f, 19.8f)
                        lineTo(12f, 16.7f)
                        lineTo(6.8f, 19.8f)
                        lineTo(8.3f, 13.4f)
                        lineTo(3.4f, 9.1f)
                        lineTo(9.9f, 8.4f)
                        close()
                    }
                    path(fill = SolidColor(Color(0xFFF4A07A))) {
                        moveTo(12f, 7.5f)
                        lineTo(13.1f, 10.6f)
                        lineTo(16.3f, 10.9f)
                        lineTo(13.9f, 13f)
                        lineTo(14.6f, 16.1f)
                        lineTo(12f, 14.6f)
                        lineTo(9.4f, 16.1f)
                        lineTo(10.1f, 13f)
                        lineTo(7.7f, 10.9f)
                        lineTo(10.9f, 10.6f)
                        close()
                    }
                }.build()
        return _claude!!
    }

private var _claude: ImageVector? = null

/** Grok / xAI-inspired bold mark. */
val GrokIcon: ImageVector
    get() {
        if (_grok != null) return _grok!!
        _grok =
            ImageVector
                .Builder(
                    name = "Grok",
                    defaultWidth = 24.dp,
                    defaultHeight = 24.dp,
                    viewportWidth = 24f,
                    viewportHeight = 24f,
                ).apply {
                    path(fill = SolidColor(Color(0xFF111111))) {
                        moveTo(5f, 3.5f)
                        lineTo(19f, 3.5f)
                        lineTo(19f, 20.5f)
                        lineTo(5f, 20.5f)
                        close()
                    }
                    // Stylized G as filled light arcs via polygon approximation
                    path(fill = SolidColor(Color(0xFFE8E8E8))) {
                        // Outer ring (donut via even-odd would need hole; use thick stroke-like bars)
                        moveTo(12f, 7.2f)
                        lineTo(14.6f, 8.1f)
                        lineTo(15.8f, 10.5f)
                        lineTo(14.2f, 10.5f)
                        lineTo(13.5f, 9.1f)
                        lineTo(12f, 8.6f)
                        lineTo(10.2f, 9.3f)
                        lineTo(9.3f, 11f)
                        lineTo(9.3f, 13f)
                        lineTo(10.2f, 14.7f)
                        lineTo(12f, 15.4f)
                        lineTo(13.5f, 14.9f)
                        lineTo(14.2f, 13.5f)
                        lineTo(12.2f, 13.5f)
                        lineTo(12.2f, 12.1f)
                        lineTo(16.2f, 12.1f)
                        lineTo(16.2f, 14.6f)
                        lineTo(15.4f, 16.2f)
                        lineTo(12.8f, 17.2f)
                        lineTo(10f, 16.5f)
                        lineTo(8.2f, 14.4f)
                        lineTo(7.6f, 12f)
                        lineTo(8.2f, 9.6f)
                        lineTo(10f, 7.5f)
                        close()
                    }
                }.build()
        return _grok!!
    }

private var _grok: ImageVector? = null

/** OpenCode-inspired code brackets. */
val OpenCodeIcon: ImageVector
    get() {
        if (_openCode != null) return _openCode!!
        _openCode =
            ImageVector
                .Builder(
                    name = "OpenCode",
                    defaultWidth = 24.dp,
                    defaultHeight = 24.dp,
                    viewportWidth = 24f,
                    viewportHeight = 24f,
                ).apply {
                    path(fill = SolidColor(Color(0xFF0EA5E9))) {
                        moveTo(4f, 5f)
                        lineTo(20f, 5f)
                        lineTo(20f, 19f)
                        lineTo(4f, 19f)
                        close()
                    }
                    path(
                        fill = SolidColor(Color.Transparent),
                        stroke = SolidColor(Color.White),
                        strokeLineWidth = 1.7f,
                        strokeLineCap = StrokeCap.Round,
                        strokeLineJoin = StrokeJoin.Round,
                    ) {
                        moveTo(9.2f, 8.5f)
                        lineTo(6.8f, 12f)
                        lineTo(9.2f, 15.5f)
                    }
                    path(
                        fill = SolidColor(Color.Transparent),
                        stroke = SolidColor(Color.White),
                        strokeLineWidth = 1.7f,
                        strokeLineCap = StrokeCap.Round,
                        strokeLineJoin = StrokeJoin.Round,
                    ) {
                        moveTo(14.8f, 8.5f)
                        lineTo(17.2f, 12f)
                        lineTo(14.8f, 15.5f)
                    }
                    path(
                        fill = SolidColor(Color.Transparent),
                        stroke = SolidColor(Color.White),
                        strokeLineWidth = 1.7f,
                        strokeLineCap = StrokeCap.Round,
                    ) {
                        moveTo(12.8f, 7.8f)
                        lineTo(11.2f, 16.2f)
                    }
                }.build()
        return _openCode!!
    }

private var _openCode: ImageVector? = null

fun ToolKind.icon(): ImageVector = when (this) {
    ToolKind.Claude -> ClaudeIcon
    ToolKind.Grok -> GrokIcon
    ToolKind.OpenCode -> OpenCodeIcon
}

@Composable
fun ToolIcon(
    tool: ToolKind,
    modifier: Modifier = Modifier,
    size: Dp = 18.dp,
    tint: Color? = null,
) {
    Icon(
        imageVector = tool.icon(),
        contentDescription = tool.displayName,
        modifier = modifier.size(size),
        tint = tint ?: Color.Unspecified,
    )
}
