package com.vokie.tts

import com.vokie.domain.model.MessageType
import com.vokie.domain.model.VokieLanguage
import org.junit.Assert.*
import org.junit.Test

class TtsCoreTest {
    @Test fun allMessageLanguagesMapToMmsIsoCodesWithoutInventedPackages() {
        val expected = mapOf(
            VokieLanguage.EN to "eng", VokieLanguage.HI to "hin", VokieLanguage.GU to "guj",
            VokieLanguage.MR to "mar", VokieLanguage.KN to "kan", VokieLanguage.ML to "mal",
            VokieLanguage.TA to "tam", VokieLanguage.TE to "tel", VokieLanguage.OR to "ory",
            VokieLanguage.BN to "ben",
        )
        expected.forEach { (messageLanguage, iso) ->
            val language = requireNotNull(TtsLanguage.fromMessageCode(messageLanguage.code))
            assertEquals(iso, language.iso6393)
            assertEquals(language, TtsLanguage.fromIso6393(iso))
        }
        assertEquals(10, TtsLanguage.entries.size)
    }

    @Test fun stateMachineRequiresInitializationAndRealSynthesisOrder() {
        val machine = TtsStateMachine()
        machine.moveTo(TtsState.MODEL_MISSING)
        machine.moveTo(TtsState.IMPORTING)
        machine.moveTo(TtsState.VALIDATING)
        machine.moveTo(TtsState.INITIALIZING)
        machine.moveTo(TtsState.READY)
        machine.moveTo(TtsState.SYNTHESIZING)
        machine.moveTo(TtsState.READY)
        machine.moveTo(TtsState.PLAYING)
        machine.moveTo(TtsState.COMPLETED)
        assertEquals(TtsState.COMPLETED, machine.state)

        try {
            TtsStateMachine().moveTo(TtsState.PLAYING)
            fail("Playback without model initialization must be rejected")
        } catch (_: IllegalStateException) { }
    }

    @Test fun metricsUseGeneratedAudioDuration() {
        val audio = AudioBuffer(FloatArray(16_000 * 4), 16_000)
        assertEquals(4_000L, audio.durationMs)
        assertEquals(0.5, calculateTtsRealTimeFactor(2_000, audio.durationMs)!!, 0.0001)
        assertNull(calculateTtsRealTimeFactor(100, 0))
    }

    @Test fun speedConfigurationIsBounded() {
        assertEquals(0.75f, validateTtsSpeed(0.75f))
        assertEquals(1.0f, validateTtsSpeed(1.0f))
        assertEquals(1.5f, validateTtsSpeed(1.5f))
        assertInvalid { validateTtsSpeed(0.5f) }
        assertInvalid { validateTtsSpeed(2.0f) }
    }

    @Test fun sosHasPriorityWhileNormalMessagesRemainOrdered() {
        val queue = TtsPriorityQueue()
        queue.add(TtsQueueItem("one", "one", TtsLanguage.ENGLISH, MessageType.TEXT))
        queue.add(TtsQueueItem("two", "two", TtsLanguage.ENGLISH, MessageType.TEXT))
        queue.add(TtsQueueItem("sos", "help", TtsLanguage.ENGLISH, MessageType.SOS))
        assertEquals(listOf("sos", "one", "two"), queue.ids())
        assertEquals("sos", queue.removeFirstOrNull()?.messageId)
    }

    @Test fun errorsNeverMapToSuccess() {
        assertEquals(TtsErrorCode.MODEL_INVALID, mapTtsFailure(TtsException(TtsErrorCode.MODEL_INVALID, "bad"), TtsErrorCode.SYNTHESIS_FAILED, "fallback").code)
        assertEquals(TtsErrorCode.OUT_OF_MEMORY, mapTtsFailure(OutOfMemoryError(), TtsErrorCode.SYNTHESIS_FAILED, "fallback").code)
        assertEquals(TtsErrorCode.MODEL_LOAD_FAILED, mapTtsFailure(UnsatisfiedLinkError(), TtsErrorCode.SYNTHESIS_FAILED, "fallback").code)
    }

    private fun assertInvalid(block: () -> Unit) {
        try { block(); fail("Expected invalid configuration") } catch (_: IllegalArgumentException) { }
    }
}
