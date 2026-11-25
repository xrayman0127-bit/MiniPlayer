package com.musab.miniplayer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp

/**
 * Minimal rounded line icons (24x24 grid, 1.75dp stroke at every size).
 * Pure local ImageVectors: no icon library and no extra dependency.
 */
internal enum class AppIconKind {
    MoreVertical,
    MoreHorizontal,
    Heart,
    HeartFilled,
    Repeat,
    RepeatOne,
    SkipBack,
    SkipForward,
    Play,
    Pause,
    Shuffle,
    Library,
    Music2,
    Music
}

private const val STROKE_DP = 1.75f
private const val VIEWPORT = 24f

private class IconSpec(
    val paths: List<String>,
    val filled: Boolean = false
)

private val ICON_SPECS: Map<AppIconKind, IconSpec> = mapOf(
    AppIconKind.MoreVertical to IconSpec(
        paths = listOf(
            "M11 5 a1 1 0 1 0 2 0 a1 1 0 1 0 -2 0 Z",
            "M11 12 a1 1 0 1 0 2 0 a1 1 0 1 0 -2 0 Z",
            "M11 19 a1 1 0 1 0 2 0 a1 1 0 1 0 -2 0 Z"
        ),
        filled = true
    ),
    AppIconKind.MoreHorizontal to IconSpec(
        paths = listOf(
            "M4 12 a1 1 0 1 0 2 0 a1 1 0 1 0 -2 0 Z",
            "M11 12 a1 1 0 1 0 2 0 a1 1 0 1 0 -2 0 Z",
            "M18 12 a1 1 0 1 0 2 0 a1 1 0 1 0 -2 0 Z"
        ),
        filled = true
    ),
    AppIconKind.Heart to IconSpec(
        paths = listOf(
            "M12 20 C12 20 4 15.1 4 9 A4.5 4.5 0 0 1 12 6.3 A4.5 4.5 0 0 1 20 9 C20 15.1 12 20 12 20 Z"
        )
    ),
    AppIconKind.HeartFilled to IconSpec(
        paths = listOf(
            "M12 20 C12 20 4 15.1 4 9 A4.5 4.5 0 0 1 12 6.3 A4.5 4.5 0 0 1 20 9 C20 15.1 12 20 12 20 Z"
        ),
        filled = true
    ),
    AppIconKind.Repeat to IconSpec(
        paths = listOf(
            "M4 11 V10 A3.5 3.5 0 0 1 7.5 6.5 H19",
            "M16 3.5 L19 6.5 L16 9.5",
            "M20 13 V14 A3.5 3.5 0 0 1 16.5 17.5 H5",
            "M8 14.5 L5 17.5 L8 20.5"
        )
    ),
    AppIconKind.RepeatOne to IconSpec(
        paths = listOf(
            "M4 11 V10 A3.5 3.5 0 0 1 7.5 6.5 H19",
            "M16 3.5 L19 6.5 L16 9.5",
            "M20 13 V14 A3.5 3.5 0 0 1 16.5 17.5 H5",
            "M8 14.5 L5 17.5 L8 20.5",
            "M10.7 10.6 L12.4 9.6 V14.4"
        )
    ),
    AppIconKind.SkipBack to IconSpec(
        paths = listOf(
            "M5 5.5 V18.5",
            "M19 6.2 V17.8 L9 12 Z"
        )
    ),
    AppIconKind.SkipForward to IconSpec(
        paths = listOf(
            "M19 5.5 V18.5",
            "M5 6.2 V17.8 L15 12 Z"
        )
    ),
    AppIconKind.Play to IconSpec(
        paths = listOf(
            "M8.5 5.2 V18.8 L19 12 Z"
        )
    ),
    AppIconKind.Pause to IconSpec(
        paths = listOf(
            "M7.4 5 L8.6 5 A1.4 1.4 0 0 1 10 6.4 L10 17.6 A1.4 1.4 0 0 1 8.6 19 L7.4 19 A1.4 1.4 0 0 1 6 17.6 L6 6.4 A1.4 1.4 0 0 1 7.4 5 Z",
            "M15.4 5 L16.6 5 A1.4 1.4 0 0 1 18 6.4 L18 17.6 A1.4 1.4 0 0 1 16.6 19 L15.4 19 A1.4 1.4 0 0 1 14 17.6 L14 6.4 A1.4 1.4 0 0 1 15.4 5 Z"
        )
    ),
    AppIconKind.Shuffle to IconSpec(
        paths = listOf(
            "M3.5 7 H6 C9.5 7 11 9.5 12 12 C13 14.5 14.5 17 18 17 H20",
            "M3.5 17 H6 C7.5 17 8.7 16.3 9.6 15.2",
            "M14.4 8.8 C15.3 7.7 16.5 7 18 7 H20",
            "M17.5 4.5 L20 7 L17.5 9.5",
            "M17.5 14.5 L20 17 L17.5 19.5"
        )
    ),
    AppIconKind.Library to IconSpec(
        paths = listOf(
            "M5 5 V19",
            "M10.5 5 V19",
            "M15.5 5.4 L19.5 18.6"
        )
    ),
    AppIconKind.Music2 to IconSpec(
        paths = listOf(
            "M5 17.5 a3 3 0 1 0 6 0 a3 3 0 1 0 -6 0 Z",
            "M11 17.5 V5.2 C13.8 5.6 16.8 7 17.8 10"
        )
    ),
    AppIconKind.Music to IconSpec(
        paths = listOf(
            "M4.5 17.5 a2.5 2.5 0 1 0 5 0 a2.5 2.5 0 1 0 -5 0 Z",
            "M14 15.5 a2.5 2.5 0 1 0 5 0 a2.5 2.5 0 1 0 -5 0 Z",
            "M9.5 17.5 V6.5 L19 4.5 V15.5",
            "M9.5 10.2 L19 8.2"
        )
    )
)

/**
 * The stroke is expressed in viewport units, so it is derived from the
 * displayed size to keep the on-screen stroke at exactly 1.75dp.
 */
private fun buildIcon(kind: AppIconKind, sizeDp: Float): ImageVector {
    val spec = ICON_SPECS.getValue(kind)
    val strokeWidth = STROKE_DP * VIEWPORT / sizeDp
    val brush = SolidColor(Color.Black)
    return ImageVector.Builder(
        name = "AppIcon." + kind.name,
        defaultWidth = sizeDp.dp,
        defaultHeight = sizeDp.dp,
        viewportWidth = VIEWPORT,
        viewportHeight = VIEWPORT
    ).apply {
        spec.paths.forEach { d ->
            addPath(
                pathData = PathParser().parsePathString(d).toNodes(),
                fill = if (spec.filled) brush else null,
                stroke = brush,
                strokeLineWidth = strokeWidth,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            )
        }
    }.build()
}

/**
 * Draws a line icon in place of a text glyph without touching the layout.
 *
 * [footprint] and [fontSize] are the original glyph and its size. They are
 * composed invisibly and only define the measured size, so row heights,
 * spacing, touch area and font-scale behaviour stay exactly as before.
 * The icon is drawn centered over that box at a fixed [iconSize] and never
 * contributes to the measured size.
 *
 * [tint] receives the color expression the glyph used. [modifier] receives
 * the original modifier chain (clickable included) unchanged.
 */
@Composable
internal fun AppIcon(
    kind: AppIconKind,
    footprint: String,
    fontSize: TextUnit,
    iconSize: Dp,
    tint: Color,
    modifier: Modifier = Modifier,
    contentDescription: String? = null
) {
    val vector = remember(kind, iconSize) { buildIcon(kind, iconSize.value) }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = footprint,
            color = Color.Transparent,
            fontSize = fontSize,
            modifier = Modifier.clearAndSetSemantics { }
        )

        Box(
            modifier = Modifier
                .matchParentSize()
                .wrapContentSize(Alignment.Center, unbounded = true)
        ) {
            Icon(
                imageVector = vector,
                contentDescription = contentDescription,
                tint = tint,
                modifier = Modifier.size(iconSize)
            )
        }
    }
}
