package com.vokie.tts

import com.vokie.domain.model.MessageType
import com.vokie.domain.model.VokieLanguage
import kotlinx.coroutines.flow.StateFlow

const val SHERPA_ONNX_VERSION = "1.13.6"
const val DEFAULT_TTS_SPEED = 1.0f
const val MIN_TTS_SPEED = 0.75f
const val MAX_TTS_SPEED = 1.5f

data class TtsModelFile(val fileName: String, val sizeBytes: Long, val sha256: String)
data class TtsModelPackage(
    val officialArchiveName: String,
    val officialArchiveSizeBytes: Long,
    val officialArchiveSha256: String,
    val model: TtsModelFile,
    val tokens: TtsModelFile,
    val lexicon: TtsModelFile? = null,
    val dataFilesRequired: Boolean = false,
    val quantization: String? = null,
    val license: String,
    val sourceUrl: String,
)

enum class TtsLanguage(
    val iso6393: String,
    val messageLanguage: VokieLanguage,
    val nativeName: String,
    val modelPackage: TtsModelPackage?,
) {
    ENGLISH(
        "eng", VokieLanguage.EN, "English",
        TtsModelPackage(
            officialArchiveName = "vits-mms-eng.tar.bz2",
            officialArchiveSizeBytes = 107_737_708,
            officialArchiveSha256 = "8712cb52f71ee00bde27b8c18058d97a794fccf873c4629fbea0de87d31366b4",
            model = TtsModelFile("model.onnx", 114_016_948, "e3a198f6a4473429bab138be040e7cd40d2cab7a31b6410ff0a94d5a7fbbc254"),
            tokens = TtsModelFile("tokens.txt", 303, "dff08580748be688d9112d62d6352422c56d372dfe34b24ea3f66fa1b75cfaa9"),
            quantization = null,
            license = "CC-BY-NC-4.0",
            sourceUrl = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-mms-eng.tar.bz2",
        ),
    ),
    HINDI("hin", VokieLanguage.HI, "हिन्दी", null),
    GUJARATI("guj", VokieLanguage.GU, "ગુજરાતી", null),
    MARATHI("mar", VokieLanguage.MR, "मराठी", null),
    KANNADA("kan", VokieLanguage.KN, "ಕನ್ನಡ", null),
    MALAYALAM("mal", VokieLanguage.ML, "മലയാളം", null),
    TAMIL("tam", VokieLanguage.TA, "தமிழ்", null),
    TELUGU("tel", VokieLanguage.TE, "తెలుగు", null),
    ODIA("ory", VokieLanguage.OR, "ଓଡ଼ିଆ", null),
    BENGALI("ben", VokieLanguage.BN, "বাংলা", null);

    val hasOfficialSherpaMmsPackage: Boolean get() = modelPackage != null

    companion object {
        fun fromMessageCode(code: String): TtsLanguage? = entries.firstOrNull { it.messageLanguage.code == code.uppercase() }
        fun fromIso6393(code: String): TtsLanguage? = entries.firstOrNull { it.iso6393 == code.lowercase() }
    }
}

enum class TtsState { UNINITIALIZED, MODEL_MISSING, IMPORTING, VALIDATING, INITIALIZING, READY, SYNTHESIZING, PLAYING, COMPLETED, ERROR, MODEL_LOAD_FAILED }
enum class TtsErrorCode { MODEL_MISSING, MODEL_LOAD_FAILED, MODEL_INVALID, UNSUPPORTED_LANGUAGE, SYNTHESIS_FAILED, AUDIO_OUTPUT_FAILED, AUDIO_FOCUS_FAILED, OUT_OF_MEMORY }
enum class MessageTtsState { QUEUED, SYNTHESIZING, PLAYING, COMPLETED, FAILED }

data class TtsFailure(val code: TtsErrorCode, val userMessage: String, val cause: Throwable? = null)
class TtsException(val code: TtsErrorCode, message: String, cause: Throwable? = null) : Exception(message, cause)

data class AudioBuffer(val samples: FloatArray, val sampleRate: Int) {
    val durationMs: Long get() = if (sampleRate > 0) samples.size.toLong() * 1_000L / sampleRate else 0
}

data class TtsResult(
    val textLength: Int,
    val language: TtsLanguage,
    val synthesisTimeMs: Long,
    val audioDurationMs: Long,
    val timestamp: Long,
    val firstSynthesisForModel: Boolean,
) {
    val realTimeFactor: Double? get() = calculateTtsRealTimeFactor(synthesisTimeMs, audioDurationMs)
}

data class TtsStatus(
    val state: TtsState,
    val activeLanguage: TtsLanguage? = null,
    val result: TtsResult? = null,
    val failure: TtsFailure? = null,
    val modelLoadTimeMs: Long? = null,
    val installedModelBytes: Long = 0,
)

fun calculateTtsRealTimeFactor(synthesisTimeMs: Long, audioDurationMs: Long): Double? =
    if (synthesisTimeMs < 0 || audioDurationMs <= 0) null else synthesisTimeMs.toDouble() / audioDurationMs

fun validateTtsSpeed(speed: Float): Float {
    require(speed in MIN_TTS_SPEED..MAX_TTS_SPEED) { "Speech speed must be between ${MIN_TTS_SPEED}x and ${MAX_TTS_SPEED}x" }
    return speed
}

interface TtsEngine {
    val status: StateFlow<TtsStatus>
    suspend fun initialize(language: TtsLanguage)
    suspend fun install(language: TtsLanguage, installModel: suspend () -> Unit)
    suspend fun synthesize(text: String, language: TtsLanguage, speed: Float = DEFAULT_TTS_SPEED): Pair<AudioBuffer, TtsResult>
    suspend fun play(audio: AudioBuffer, emergency: Boolean = false)
    suspend fun stop()
    fun release()
}

internal class TtsStateMachine(initial: TtsState = TtsState.UNINITIALIZED) {
    var state = initial
        private set

    fun moveTo(next: TtsState) {
        val allowed = when (state) {
            TtsState.UNINITIALIZED -> setOf(TtsState.INITIALIZING, TtsState.MODEL_MISSING)
            TtsState.MODEL_MISSING -> setOf(TtsState.IMPORTING, TtsState.INITIALIZING, TtsState.UNINITIALIZED)
            TtsState.IMPORTING -> setOf(TtsState.VALIDATING, TtsState.ERROR, TtsState.MODEL_MISSING)
            TtsState.VALIDATING -> setOf(TtsState.INITIALIZING, TtsState.ERROR, TtsState.MODEL_MISSING)
            TtsState.INITIALIZING -> setOf(TtsState.READY, TtsState.MODEL_MISSING, TtsState.MODEL_LOAD_FAILED, TtsState.ERROR, TtsState.UNINITIALIZED)
            TtsState.READY -> setOf(TtsState.SYNTHESIZING, TtsState.PLAYING, TtsState.INITIALIZING, TtsState.UNINITIALIZED)
            TtsState.SYNTHESIZING -> setOf(TtsState.PLAYING, TtsState.READY, TtsState.ERROR, TtsState.UNINITIALIZED)
            TtsState.PLAYING -> setOf(TtsState.COMPLETED, TtsState.ERROR, TtsState.READY, TtsState.UNINITIALIZED)
            TtsState.COMPLETED -> setOf(TtsState.SYNTHESIZING, TtsState.INITIALIZING, TtsState.READY, TtsState.UNINITIALIZED)
            TtsState.ERROR -> setOf(TtsState.IMPORTING, TtsState.INITIALIZING, TtsState.SYNTHESIZING, TtsState.READY, TtsState.UNINITIALIZED)
            TtsState.MODEL_LOAD_FAILED -> setOf(TtsState.IMPORTING, TtsState.INITIALIZING, TtsState.UNINITIALIZED)
        }
        check(next in allowed) { "Invalid TTS transition: $state -> $next" }
        state = next
    }
}

fun mapTtsFailure(error: Throwable, fallback: TtsErrorCode, message: String): TtsFailure = when (error) {
    is TtsException -> TtsFailure(error.code, error.message ?: message, error)
    is OutOfMemoryError -> TtsFailure(TtsErrorCode.OUT_OF_MEMORY, "Not enough memory to synthesize speech locally.", error)
    is UnsatisfiedLinkError -> TtsFailure(TtsErrorCode.MODEL_LOAD_FAILED, "The sherpa-onnx native engine is unavailable.", error)
    else -> TtsFailure(fallback, message, error)
}

data class TtsQueueItem(val messageId: String, val text: String, val language: TtsLanguage, val messageType: MessageType)
