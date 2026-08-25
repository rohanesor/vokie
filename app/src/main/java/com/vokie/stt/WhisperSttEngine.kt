package com.vokie.stt

import android.content.Context
import android.net.Uri
import android.os.SystemClock
import com.vokie.communication.VokieLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

class WhisperNative {
    init { System.loadLibrary("vokie_whisper") }
    external fun nativeInit(modelPath: String): Long
    external fun nativeTranscribe(context: Long, samples: FloatArray, language: String, threads: Int): String
    external fun nativeFree(context: Long)
}

/** One application-scoped whisper.cpp context. Audio and inference never leave the device. */
class WhisperSttEngine(
    private val context: Context,
    val model: SttModel = WhisperTinyMultilingualQ5_1,
    private val native: WhisperNative = WhisperNative(),
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    vadEngine: VadEngine = EnergyVadEngine(),
) : SttEngine {
    private val modelStore = SttModelStore(context, model)
    private val recorder = MicrophoneAudioRecorder(context, scope, vadEngine)
    private val stateMachine = SttStateMachine()
    private val inferenceMutex = Mutex()
    private val initializationMutex = Mutex()
    private val processing = AtomicBoolean(false)
    private val _status = MutableStateFlow(SttStatus(SttState.UNINITIALIZED))
    override val status: StateFlow<SttStatus> = _status.asStateFlow()
    private var nativeContext = 0L
    private var activeLanguage = SttLanguage.ENGLISH

    override suspend fun initialize() = initializationMutex.withLock {
        if (nativeContext != 0L) {
            if (stateMachine.state in setOf(SttState.READY, SttState.RESULT)) return@withLock
            if (stateMachine.state == SttState.ERROR) {
                moveTo(SttState.INITIALIZING)
                moveTo(SttState.READY)
                return@withLock
            }
        }
        moveTo(SttState.INITIALIZING)
        if (!modelStore.isInstalled()) {
            moveTo(SttState.MODEL_MISSING, failure = SttFailure(SttErrorCode.MODEL_MISSING, "STT MODEL NOT INSTALLED"))
            return@withLock
        }
        val modelFile = model.localFile(context)
        val size = modelFile.length()
        val started = SystemClock.elapsedRealtime()
        try {
            val loaded = withContext(Dispatchers.IO) { native.nativeInit(modelFile.absolutePath) }
            check(loaded != 0L) { "whisper.cpp returned an empty context" }
            nativeContext = loaded
            val loadTime = SystemClock.elapsedRealtime() - started
            moveTo(SttState.READY, modelLoadTimeMs = loadTime, installedModelBytes = size)
            VokieLog.stt("Model loaded in ${loadTime}ms: ${model.id}")
        } catch (error: Throwable) {
            fail(mapSttFailure(error, SttErrorCode.MODEL_LOAD_FAILED, "The local STT model could not be loaded."))
        }
    }

    suspend fun installModel(uri: Uri) = initializationMutex.withLock {
        releaseContext()
        moveTo(SttState.IMPORTING, failure = null)
        try {
            moveTo(SttState.VALIDATING)
            val installed = modelStore.install(uri)
            val size = installed.length()
            moveTo(SttState.INITIALIZING, installedModelBytes = size)
            val started = SystemClock.elapsedRealtime()
            val loaded = withContext(Dispatchers.IO) { native.nativeInit(installed.absolutePath) }
            check(loaded != 0L)
            nativeContext = loaded
            val loadTime = SystemClock.elapsedRealtime() - started
            moveTo(SttState.READY, modelLoadTimeMs = loadTime, installedModelBytes = size)
            VokieLog.stt("Model installed and loaded: ${model.id} (${size} bytes, ${loadTime}ms)")
        } catch (error: Throwable) {
            fail(mapSttFailure(error, SttErrorCode.MODEL_LOAD_FAILED, error.message ?: "The selected STT model could not be installed."))
        }
    }

    override suspend fun start(language: SttLanguage) {
        if (language !in model.supportedLanguages) {
            fail(SttFailure(SttErrorCode.UNSUPPORTED_LANGUAGE, "The selected language is not supported by this model.")); return
        }
        if (nativeContext == 0L) {
            initialize()
            if (nativeContext == 0L) return
        }
        if (stateMachine.state !in setOf(SttState.READY, SttState.RESULT, SttState.ERROR)) return
        activeLanguage = language
        processing.set(false)
        moveTo(SttState.LISTENING, vadState = VadState.WAITING_FOR_SPEECH)
        try {
            recorder.start(
                onVadState = { vad -> _status.value = _status.value.copy(vadState = vad) },
                onFinalized = { audio -> scope.launch { processCaptured(audio, activeLanguage) } },
                onFailure = { failure -> scope.launch { fail(failure) } },
            )
            VokieLog.stt("Listening started: ${language.whisperCode}")
        } catch (error: Throwable) {
            fail(mapSttFailure(error, SttErrorCode.AUDIO_CAPTURE_FAILED, "Audio capture could not start."))
        }
    }

    override suspend fun stop() {
        val audio = recorder.stop()
        if (audio != null) processCaptured(audio, activeLanguage)
        else if (stateMachine.state == SttState.LISTENING && !processing.get()) {
            fail(SttFailure(SttErrorCode.NO_SPEECH, "No speech was detected. Hold the button and speak, then retry."))
        }
    }

    override suspend fun transcribe(audio: FloatArray, language: SttLanguage, audioDurationMs: Long): SttResult {
        require(audio.isNotEmpty() && audio.size <= WHISPER_SAMPLE_RATE * 30) { "Audio must be between 1 sample and 30 seconds" }
        require(audioDurationMs > 0) { "Audio duration must be positive" }
        if (language !in model.supportedLanguages) throw SttException(SttErrorCode.UNSUPPORTED_LANGUAGE, "Unsupported STT language")
        val handle = nativeContext
        if (handle == 0L) throw SttException(SttErrorCode.STT_INITIALIZATION_FAILED, "STT is not initialized")
        return inferenceMutex.withLock {
            val started = SystemClock.elapsedRealtime()
            val text = withContext(Dispatchers.Default) {
                native.nativeTranscribe(handle, audio, language.whisperCode, Runtime.getRuntime().availableProcessors().coerceIn(1, 4)).trim()
            }
            val processingTime = SystemClock.elapsedRealtime() - started
            if (text.isBlank()) throw SttException(SttErrorCode.STT_INFERENCE_FAILED, "Speech was detected, but no text was recognized.")
            SttResult(text, language, confidence = null, processingTimeMs = processingTime, audioDurationMs = audioDurationMs, timestamp = System.currentTimeMillis())
        }
    }

    override fun release() {
        recorder.release()
        releaseContext()
        processing.set(false)
        if (stateMachine.state != SttState.UNINITIALIZED) moveTo(SttState.UNINITIALIZED)
    }

    private suspend fun processCaptured(audio: CapturedAudio, language: SttLanguage) {
        if (!processing.compareAndSet(false, true)) return
        try {
            if (stateMachine.state == SttState.LISTENING) moveTo(SttState.PROCESSING, vadState = VadState.TRANSCRIBING)
            val result = transcribe(audio.samples, language, audio.durationMs)
            moveTo(SttState.RESULT, vadState = VadState.TEXT_READY, result = result)
            VokieLog.stt("Result ready: audio=${result.audioDurationMs}ms processing=${result.processingTimeMs}ms rtf=${result.realTimeFactor}")
        } catch (error: Throwable) {
            fail(mapSttFailure(error, SttErrorCode.STT_INFERENCE_FAILED, "Local speech recognition failed."))
        } finally {
            processing.set(false)
        }
    }

    private fun moveTo(
        state: SttState,
        vadState: VadState = _status.value.vadState,
        result: SttResult? = null,
        failure: SttFailure? = null,
        modelLoadTimeMs: Long? = _status.value.modelLoadTimeMs,
        installedModelBytes: Long = _status.value.installedModelBytes,
    ) {
        stateMachine.moveTo(state)
        _status.value = SttStatus(state, vadState, result, failure, modelLoadTimeMs, installedModelBytes)
    }

    private fun fail(failure: SttFailure) {
        if (stateMachine.state != SttState.ERROR) moveTo(SttState.ERROR, failure = failure)
        else _status.value = _status.value.copy(failure = failure)
        VokieLog.stt("${failure.code}: ${failure.userMessage}")
    }

    private fun releaseContext() {
        val handle = nativeContext
        nativeContext = 0
        if (handle != 0L) runCatching { native.nativeFree(handle) }
    }

}
