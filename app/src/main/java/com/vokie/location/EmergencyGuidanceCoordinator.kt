package com.vokie.location

import com.vokie.proximity.FilteredRssi
import com.vokie.proximity.ProximityGuidance
import com.vokie.proximity.ProximityGuidanceEngine
import com.vokie.proximity.RssiTrend
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class EmergencyGuidanceState(
    val location: LocationGuidance,
    val proximity: ProximityGuidance?,
    val receiverAccuracyMeters: Float?,
    val senderAgeMs: Long?,
    val filteredRssiDbm: Double?,
    val rssiTrend: RssiTrend,
    val timestamp: Long,
)

/** App-level telemetry combiner; sender metadata will remain local until a future wire-protocol milestone. */
class EmergencyGuidanceCoordinator(
    private val locationEngine: LocationGuidanceEngine = LocationGuidanceEngine(),
    private val proximityEngine: ProximityGuidanceEngine = ProximityGuidanceEngine(),
) {
    private val _state = MutableStateFlow(EmergencyGuidanceState(LocationGuidance(null,null,null,null,RelativeDirection.UNKNOWN,LocationConfidence.INVALID,LocationFreshness.UNAVAILABLE), null, null, null, null, RssiTrend.UNKNOWN, 0))
    val state: StateFlow<EmergencyGuidanceState> = _state
    private var previousDistance: Double? = null
    fun update(sender: LocationMetadata, receiver: LocationMetadata, heading: Heading?, rssi: FilteredRssi?, now: Long): EmergencyGuidanceState {
        val guidance = locationEngine.guide(sender, receiver, heading?.degrees, now)
        val proximity = proximityEngine.guide(guidance, previousDistance, rssi)
        previousDistance = guidance.distanceMeters
        return EmergencyGuidanceState(guidance, proximity, receiver.accuracyMeters, sender.timestamp?.let { now - it }, rssi?.rssiDbm, rssi?.trend ?: RssiTrend.UNKNOWN, now).also { _state.value = it }
    }
}
