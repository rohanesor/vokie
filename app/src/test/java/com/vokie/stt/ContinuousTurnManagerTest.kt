package com.vokie.stt

import com.vokie.domain.model.VokieLanguage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Deterministic fake used only to drive the manager's state-machine assertions. */
private class FakeStt : SttEngine {
    private val _status = MutableStateFlow(SttStatus(SttState.UNINITIALIZED))
    override val status: StateFlow<SttStatus> = _status.asStateFlow()
    val starts = mutableListOf<Triple<SttLanguage, UserLanguageProfile, Boolean>>()
    var stops = 0; var releases = 0
    override suspend fun initialize() { _status.value = SttStatus(SttState.READY) }
    override suspend fun start(language: SttLanguage, preferredLanguage: UserLanguageProfile, finalizeOnVad: Boolean) {
        starts += Triple(language, preferredLanguage, finalizeOnVad); _status.value = SttStatus(SttState.LISTENING)
    }
    override suspend fun stop() { stops++ }
    override suspend fun transcribe(audio: FloatArray, language: SttLanguage, audioDurationMs: Long): SttResult = throw NotImplementedError("fake")
    override fun release() { releases++; _status.value = SttStatus(SttState.UNINITIALIZED) }
    suspend fun emitResult(text: String, language: SttLanguage, ts: Long) {
        _status.value = SttStatus(SttState.PROCESSING)
        _status.value = SttStatus(SttState.RESULT, result = SttResult(text, language, null, 42L, 1_000L, ts))
    }
    suspend fun emitError(code: SttErrorCode, message: String) {
        _status.value = SttStatus(SttState.ERROR, failure = SttFailure(code, message))
    }
}

class ContinuousTurnManagerTest {
    private fun harness(block: suspend TestHarness.() -> Unit) = runBlocking {
        val stt = FakeStt(); val scope = CoroutineScope(coroutineContext + SupervisorJob())
        val clock = MutableClock(); val manager = ContinuousTurnManager(stt, scope, clockMs = clock::now)
        val events = mutableListOf<TurnEvent>()
        val job: Job = launch { manager.events.collect { events += it } }
        try { TestHarness(stt, manager, events, clock).block() } finally { job.cancel(); scope.cancel() }
    }
    private class MutableClock(var current: Long = 1_000L) { fun now(): Long = current }
    private class TestHarness(val stt: FakeStt, val manager: ContinuousTurnManager, val events: MutableList<TurnEvent>, val clock: MutableClock) {
        suspend fun awaitStopped() = withTimeout(2_000L) { while (events.none { it is TurnEvent.Stopped }) yield() }
        suspend fun awaitCompletedCount(count: Int) = withTimeout(2_000L) { while (events.count { it is TurnEvent.TurnCompleted } < count) yield() }
        suspend fun awaitStarted() = withTimeout(2_000L) { while (events.none { it is TurnEvent.Started }) yield() }
    }

    @Test fun pttSingleTurnEmitsSentencesThenStops() = harness {
        manager.start(TurnMode.PUSH_TO_TALK, SttLanguage.ENGLISH, UserLanguageProfile.same(VokieLanguage.EN)); awaitStarted()
        stt.emitResult("Hello. World.", SttLanguage.ENGLISH, ts = 1); awaitStopped()
        assertEquals(listOf(TurnMode.PUSH_TO_TALK), events.filterIsInstance<TurnEvent.Started>().map { it.mode })
        assertEquals(listOf("Hello.", "World."), events.filterIsInstance<TurnEvent.Sentence>().map { it.text })
        val completed = events.filterIsInstance<TurnEvent.TurnCompleted>().single()
        assertEquals(2, completed.sentenceCount); assertEquals(42L, completed.sttProcessingMs); assertEquals(1_000L, completed.audioDurationMs)
        assertEquals(1, stt.starts.size); assertEquals(false, stt.starts.single().third)
        assertEquals(TurnState.STOPPED, manager.state.value)
    }

    @Test fun pttSingleSentenceStillEmitsOneSentenceAndOneCompletion() = harness {
        manager.start(TurnMode.PUSH_TO_TALK, SttLanguage.HINDI, UserLanguageProfile.same(VokieLanguage.HI)); awaitStarted()
        stt.emitResult("मुझे मदद चाहिए", SttLanguage.HINDI, ts = 2); awaitStopped()
        assertEquals(listOf("मुझे मदद चाहिए"), events.filterIsInstance<TurnEvent.Sentence>().map { it.text })
        assertEquals(1, events.filterIsInstance<TurnEvent.TurnCompleted>().single().sentenceCount)
    }

    @Test fun pttHindiDandaSplitsSentences() = harness {
        manager.start(TurnMode.PUSH_TO_TALK, SttLanguage.HINDI, UserLanguageProfile.same(VokieLanguage.HI)); awaitStarted()
        stt.emitResult("मुझे मदद चाहिए। जल्दी आओ।", SttLanguage.HINDI, ts = 3); awaitStopped()
        assertEquals(listOf("मुझे मदद चाहिए।", "जल्दी आओ।"), events.filterIsInstance<TurnEvent.Sentence>().map { it.text })
    }

    @Test fun continuousModeAutomaticallyResumesAfterEachTurn() = harness {
        manager.start(TurnMode.CONTINUOUS, SttLanguage.ENGLISH, UserLanguageProfile.same(VokieLanguage.EN)); awaitStarted()
        stt.emitResult("First one.", SttLanguage.ENGLISH, ts = 10); awaitCompletedCount(1)
        stt.emitResult("Second one.", SttLanguage.ENGLISH, ts = 11); awaitCompletedCount(2)
        stt.emitResult("Third one.", SttLanguage.ENGLISH, ts = 12); awaitCompletedCount(3)
        // Continuous mode auto-restarts even after the final turn (mic keeps listening).
        assertEquals(4, stt.starts.size); assertTrue(stt.starts.all { it.third }); assertEquals(0, stt.stops)
        assertTrue(events.none { it is TurnEvent.Stopped })
        assertEquals(4, events.filterIsInstance<TurnEvent.Started>().size)
    }

    @Test fun continuousModeStopHaltsAutoRestart() = harness {
        manager.start(TurnMode.CONTINUOUS, SttLanguage.ENGLISH, UserLanguageProfile.same(VokieLanguage.EN)); awaitStarted()
        stt.emitResult("First.", SttLanguage.ENGLISH, ts = 20); awaitCompletedCount(1)
        val startsBeforeStop = stt.starts.size
        manager.stop(); stt.emitResult("Should not restart.", SttLanguage.ENGLISH, ts = 21)
        withTimeout(2_000L) { while (events.none { it is TurnEvent.Stopped }) yield() }
        assertEquals(1, stt.stops)
        // After stop, at most the second result may finalise but must not trigger any further start beyond the one
        // already queued before stop() was observed.
        assertTrue("starts after stop: ${stt.starts.size} startsBeforeStop=$startsBeforeStop", stt.starts.size == startsBeforeStop)
        assertTrue(events.any { it is TurnEvent.Stopped })
    }

    @Test fun duplicateResultTimestampIsIgnored() = harness {
        manager.start(TurnMode.PUSH_TO_TALK, SttLanguage.ENGLISH, UserLanguageProfile.same(VokieLanguage.EN)); awaitStarted()
        stt.emitResult("Hello.", SttLanguage.ENGLISH, ts = 30); awaitStopped()
        val sentencesBefore = events.filterIsInstance<TurnEvent.Sentence>().size
        stt.emitResult("Hello.", SttLanguage.ENGLISH, ts = 30); yield()
        assertEquals(sentencesBefore, events.filterIsInstance<TurnEvent.Sentence>().size)
    }

    @Test fun sttErrorPropagatesAndStopsTurn() = harness {
        manager.start(TurnMode.PUSH_TO_TALK, SttLanguage.ENGLISH, UserLanguageProfile.same(VokieLanguage.EN)); awaitStarted()
        stt.emitError(SttErrorCode.NO_SPEECH, "No speech detected."); awaitStopped()
        val err = events.filterIsInstance<TurnEvent.Error>().single()
        assertEquals(SttErrorCode.NO_SPEECH, err.code); assertEquals(TurnState.ERROR, manager.state.value)
    }

    @Test fun completedIncludesMonotonicTimingFromInjectedClock() = harness {
        clock.current = 1_000L
        manager.start(TurnMode.PUSH_TO_TALK, SttLanguage.ENGLISH, UserLanguageProfile.same(VokieLanguage.EN)); awaitStarted()
        clock.current = 1_500L
        stt.emitResult("Done.", SttLanguage.ENGLISH, ts = 40); awaitStopped()
        val completed = events.filterIsInstance<TurnEvent.TurnCompleted>().single()
        assertEquals(1_000L, completed.startedAtMs); assertEquals(1_500L, completed.completedAtMs)
    }

    @Test fun turnIdsAreDistinctAcrossContinuousTurns() = harness {
        manager.start(TurnMode.CONTINUOUS, SttLanguage.ENGLISH, UserLanguageProfile.same(VokieLanguage.EN)); awaitStarted()
        stt.emitResult("One.", SttLanguage.ENGLISH, ts = 50); awaitCompletedCount(1)
        stt.emitResult("Two.", SttLanguage.ENGLISH, ts = 51); awaitCompletedCount(2)
        val ids = events.filterIsInstance<TurnEvent.Started>().map { it.turnId }
        assertEquals(3, ids.size); assertEquals(ids.toSet().size, ids.size)
    }

    @Test fun startedEventCarriesConfiguredModeAndLanguage() = harness {
        manager.start(TurnMode.PUSH_TO_TALK, SttLanguage.TAMIL, UserLanguageProfile.same(VokieLanguage.TA)); awaitStarted()
        val started = events.filterIsInstance<TurnEvent.Started>().single()
        assertEquals(TurnMode.PUSH_TO_TALK, started.mode); assertEquals(VokieLanguage.TA, started.language)
    }
}
