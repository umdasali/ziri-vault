package com.kryptsafe.ui.theme

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ---- Design tokens (single source of truth for the whole app) -------------------------------
val Canvas = Color(0xFF121212)          // primary screen background (deep matte black)
val CardBg = Color(0xFF1E1E1E)          // elevated card / sheet / dialog surface
val ChipBg = Color(0xFF2C2C2E)          // inactive pill & key surface
val Ink = Color(0xFF121212)             // inverse text/icons on white (active) surfaces
val TextPrimary = Color.White
val TextSecondary = Color(0xFF9E9E9E)   // subtitles, timestamps, inactive text
val Gold = Color(0xFFE5B84B)            // accent — used sparingly (story rings)
val GoldLight = Color(0xFFF3D98B)
val Danger = Color(0xFFFF6B6B)
val Hairline = Color.White.copy(alpha = 0.08f)  // 1dp separators instead of harsh borders
val GlassFill = Color.White.copy(alpha = 0.15f) // glass overlay fill

private val DarkScheme = darkColorScheme(
    primary = Color.White, onPrimary = Ink,
    secondary = ChipBg, onSecondary = Color.White,
    background = Canvas, onBackground = TextPrimary,
    surface = CardBg, onSurface = TextPrimary,
    surfaceTint = Color.Transparent,
    surfaceContainer = CardBg, surfaceContainerHigh = CardBg,
    surfaceVariant = ChipBg, onSurfaceVariant = TextSecondary,
    outline = Hairline, error = Danger,
)

/** Plus Jakarta Sans, bundled in the app (variable font) so nothing is fetched from the network. */
@OptIn(ExperimentalTextApi::class)
val Jakarta = FontFamily(
    listOf(400, 500, 600, 700, 800).map { w ->
        Font(com.kryptsafe.R.font.plus_jakarta_sans, FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(w)))
    }
)

private fun jakartaTypography(): Typography {
    val t = Typography()
    fun TextStyle.j() = copy(fontFamily = Jakarta)
    return t.copy(
        displayLarge = t.displayLarge.j(), displayMedium = t.displayMedium.j(), displaySmall = t.displaySmall.j(),
        headlineLarge = t.headlineLarge.j(), headlineMedium = t.headlineMedium.j(), headlineSmall = t.headlineSmall.j(),
        titleLarge = t.titleLarge.j(), titleMedium = t.titleMedium.j(), titleSmall = t.titleSmall.j(),
        bodyLarge = t.bodyLarge.j(), bodyMedium = t.bodyMedium.j(), bodySmall = t.bodySmall.j(),
        labelLarge = t.labelLarge.j(), labelMedium = t.labelMedium.j(), labelSmall = t.labelSmall.j(),
    )
}

@Composable
fun KryptSafeTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DarkScheme, typography = jakartaTypography()) {
        SmokeBackground {
            CompositionLocalProvider(LocalContentColor provides TextPrimary) { content() }
        }
    }
}

/** Matte-black canvas with a few huge, faint grey "smoke" washes drifting slowly (as in the reference). */
@Composable
fun SmokeBackground(content: @Composable () -> Unit) {
    val t = rememberInfiniteTransition(label = "smoke")
    val a by t.animateFloat(0f, 1f, infiniteRepeatable(tween(28000, easing = LinearEasing), RepeatMode.Reverse), label = "a")
    val b by t.animateFloat(0f, 1f, infiniteRepeatable(tween(37000, easing = LinearEasing), RepeatMode.Reverse), label = "b")
    Box(
        Modifier.fillMaxSize().background(Canvas).drawBehind {
            val w = size.width; val h = size.height; val r = maxOf(w, h)
            fun wash(c: Color, cx: Float, cy: Float, rad: Float) = drawCircle(
                Brush.radialGradient(listOf(c, Color.Transparent), center = Offset(cx, cy), radius = rad), radius = rad, center = Offset(cx, cy)
            )
            wash(Color(0xFF4A5156).copy(alpha = 0.34f), w * (0.20f + 0.25f * a), h * (0.10f + 0.06f * b), r * 0.46f)
            wash(Color(0xFF3B4247).copy(alpha = 0.30f), w * (0.95f - 0.25f * b), h * (0.45f + 0.08f * a), r * 0.50f)
            wash(Color(0xFF454B50).copy(alpha = 0.26f), w * (0.10f + 0.20f * a), h * (0.88f - 0.08f * b), r * 0.46f)
        }
    ) { content() }
}

/** Average colour of the lower part of a thumbnail, darkened — used to tint the bottom scrim like the reference. */
fun scrimColorOf(bmp: android.graphics.Bitmap): Color = runCatching {
    val h = bmp.height; val w = bmp.width
    val crop = android.graphics.Bitmap.createBitmap(bmp, 0, (h * 0.6f).toInt(), w, (h * 0.4f).toInt().coerceAtLeast(1))
    val px = android.graphics.Bitmap.createScaledBitmap(crop, 1, 1, true).getPixel(0, 0)
    Color(android.graphics.Color.red(px) / 255f * 0.62f, android.graphics.Color.green(px) / 255f * 0.62f, android.graphics.Color.blue(px) / 255f * 0.62f)
}.getOrDefault(Color.Black)

// ---- Shared surfaces ------------------------------------------------------------------------
/**
 * Glass overlay: white @15% with a hairline rim. (Android has no built-in backdrop blur, so the glass look is
 * produced by the translucent fill over the matte-black canvas / photo content.)
 */
fun Modifier.glass(shape: Shape): Modifier = this.clip(shape).background(GlassFill).border(1.dp, Hairline, shape)

/** Elevated charcoal card with a hairline rim. */
fun Modifier.cardSurface(shape: Shape): Modifier = this.clip(shape).background(CardBg).border(1.dp, Hairline, shape)

@Composable
fun KsCard(modifier: Modifier = Modifier, corner: Dp = 26.dp, content: @Composable () -> Unit) {
    Box(modifier.cardSurface(RoundedCornerShape(corner))) { content() }
}

@Composable
fun GlassCard(modifier: Modifier = Modifier, corner: Dp = 26.dp, content: @Composable () -> Unit) {
    Box(modifier.glass(RoundedCornerShape(corner))) { content() }
}

/** Circular glass icon button (header actions, overlays, nav). [badge] draws the small gold notification dot. */
@Composable
fun GlassIconButton(
    onClick: () -> Unit, modifier: Modifier = Modifier, active: Boolean = false, badge: Boolean = false,
    size: Dp = 48.dp, contentDescription: String? = null, content: @Composable () -> Unit,
) {
    Box(modifier.size(size).then(if (contentDescription != null) Modifier.semantics { this.contentDescription = contentDescription } else Modifier)) {
        Box(
            Modifier.matchParentSize().clip(CircleShape)
                .then(if (active) Modifier.background(Color.White) else Modifier.background(GlassFill).border(1.dp, Hairline, CircleShape))
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) { content() }
        if (badge) Box(Modifier.align(Alignment.TopEnd).padding(top = 9.dp, end = 9.dp).size(9.dp).clip(CircleShape).background(Gold))
    }
}

/** Full-pill filter chip: white + dark text when active, muted slate + white text otherwise. */
@Composable
fun KsChip(text: String, active: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.clip(CircleShape).background(if (active) Color.White else ChipBg).clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 11.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, fontSize = 13.sp, fontWeight = FontWeight.Medium, lineHeight = 13.sp, color = if (active) Ink else TextPrimary)
    }
}

/** Small glass badge for overlays (type tag, counter). */
@Composable
fun GlassBadge(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(modifier.glass(CircleShape).padding(horizontal = 10.dp, vertical = 6.dp), contentAlignment = Alignment.Center) { content() }
}
