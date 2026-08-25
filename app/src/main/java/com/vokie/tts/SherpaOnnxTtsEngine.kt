package com.vokie.tts

import android.os.SystemClock
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import com.vokie.communication.VokieLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

/** Lazy, single-context MMS/VITS implementation backed by the official sherpa-onnx Android AAR. */
class SherpaOnnxTtsEngine(
    private val modelManager: TtsModelManager,
    private val audioPlayer: VokieAudioPlayer,
    private val numThreads: Int = DEFAULT_THREADS,
) : TtsEngine {
    private val stateMachine = TtsStateMachine()
    private val operationMutex = Mutex()
    private val stopRequested = AtomicBoolean(false)
    private val _status = MutableStateFlow(TtsStatus(TtsState.UNINITIALIZED))
    override val status: StateFlow<TtsStatus> = _status.asStateFlow()
    private var tts: OfflineTts? = null
    private var activeLanguage: TtsLanguage? = null
    private var firstSynthesis = true

    init { require(numThreads in 1..4) { "TTS inference threads must be between 1 and 4" } }

    override suspend fun initialize(language: TtsLanguage) = operationMutex.withLock {
        initializeLocked(language)
    }

    override suspend fun synthesize(text: String, language: TtsLanguage, speed: Float): Pair<AudioBuffer, TtsResult> = operationMutex.withLock {
        val normalized = text.trim()
        if (normalized.isEmpty() || normalized.length > MAX_TEXT_CHARS) throw TtsException(TtsErrorCode.SYNTHESIS_FAILED, "Speech text must contain 1 to $MAX_TEXT_CHARS characters.")
        validateTtsSpeed(speed)
        if (tts == null || activeLanguage != language) initializeLocked(language)
        val nativeTts = tts ?: throw status.value.failure?.let { TtsException(it.code, it.userMessage, it.cause) }
            ?: TtsException(TtsErrorCode.MODEL_LOAD_FAILED, "TTS model is not ready")
        stopRequested.set(false)
        moveTo(TtsState.SYNTHESIZING, language)
        try {
            val started = SystemClock.elapsedRealtime()
            val generated = withContext(Dispatchers.Default) { nativeTts.generate(normalized, sid = 0, speed = speed) }
            val synthesisTime = SystemClock.elapsedRealtime() - started
            if (stopRequested.get()) throw TtsException(TtsErrorCode.SYNTHESIS_FAILED, "Speech synthesis was stopped.")
            require(generated.sampleRate > 0 && generated.samples.isNotEmpty()) { "sherpa-onnx returned empty audio" }
            require(generated.samples.size <= generated.sampleRate * MAX_AUDIO_SECONDS) { "Generated audio exceeds the safety limit" }
            val audio = AudioBuffer(generated.samples, generated.sampleRate)
            val result = TtsResult(normalized.length, language, synthesisTime, audio.durationMs, System.currentTimeMillis(), firstSynthesis)
            firstSynthesis = false
            moveTo(TtsState.READY, language, result = result)
            VokieLog.tts("Synthesized ${normalized.length} chars: audio=${audio.durationMs}ms processing=${synthesisTime}ms rtf=${result.realTimeFactor}")
            audio to result
        } catch (error: Throwable) {
            val failure = mapTtsFailure(error, TtsErrorCode.SYNTHESIS_FAILED, "Local speech synthesis failed.")
            fail(failure)
            throw TtsException(failure.code, failure.userMessage, error)
        }
    }

    override suspend fun play(audio: AudioBuffer, emergency: Boolean) {
        if (stopRequested.get()) throw TtsException(TtsErrorCode.AUDIO_OUTPUT_FAILED, "Speech playback was stopped.")
        moveTo(TtsState.PLAYING, activeLanguage, result = _status.value.result)
        try {
            audioPlayer.play(audio, emergency)
            moveTo(TtsState.COMPLETED, activeLanguage, result = _status.value.result)
        } catch (error: Throwable) {
            val failure = mapTtsFailure(error, TtsErrorCode.AUDIO_OUTPUT_FAILED, "Speech audio could not be played.")
            fail(failure)
            throw TtsException(failure.code, failure.userMessage, error)
        }
    }

    override suspend fun stop() {
        stopRequested.set(true)
        audioPlayer.stop()
        if (stateMachine.state == TtsState.PLAYING) moveTo(TtsState.READY, activeLanguage, result = _status.value.result)
    }

    override fun release() {
        stopRequested.set(true)
        audioPlayer.release()
        tts?.release(); tts = null
        activeLanguage = null; firstSynthesis = true
        modelManager.releaseUnusedModel(null)
        if (stateMachine.state != TtsState.UNINITIALIZED) moveTo(TtsState.UNINITIALIZED)
    }

    private suspend fun initializeLocked(language: TtsLanguage) {
        if (tts != null && activeLanguage == language) {
            if (stateMachine.state in setOf(TtsState.ERROR, TtsState.COMPLETED)) moveTo(TtsState.READY, language)
            return
        }
        moveTo(TtsState.INITIALIZING, language)
        val pack = language.modelPackage
        if (pack == null) {
            moveTo(TtsState.MODEL_MISSING, language, failure = TtsFailure(TtsErrorCode.UNSUPPORTED_LANGUAGE, "No official sherpa-onnx vits-mms package is available for ${language.nativeName}."))
            return
        }
        if (!modelManager.isInstalled(language)) {
            moveTo(TtsState.MODEL_MISSING, language, failure = TtsFailure(TtsErrorCode.MODEL_MISSING, "TTS MODEL NOT INSTALLED for ${language.nativeName}."))
            return
        }
        val started = SystemClock.elapsedRealtime()
        try {
            tts?.release(); tts = null
            activeLanguage = null
            modelManager.releaseUnusedModel(language)
            val vits = OfflineTtsVitsModelConfig(
                model = modelManager.modelFile(language).absolutePath,
                tokens = modelManager.tokensFile(language).absolutePath,
                lexicon = "",
                dataDir = "",
                dictDir = "",
                lengthScale = 1.0f,
            )
            val modelConfig = OfflineTtsModelConfig(vits = vits, numThreads = numThreads, debug = false, provider = "cpu")
            val loaded = withContext(Dispatchers.IO) { OfflineTts(config = OfflineTtsConfig(model = modelConfig, maxNumSentences = 1)) }
            tts = loaded; activeLanguage = language; firstSynthesis = true
            val loadTime = SystemClock.elapsedRealtime() - started
            moveTo(TtsState.READY, language, modelLoadTimeMs = loadTime)
            VokieLog.tts("Loaded ${pack.officialArchiveName} in ${loadTime}ms")
        } catch (error: Throwable) {
            tts?.release(); tts = null; activeLanguage = null
            val failure = mapTtsFailure(error, TtsErrorCode.MODEL_LOAD_FAILED, "The ${language.nativeName} TTS model could not be loaded.")
            moveTo(TtsState.MODEL_LOAD_FAILED, language, failure = failure)
        }
    }

    private fun moveTo(
        state: TtsState,
        language: TtsLanguage? = _status.value.activeLanguage,
        result: TtsResult? = null,
        failure: TtsFailure? = null,
        modelLoadTimeMs: Long? = _status.value.modelLoadTimeMs,
    ) {
        stateMachine.moveTo(state)
        _status.value = TtsStatus(state, language, result, failure, modelLoadTimeMs)
    }

    private fun fail(failure: TtsFailure) {
        if (stateMachine.state != TtsState.ERROR) moveTo(TtsState.ERROR, activeLanguage, failure = failure)
        else _status.value = _status.value.copy(failure = failure)
        VokieLog.tts("${failure.code}: ${failure.userMessage}")
    }

    companion object {
        const val DEFAULT_THREADS = 2
        const val MAX_TEXT_CHARS = 500
        const val MAX_AUDIO_SECONDS = 120
    }
}
