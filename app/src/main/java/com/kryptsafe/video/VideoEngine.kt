package com.kryptsafe.video

import android.content.Context
import android.view.View
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

data class TrackOption(val id: String, val label: String, val selected: Boolean)

/** Observable playback state shared by every engine, so the Compose UI is engine-agnostic. */
class VideoState {
    var playing by mutableStateOf(false)
    var ended by mutableStateOf(false)
    var buffering by mutableStateOf(true)
    var positionMs by mutableLongStateOf(0L)
    var durationMs by mutableLongStateOf(0L)
    var speed by mutableFloatStateOf(1f)
    var muted by mutableStateOf(false)
    var loop by mutableStateOf(false)
    var fill by mutableStateOf(false)
    var videoWidth by mutableStateOf(0)
    var videoHeight by mutableStateOf(0)
    var audioTracks by mutableStateOf<List<TrackOption>>(emptyList())
    var textTracks by mutableStateOf<List<TrackOption>>(emptyList())
    var error by mutableStateOf<String?>(null)
}

/** Stand-in used when an engine can't even be created (damaged file, native library problem) so the UI shows an error instead of crashing. */
class FailedEngine(override val kind: EngineKind) : VideoEngine() {
    init { state.buffering = false; state.error = "OPEN_FAILED" }
    override fun createView(context: Context): View = View(context)
    override fun play() {}
    override fun pause() {}
    override fun seekTo(ms: Long) {}
    override fun setSpeed(speed: Float) {}
    override fun setMuted(muted: Boolean) {}
    override fun setLoop(loop: Boolean) {}
    override fun setFill(fill: Boolean) {}
    override fun selectAudio(id: String) {}
    override fun selectText(id: String?) {}
    override fun poll() {}
    override fun release() {}
}

enum class EngineKind(val label: String) { EXO("Standard"), VLC("Compatibility") }

/** A video playback backend. Both implementations read the encrypted vault file and decrypt in memory only. */
abstract class VideoEngine {
    val state = VideoState()
    abstract val kind: EngineKind
    abstract fun createView(context: Context): View
    abstract fun play()
    abstract fun pause()
    abstract fun seekTo(ms: Long)
    abstract fun setSpeed(speed: Float)
    abstract fun setMuted(muted: Boolean)
    abstract fun setLoop(loop: Boolean)
    abstract fun setFill(fill: Boolean)
    abstract fun selectAudio(id: String)
    /** [id] null turns subtitles off. */
    abstract fun selectText(id: String?)
    /** Called ~4x/second from the UI to refresh position/tracks for engines that don't push updates. */
    abstract fun poll()
    abstract fun release()

    /** Restart from the beginning after the video ended (engines may need to rebuild their media). */
    open fun replay() { seekTo(0); play() }

    fun togglePlay() { if (state.ended) replay() else if (state.playing) pause() else play() }
}
