package com.kryptsafe.ui

import android.graphics.Bitmap
import android.graphics.Color as AColor
import android.graphics.pdf.PdfRenderer
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kryptsafe.ui.theme.*
import com.kryptsafe.vault.ProxyFdSource
import com.kryptsafe.vault.VaultItem
import com.kryptsafe.vault.VaultViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

private class PdfHolder {
    var source: ProxyFdSource? = null
    var renderer: PdfRenderer? = null
    val lock = Mutex()
    fun close() { runCatching { renderer?.close() }; runCatching { source?.close() }; renderer = null; source = null }
}

/** Renders a vault PDF page by page. The file is decrypted on demand through an in-memory proxy file descriptor. */
@Composable
fun PdfReaderScreen(item: VaultItem, vm: VaultViewModel, onClose: () -> Unit) {
    val context = LocalContext.current
    BackHandler(onBack = onClose)
    val holder = remember { PdfHolder() }
    var pageCount by remember { mutableIntStateOf(-1) }
    var error by remember { mutableStateOf<String?>(null) }
    var zoom by remember { mutableFloatStateOf(1f) }

    LaunchedEffect(item.id) {
        withContext(Dispatchers.IO) {
            try {
                val src = ProxyFdSource(context, vm.openReader(item.id))
                holder.source = src
                val r = PdfRenderer(src.descriptor)
                holder.renderer = r
                pageCount = r.pageCount
            } catch (e: Exception) {
                error = "This PDF can't be opened (it may be damaged or password-protected)."
            }
        }
    }
    DisposableEffect(Unit) { onDispose { holder.close() } }

    val listState = rememberLazyListState()
    val screenW = LocalConfiguration.current.screenWidthDp.dp
    val density = LocalDensity.current

    Box(Modifier.fillMaxSize().background(Canvas)) {
        when {
            error != null -> KsCard(Modifier.align(Alignment.Center).padding(32.dp)) { Text(error!!, Modifier.padding(24.dp), fontSize = 14.sp) }
            pageCount < 0 -> CircularProgressIndicator(Modifier.align(Alignment.Center), color = Color.White)
            else -> Box(Modifier.fillMaxSize().horizontalScroll(rememberScrollState())) {
                val pageWidth = (screenW - 32.dp) * zoom
                val targetPx = with(density) { (pageWidth * 1.6f).roundToPx() }
                LazyColumn(
                    Modifier.width(pageWidth + 32.dp).fillMaxHeight(), state = listState,
                    contentPadding = PaddingValues(top = 96.dp, bottom = 40.dp, start = 16.dp, end = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    itemsIndexed(List(pageCount) { it }, key = { _, i -> "$i-$zoom" }) { _, index ->
                        PdfPage(holder, index, targetPx)
                    }
                }
            }
        }

        Row(Modifier.align(Alignment.TopStart).fillMaxWidth().windowInsetsPadding(WindowInsets.systemBars).padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            GlassIconButton(onClick = onClose) { Icon(Icons.Default.Close, "Close", tint = Color.White) }
            Spacer(Modifier.width(12.dp))
            Text(item.meta.name, Modifier.weight(1f), fontSize = 16.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (pageCount > 0) {
                GlassBadge { Text("${(listState.firstVisibleItemIndex + 1).coerceAtMost(pageCount)} / $pageCount", fontSize = 11.sp, fontWeight = FontWeight.Medium, lineHeight = 11.sp) }
                Spacer(Modifier.width(10.dp))
                GlassIconButton(onClick = { zoom = if (zoom > 1f) 1f else 2f }, active = zoom > 1f) {
                    Text(if (zoom > 1f) "1×" else "2×", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = if (zoom > 1f) Ink else Color.White)
                }
            }
        }
    }
}

@Composable
private fun PdfPage(holder: PdfHolder, index: Int, targetPx: Int) {
    val bmp by produceState<Bitmap?>(null, index, targetPx) {
        value = withContext(Dispatchers.IO) {
            holder.lock.withLock {
                val r = holder.renderer ?: return@withLock null
                runCatching {
                    r.openPage(index).use { p ->
                        val h = (targetPx.toFloat() * p.height / p.width).toInt().coerceAtLeast(1)
                        Bitmap.createBitmap(targetPx, h, Bitmap.Config.ARGB_8888).also {
                            it.eraseColor(AColor.WHITE)
                            p.render(it, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        }
                    }
                }.getOrNull()
            }
        }
    }
    val shape = RoundedCornerShape(14.dp)
    val b = bmp
    if (b != null) Image(b.asImageBitmap(), "Page ${index + 1}", Modifier.fillMaxWidth().clip(shape).border(1.dp, Hairline, shape), contentScale = ContentScale.FillWidth)
    else Box(Modifier.fillMaxWidth().aspectRatio(0.72f).clip(shape).background(CardBg), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = Color.White.copy(0.7f), modifier = Modifier.size(24.dp), strokeWidth = 2.5.dp)
    }
}
