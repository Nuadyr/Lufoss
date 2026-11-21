package io.github.ardalorn.lufoss.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/** The icons from the ArWalk page, as vectors drawn from the same SVG paths. */
object LufossIcons {
    val search: ImageVector by lazy {
        build("Search", strokes = listOf("M4.3 10.5a6.2 6.2 0 1 0 12.4 0a6.2 6.2 0 1 0 -12.4 0", "M15.2 15.2l5 5"))
    }
    val clear: ImageVector by lazy { build("Clear", strokes = listOf("M6 6l12 12M18 6L6 18")) }
    val check: ImageVector by lazy { build("Selected", strokes = listOf("M5 12.5l4.5 4.5L19 7.5")) }
    val plus: ImageVector by lazy { build("Zoom in", strokes = listOf("M12 5v14M5 12h14")) }
    val minus: ImageVector by lazy { build("Zoom out", strokes = listOf("M5 12h14")) }
    val info: ImageVector by lazy {
        build("About", strokes = listOf("M3 12a9 9 0 1 0 18 0a9 9 0 1 0 -18 0", "M12 11v5M12 7.6v.2"))
    }
    val flag: ImageVector by lazy { build("Start point", strokes = listOf("M6 21V4M6 5h11l-2 4 2 4H6")) }
    val loop: ImageVector by lazy {
        build("Round trip", strokes = listOf("M20 12a8 8 0 1 1 -2.6 -5.9M20 4v4.5h-4.5"))
    }
    val locateFree: ImageVector by lazy {
        build("Locate", strokes = listOf("M8 12a4 4 0 1 0 8 0a4 4 0 1 0 -8 0", "M12 2v4M12 18v4M2 12h4M18 12h4"))
    }
    val locateNorth: ImageVector by lazy {
        build(
            "Following, north up",
            strokes = listOf("M12 2v4M12 18v4M2 12h4M18 12h4"),
            fills = listOf("M8 12a4 4 0 1 0 8 0a4 4 0 1 0 -8 0")
        )
    }
    val locateHeading: ImageVector by lazy {
        build(
            "Following, heading up",
            strokes = listOf("M12 3.5L19 20.5L12 16.5L5 20.5z"),
            fills = listOf("M12 3.5L19 20.5L12 16.5L5 20.5z")
        )
    }

    private fun build(name: String, strokes: List<String>, fills: List<String> = emptyList()): ImageVector {
        val builder = ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        )
        fills.forEach { d ->
            builder.addPath(
                pathData = PathParser().parsePathString(d).toNodes(),
                fill = SolidColor(Color.Black)
            )
        }
        strokes.forEach { d ->
            builder.addPath(
                pathData = PathParser().parsePathString(d).toNodes(),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = STROKE_WIDTH,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            )
        }
        return builder.build()
    }

    private const val STROKE_WIDTH = 2.2f
}
