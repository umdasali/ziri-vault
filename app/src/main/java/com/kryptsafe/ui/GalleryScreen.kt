package com.kryptsafe.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kryptsafe.crypto.MediaKind
import com.kryptsafe.ui.theme.*
import com.kryptsafe.vault.VaultItem
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class VaultFilter(val title: String) { ALL("All"), PHOTOS("Photos"), VIDEOS("Videos"), FILES("Files") }

private fun formatSize(bytes: Long): String = when {
    bytes >= 1L shl 30 -> "%.1f GB".format(bytes / (1L shl 30).toDouble())
    bytes >= 1L shl 20 -> "%.1f MB".format(bytes / (1L shl 20).toDouble())
    bytes >= 1L shl 10 -> "%.0f KB".format(bytes / 1024.0)
    else -> "$bytes B"
}

private fun badgeLabel(i: VaultItem): String = when (i.meta.kind) {
    MediaKind.VIDEO -> "Video"
    else -> i.meta.name.substringAfterLast('.', "").uppercase().take(4).ifEmpty { "FILE" }
}

private fun kindLabel(k: MediaKind) = when (k) { MediaKind.IMAGE -> "Photo"; MediaKind.VIDEO -> "Video"; MediaKind.DOCUMENT -> "Document"; else -> "File" }

/** Loads a thumbnail off the main thread together with its tinted scrim colour. */
@Composable
private fun rememberThumb(item: VaultItem, thumbnail: (String) -> android.graphics.Bitmap?): Pair<android.graphics.Bitmap?, Color> {
    val state by produceState<Pair<android.graphics.Bitmap?, Color>>(null to Color.Black, item.id) {
        value = withContext(Dispatchers.IO) { thumbnail(item.id).let { it to (it?.let(::scrimColorOf) ?: Color.Black) } }
    }
    return state
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GalleryScreen(
    items: List<VaultItem>,
    busyMessage: String?,
    thumbnail: (String) -> android.graphics.Bitmap?,
    biometricAvailable: Boolean,
    biometricEnabled: Boolean,
    onOpen: (VaultItem) -> Unit,
    onImportMedia: () -> Unit,
    onImportFiles: () -> Unit,
    onDelete: (Set<String>) -> Unit,
    onExport: (Set<String>) -> Unit,
    onOpenSettings: () -> Unit,
    onLock: () -> Unit,
) {
    val haze = rememberHazeState()
    var filter by remember { mutableStateOf(VaultFilter.ALL) }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var menu by remember { mutableStateOf(false) }
    var addMenu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmRestore by remember { mutableStateOf(false) }
    val selecting = selected.isNotEmpty()
    LaunchedEffect(items) { selected = selected.filter { id -> items.any { it.id == id } }.toSet() }
    BackHandler(enabled = selecting) { selected = emptySet() }
    BackHandler(enabled = !selecting && filter != VaultFilter.ALL) { filter = VaultFilter.ALL }

    fun isFile(i: VaultItem) = i.meta.kind == MediaKind.DOCUMENT || i.meta.kind == MediaKind.OTHER
    val shown = when (filter) {
        VaultFilter.ALL -> items
        VaultFilter.PHOTOS -> items.filter { it.meta.kind == MediaKind.IMAGE }
        VaultFilter.VIDEOS -> items.filter { it.meta.kind == MediaKind.VIDEO }
        VaultFilter.FILES -> items.filter(::isFile)
    }
    val photos = items.count { it.meta.kind == MediaKind.IMAGE }
    val videos = items.count { it.meta.kind == MediaKind.VIDEO }
    val files = items.count(::isFile)

    fun clickMod(item: VaultItem): Modifier {
        val isSel = item.id in selected
        return Modifier.combinedClickable(
            onClick = { if (selecting) selected = if (isSel) selected - item.id else selected + item.id else onOpen(item) },
            onLongClick = { selected = selected + item.id },
        )
    }

    Box(Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize().hazeSource(haze),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 140.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // ---- Header: menu · avatar ........ lock ------------------------------------------
            item(span = { GridItemSpan(maxLineSpan) }) {
                Box(Modifier.windowInsetsPadding(WindowInsets.statusBars).padding(top = 12.dp)) {
                    if (selecting) {
                        GlassCard(Modifier.fillMaxWidth(), corner = 24.dp) {
                            Row(Modifier.padding(start = 20.dp, end = 8.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("${selected.size} selected", fontSize = 17.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                                TextButton(onClick = { confirmRestore = true }) { Text("Restore", fontWeight = FontWeight.Medium) }
                                TextButton(onClick = { confirmDelete = true }) { Text("Delete", color = Danger, fontWeight = FontWeight.Medium) }
                                TextButton(onClick = { selected = emptySet() }) { Text("Cancel", color = TextSecondary) }
                            }
                        }
                    } else {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Box {
                                // Gold dot = a real suggestion: biometric unlock is available but not switched on yet.
                                GlassIconButton(onClick = { menu = true }, badge = biometricAvailable && !biometricEnabled) { MenuGlyph(24.dp, Color.White) }
                                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, shape = RoundedCornerShape(20.dp), containerColor = CardBg) {
                                    DropdownMenuItem(text = { Text("Settings") }, onClick = { menu = false; onOpenSettings() })
                                    DropdownMenuItem(text = { Text("Lock now") }, onClick = { menu = false; onLock() })
                                }
                            }
                            Spacer(Modifier.width(10.dp))
                            Image(androidx.compose.ui.res.painterResource(com.kryptsafe.R.drawable.ic_logo_tile), null, Modifier.size(48.dp))
                            Spacer(Modifier.weight(1f))
                            GlassIconButton(onClick = onLock) { LockGlyph(22.dp, Color.White, hole = Color(0xFF3A3F42)) }
                        }
                    }
                }
            }

            // ---- Greeting card -----------------------------------------------------------------
            item(span = { GridItemSpan(maxLineSpan) }) {
                GlassCard(Modifier.fillMaxWidth(), corner = 24.dp) {
                    Row(Modifier.padding(horizontal = 18.dp, vertical = 16.dp), verticalAlignment = Alignment.Top) {
                        ShieldGlyph(26.dp, Color.White, Modifier.padding(top = 2.dp))
                        Spacer(Modifier.width(14.dp))
                        Column {
                            Text("Hi there, your vault is secure", fontSize = 19.sp, fontWeight = FontWeight.Normal, lineHeight = 25.sp)
                            Spacer(Modifier.height(3.dp))
                            Text("$photos ${if (photos == 1) "photo" else "photos"} · $videos ${if (videos == 1) "video" else "videos"} · $files ${if (files == 1) "file" else "files"} · ${formatSize(items.sumOf { it.size })}", fontSize = 12.5.sp, color = TextSecondary, lineHeight = 17.sp)
                        }
                    }
                }
            }

            // ---- Stories: latest items as gold-ringed squircles (+ add) -------------------------
            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    val sq = RoundedCornerShape(18.dp)
                    Box(Modifier.size(68.dp).clip(sq).background(ChipBg).border(1.dp, Hairline, sq).clickable { addMenu = true }, contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Add, "Add", tint = Color.White, modifier = Modifier.size(26.dp))
                    }
                    items.take(8).forEach { item -> Story(item, thumbnail) { onOpen(item) } }
                }
            }

            // ---- Filter chips ------------------------------------------------------------------
            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    VaultFilter.entries.forEach { f -> KsChip(f.title, active = f == filter) { filter = f; selected = emptySet() } }
                }
            }

            if (shown.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) { EmptyState(filter) }
            else {
                // ---- Hero carousel (peeks the next card) ---------------------------------------
                item(span = { GridItemSpan(maxLineSpan) }) {
                    val hero = shown.take(5)
                    val pager = rememberPagerState { hero.size }
                    HorizontalPager(
                        pager, Modifier.fillMaxWidth().layout20(), contentPadding = PaddingValues(start = 20.dp, end = if (hero.size > 1) 52.dp else 20.dp),
                        pageSpacing = 12.dp, key = { hero[it].id },
                    ) { page ->
                        HeroCard(hero[page], thumbnail, hero[page].id in selected, clickMod(hero[page]))
                    }
                }
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.Bottom) {
                        Text("Library", fontSize = 22.sp, fontWeight = FontWeight.Medium, lineHeight = 27.sp, modifier = Modifier.weight(1f))
                        Text("${shown.size} item${if (shown.size == 1) "" else "s"}", fontSize = 13.sp, color = TextSecondary, lineHeight = 18.sp)
                    }
                }
            }

            items(shown, key = { it.id }) { item -> FeedCard(item, thumbnail, item.id in selected, clickMod(item)) }
        }

        // Soft fade behind the status bar so scrolled content never fights with the clock/signal icons.
        Box(
            Modifier.align(Alignment.TopCenter).fillMaxWidth()
                .height(WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 10.dp)
                .background(Brush.verticalGradient(listOf(Canvas.copy(alpha = 0.92f), Color.Transparent)))
        )

        // ---- Floating glass navigation pill (real backdrop blur on Android 12+) -----------------
        Box(Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.navigationBars).padding(bottom = 16.dp)) {
            Row(
                Modifier.clip(CircleShape)
                    .hazeEffect(haze) { blurRadius = 16.dp; tints = listOf(HazeTint(Color.Black.copy(alpha = 0.28f)), HazeTint(GlassFill)); noiseFactor = 0f }
                    .border(1.dp, Hairline, CircleShape)
                    .padding(7.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically,
            ) {
                val home = filter == VaultFilter.ALL
                GlassIconButton(onClick = { filter = VaultFilter.ALL; selected = emptySet() }, active = home) { HomeGlyph(24.dp, if (home) Ink else Color.White, filled = home) }
                Box {
                    GlassIconButton(onClick = { addMenu = true }) { Icon(Icons.Default.Add, "Add", tint = Color.White, modifier = Modifier.size(26.dp)) }
                    DropdownMenu(expanded = addMenu, onDismissRequest = { addMenu = false }, shape = RoundedCornerShape(20.dp), containerColor = CardBg) {
                        DropdownMenuItem(text = { Text("Photos & videos") }, onClick = { addMenu = false; onImportMedia() })
                        DropdownMenuItem(text = { Text("Files (PDF, documents)") }, onClick = { addMenu = false; onImportFiles() })
                    }
                }
                GlassIconButton(onClick = onLock) { LockGlyph(22.dp, Color.White, hole = Color(0xFF3A3F42)) }
            }
        }

        if (busyMessage != null) Box(Modifier.fillMaxSize().background(Color.Black.copy(0.6f)).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}, contentAlignment = Alignment.Center) {
            KsCard {
                Row(Modifier.padding(horizontal = 26.dp, vertical = 22.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp), strokeWidth = 3.dp)
                    Spacer(Modifier.width(16.dp)); Text(busyMessage, fontWeight = FontWeight.Medium)
                }
            }
        }
    }

    if (confirmRestore) AlertDialog(
        onDismissRequest = { confirmRestore = false },
        shape = RoundedCornerShape(28.dp), containerColor = CardBg,
        title = { Text("Restore ${selected.size} item(s)?", fontWeight = FontWeight.Medium) },
        text = { Text("Restored files are saved to a folder you choose and are NOT encrypted there — other apps with access to that folder can open them. Your encrypted copies stay in the vault.", color = TextSecondary) },
        confirmButton = { TextButton(onClick = { onExport(selected); selected = emptySet(); confirmRestore = false }) { Text("Choose folder", fontWeight = FontWeight.Medium) } },
        dismissButton = { TextButton(onClick = { confirmRestore = false }) { Text("Cancel", color = TextSecondary) } },
    )

    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        shape = RoundedCornerShape(28.dp), containerColor = CardBg,
        title = { Text("Delete ${selected.size} item(s)?", fontWeight = FontWeight.Medium) },
        text = { Text("They'll be permanently removed from your vault. This can't be undone.", color = TextSecondary) },
        confirmButton = { TextButton(onClick = { onDelete(selected); selected = emptySet(); confirmDelete = false }) { Text("Delete", color = Danger, fontWeight = FontWeight.Medium) } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel", color = TextSecondary) } },
    )
}

/** Lets the pager bleed to the screen edges while the rest of the grid keeps its 20dp margin. */
private fun Modifier.layout20(): Modifier = this.layout { m, c ->
    val extra = 40.dp.roundToPx()
    val p = m.measure(c.copy(maxWidth = c.maxWidth + extra, minWidth = c.maxWidth + extra))
    layout(c.maxWidth, p.height) { p.place(-extra / 2, 0) }
}

/** Story squircle: 18dp radius, 1.5dp gold-gradient ring, real thumbnail inside. */
@Composable
private fun Story(item: VaultItem, thumbnail: (String) -> android.graphics.Bitmap?, onClick: () -> Unit) {
    val (bmp, _) = rememberThumb(item, thumbnail)
    val ring = RoundedCornerShape(18.dp)
    Box(
        Modifier.size(68.dp).clip(ring).border(1.5.dp, Brush.linearGradient(listOf(Gold, GoldLight, Gold)), ring).padding(3.5.dp)
            .clip(RoundedCornerShape(14.dp)).background(CardBg).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (bmp != null) Image(bmp.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else if (item.meta.kind == MediaKind.VIDEO) VideoGlyph(26.dp, Color.White) else DocGlyph(26.dp, Color.White)
    }
}

@Composable
private fun EmptyState(filter: VaultFilter) {
    KsCard(Modifier.fillMaxWidth().padding(top = 4.dp), corner = 28.dp) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 34.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(64.dp).clip(CircleShape).background(ChipBg), contentAlignment = Alignment.Center) {
                when (filter) {
                    VaultFilter.PHOTOS -> PhotoGlyph(30.dp, Color.White)
                    VaultFilter.VIDEOS -> VideoGlyph(30.dp, Color.White)
                    VaultFilter.FILES -> DocGlyph(30.dp, Color.White)
                    VaultFilter.ALL -> LockGlyph(30.dp, Color.White, hole = ChipBg)
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(if (filter == VaultFilter.ALL) "Your vault is empty" else "No ${filter.title.lowercase()} yet", fontSize = 22.sp, fontWeight = FontWeight.Medium, lineHeight = 27.sp)
            Spacer(Modifier.height(6.dp))
            Text("Tap + to add ${if (filter == VaultFilter.ALL) "photos, videos and files" else filter.title.lowercase()}. They're encrypted on this device and never leave it.",
                fontSize = 13.sp, color = TextSecondary, textAlign = TextAlign.Center, lineHeight = 18.sp)
        }
    }
}

/** Big carousel card: full-bleed photo, scrim tinted with the photo's own colour, light-weight title. */
@Composable
private fun HeroCard(item: VaultItem, thumbnail: (String) -> android.graphics.Bitmap?, selected: Boolean, modifier: Modifier) {
    val (bmp, tint) = rememberThumb(item, thumbnail)
    val shape = RoundedCornerShape(28.dp)
    Box(
        modifier.fillMaxWidth().height(380.dp).clip(shape).background(CardBg)
            .border(if (selected) 2.dp else 1.dp, if (selected) Color.White else Hairline, shape),
    ) {
        if (bmp != null) Image(bmp.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xFF3A3F42), CardBg))), contentAlignment = Alignment.Center) { if (item.meta.kind == MediaKind.VIDEO) VideoGlyph(72.dp, Color.White.copy(0.55f)) else DocGlyph(72.dp, Color.White.copy(0.55f)) }
        Box(Modifier.matchParentSize().background(Brush.verticalGradient(0f to Color.Transparent, 0.38f to Color.Transparent, 1f to tint.copy(alpha = 0.94f))))
        if (item.meta.kind != MediaKind.IMAGE) GlassBadge(Modifier.align(Alignment.TopStart).padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (item.meta.kind == MediaKind.VIDEO) { Icon(Icons.Default.PlayArrow, null, tint = Color.White, modifier = Modifier.size(14.dp)); Spacer(Modifier.width(3.dp)) }
                Text(badgeLabel(item), fontSize = 11.sp, fontWeight = FontWeight.Medium, lineHeight = 11.sp)
            }
        }
        if (selected) Box(Modifier.align(Alignment.TopEnd).padding(16.dp).size(28.dp).clip(CircleShape).background(Color.White), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Check, null, tint = Ink, modifier = Modifier.size(17.dp))
        }
        Column(Modifier.align(Alignment.BottomStart).padding(start = 22.dp, end = 22.dp, bottom = 22.dp)) {
            Text("${kindLabel(item.meta.kind)} · ${formatSize(item.size)}", fontSize = 13.sp, color = Color.White.copy(alpha = 0.78f), lineHeight = 18.sp)
            Spacer(Modifier.height(2.dp))
            Text(item.meta.name, fontSize = 28.sp, fontWeight = FontWeight.Medium, lineHeight = 33.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Grid card: thumbnail, tinted bottom scrim, white title + grey subtitle, glass badges. */
@Composable
private fun FeedCard(item: VaultItem, thumbnail: (String) -> android.graphics.Bitmap?, selected: Boolean, modifier: Modifier) {
    val (bmp, tint) = rememberThumb(item, thumbnail)
    val shape = RoundedCornerShape(26.dp)
    Box(
        modifier.aspectRatio(0.82f).clip(shape).background(CardBg)
            .border(if (selected) 2.dp else 1.dp, if (selected) Color.White else Hairline, shape),
    ) {
        if (bmp != null) Image(bmp.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xFF3A3F42), CardBg))), contentAlignment = Alignment.Center) { if (item.meta.kind == MediaKind.VIDEO) VideoGlyph(44.dp, Color.White.copy(0.55f)) else DocGlyph(44.dp, Color.White.copy(0.55f)) }
        Box(Modifier.matchParentSize().background(Brush.verticalGradient(0f to Color.Transparent, 0.42f to Color.Transparent, 1f to tint.copy(alpha = 0.92f))))
        if (item.meta.kind == MediaKind.VIDEO || item.meta.kind == MediaKind.DOCUMENT)
            GlassBadge(Modifier.align(Alignment.TopStart).padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (item.meta.kind == MediaKind.VIDEO) { Icon(Icons.Default.PlayArrow, null, tint = Color.White, modifier = Modifier.size(13.dp)); Spacer(Modifier.width(3.dp)) }
                    Text(badgeLabel(item), fontSize = 11.sp, fontWeight = FontWeight.Medium, lineHeight = 11.sp)
                }
            }
        if (selected) Box(Modifier.align(Alignment.TopEnd).padding(12.dp).size(26.dp).clip(CircleShape).background(Color.White), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Check, null, tint = Ink, modifier = Modifier.size(16.dp))
        }
        Column(Modifier.align(Alignment.BottomStart).padding(14.dp)) {
            Text(item.meta.name, fontSize = 14.sp, fontWeight = FontWeight.Medium, lineHeight = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${kindLabel(item.meta.kind)} · ${formatSize(item.size)}", fontSize = 12.sp, color = Color.White.copy(alpha = 0.7f), lineHeight = 17.sp, maxLines = 1)
        }
    }
}
