package com.kryptsafe.video

import android.content.Context
import android.view.View
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.kryptsafe.vault.EncryptedFileDataSource
import java.io.File

/** Default engine: hardware-accelerated ExoPlayer (MP4, MKV, WebM, TS, FLV, OGG, ...). Falls back to VLC on failure. */
@OptIn(UnstableApi::class)
class ExoEngine(
    context: Context,
    itemFile: (String) -> File,
    itemId: String,
    mime: String,
    startMs: Long,
    private val onUnplayable: (String) -> Unit,
) : VideoEngine() {
    override val kind = EngineKind.EXO
    private val player: ExoPlayer = ExoPlayer.Builder(context)
        // Respect audio focus (calls, other apps) and stop when headphones are unplugged, so private audio never blasts from the speaker.
        .setAudioAttributes(androidx.media3.common.AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(), true)
        .setHandleAudioBecomingNoisy(true)
        .build()
    private var view: PlayerView? = null
    private var tracks: Tracks = Tracks.EMPTY

    init {
        val source = ProgressiveMediaSource.Factory(EncryptedFileDataSource.factory(itemFile))
            .createMediaSource(MediaItem.Builder().setUri("ksf://$itemId").setMimeType(mime).build())
        player.setMediaSource(source)
        if (startMs > 0) player.seekTo(startMs)
        player.prepare()
        player.playWhenReady = true
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) { state.playing = isPlaying }
            override fun onPlaybackStateChanged(s: Int) {
                state.ended = s == Player.STATE_ENDED
                state.buffering = s == Player.STATE_BUFFERING
                if (player.duration > 0) state.durationMs = player.duration
            }
            override fun onVideoSizeChanged(v: VideoSize) { state.videoWidth = v.width; state.videoHeight = v.height }
            override fun onTracksChanged(t: Tracks) { tracks = t; refreshTracks() }
            override fun onPlayerError(e: PlaybackException) { onUnplayable(e.errorCodeName) }
        })
    }

    override fun createView(context: Context): View = PlayerView(context).apply {
        useController = false
        player = this@ExoEngine.player
        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
        keepScreenOn = true
        setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
        view = this
    }

    override fun play() = player.play()
    override fun pause() = player.pause()
    override fun seekTo(ms: Long) { player.seekTo(ms.coerceAtLeast(0)); state.positionMs = ms.coerceAtLeast(0) }
    override fun setSpeed(speed: Float) { player.playbackParameters = PlaybackParameters(speed); state.speed = speed }
    override fun setMuted(muted: Boolean) { player.volume = if (muted) 0f else 1f; state.muted = muted }
    override fun setLoop(loop: Boolean) { player.repeatMode = if (loop) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF; state.loop = loop }
    override fun setFill(fill: Boolean) {
        view?.resizeMode = if (fill) AspectRatioFrameLayout.RESIZE_MODE_ZOOM else AspectRatioFrameLayout.RESIZE_MODE_FIT
        state.fill = fill
    }

    private fun label(f: androidx.media3.common.Format, index: Int, kind: String): String {
        val lang = f.language?.takeIf { it != "und" }?.let { java.util.Locale.forLanguageTag(it).displayLanguage }
        val name = f.label ?: lang ?: "$kind ${index + 1}"
        val extra = if (kind == "Audio" && f.channelCount > 0) " · ${f.channelCount}ch" else ""
        return name + extra
    }

    private fun refreshTracks() {
        var ai = 0; var ti = 0
        val a = mutableListOf<TrackOption>(); val t = mutableListOf<TrackOption>()
        tracks.groups.forEachIndexed { gi, g ->
            when (g.type) {
                C.TRACK_TYPE_AUDIO -> if (g.isSupported) a += TrackOption("$gi", label(g.getTrackFormat(0), ai++, "Audio"), g.isSelected)
                C.TRACK_TYPE_TEXT -> if (g.isSupported) t += TrackOption("$gi", label(g.getTrackFormat(0), ti++, "Subtitle"), g.isSelected)
            }
        }
        state.audioTracks = a; state.textTracks = t
    }

    override fun selectAudio(id: String) {
        val g = tracks.groups.getOrNull(id.toInt()) ?: return
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon().setOverrideForType(TrackSelectionOverride(g.mediaTrackGroup, 0)).build()
    }

    override fun selectText(id: String?) {
        val b = player.trackSelectionParameters.buildUpon()
        if (id == null) b.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
        else tracks.groups.getOrNull(id.toInt())?.let { b.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false).setOverrideForType(TrackSelectionOverride(it.mediaTrackGroup, 0)) }
        player.trackSelectionParameters = b.build()
    }

    override fun poll() {
        state.positionMs = player.currentPosition
        if (player.duration > 0) state.durationMs = player.duration
    }

    override fun release() { view?.player = null; player.release() }
}
