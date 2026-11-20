package com.kryptsafe.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kryptsafe.ui.theme.*
import com.kryptsafe.vault.VaultItem
import com.kryptsafe.vault.VaultViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val MAX_BYTES = 512 * 1024

/** Reads a small text-like vault file fully in memory. Large files are truncated with a notice. */
@Composable
fun TextReaderScreen(item: VaultItem, vm: VaultViewModel, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    var lines by remember { mutableStateOf<List<String>?>(null) }
    var truncated by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(item.id) {
        withContext(Dispatchers.IO) {
            try {
                vm.openReader(item.id).use { r ->
                    val buf = ByteArray(minOf(r.size, MAX_BYTES.toLong()).toInt())
                    var off = 0
                    while (off < buf.size) { val n = r.readAt(off.toLong(), buf, off, buf.size - off); if (n <= 0) break; off += n }
                    truncated = r.size > MAX_BYTES
                    lines = String(buf, 0, off, Charsets.UTF_8).lines()
                }
            } catch (e: Exception) { error = "This file can't be read." }
        }
    }

    Box(Modifier.fillMaxSize().background(Canvas)) {
        when {
            error != null -> KsCard(Modifier.align(Alignment.Center).padding(32.dp)) { Text(error!!, Modifier.padding(24.dp), fontSize = 14.sp) }
            lines == null -> CircularProgressIndicator(Modifier.align(Alignment.Center), color = Color.White)
            else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 96.dp, bottom = 40.dp)) {
                itemsIndexed(lines!!) { i, line ->
                    Row {
                        Text("${i + 1}", Modifier.width(36.dp), fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = TextSecondary, lineHeight = 18.sp)
                        Text(line.ifEmpty { " " }, fontSize = 12.5.sp, fontFamily = FontFamily.Monospace, lineHeight = 18.sp)
                    }
                }
                if (truncated) item { Text("— Showing the first ${MAX_BYTES / 1024} KB. Use Restore to open the whole file. —", Modifier.padding(top = 16.dp), fontSize = 12.sp, color = TextSecondary) }
            }
        }
        Row(Modifier.align(Alignment.TopStart).fillMaxWidth().windowInsetsPadding(WindowInsets.systemBars).padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            GlassIconButton(onClick = onClose) { Icon(Icons.Default.Close, "Close", tint = Color.White) }
            Spacer(Modifier.width(12.dp))
            Text(item.meta.name, Modifier.weight(1f), fontSize = 16.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
