package com.tuplayer.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** The icons TU Player uses. Drawn with paths, so the app does not depend on an icon font. */
enum class Glyph {
    PLAY, PAUSE, PREVIOUS, NEXT, REPLAY, FORWARD, LOCK, ROTATE, ASPECT, SPEED, MORE, BACK,
    FILM, FOLDER, GLOBE, SLIDERS, SEARCH, CLOSE,
    NIGHT, SHUFFLE, REPEAT, MUTE, TIMER, AB, WAVE, CAMERA, INFO, MUSIC, CAPTIONS,
    PIP, TRASH, MOVE
}

/**
 * Draws one glyph on a 24-unit grid, scaled to [size]. [tint] colours strokes and fills.
 * Replay and Forward draw only the arrow. Callers add the "10" label on top.
 */
@Composable
fun GlyphIcon(glyph: Glyph, tint: Color, modifier: Modifier = Modifier, size: Dp = 24.dp) {
    Canvas(modifier.size(size)) {
        val u = this.size.minDimension / 24f
        drawGlyph(glyph, tint, u)
    }
}

private fun DrawScope.drawGlyph(glyph: Glyph, tint: Color, u: Float) {
    fun p(x: Float, y: Float) = Offset(x * u, y * u)
    val stroke = Stroke(width = 2f * u, cap = StrokeCap.Round)
    when (glyph) {
        Glyph.PLAY -> drawPath(Path().apply {
            moveTo(p(7f, 4.5f).x, p(7f, 4.5f).y); lineTo(p(20f, 12f).x, p(20f, 12f).y); lineTo(p(7f, 19.5f).x, p(7f, 19.5f).y); close()
        }, tint)
        Glyph.PAUSE -> {
            drawRect(tint, p(6f, 4.5f), Size(4.5f * u, 15f * u))
            drawRect(tint, p(13.5f, 4.5f), Size(4.5f * u, 15f * u))
        }
        Glyph.PREVIOUS -> {
            drawRect(tint, p(4.5f, 5f), Size(2.5f * u, 14f * u))
            drawPath(Path().apply {
                moveTo(p(19f, 5f).x, p(19f, 5f).y); lineTo(p(19f, 19f).x, p(19f, 19f).y); lineTo(p(8.5f, 12f).x, p(8.5f, 12f).y); close()
            }, tint)
        }
        Glyph.NEXT -> {
            drawRect(tint, p(17f, 5f), Size(2.5f * u, 14f * u))
            drawPath(Path().apply {
                moveTo(p(5f, 5f).x, p(5f, 5f).y); lineTo(p(5f, 19f).x, p(5f, 19f).y); lineTo(p(15.5f, 12f).x, p(15.5f, 12f).y); close()
            }, tint)
        }
        Glyph.REPLAY, Glyph.FORWARD -> {
            val mirror = if (glyph == Glyph.FORWARD) -1f else 1f
            fun mx(x: Float) = if (mirror > 0) x else 24f - x
            // Arc from the top, going round counter-clockwise for replay and clockwise for forward.
            drawArc(
                color = tint,
                startAngle = if (mirror > 0) -90f else -90f,
                sweepAngle = if (mirror > 0) -290f else 290f,
                useCenter = false,
                topLeft = p(4.5f, 4.5f),
                size = Size(15f * u, 15f * u),
                style = stroke
            )
            // Arrow head at the start of the arc.
            drawPath(Path().apply {
                moveTo(p(mx(9.5f), 2.5f).x, p(mx(9.5f), 2.5f).y)
                lineTo(p(mx(9.5f), 6.8f).x, p(mx(9.5f), 6.8f).y)
                lineTo(p(mx(5.9f), 4.6f).x, p(mx(5.9f), 4.6f).y)
                close()
            }, tint)
        }
        Glyph.LOCK -> {
            drawRoundRect(tint, p(6f, 11f), Size(12f * u, 9.5f * u), androidx.compose.ui.geometry.CornerRadius(2f * u))
            drawPath(Path().apply {
                moveTo(p(8.5f, 11f).x, p(8.5f, 11f).y)
                lineTo(p(8.5f, 8f).x, p(8.5f, 8f).y)
                cubicTo(p(8.5f, 5.8f).x, p(8.5f, 5.8f).y, p(10f, 4.5f).x, p(10f, 4.5f).y, p(12f, 4.5f).x, p(12f, 4.5f).y)
                cubicTo(p(14f, 4.5f).x, p(14f, 4.5f).y, p(15.5f, 5.8f).x, p(15.5f, 5.8f).y, p(15.5f, 8f).x, p(15.5f, 8f).y)
                lineTo(p(15.5f, 11f).x, p(15.5f, 11f).y)
            }, tint, style = stroke)
        }
        Glyph.ROTATE -> {
            drawRoundRect(tint, p(7.5f, 3f), Size(9f * u, 18f * u), androidx.compose.ui.geometry.CornerRadius(2f * u), style = stroke)
            drawArc(tint, startAngle = 200f, sweepAngle = 110f, useCenter = false, topLeft = p(2f, 6f), size = Size(8f * u, 8f * u), style = stroke)
        }
        Glyph.ASPECT -> {
            drawRoundRect(tint, p(3f, 6f), Size(18f * u, 12f * u), androidx.compose.ui.geometry.CornerRadius(2f * u), style = stroke)
            drawRect(tint, p(7f, 9.5f), Size(10f * u, 5f * u), style = stroke)
        }
        Glyph.SPEED -> {
            drawArc(tint, startAngle = 180f, sweepAngle = 180f, useCenter = false, topLeft = p(3.5f, 5f), size = Size(17f * u, 17f * u), style = stroke)
            drawLine(tint, p(12f, 14f), p(16f, 8.5f), strokeWidth = 2f * u, cap = StrokeCap.Round)
            drawCircle(tint, radius = 1.8f * u, center = p(12f, 14f))
        }
        Glyph.MORE -> {
            drawCircle(tint, radius = 1.9f * u, center = p(12f, 5.5f))
            drawCircle(tint, radius = 1.9f * u, center = p(12f, 12f))
            drawCircle(tint, radius = 1.9f * u, center = p(12f, 18.5f))
        }
        Glyph.BACK -> drawPath(Path().apply {
            moveTo(p(15f, 5f).x, p(15f, 5f).y); lineTo(p(8f, 12f).x, p(8f, 12f).y); lineTo(p(15f, 19f).x, p(15f, 19f).y)
        }, tint, style = stroke)
        Glyph.FILM -> {
            drawRoundRect(tint, p(3f, 5f), Size(18f * u, 14f * u), androidx.compose.ui.geometry.CornerRadius(2f * u), style = stroke)
            for (y in listOf(8f, 11.5f, 15f)) {
                drawRect(tint, p(5.5f, y), Size(1.5f * u, 1.5f * u))
                drawRect(tint, p(17f, y), Size(1.5f * u, 1.5f * u))
            }
        }
        Glyph.FOLDER -> drawPath(Path().apply {
            moveTo(p(3f, 7f).x, p(3f, 7f).y); lineTo(p(3f, 18f).x, p(3f, 18f).y)
            lineTo(p(21f, 18f).x, p(21f, 18f).y); lineTo(p(21f, 9f).x, p(21f, 9f).y)
            lineTo(p(12f, 9f).x, p(12f, 9f).y); lineTo(p(10f, 6.5f).x, p(10f, 6.5f).y); lineTo(p(3f, 6.5f).x, p(3f, 6.5f).y); close()
        }, tint, style = stroke)
        Glyph.GLOBE -> {
            drawCircle(tint, radius = 8.5f * u, center = p(12f, 12f), style = stroke)
            drawOval(tint, p(8.5f, 3.5f), Size(7f * u, 17f * u), style = stroke)
            drawLine(tint, p(3.5f, 12f), p(20.5f, 12f), strokeWidth = 2f * u)
        }
        Glyph.SLIDERS -> {
            drawLine(tint, p(3f, 7f), p(21f, 7f), strokeWidth = 2f * u, cap = StrokeCap.Round)
            drawLine(tint, p(3f, 12f), p(21f, 12f), strokeWidth = 2f * u, cap = StrokeCap.Round)
            drawLine(tint, p(3f, 17f), p(21f, 17f), strokeWidth = 2f * u, cap = StrokeCap.Round)
            drawCircle(tint, radius = 2.6f * u, center = p(15f, 7f))
            drawCircle(tint, radius = 2.6f * u, center = p(8f, 12f))
            drawCircle(tint, radius = 2.6f * u, center = p(16f, 17f))
        }
        Glyph.SEARCH -> {
            drawCircle(tint, radius = 6.5f * u, center = p(10.5f, 10.5f), style = stroke)
            drawLine(tint, p(15.5f, 15.5f), p(20f, 20f), strokeWidth = 2.2f * u, cap = StrokeCap.Round)
        }
        Glyph.NIGHT -> drawArc(tint, startAngle = 40f, sweepAngle = 280f, useCenter = false, topLeft = p(4f, 4f), size = Size(16f * u, 16f * u), style = stroke)
        Glyph.SHUFFLE -> {
            drawPath(Path().apply {
                moveTo(p(3f, 7f).x, p(3f, 7f).y); lineTo(p(8f, 7f).x, p(8f, 7f).y); lineTo(p(16f, 17f).x, p(16f, 17f).y); lineTo(p(21f, 17f).x, p(21f, 17f).y)
            }, tint, style = stroke)
            drawPath(Path().apply {
                moveTo(p(3f, 17f).x, p(3f, 17f).y); lineTo(p(8f, 17f).x, p(8f, 17f).y); lineTo(p(11f, 14f).x, p(11f, 14f).y)
            }, tint, style = stroke)
            drawPath(Path().apply {
                moveTo(p(14f, 10f).x, p(14f, 10f).y); lineTo(p(16f, 7f).x, p(16f, 7f).y); lineTo(p(21f, 7f).x, p(21f, 7f).y)
            }, tint, style = stroke)
        }
        Glyph.REPEAT -> {
            drawRoundRect(tint, p(4f, 7f), Size(16f * u, 10f * u), androidx.compose.ui.geometry.CornerRadius(3f * u), style = stroke)
            drawPath(Path().apply {
                moveTo(p(17f, 3.5f).x, p(17f, 3.5f).y); lineTo(p(20.5f, 7f).x, p(20.5f, 7f).y); lineTo(p(17f, 10.5f).x, p(17f, 10.5f).y); close()
            }, tint)
        }
        Glyph.MUTE -> {
            drawPath(Path().apply {
                moveTo(p(3f, 9f).x, p(3f, 9f).y); lineTo(p(7f, 9f).x, p(7f, 9f).y); lineTo(p(12f, 5f).x, p(12f, 5f).y)
                lineTo(p(12f, 19f).x, p(12f, 19f).y); lineTo(p(7f, 15f).x, p(7f, 15f).y); lineTo(p(3f, 15f).x, p(3f, 15f).y); close()
            }, tint)
            drawLine(tint, p(15f, 9f), p(21f, 15f), strokeWidth = 2f * u, cap = StrokeCap.Round)
            drawLine(tint, p(21f, 9f), p(15f, 15f), strokeWidth = 2f * u, cap = StrokeCap.Round)
        }
        Glyph.TIMER -> {
            drawCircle(tint, radius = 8f * u, center = p(12f, 13.5f), style = stroke)
            drawLine(tint, p(12f, 13.5f), p(12f, 9.5f), strokeWidth = 2f * u, cap = StrokeCap.Round)
            drawLine(tint, p(9f, 2.5f), p(15f, 2.5f), strokeWidth = 2f * u, cap = StrokeCap.Round)
        }
        Glyph.AB -> {
            drawLine(tint, p(3f, 8f), p(20f, 8f), strokeWidth = 2f * u, cap = StrokeCap.Round)
            drawPath(Path().apply {
                moveTo(p(21f, 8f).x, p(21f, 8f).y); lineTo(p(17f, 5f).x, p(17f, 5f).y); lineTo(p(17f, 11f).x, p(17f, 11f).y); close()
            }, tint)
            drawLine(tint, p(21f, 16f), p(4f, 16f), strokeWidth = 2f * u, cap = StrokeCap.Round)
            drawPath(Path().apply {
                moveTo(p(3f, 16f).x, p(3f, 16f).y); lineTo(p(7f, 13f).x, p(7f, 13f).y); lineTo(p(7f, 19f).x, p(7f, 19f).y); close()
            }, tint)
        }
        Glyph.WAVE -> for ((x, h) in listOf(4f to 6f, 8f to 12f, 12f to 18f, 16f to 10f, 20f to 6f)) {
            drawLine(tint, p(x, 12f - h / 2f), p(x, 12f + h / 2f), strokeWidth = 2.2f * u, cap = StrokeCap.Round)
        }
        Glyph.CAMERA -> {
            drawRoundRect(tint, p(3f, 7f), Size(18f * u, 13f * u), androidx.compose.ui.geometry.CornerRadius(2.5f * u), style = stroke)
            drawCircle(tint, radius = 3.5f * u, center = p(12f, 13.5f), style = stroke)
            drawRoundRect(tint, p(8f, 4.5f), Size(8f * u, 3f * u), androidx.compose.ui.geometry.CornerRadius(1f * u), style = stroke)
        }
        Glyph.INFO -> {
            drawCircle(tint, radius = 9f * u, center = p(12f, 12f), style = stroke)
            drawCircle(tint, radius = 1.4f * u, center = p(12f, 7.5f))
            drawLine(tint, p(12f, 11f), p(12f, 17f), strokeWidth = 2.2f * u, cap = StrokeCap.Round)
        }
        Glyph.MUSIC -> {
            drawCircle(tint, radius = 2.8f * u, center = p(8f, 17f))
            drawLine(tint, p(10.8f, 17f), p(10.8f, 5f), strokeWidth = 2f * u, cap = StrokeCap.Round)
            drawLine(tint, p(10.8f, 5f), p(18f, 7.5f), strokeWidth = 2f * u, cap = StrokeCap.Round)
        }
        Glyph.CAPTIONS -> {
            drawRoundRect(tint, p(3f, 5f), Size(18f * u, 14f * u), androidx.compose.ui.geometry.CornerRadius(2.5f * u), style = stroke)
            drawLine(tint, p(6f, 11f), p(11f, 11f), strokeWidth = 2f * u, cap = StrokeCap.Round)
            drawLine(tint, p(13f, 11f), p(18f, 11f), strokeWidth = 2f * u, cap = StrokeCap.Round)
            drawLine(tint, p(6f, 15f), p(12f, 15f), strokeWidth = 2f * u, cap = StrokeCap.Round)
            drawLine(tint, p(14f, 15f), p(18f, 15f), strokeWidth = 2f * u, cap = StrokeCap.Round)
        }
        Glyph.PIP -> {
            drawRoundRect(tint, p(3f, 5f), Size(18f * u, 14f * u), androidx.compose.ui.geometry.CornerRadius(2.5f * u), style = stroke)
            drawRoundRect(tint, p(11f, 11f), Size(7f * u, 5f * u), androidx.compose.ui.geometry.CornerRadius(1.2f * u))
        }
        Glyph.TRASH -> {
            drawLine(tint, p(4f, 7f), p(20f, 7f), strokeWidth = 2f * u, cap = StrokeCap.Round)
            drawLine(tint, p(9f, 7f), p(9f, 4.5f), strokeWidth = 2f * u, cap = StrokeCap.Round)
            drawLine(tint, p(15f, 7f), p(15f, 4.5f), strokeWidth = 2f * u, cap = StrokeCap.Round)
            drawPath(Path().apply {
                moveTo(p(6f, 9f).x, p(6f, 9f).y); lineTo(p(7f, 20f).x, p(7f, 20f).y); lineTo(p(17f, 20f).x, p(17f, 20f).y); lineTo(p(18f, 9f).x, p(18f, 9f).y)
            }, tint, style = stroke)
        }
        Glyph.MOVE -> {
            drawRoundRect(tint, p(3f, 13f), Size(18f * u, 7f * u), androidx.compose.ui.geometry.CornerRadius(2f * u), style = stroke)
            drawLine(tint, p(12f, 4f), p(12f, 11f), strokeWidth = 2f * u, cap = StrokeCap.Round)
            drawPath(Path().apply {
                moveTo(p(8f, 8f).x, p(8f, 8f).y); lineTo(p(12f, 12f).x, p(12f, 12f).y); lineTo(p(16f, 8f).x, p(16f, 8f).y)
            }, tint, style = stroke)
        }
        Glyph.CLOSE -> {
            drawLine(tint, p(6f, 6f), p(18f, 18f), strokeWidth = 2.2f * u, cap = StrokeCap.Round)
            drawLine(tint, p(18f, 6f), p(6f, 18f), strokeWidth = 2.2f * u, cap = StrokeCap.Round)
        }
    }
}
