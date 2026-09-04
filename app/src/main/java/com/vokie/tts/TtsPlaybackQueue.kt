package com.vokie.tts

import com.vokie.domain.model.MessageType
import com.vokie.stt.TurnTimingFailure
import com.vokie.stt.TurnTimingRecorder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class TtsPriorityQueue {
    private val items = ArrayDeque<TtsQueueItem>()
    fun add(item: TtsQueueItem) { if (item.messageType == MessageType.SOS) items.addFirst(item) else items.addLast(item) }
    fun removeFirstOrNull(): TtsQueueItem? = if (items.isEmpty()) null else items.removeFirst()
    fun remove(messageId: String) { items.removeAll { it.messageId == messageId } }
    fun ids(): List<String> = items.map { it.messageId }
}

/** Small application-scoped sequential queue. Only one model inference or AudioTrack is active. */
class TtsPlaybackQueue(
    private val engine: TtsEngine,
    private val speed: StateFlow<Float>,
    private val scope: CoroutineScope,
    private val timing: TurnTimingRecorder? = null,
) {
    private val lock = Mutex()
    private val queue = TtsPriorityQueue()
    private val signal = Channel<Unit>(Channel.CONFLATED)
    private val activeIds = mutableSetOf<String>()
    private val _messageStates = MutableStateFlow<Map<String, MessageTtsState>>(emptyMap())
    val messageStates: StateFlow<Map<String, MessageTtsState>> = _messageStates.asStateFlow()
    private var worker: Job? = null
    private var current: TtsQueueItem? = null

    fun start() {
        if (worker?.isActive == true) return
        worker = scope.launch {
            for (ignored in signal) {
                while (true) {
                    val item = lock.withLock { queue.removeFirstOrNull()?.also { current = it } } ?: break
                    process(item)
                    lock.withLock { activeIds.remove(item.messageId); current = null }
                }
            }
        }
    }

    suspend fun enqueue(item: TtsQueueItem) {
        var preemptNormalPlayback: Boolean
        lock.withLock {
            if (!activeIds.add(item.messageId)) return
            preemptNormalPlayback = item.messageType == MessageType.SOS && current != null && current?.messageType != MessageType.SOS
            queue.add(item)
            setState(item.messageId, MessageTtsState.QUEUED)
        }
        if (preemptNormalPlayback) engine.stop()
        signal.trySend(Unit)
    }

    suspend fun stop(messageId: String? = null, acknowledgedSos: Boolean = false) {
        lock.withLock {
            if (messageId != null) {
                queue.remove(messageId); activeIds.remove(messageId)
                if (current?.messageId != messageId) setState(messageId, if (acknowledgedSos) MessageTtsState.COMPLETED else MessageTtsState.FAILED)
            } else {
                queue.ids().forEach { setState(it, MessageTtsState.FAILED); activeIds.remove(it) }
                queue.ids().forEach(queue::remove)
            }
        }
        if (messageId == null || current?.messageId == messageId) {
            engine.stop()
            current?.messageId?.let { setState(it, if (acknowledgedSos) MessageTtsState.COMPLETED else MessageTtsState.FAILED) }
        }
    }

    fun release() {
        worker?.cancel(); worker = null
        engine.release()
        signal.close()
    }

    private suspend fun process(item: TtsQueueItem) {
        try {
            setState(item.messageId, MessageTtsState.SYNTHESIZING)
            timing?.ttsStart(item.messageId)
            val synthesis = engine.synthesize(item.text, item.language, speed.value)
            val audio = synthesis.first
            timing?.audioReady(item.messageId, audio.durationMs, synthesis.second.realTimeFactor)
            setState(item.messageId, MessageTtsState.PLAYING)
            // Engine.play enters AudioTrack playback immediately after queue handoff.
            timing?.playbackStart(item.messageId)
            engine.play(audio, emergency = item.messageType == MessageType.SOS)
            timing?.playbackComplete(item.messageId)
            setState(item.messageId, MessageTtsState.COMPLETED)
        } catch (_: Throwable) {
            timing?.fail(null, item.messageId, TurnTimingFailure.TTS)
            setState(item.messageId, MessageTtsState.FAILED)
        }
    }

    private fun setState(messageId: String, state: MessageTtsState) {
        _messageStates.value = _messageStates.value + (messageId to state)
    }
}
