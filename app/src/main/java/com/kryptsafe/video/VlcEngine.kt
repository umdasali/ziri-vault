package com.kryptsafe.video

import android.content.Context
import android.view.View
import com.kryptsafe.crypto.VaultFormat
import com.kryptsafe.vault.ProxyFdSource
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout

/**
 * Compatibility engine (VLC) for formats ExoPlayer can't handle. The vault file is exposed through an in-memory
 * proxy file descriptor, so VLC reads decrypted bytes on demand and nothing is written to disk.
 */
class VlcEngine(
    private val context: Context,
    private val openReader: () -> VaultFormat.Reader,
    private val startMs: Long,
    private val onUnplayable: (String) -> Unit,
) : VideoEngine() {
    override val kind = EngineKind.VLC
    // Opened first so a damaged file throws before any native VLC object exists (nothing to leak).
    private var source = ProxyFdSource(context, openReader())
    private val vlc = try { LibVLC(context, arrayListOf("--avcodec-hw=any")) } catch (e: Throwable) { source.close(); throw e }
    private val player = MediaPlayer(vlc)
    private var layout: VLCVideoLayout? = null
    private var released = false
    private var seekedToStart = false

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
    private val focusRequest = android.media.AudioFocusRequest.Builder(android.media.AudioManager.AUDIOFOCUS_GAIN)
        .setAudioAttributes(android.media.AudioAttributes.Builder().setUsage(android.media.AudioAttributes.USAGE_MEDIA).setContentType(android.media.AudioAttributes.CONTENT_TYPE_MOVIE).build())
        .setOnAudioFocusChangeListener { change ->
            if (change == android.media.AudioManager.AUDIOFOCUS_LOSS || change == android.media.AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) runCatching { player.pause() }
        }.build()
    private val noisyReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(c: Context?, i: android.content.Intent?) { runCatching { player.pause() } } // headphones unplugged
    }

    init {
        audioManager.requestAudioFocus(focusRequest)
        androidx.core.content.ContextCompat.registerReceiver(context, noisyReceiver,
            android.content.IntentFilter(android.media.AudioManager.ACTION_AUDIO_BECOMING_NOISY), androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED)
        player.setEventListener { e ->
            when (e.type) {
                MediaPlayer.Event.Playing -> { state.playing = true; state.ended = false; state.buffering = false
                    if (!seekedToStart && startMs > 0) { seekedToStart = true; player.setTime(startMs) } }
                MediaPlayer.Event.Paused, MediaPlayer.Event.Stopped -> state.playing = false
                MediaPlayer.Event.EndReached -> {
                    if (state.loop) replay() else { state.playing = false; state.ended = true }
                }
                MediaPlayer.Event.LengthChanged -> state.durationMs = e.lengthChanged
                MediaPlayer.Event.Buffering -> state.buffering = e.buffering < 100f
                MediaPlayer.Event.EncounteredError -> onUnplayable("VLC_ERROR")
                MediaPlayer.Event.ESAdded, MediaPlayer.Event.ESSelected, MediaPlayer.Event.Vout -> { refreshTracks(); refreshSize() }
            }
        }
    }

    private var mediaStarted = false

    /**
     * May be called again (e.g. after rotation): VLC doesn't re-fit an existing surface, so the UI hands us a fresh
     * layout and we re-attach the same player to it without restarting playback.
     */
    override fun createView(context: Context): View {
        if (mediaStarted) runCatching { player.detachViews() }
        val l = VLCVideoLayout(context)
        layout = l
        player.attachViews(l, null, false, false)
        if (!mediaStarted) {
            mediaStarted = true
            val media = Media(vlc, source.descriptor.fileDescriptor)
            player.media = media
            media.release()
            player.play()
        } else {
            player.setVideoScale(if (state.fill) MediaPlayer.ScaleType.SURFACE_FILL else MediaPlayer.ScaleType.SURFACE_BEST_FIT)
        }
        return l
    }

    /**
     * VLC releases the input when playback ends, so replaying needs a brand-new in-memory descriptor + media.
     */
    override fun replay() {
        if (released) return
        val fresh = ProxyFdSource(context, openReader())
        val media = Media(vlc, fresh.descriptor.fileDescriptor)
        player.media = media
        media.release()
        val old = source
        source = fresh
        state.ended = false
        player.play()
        runCatching { old.close() }
    }

    private fun refreshSize() {
        player.currentVideoTrack?.let { state.videoWidth = it.width; state.videoHeight = it.height }
    }

    private fun refreshTracks() {
        val curA = player.audioTrack; val curS = player.spuTrack
        state.audioTracks = (player.audioTracks ?: emptyArray()).filter { it.id >= 0 }.map { TrackOption("${it.id}", it.name, it.id == curA) }
        state.textTracks = (player.spuTracks ?: emptyArray()).filter { it.id >= 0 }.map { TrackOption("${it.id}", it.name, it.id == curS) }
    }

    override fun play() = player.play()
    override fun pause() = player.pause()
    override fun seekTo(ms: Long) { player.setTime(ms.coerceAtLeast(0)); state.positionMs = ms.coerceAtLeast(0); state.ended = false }
    override fun setSpeed(speed: Float) { player.setRate(speed); state.speed = speed }
    override fun setMuted(muted: Boolean) { player.setVolume(if (muted) 0 else 100); state.muted = muted }
    override fun setLoop(loop: Boolean) { state.loop = loop }
    override fun setFill(fill: Boolean) {
        player.setVideoScale(if (fill) MediaPlayer.ScaleType.SURFACE_FILL else MediaPlayer.ScaleType.SURFACE_BEST_FIT)
        state.fill = fill
    }
    override fun selectAudio(id: String) { player.setAudioTrack(id.toInt()); refreshTracks() }
    override fun selectText(id: String?) { player.setSpuTrack(id?.toInt() ?: -1); refreshTracks() }

    override fun poll() {
        if (released) return
        state.positionMs = player.time
        if (player.length > 0) state.durationMs = player.length
    }

    override fun release() {
        if (released) return
        released = true
        runCatching { context.unregisterReceiver(noisyReceiver) }
        runCatching { audioManager.abandonAudioFocusRequest(focusRequest) }
        runCatching { player.stop() }
        runCatching { player.detachViews() }
        runCatching { player.release() }
        runCatching { vlc.release() }
        runCatching { source.close() }
    }
}
