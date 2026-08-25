package com.vokie.tts

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.CompletableDeferred
import java.util.concurrent.atomic.AtomicReference

/** Plays in-memory PCM directly through AudioTrack. No WAV or temporary playback file is created. */
class VokieAudioPlayer(private val context: Context) {
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val currentTrack = AtomicReference<AudioTrack?>(null)
    private val currentCompletion = AtomicReference<CompletableDeferred<Unit>?>(null)
    private var focusRequest: AudioFocusRequest? = null
    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        val track = currentTrack.get()
        when (change) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                track?.setVolume(1f)
                if (track?.playState == AudioTrack.PLAYSTATE_PAUSED) runCatching { track.play() }
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> track?.setVolume(0.25f)
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> runCatching { track?.pause() }
            AudioManager.AUDIOFOCUS_LOSS -> stopWithFailure(TtsException(TtsErrorCode.AUDIO_FOCUS_FAILED, "Audio focus was lost."))
        }
    }

    suspend fun play(buffer: AudioBuffer, emergency: Boolean) {
        require(buffer.sampleRate > 0 && buffer.samples.isNotEmpty())
        require(buffer.durationMs in 1..MAX_AUDIO_DURATION_MS) { "Generated audio duration is outside the playback limit" }
        stop()
        if (!requestAudioFocus(emergency)) throw TtsException(TtsErrorCode.AUDIO_FOCUS_FAILED, "Audio focus is unavailable.")
        try {
            repeat(if (emergency) SOS_REPEAT_COUNT else 1) { playOnce(buffer, emergency) }
        } finally {
            abandonAudioFocus()
        }
    }

    fun stop() {
        currentCompletion.getAndSet(null)?.cancel()
        currentTrack.getAndSet(null)?.let { track ->
            runCatching { track.pause() }; runCatching { track.flush() }; runCatching { track.stop() }; runCatching { track.release() }
        }
        abandonAudioFocus()
    }

    fun release() = stop()

    private suspend fun playOnce(buffer: AudioBuffer, emergency: Boolean) {
        val attributes = AudioAttributes.Builder()
            .setUsage(if (emergency) AudioAttributes.USAGE_ALARM else AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()
        val format = AudioFormat.Builder()
            .setSampleRate(buffer.sampleRate)
            .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
            .build()
        val track = try {
            AudioTrack.Builder()
                .setAudioAttributes(attributes)
                .setAudioFormat(format)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .setBufferSizeInBytes(buffer.samples.size * Float.SIZE_BYTES)
                .build()
        } catch (error: Throwable) {
            throw TtsException(TtsErrorCode.AUDIO_OUTPUT_FAILED, "Audio output could not be initialized.", error)
        }
        if (track.state != AudioTrack.STATE_INITIALIZED) {
            track.release()
            throw TtsException(TtsErrorCode.AUDIO_OUTPUT_FAILED, "Audio output could not be initialized.")
        }
        val completion = CompletableDeferred<Unit>()
        currentTrack.set(track); currentCompletion.set(completion)
        track.setPlaybackPositionUpdateListener(object : AudioTrack.OnPlaybackPositionUpdateListener {
            override fun onMarkerReached(audioTrack: AudioTrack) { completion.complete(Unit) }
            override fun onPeriodicNotification(audioTrack: AudioTrack) = Unit
        }, Handler(Looper.getMainLooper()))
        val written = track.write(buffer.samples, 0, buffer.samples.size, AudioTrack.WRITE_BLOCKING)
        if (written != buffer.samples.size) {
            stopWithFailure(TtsException(TtsErrorCode.AUDIO_OUTPUT_FAILED, "Generated speech could not be written to audio output."))
        }
        track.notificationMarkerPosition = buffer.samples.size
        track.setVolume(1f) // Maximum application gain; this does not override the user's device volume.
        try {
            track.play()
            completion.await()
        } finally {
            currentCompletion.compareAndSet(completion, null)
            if (currentTrack.compareAndSet(track, null)) {
                runCatching { track.stop() }; track.release()
            }
        }
    }

    private fun stopWithFailure(error: Throwable) {
        currentCompletion.getAndSet(null)?.completeExceptionally(error)
        currentTrack.getAndSet(null)?.let { track -> runCatching { track.stop() }; track.release() }
    }

    @Suppress("DEPRECATION")
    private fun requestAudioFocus(emergency: Boolean): Boolean {
        val manager = audioManager ?: return false
        val gain = if (emergency) AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE else AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
        val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val attributes = AudioAttributes.Builder()
                .setUsage(if (emergency) AudioAttributes.USAGE_ALARM else AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
            AudioFocusRequest.Builder(gain).setAudioAttributes(attributes).setOnAudioFocusChangeListener(focusListener).build()
                .also { focusRequest = it }.let(manager::requestAudioFocus)
        } else manager.requestAudioFocus(focusListener, if (emergency) AudioManager.STREAM_ALARM else AudioManager.STREAM_MUSIC, gain)
        return result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    @Suppress("DEPRECATION")
    private fun abandonAudioFocus() {
        val manager = audioManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) focusRequest?.let(manager::abandonAudioFocusRequest)
        else manager.abandonAudioFocus(focusListener)
        focusRequest = null
    }

    private companion object {
        const val MAX_AUDIO_DURATION_MS = 120_000L
        const val SOS_REPEAT_COUNT = 2
    }
}
