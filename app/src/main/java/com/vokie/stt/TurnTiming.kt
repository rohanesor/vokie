package com.vokie.stt

import android.os.SystemClock

/** Local-only monotonic timing record. It is never persisted or encoded in PacketV2. */
enum class TurnTimingStatus { INCOMPLETE, COMPLETE, FAILED }
enum class TurnTimingFailure { STT, TRANSPORT, TRANSLATION, TTS, PLAYBACK }

data class TurnTimingSnapshot(
    val turnId: String?, val messageId: String?,
    val t0SpeechStartNs: Long? = null, val t1EndpointNs: Long? = null,
    val t2SttCompleteNs: Long? = null, val t3PacketCreatedNs: Long? = null,
    val t4PacketReceivedNs: Long? = null, val t5TranslationCompleteNs: Long? = null,
    val t6TtsStartNs: Long? = null, val t7AudioReadyNs: Long? = null,
    val t8PlaybackStartNs: Long? = null, val failure: TurnTimingFailure? = null,
) {
    val status get() = if (failure != null) TurnTimingStatus.FAILED else if (listOf(t0SpeechStartNs,t1EndpointNs,t2SttCompleteNs,t3PacketCreatedNs,t4PacketReceivedNs,t5TranslationCompleteNs,t6TtsStartNs,t7AudioReadyNs,t8PlaybackStartNs).all { it != null }) TurnTimingStatus.COMPLETE else TurnTimingStatus.INCOMPLETE
    private fun delta(a: Long?, b: Long?) = if (a != null && b != null && b >= a) (b-a)/1_000_000L else null
    val speechDurationMs get() = delta(t0SpeechStartNs,t1EndpointNs)
    val sttLatencyMs get() = delta(t1EndpointNs,t2SttCompleteNs)
    val packetPreparationMs get() = delta(t2SttCompleteNs,t3PacketCreatedNs)
    val transportLatencyMs get() = delta(t3PacketCreatedNs,t4PacketReceivedNs)
    val translationLatencyMs get() = delta(t4PacketReceivedNs,t5TranslationCompleteNs)
    val ttsLatencyMs get() = delta(t6TtsStartNs,t7AudioReadyNs)
    val playbackQueueDelayMs get() = delta(t7AudioReadyNs,t8PlaybackStartNs)
    val endToEndLatencyMs get() = delta(t0SpeechStartNs,t8PlaybackStartNs)
    val postSttLatencyMs get() = delta(t2SttCompleteNs,t8PlaybackStartNs)
}

/** Debug-local bounded recorder. Sender and receiver logs correlate by messageId across phones. */
class TurnTimingRecorder(private val nowNs: () -> Long = { SystemClock.elapsedRealtimeNanos() }) {
    private val byTurn = linkedMapOf<String, TurnTimingSnapshot>()
    private val turnByMessage = mutableMapOf<String, String>()
    @Synchronized fun start(turnId: String) = updateTurn(turnId) { it.copy(t0SpeechStartNs = it.t0SpeechStartNs ?: nowNs()) }
    @Synchronized fun endpoint(turnId: String) = updateTurn(turnId) { it.copy(t1EndpointNs = it.t1EndpointNs ?: nowNs()) }
    @Synchronized fun sttComplete(turnId: String) = updateTurn(turnId) { it.copy(t2SttCompleteNs = it.t2SttCompleteNs ?: nowNs()) }
    @Synchronized fun packetCreated(turnId: String, messageId: String) { turnByMessage[messageId] = turnId; updateTurn(turnId) { it.copy(messageId=messageId, t3PacketCreatedNs=it.t3PacketCreatedNs ?: nowNs()) } }
    @Synchronized fun packetReceived(messageId: String) = updateMessage(messageId) { it.copy(t4PacketReceivedNs=it.t4PacketReceivedNs ?: nowNs()) }
    @Synchronized fun translationComplete(messageId: String) = updateMessage(messageId) { it.copy(t5TranslationCompleteNs=it.t5TranslationCompleteNs ?: nowNs()) }
    @Synchronized fun ttsStart(messageId: String) = updateMessage(messageId) { it.copy(t6TtsStartNs=it.t6TtsStartNs ?: nowNs()) }
    @Synchronized fun audioReady(messageId: String) = updateMessage(messageId) { it.copy(t7AudioReadyNs=it.t7AudioReadyNs ?: nowNs()) }
    @Synchronized fun playbackStart(messageId: String) = updateMessage(messageId) { it.copy(t8PlaybackStartNs=it.t8PlaybackStartNs ?: nowNs()) }
    @Synchronized fun fail(turnId: String?, messageId: String?, failure: TurnTimingFailure) {
        val id = turnId ?: messageId?.let { turnByMessage[it] } ?: "receiver:$messageId"
        updateTurn(id) { it.copy(messageId=messageId ?: it.messageId, failure=failure) }
    }
    @Synchronized fun snapshotForTurn(turnId: String) = byTurn[turnId]
    @Synchronized fun snapshotForMessage(messageId: String): TurnTimingSnapshot? = turnByMessage[messageId]?.let(byTurn::get) ?: byTurn["receiver:$messageId"]
    private fun updateMessage(messageId: String, f: (TurnTimingSnapshot)->TurnTimingSnapshot) { val id=turnByMessage[messageId] ?: "receiver:$messageId"; updateTurn(id) { f(it.copy(messageId=messageId)) } }
    private fun updateTurn(id: String, f: (TurnTimingSnapshot)->TurnTimingSnapshot) { byTurn[id]=f(byTurn[id] ?: TurnTimingSnapshot(id.takeUnless { it.startsWith("receiver:") }, null)); while(byTurn.size>128) byTurn.remove(byTurn.keys.first()) }
}
