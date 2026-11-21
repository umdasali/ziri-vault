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
private const val MAX_ROW_CHARS = 600

/** Handles UTF-16 (BOM), UTF-8, and falls back to Windows-1252 for legacy files. A cut-off multi-byte character is dropped, not shown as garbage. */
private fun decode(buf: ByteArray, len: Int, cutOff: Boolean): String {
    if (len >= 2) {
        val b0 = buf[0].toInt() and 0xFF; val b1 = buf[1].toInt() and 0xFF
        if (b0 == 0xFF && b1 == 0xFE) return String(buf, 2, (len - 2) and 1.inv(), Charsets.UTF_16LE)
        if (b0 == 0xFE && b1 == 0xFF) return String(buf, 2, (len - 2) and 1.inv(), Charsets.UTF_16BE)
    }
    var end = len
    if (cutOff) { // step back over an incomplete trailing UTF-8 sequence
        var i = end - 1
        while (i > 0 && i > end - 4 && (buf[i].toInt() and 0xC0) == 0x80) i--
        val lead = buf[i].toInt() and 0xFF
        val need = when { lead >= 0xF0 -> 4; lead >= 0xE0 -> 3; lead >= 0xC0 -> 2; else -> 1 }
        if (need > 1 && i + need > end) end = i
    }
    val start = if (end >= 3 && buf[0] == 0xEF.toByte() && buf[1] == 0xBB.toByte() && buf[2] == 0xBF.toByte()) 3 else 0
    return try {
        Charsets.UTF_8.newDecoder().onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
            .decode(java.nio.ByteBuffer.wrap(buf, start, end - start)).toString()
    } catch (e: java.nio.charset.CharacterCodingException) {
        String(buf, start, end - start, java.nio.charset.Charset.forName("windows-1252"))
    }
}

/** One row per line, but very long lines (minified JSON/CSV) are split so a single giant Text can't freeze the screen. */
private fun toRows(text: String): List<Pair<Int?, String>> {
    val rows = ArrayList<Pair<Int?, String>>()
    text.lines().forEachIndexed { i, line ->
        if (line.length <= MAX_ROW_CHARS) rows += (i + 1) to line
        else line.chunked(MAX_ROW_CHARS).forEachIndexed { j, part -> rows += (if (j == 0) i + 1 else null) to part }
    }
    return rows
}

/** Reads a small text-like vault file fully in memory. Large files are truncated with a notice. */
@Composable
fun TextReaderScreen(item: VaultItem, vm: VaultViewModel, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    var lines by remember { mutableStateOf<List<Pair<Int?, String>>?>(null) }
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
                    lines = toRows(decode(buf, off, truncated))
                }
            } catch (e: Exception) { error = "This file can't be read." }
        }
    }

    Box(Modifier.fillMaxSize().background(Canvas)) {
        when {
            error != null -> KsCard(Modifier.align(Alignment.Center).padding(32.dp)) { Text(error!!, Modifier.padding(24.dp), fontSize = 14.sp) }
            lines == null -> CircularProgressIndicator(Modifier.align(Alignment.Center), color = Color.White)
            else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 96.dp, bottom = 40.dp)) {
                itemsIndexed(lines!!) { _, (no, line) ->
                    Row {
                        Text(no?.toString() ?: "", Modifier.width(36.dp), fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = TextSecondary, lineHeight = 18.sp)
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
