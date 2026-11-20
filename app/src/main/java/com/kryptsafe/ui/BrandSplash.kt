package com.kryptsafe.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kryptsafe.R
import com.kryptsafe.ui.theme.Canvas
import com.kryptsafe.ui.theme.TextSecondary

/**
 * Branded splash shown on cold start. The logo sits exactly where the system splash icon was
 * (centred, 149dp), so the hand-off from the OS splash is seamless; the wordmark and tagline then fade in.
 */
@Composable
fun BrandSplash(onDone: () -> Unit) {
    val text = remember { Animatable(0f) }
    val whole = remember { Animatable(1f) }
    LaunchedEffect(Unit) {
        text.animateTo(1f, tween(450, delayMillis = 120))
        kotlinx.coroutines.delay(650)
        whole.animateTo(0f, tween(260))
        onDone()
    }
    Box(
        Modifier.fillMaxSize().alpha(whole.value).background(Canvas)
            // swallow touches so nothing underneath can be tapped during the splash
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
    ) {
        Image(painterResource(R.drawable.ic_logo_tile), null, Modifier.size(149.dp).align(Alignment.Center))
        Column(
            Modifier.align(Alignment.Center).offset(y = 149.dp / 2 + 56.dp).alpha(text.value),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("KryptSafe", fontSize = 30.sp, fontWeight = FontWeight.Medium, lineHeight = 36.sp)
            Spacer(Modifier.height(6.dp))
            Text("Private. Encrypted. Offline.", fontSize = 13.sp, color = TextSecondary, lineHeight = 18.sp)
        }
        Text(
            "by Ziriverse", fontSize = 12.sp, color = TextSecondary, lineHeight = 17.sp,
            modifier = Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.navigationBars).padding(bottom = 36.dp).alpha(text.value),
        )
    }
}
