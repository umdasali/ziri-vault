package com.kryptsafe.ui

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.media.AudioManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.kryptsafe.ui.theme.*
import com.kryptsafe.video.EngineKind
import com.kryptsafe.video.ExoEngine
import com.kryptsafe.video.VideoEngine
import com.kryptsafe.video.VlcEngine
import com.kryptsafe.vault.VaultItem
import com.kryptsafe.vault.VaultViewModel
import kotlinx.coroutines.delay

private fun fmt(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0); val h = s / 3600; val m = (s % 3600) / 60; val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}

private val SPEEDS = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)
private fun speedLabel(s: Float) = (if (s % 1f == 0f) "%.0f" else "%s").format(if (s % 1f == 0f) s else s.toString().trimEnd('0')) + "×"

/**
 * Private video player. Decrypts in memory only. Tries ExoPlayer first (hardware decoding); if the file can't be
 * played, switches to the VLC compatibility engine automatically. Both share the same controls.
 */
@Composable
fun VideoPlayerScreen(videos: List<VaultItem>, startId: String, vm: VaultViewModel, onClose: () -> Unit) {
    val context = LocalContext.current
    val activity = context as? Activity
    BackHandler(onBack = onClose)

    var index by remember { mutableIntStateOf(videos.indexOfFirst { it.id == startId }.coerceAtLeast(0)) }
    val item = videos[index]

    // ---- user settings that survive engine/video changes
    var speed by remember { mutableFloatStateOf(1f) }
    var loop by remember { mutableStateOf(false) }
    var muted by remember { mutableStateOf(false) }
    var fill by remember { mutableStateOf(false) }
    var landscape by remember { mutableStateOf(false) }

    // ---- engine selection (+ automatic fallback)
    var kind by remember(item.id) { mutableStateOf(EngineKind.EXO) }
    var resumeAt by remember(item.id) { mutableLongStateOf(0L) }
    var failure by remember(item.id, kind) { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }

    val engine: VideoEngine = remember(item.id, kind) {
        when (kind) {
            EngineKind.EXO -> ExoEngine(context, vm::itemFile, item.id, item.meta.mime, resumeAt) { failure = it }
            EngineKind.VLC -> VlcEngine(context, { vm.openReader(item.id) }, resumeAt) { failure = it }
        }
    }
    DisposableEffect(engine) { onDispose { engine.release() } }
    // With a delayed auto-lock the vault stays open in the background — never keep playing audio there.
    DisposableEffect(engine) {
        val owner = context as? androidx.lifecycle.LifecycleOwner
        val obs = androidx.lifecycle.LifecycleEventObserver { _, e -> if (e == androidx.lifecycle.Lifecycle.Event.ON_STOP) engine.pause() }
        owner?.lifecycle?.addObserver(obs)
        onDispose { owner?.lifecycle?.removeObserver(obs) }
    }
    LaunchedEffect(failure) {
        if (failure != null && kind == EngineKind.EXO) {
            resumeAt = engine.state.positionMs; notice = "Switched to the compatibility engine for this file"; kind = EngineKind.VLC
        }
    }
    LaunchedEffect(engine) { engine.setSpeed(speed); engine.setLoop(loop); engine.setMuted(muted); engine.setFill(fill) }
    LaunchedEffect(engine) { while (true) { engine.poll(); delay(250) } }
    LaunchedEffect(notice) { if (notice != null) { delay(3500); notice = null } }

    // ---- orientation / brightness / volume housekeeping
    DisposableEffect(Unit) {
        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            activity?.window?.let { w -> w.attributes = w.attributes.also { it.screenBrightness = -1f } }
        }
    }
    val audio = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }

    val st = engine.state
    val switchEngine: () -> Unit = {
        resumeAt = engine.state.positionMs
        notice = if (kind == EngineKind.EXO) "Compatibility engine (VLC) — handles AVI, WMV, MPEG and more" else null
        kind = if (kind == EngineKind.EXO) EngineKind.VLC else EngineKind.EXO
    }
    var controls by remember { mutableStateOf(true) }
    var touch by remember { mutableIntStateOf(0) }
    var dragging by remember { mutableStateOf<Float?>(null) }
    var level by remember { mutableStateOf<Pair<String, Float>?>(null) }
    var flash by remember { mutableStateOf<Pair<Boolean, Int>?>(null) } // (isForward, tapCount)
    var changing by remember { mutableIntStateOf(0) }
    LaunchedEffect(controls, st.playing, touch) { if (controls && st.playing) { delay(3500); controls = false } }
    LaunchedEffect(level) { if (level != null) { delay(900); level = null } }
    LaunchedEffect(flash) { if (flash != null) { delay(700); flash = null } }

    Box(Modifier.fillMaxSize().background(Canvas)) {
        // Rebuilt on rotation so engines that can't re-fit an existing surface (VLC) get a fresh one.
        key(engine, LocalConfiguration.current.orientation) { AndroidView(factory = { engine.createView(it) }, modifier = Modifier.fillMaxSize()) }

        // ---- gesture layer: tap = controls, double-tap = ±10s, drag left = brightness, drag right = volume
        Box(
            Modifier.fillMaxSize()
                .pointerInput(engine) {
                    detectTapGestures(
                        onTap = { controls = !controls; touch++ },
                        onDoubleTap = { o ->
                            val fwd = o.x > size.width / 2
                            engine.seekTo(engine.state.positionMs + if (fwd) 10_000 else -10_000)
                            flash = fwd to ((flash?.second ?: 0) + 1)
                        },
                    )
                }
                .pointerInput(engine) {
                    var left = true; var value = 0.5f
                    detectVerticalDragGestures(
                        onDragStart = { o ->
                            left = o.x < size.width / 2
                            value = if (left) (activity?.window?.attributes?.screenBrightness?.takeIf { it >= 0 } ?: 0.5f)
                            else audio.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                        },
                        onVerticalDrag = { c, dy ->
                            c.consume()
                            value = (value - dy / size.height * 1.3f).coerceIn(0f, 1f)
                            if (left) {
                                activity?.window?.let { w -> w.attributes = w.attributes.also { it.screenBrightness = value.coerceAtLeast(0.02f) } }
                                level = "Brightness" to value
                            } else {
                                audio.setStreamVolume(AudioManager.STREAM_MUSIC, (value * audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)).toInt(), 0)
                                level = "Volume" to value
                            }
                        },
                    )
                },
        )

        // ---- transient indicators
        flash?.let { (fwd, n) ->
            GlassBadge(Modifier.align(if (fwd) Alignment.CenterEnd else Alignment.CenterStart).padding(horizontal = 48.dp)) {
                Text((if (fwd) "+" else "−") + "${10 * n}s", fontSize = 16.sp, fontWeight = FontWeight.Medium)
            }
        }
        level?.let { (name, v) ->
            GlassBadge(Modifier.align(Alignment.TopCenter).windowInsetsPadding(WindowInsets.systemBars).padding(top = 80.dp)) {
                Text("$name ${(v * 100).toInt()}%", fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
            }
        }
        if (st.buffering && st.error == null && failure == null) androidx.compose.material3.CircularProgressIndicator(Modifier.align(Alignment.Center).size(34.dp), color = Color.White, strokeWidth = 3.dp)

        if (failure != null && kind == EngineKind.VLC) KsCard(Modifier.align(Alignment.Center).padding(32.dp)) {
            Column(Modifier.padding(24.dp)) {
                Text("This video can't be played", fontSize = 18.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(6.dp))
                Text("Neither engine could decode this file. It may be damaged or use an unsupported codec. Use Restore to open it in another app.", fontSize = 13.sp, color = TextSecondary, lineHeight = 18.sp)
            }
        }

        AnimatedVisibility(controls || st.ended || failure != null, enter = fadeIn(), exit = fadeOut()) {
            Box(Modifier.fillMaxSize()) {
                Box(Modifier.align(Alignment.TopCenter).fillMaxWidth().height(150.dp).background(Brush.verticalGradient(listOf(Color.Black.copy(0.65f), Color.Transparent))))
                Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(300.dp).background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(0.75f)))))

                // top bar
                Column(Modifier.align(Alignment.TopStart).fillMaxWidth().windowInsetsPadding(WindowInsets.systemBars).padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        GlassIconButton(onClick = onClose) { Icon(Icons.Default.Close, "Close", tint = Color.White) }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(item.meta.name, fontSize = 16.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (st.videoWidth > 0) Text("${st.videoWidth}×${st.videoHeight}  ·  ${if (videos.size > 1) "${index + 1} of ${videos.size}  ·  " else ""}${engine.kind.label}", fontSize = 12.sp, color = TextSecondary, lineHeight = 16.sp)
                        }
                        GlassIconButton(onClick = { switchEngine(); touch++ }, active = kind == EngineKind.VLC, size = 44.dp, contentDescription = "Switch playback engine") {
                            EngineGlyph(22.dp, if (kind == EngineKind.VLC) Ink else Color.White)
                        }
                        Spacer(Modifier.width(8.dp))
                        GlassBadge {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                LockGlyph(13.dp, Gold, hole = Color(0xFF3A3F42)); Spacer(Modifier.width(5.dp))
                                Text("In-memory", fontSize = 11.sp, fontWeight = FontWeight.Medium, lineHeight = 11.sp)
                            }
                        }
                    }
                    notice?.let { GlassBadge(Modifier.padding(top = 10.dp)) { Text(it, fontSize = 12.sp, fontWeight = FontWeight.Medium) } }
                }

                // transport: previous · −10 · play/pause · +10 · next
                Row(Modifier.align(Alignment.Center), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    val hasPrev = index > 0; val hasNext = index < videos.lastIndex
                    GlassIconButton(onClick = { if (hasPrev) index-- }, size = 48.dp, modifier = Modifier.alpha(if (hasPrev) 1f else 0.3f), contentDescription = "Previous video") { SkipGlyph(22.dp, Color.White, next = false) }
                    GlassIconButton(onClick = { engine.seekTo(st.positionMs - 10_000); touch++ }, size = 54.dp, contentDescription = "Back 10 seconds") { Seek10Glyph(30.dp, Color.White, forward = false) }
                    GlassIconButton(onClick = { engine.togglePlay(); touch++ }, active = true, size = 72.dp, contentDescription = if (st.playing) "Pause" else "Play") {
                        if (st.playing) PauseGlyph(30.dp, Ink) else Icon(Icons.Default.PlayArrow, null, tint = Ink, modifier = Modifier.size(36.dp))
                    }
                    GlassIconButton(onClick = { engine.seekTo(st.positionMs + 10_000); touch++ }, size = 54.dp, contentDescription = "Forward 10 seconds") { Seek10Glyph(30.dp, Color.White, forward = true) }
                    GlassIconButton(onClick = { if (hasNext) index++ }, size = 48.dp, modifier = Modifier.alpha(if (hasNext) 1f else 0.3f), contentDescription = "Next video") { SkipGlyph(22.dp, Color.White, next = true) }
                }

                // bottom: seek bar + option pills
                GlassCard(Modifier.align(Alignment.BottomCenter).fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars).padding(horizontal = 16.dp, vertical = 16.dp), corner = 28.dp) {
                    Column(Modifier.padding(top = 10.dp, bottom = 14.dp)) {
                        Column(Modifier.padding(horizontal = 18.dp)) {
                            Slider(
                                value = dragging ?: if (st.durationMs > 0) (st.positionMs.toFloat() / st.durationMs).coerceIn(0f, 1f) else 0f,
                                onValueChange = { dragging = it; touch++ },
                                onValueChangeFinished = { dragging?.let { engine.seekTo((it * st.durationMs).toLong()) }; dragging = null },
                                colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.White, inactiveTrackColor = Color.White.copy(0.25f)),
                            )
                            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
                                Text(fmt(dragging?.let { (it * st.durationMs).toLong() } ?: st.positionMs), fontSize = 12.sp, color = TextSecondary, lineHeight = 16.sp)
                                Spacer(Modifier.weight(1f))
                                Text(fmt(st.durationMs), fontSize = 12.sp, color = TextSecondary, lineHeight = 16.sp)
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            // speed — a text label is the clearest thing here
                            var m by remember { mutableStateOf(false) }
                            Box {
                                KsChip(speedLabel(speed), active = speed != 1f) { m = true; touch++ }
                                DropdownMenu(m, { m = false }, shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp), containerColor = CardBg) {
                                    SPEEDS.forEach { sp -> DropdownMenuItem(text = { Text((if (sp == speed) "✓  " else "     ") + speedLabel(sp) + if (sp == 1f) "  Normal" else "") }, onClick = { speed = sp; engine.setSpeed(sp); m = false }) }
                                }
                            }
                            IconToggle(loop, "Loop", { loop = !loop; engine.setLoop(loop); touch++ }) { RepeatGlyph(22.dp, it) }
                            IconToggle(muted, if (muted) "Unmute" else "Mute", { muted = !muted; engine.setMuted(muted); touch++ }) { VolumeGlyph(22.dp, it, muted) }
                            IconToggle(fill, if (fill) "Fit to screen" else "Fill screen", { fill = !fill; engine.setFill(fill); touch++ }) { FitGlyph(22.dp, it) }
                            IconToggle(landscape, "Lock landscape", {
                                landscape = !landscape
                                activity?.requestedOrientation = if (landscape) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                                touch++
                            }) { RotateGlyph(22.dp, it) }
                            // audio + subtitles — one menu; names need text
                            var mt by remember { mutableStateOf(false) }
                            val subsOn = st.textTracks.any { it.selected }
                            Box {
                                GlassIconButton(onClick = { mt = true; touch++ }, active = subsOn, size = 44.dp, contentDescription = "Audio and subtitles") { TracksGlyph(22.dp, if (subsOn) Ink else Color.White) }
                                DropdownMenu(mt, { mt = false }, shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp), containerColor = CardBg) {
                                    DropdownMenuItem(text = { Text("Audio", fontSize = 12.sp, color = TextSecondary) }, onClick = {}, enabled = false)
                                    if (st.audioTracks.isEmpty()) DropdownMenuItem(text = { Text("Default audio") }, onClick = { mt = false })
                                    st.audioTracks.forEach { t -> DropdownMenuItem(text = { Text((if (t.selected) "✓  " else "     ") + t.label) }, onClick = { engine.selectAudio(t.id); mt = false }) }
                                    androidx.compose.material3.HorizontalDivider(color = Hairline)
                                    DropdownMenuItem(text = { Text("Subtitles", fontSize = 12.sp, color = TextSecondary) }, onClick = {}, enabled = false)
                                    if (st.textTracks.isEmpty()) DropdownMenuItem(text = { Text("None in this file", color = TextSecondary) }, onClick = { mt = false })
                                    else {
                                        DropdownMenuItem(text = { Text((if (!subsOn) "✓  " else "     ") + "Off") }, onClick = { engine.selectText(null); mt = false })
                                        st.textTracks.forEach { t -> DropdownMenuItem(text = { Text((if (t.selected) "✓  " else "     ") + t.label) }, onClick = { engine.selectText(t.id); mt = false }) }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Round glass toggle: white with a dark icon when on, translucent with a white icon when off. */
@Composable
private fun IconToggle(active: Boolean, description: String, onClick: () -> Unit, glyph: @Composable (Color) -> Unit) =
    GlassIconButton(onClick = onClick, active = active, size = 44.dp, contentDescription = description) { glyph(if (active) Ink else Color.White) }
