package com.vokie.ml

import com.vokie.domain.model.SpeechToTextEngine
import com.vokie.domain.model.TextToSpeechEngine

/** Offline ML seams. Replace implementations with bundled/local models without UI changes. */
class LocalSpeechToTextEngine : SpeechToTextEngine {
    override suspend fun transcribe(audio: ByteArray): String = ""
}

class LocalTextToSpeechEngine : TextToSpeechEngine {
    override suspend fun synthesize(text: String, language: String) { /* Android TextToSpeech adapter */ }
}
