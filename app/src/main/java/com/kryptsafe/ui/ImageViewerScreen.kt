package com.kryptsafe.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import com.kryptsafe.vault.VaultItem
import com.kryptsafe.vault.VaultRepository
import com.kryptsafe.vault.VaultViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Decrypts straight into memory: no temp file, nothing is written to cache or the gallery. */
private fun decodeFull(vm: VaultViewModel, id: String, maxDim: Int): Bitmap? = runCatching {
    vm.openReader(id).use { r ->
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeStream(r.openStream(), null, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= maxDim || bounds.outHeight / (sample * 2) >= maxDim) sample *= 2
        val bmp = BitmapFactory.decodeStream(r.openStream(), null, BitmapFactory.Options().apply { inSampleSize = sample })
        val rot = VaultRepository.exifRotation(r.openStream())
        bmp?.let { VaultRepository.rotate(it, rot) }
    }
}.getOrNull()

@Composable
fun ImageViewerScreen(images: List<VaultItem>, startId: String, vm: VaultViewModel, onClose: () -> Unit) {
    val start = images.indexOfFirst { it.id == startId }.coerceAtLeast(0)
    val pager = rememberPagerState(initialPage = start) { images.size }
    var zoomed by remember { mutableStateOf(false) }
    androidx.activity.compose.BackHandler(onBack = onClose)
    val maxDim = with(LocalDensity.current) { maxOf(LocalConfiguration.current.screenHeightDp, LocalConfiguration.current.screenWidthDp).dp.roundToPx() }

    Box(Modifier.fillMaxSize().background(com.kryptsafe.ui.theme.Canvas)) {
        HorizontalPager(pager, Modifier.fillMaxSize(), userScrollEnabled = !zoomed, key = { images[it].id }) { page ->
            ZoomableImage(images[page], vm, maxDim, onZoomChanged = { if (pager.currentPage == page) zoomed = it })
        }
        // Footer: what you're looking at, and a reminder that it only exists decrypted in memory.
        val cur = images[pager.currentPage.coerceIn(0, images.lastIndex)]
        Column(
            Modifier.align(Alignment.BottomStart).fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars).padding(horizontal = 20.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(cur.meta.name, fontSize = 22.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium, lineHeight = 27.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                val ext = cur.meta.name.substringAfterLast('.', "").uppercase().ifEmpty { "IMG" }
                val kb = cur.size / 1024
                com.kryptsafe.ui.theme.GlassBadge { Text(ext, fontSize = 11.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium, lineHeight = 11.sp) }
                com.kryptsafe.ui.theme.GlassBadge { Text(if (kb >= 1024) "%.1f MB".format(kb / 1024.0) else "$kb KB", fontSize = 11.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium, lineHeight = 11.sp) }
                com.kryptsafe.ui.theme.GlassBadge {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        LockGlyph(13.dp, com.kryptsafe.ui.theme.Gold, hole = Color(0xFF3A3F42))
                        Spacer(Modifier.width(5.dp))
                        Text("Decrypted in memory only", fontSize = 11.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium, lineHeight = 11.sp)
                    }
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.systemBars).padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            com.kryptsafe.ui.theme.GlassIconButton(onClick = onClose) { androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Default.Close, "Close", tint = Color.White) }
            Spacer(Modifier.weight(1f))
            com.kryptsafe.ui.theme.GlassBadge { Text("${pager.currentPage + 1} / ${images.size}", color = Color.White, fontSize = 11.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium, lineHeight = 11.sp) }
        }
    }
}

@Composable
private fun ZoomableImage(item: VaultItem, vm: VaultViewModel, maxDim: Int, onZoomChanged: (Boolean) -> Unit) {
    val bmp by produceState<Bitmap?>(null, item.id) { value = withContext(Dispatchers.IO) { decodeFull(vm, item.id, maxDim) } }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    LaunchedEffect(scale) { onZoomChanged(scale > 1.02f) }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val b = bmp
        if (b == null) CircularProgressIndicator(color = Color.White)
        else Image(
            b.asImageBitmap(), null,
            Modifier.fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(onDoubleTap = { if (scale > 1f) { scale = 1f; offset = Offset.Zero } else scale = 2.5f })
                }
                .pointerInput(Unit) {
                    // Only claim the gesture when pinching or already zoomed; a plain one-finger swipe
                    // at 1x must reach the pager so the user can flip between photos.
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        do {
                            val event = awaitPointerEvent()
                            val zoom = event.calculateZoom()
                            val pan = event.calculatePan()
                            if (event.changes.size > 1 || scale > 1f) {
                                scale = (scale * zoom).coerceIn(1f, 6f)
                                offset = if (scale > 1f) offset + pan else Offset.Zero
                                if (zoom != 1f || scale > 1f) event.changes.forEach { if (it.positionChanged()) it.consume() }
                            }
                        } while (event.changes.any { it.pressed })
                    }
                }
                .graphicsLayer(scaleX = scale, scaleY = scale, translationX = offset.x, translationY = offset.y),
            contentScale = androidx.compose.ui.layout.ContentScale.Fit,
        )
    }
}
