package com.kryptsafe.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.geometry.Size as GSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Small hand-drawn glyphs so we don't need the huge extended icon set (or emoji). */
@Composable
fun LockGlyph(size: Dp, color: Color, hole: Color = Color.Transparent, modifier: Modifier = Modifier) =
    Canvas(modifier.size(size)) {
        val w = this.size.width; val h = this.size.height
        val sw = w * 0.11f
        drawArc(color, 180f, 180f, false, Offset(w * 0.28f, h * 0.10f), Size(w * 0.44f, h * 0.50f), style = Stroke(sw, cap = StrokeCap.Round))
        drawLine(color, Offset(w * 0.28f, h * 0.35f), Offset(w * 0.28f, h * 0.50f), sw, StrokeCap.Round)
        drawLine(color, Offset(w * 0.72f, h * 0.35f), Offset(w * 0.72f, h * 0.50f), sw, StrokeCap.Round)
        drawRoundRect(color, Offset(w * 0.16f, h * 0.44f), Size(w * 0.68f, h * 0.46f), CornerRadius(w * 0.14f))
        drawCircle(hole, w * 0.07f, Offset(w * 0.5f, h * 0.62f))
        drawLine(hole, Offset(w * 0.5f, h * 0.64f), Offset(w * 0.5f, h * 0.76f), w * 0.07f, StrokeCap.Round)
    }

@Composable
fun FingerprintGlyph(size: Dp, color: Color, modifier: Modifier = Modifier) =
    Canvas(modifier.size(size)) {
        val w = this.size.width; val h = this.size.height
        val st = Stroke(w * 0.07f, cap = StrokeCap.Round)
        listOf(0.46f, 0.34f, 0.22f, 0.10f).forEachIndexed { i, r ->
            val rad = w * r
            drawArc(color, 200f + i * 6f, 280f - i * 10f, false, Offset(w / 2 - rad, h / 2 - rad), Size(rad * 2, rad * 2), style = st)
        }
        drawLine(color, Offset(w / 2, h * 0.46f), Offset(w / 2, h * 0.70f), w * 0.07f, StrokeCap.Round)
    }

@Composable
fun BackspaceGlyph(size: Dp, color: Color, modifier: Modifier = Modifier) =
    Canvas(modifier.size(size)) {
        val w = this.size.width; val h = this.size.height
        val st = Stroke(w * 0.08f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        val p = Path().apply {
            moveTo(w * 0.34f, h * 0.22f); lineTo(w * 0.88f, h * 0.22f); lineTo(w * 0.88f, h * 0.78f)
            lineTo(w * 0.34f, h * 0.78f); lineTo(w * 0.10f, h * 0.50f); close()
        }
        drawPath(p, color, style = st)
        drawLine(color, Offset(w * 0.46f, h * 0.38f), Offset(w * 0.70f, h * 0.62f), w * 0.08f, StrokeCap.Round)
        drawLine(color, Offset(w * 0.70f, h * 0.38f), Offset(w * 0.46f, h * 0.62f), w * 0.08f, StrokeCap.Round)
    }

@Composable
fun DocGlyph(size: Dp, color: Color, modifier: Modifier = Modifier) =
    Canvas(modifier.size(size)) {
        val w = this.size.width; val h = this.size.height
        val st = Stroke(w * 0.07f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        drawRoundRect(color, Offset(w * 0.20f, h * 0.10f), Size(w * 0.60f, h * 0.80f), CornerRadius(w * 0.10f), style = st)
        listOf(0.36f, 0.52f, 0.68f).forEach { y -> drawLine(color, Offset(w * 0.34f, h * y), Offset(w * 0.66f, h * y), w * 0.06f, StrokeCap.Round) }
    }

@Composable
fun PhotoGlyph(size: Dp, color: Color, modifier: Modifier = Modifier) =
    Canvas(modifier.size(size)) {
        val w = this.size.width; val h = this.size.height
        val st = Stroke(w * 0.08f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        drawRoundRect(color, Offset(w * 0.12f, h * 0.18f), Size(w * 0.76f, h * 0.64f), CornerRadius(w * 0.14f), style = st)
        drawCircle(color, w * 0.07f, Offset(w * 0.34f, h * 0.38f))
        val p = Path().apply { moveTo(w * 0.16f, h * 0.74f); lineTo(w * 0.42f, h * 0.52f); lineTo(w * 0.58f, h * 0.66f); lineTo(w * 0.70f, h * 0.56f); lineTo(w * 0.84f, h * 0.70f) }
        drawPath(p, color, style = st)
    }

@Composable
fun VideoGlyph(size: Dp, color: Color, modifier: Modifier = Modifier) =
    Canvas(modifier.size(size)) {
        val w = this.size.width; val h = this.size.height
        val st = Stroke(w * 0.08f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        drawRoundRect(color, Offset(w * 0.10f, h * 0.20f), Size(w * 0.80f, h * 0.60f), CornerRadius(w * 0.16f), style = st)
        val p = Path().apply { moveTo(w * 0.42f, h * 0.36f); lineTo(w * 0.66f, h * 0.50f); lineTo(w * 0.42f, h * 0.64f); close() }
        drawPath(p, color)
    }

@Composable
fun ShieldGlyph(size: Dp, color: Color, modifier: Modifier = Modifier) =
    Canvas(modifier.size(size)) {
        val w = this.size.width; val h = this.size.height
        val st = Stroke(w * 0.08f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        val p = Path().apply {
            moveTo(w * 0.5f, h * 0.10f); lineTo(w * 0.84f, h * 0.22f); lineTo(w * 0.84f, h * 0.50f)
            cubicTo(w * 0.84f, h * 0.72f, w * 0.66f, h * 0.86f, w * 0.5f, h * 0.92f)
            cubicTo(w * 0.34f, h * 0.86f, w * 0.16f, h * 0.72f, w * 0.16f, h * 0.50f)
            lineTo(w * 0.16f, h * 0.22f); close()
        }
        drawPath(p, color, style = st)
        val c = Path().apply { moveTo(w * 0.36f, h * 0.50f); lineTo(w * 0.47f, h * 0.61f); lineTo(w * 0.66f, h * 0.40f) }
        drawPath(c, color, style = st)
    }

@Composable
fun HomeGlyph(size: Dp, color: Color, filled: Boolean = false, modifier: Modifier = Modifier) =
    Canvas(modifier.size(size)) {
        val w = this.size.width; val h = this.size.height
        val st = Stroke(w * 0.08f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        val p = Path().apply {
            moveTo(w * 0.14f, h * 0.48f); lineTo(w * 0.5f, h * 0.16f); lineTo(w * 0.86f, h * 0.48f)
            lineTo(w * 0.78f, h * 0.48f); lineTo(w * 0.78f, h * 0.82f); lineTo(w * 0.22f, h * 0.82f); lineTo(w * 0.22f, h * 0.48f); close()
        }
        if (filled) drawPath(p, color) else drawPath(p, color, style = st)
    }

@Composable
fun StorageGlyph(size: Dp, color: Color, modifier: Modifier = Modifier) =
    Canvas(modifier.size(size)) {
        val w = this.size.width; val h = this.size.height
        val st = Stroke(w * 0.08f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        listOf(0.22f, 0.44f, 0.66f).forEach { y ->
            drawRoundRect(color, Offset(w * 0.14f, h * y), Size(w * 0.72f, h * 0.16f), CornerRadius(w * 0.08f), style = st)
        }
        drawCircle(color, w * 0.03f, Offset(w * 0.28f, h * 0.30f)); drawCircle(color, w * 0.03f, Offset(w * 0.28f, h * 0.52f)); drawCircle(color, w * 0.03f, Offset(w * 0.28f, h * 0.74f))
    }

@Composable
fun MenuGlyph(size: Dp, color: Color, modifier: Modifier = Modifier) =
    Canvas(modifier.size(size)) {
        val w = this.size.width; val h = this.size.height
        val sw = w * 0.085f
        drawLine(color, Offset(w * 0.18f, h * 0.34f), Offset(w * 0.82f, h * 0.34f), sw, StrokeCap.Round)
        drawLine(color, Offset(w * 0.18f, h * 0.50f), Offset(w * 0.58f, h * 0.50f), sw, StrokeCap.Round)
        drawLine(color, Offset(w * 0.18f, h * 0.66f), Offset(w * 0.70f, h * 0.66f), sw, StrokeCap.Round)
    }

@Composable
fun PauseGlyph(size: Dp, color: Color, modifier: Modifier = Modifier) =
    Canvas(modifier.size(size)) {
        val w = this.size.width; val h = this.size.height
        drawRoundRect(color, Offset(w * 0.24f, h * 0.18f), Size(w * 0.17f, h * 0.64f), CornerRadius(w * 0.06f))
        drawRoundRect(color, Offset(w * 0.59f, h * 0.18f), Size(w * 0.17f, h * 0.64f), CornerRadius(w * 0.06f))
    }

// ---- Video player glyphs (24-unit grid, same stroke language as the rest of the app) ----------------
private fun DrawScope.st(u: Float, w: Float = 1.9f) = Stroke(w * u, cap = StrokeCap.Round, join = StrokeJoin.Round)

private fun DrawScope.arrowHead(tip: Offset, dir: Offset, len: Float, color: Color, stroke: Stroke) {
    val ang = kotlin.math.atan2(dir.y, dir.x)
    for (sgn in listOf(-1f, 1f)) {
        val a = ang + Math.PI.toFloat() + sgn * 0.62f
        drawLine(color, tip, Offset(tip.x + len * kotlin.math.cos(a), tip.y + len * kotlin.math.sin(a)), stroke.width, StrokeCap.Round)
    }
}

@Composable
fun RepeatGlyph(size: Dp, color: Color, modifier: Modifier = Modifier) = Canvas(modifier.size(size)) {
    val u = this.size.width / 24f; val s = st(u)
    val top = Path().apply { moveTo(4 * u, 12 * u); lineTo(4 * u, 9.5f * u); quadraticTo(4 * u, 6.5f * u, 7 * u, 6.5f * u); lineTo(19 * u, 6.5f * u) }
    val bot = Path().apply { moveTo(20 * u, 12 * u); lineTo(20 * u, 14.5f * u); quadraticTo(20 * u, 17.5f * u, 17 * u, 17.5f * u); lineTo(5 * u, 17.5f * u) }
    drawPath(top, color, style = s); drawPath(bot, color, style = s)
    arrowHead(Offset(19.5f * u, 6.5f * u), Offset(1f, 0f), 3.6f * u, color, s)
    arrowHead(Offset(4.5f * u, 17.5f * u), Offset(-1f, 0f), 3.6f * u, color, s)
}

@Composable
fun VolumeGlyph(size: Dp, color: Color, muted: Boolean, modifier: Modifier = Modifier) = Canvas(modifier.size(size)) {
    val u = this.size.width / 24f; val s = st(u)
    val body = Path().apply { moveTo(3.5f * u, 9.5f * u); lineTo(7.5f * u, 9.5f * u); lineTo(12.5f * u, 5 * u); lineTo(12.5f * u, 19 * u); lineTo(7.5f * u, 14.5f * u); lineTo(3.5f * u, 14.5f * u); close() }
    drawPath(body, color)
    if (muted) {
        drawLine(color, Offset(15.5f * u, 9.5f * u), Offset(21 * u, 14.5f * u), 1.9f * u, StrokeCap.Round)
        drawLine(color, Offset(21 * u, 9.5f * u), Offset(15.5f * u, 14.5f * u), 1.9f * u, StrokeCap.Round)
    } else {
        drawPath(Path().apply { moveTo(15.5f * u, 9 * u); quadraticTo(17.8f * u, 12 * u, 15.5f * u, 15 * u) }, color, style = s)
        drawPath(Path().apply { moveTo(18.2f * u, 6.5f * u); quadraticTo(22.5f * u, 12 * u, 18.2f * u, 17.5f * u) }, color, style = s)
    }
}

@Composable
fun FitGlyph(size: Dp, color: Color, modifier: Modifier = Modifier) = Canvas(modifier.size(size)) {
    val u = this.size.width / 24f; val s = st(u)
    listOf(
        Path().apply { moveTo(4 * u, 9 * u); lineTo(4 * u, 4 * u); lineTo(9 * u, 4 * u) },
        Path().apply { moveTo(15 * u, 4 * u); lineTo(20 * u, 4 * u); lineTo(20 * u, 9 * u) },
        Path().apply { moveTo(20 * u, 15 * u); lineTo(20 * u, 20 * u); lineTo(15 * u, 20 * u) },
        Path().apply { moveTo(9 * u, 20 * u); lineTo(4 * u, 20 * u); lineTo(4 * u, 15 * u) },
    ).forEach { drawPath(it, color, style = s) }
}

@Composable
fun RotateGlyph(size: Dp, color: Color, modifier: Modifier = Modifier) = Canvas(modifier.size(size)) {
    val u = this.size.width / 24f; val s = st(u)
    drawRoundRect(color, Offset(3 * u, 10 * u), GSize(15 * u, 9 * u), CornerRadius(2 * u), style = s)
    drawPath(Path().apply { moveTo(10 * u, 5.5f * u); quadraticTo(17 * u, 3.5f * u, 20 * u, 9 * u) }, color, style = s)
    arrowHead(Offset(20 * u, 9 * u), Offset(0.35f, 1f), 3.4f * u, color, s)
}

@Composable
fun TracksGlyph(size: Dp, color: Color, modifier: Modifier = Modifier) = Canvas(modifier.size(size)) {
    val u = this.size.width / 24f; val s = st(u)
    val bubble = Path().apply {
        moveTo(6 * u, 4 * u); lineTo(18 * u, 4 * u); quadraticTo(21 * u, 4 * u, 21 * u, 7 * u); lineTo(21 * u, 13 * u)
        quadraticTo(21 * u, 16 * u, 18 * u, 16 * u); lineTo(12 * u, 16 * u); lineTo(8 * u, 20 * u); lineTo(8 * u, 16 * u)
        lineTo(6 * u, 16 * u); quadraticTo(3 * u, 16 * u, 3 * u, 13 * u); lineTo(3 * u, 7 * u); quadraticTo(3 * u, 4 * u, 6 * u, 4 * u); close()
    }
    drawPath(bubble, color, style = s)
    drawLine(color, Offset(7 * u, 9 * u), Offset(11 * u, 9 * u), 1.9f * u, StrokeCap.Round)
    drawLine(color, Offset(13.5f * u, 9 * u), Offset(17 * u, 9 * u), 1.9f * u, StrokeCap.Round)
    drawLine(color, Offset(7 * u, 12.5f * u), Offset(14 * u, 12.5f * u), 1.9f * u, StrokeCap.Round)
}

@Composable
fun EngineGlyph(size: Dp, color: Color, modifier: Modifier = Modifier) = Canvas(modifier.size(size)) {
    val u = this.size.width / 24f; val s = st(u, 1.8f)
    drawRoundRect(color, Offset(7 * u, 7 * u), GSize(10 * u, 10 * u), CornerRadius(2 * u), style = s)
    drawRoundRect(color, Offset(10.2f * u, 10.2f * u), GSize(3.6f * u, 3.6f * u), CornerRadius(0.8f * u))
    for (p in listOf(10f, 14f)) {
        drawLine(color, Offset(p * u, 3.5f * u), Offset(p * u, 7 * u), 1.8f * u, StrokeCap.Round)
        drawLine(color, Offset(p * u, 17 * u), Offset(p * u, 20.5f * u), 1.8f * u, StrokeCap.Round)
        drawLine(color, Offset(3.5f * u, p * u), Offset(7 * u, p * u), 1.8f * u, StrokeCap.Round)
        drawLine(color, Offset(17 * u, p * u), Offset(20.5f * u, p * u), 1.8f * u, StrokeCap.Round)
    }
}

@Composable
fun SkipGlyph(size: Dp, color: Color, next: Boolean, modifier: Modifier = Modifier) = Canvas(modifier.size(size)) {
    val u = this.size.width / 24f
    if (next) {
        drawPath(Path().apply { moveTo(6 * u, 6 * u); lineTo(15 * u, 12 * u); lineTo(6 * u, 18 * u); close() }, color)
        drawLine(color, Offset(18 * u, 6 * u), Offset(18 * u, 18 * u), 2.2f * u, StrokeCap.Round)
    } else {
        drawPath(Path().apply { moveTo(18 * u, 6 * u); lineTo(9 * u, 12 * u); lineTo(18 * u, 18 * u); close() }, color)
        drawLine(color, Offset(6 * u, 6 * u), Offset(6 * u, 18 * u), 2.2f * u, StrokeCap.Round)
    }
}

/** Circular "skip 10 seconds" arrow. [forward] = clockwise (+10), otherwise mirrored (−10). */
@Composable
fun Seek10Glyph(size: Dp, color: Color, forward: Boolean, modifier: Modifier = Modifier) = Box(modifier.size(size), contentAlignment = Alignment.Center) {
    Canvas(Modifier.size(size).scale(if (forward) -1f else 1f, 1f)) {
        val u = this.size.width / 24f; val s = st(u)
        val c = Offset(12 * u, 13 * u); val r = 8f * u
        drawArc(color, -50f, 290f, false, Offset(c.x - r, c.y - r), GSize(2 * r, 2 * r), style = s)
        val ang = Math.toRadians(240.0)
        val tip = Offset(c.x + r * kotlin.math.cos(ang).toFloat(), c.y + r * kotlin.math.sin(ang).toFloat())
        arrowHead(tip, Offset(0.866f, -0.5f), 3.6f * u, color, s)
    }
    Text("10", color = color, fontSize = (size.value * 0.34f).sp, fontWeight = FontWeight.Bold, lineHeight = (size.value * 0.34f).sp, modifier = Modifier.padding(top = (size.value * 0.12f).dp))
}

@Composable
fun ClockGlyph(size: Dp, color: Color, modifier: Modifier = Modifier) = Canvas(modifier.size(size)) {
    val u = this.size.width / 24f; val s = st(u)
    drawCircle(color, 8.5f * u, Offset(12 * u, 12 * u), style = s)
    drawLine(color, Offset(12 * u, 7.5f * u), Offset(12 * u, 12 * u), 1.9f * u, StrokeCap.Round)
    drawLine(color, Offset(12 * u, 12 * u), Offset(15.5f * u, 14 * u), 1.9f * u, StrokeCap.Round)
}

@Composable
fun WarningGlyph(size: Dp, color: Color, modifier: Modifier = Modifier) = Canvas(modifier.size(size)) {
    val u = this.size.width / 24f; val s = st(u)
    drawPath(Path().apply { moveTo(12 * u, 3.5f * u); lineTo(21.5f * u, 20 * u); lineTo(2.5f * u, 20 * u); close() }, color, style = s)
    drawLine(color, Offset(12 * u, 10 * u), Offset(12 * u, 14.5f * u), 1.9f * u, StrokeCap.Round)
    drawCircle(color, 1.1f * u, Offset(12 * u, 17.4f * u))
}
