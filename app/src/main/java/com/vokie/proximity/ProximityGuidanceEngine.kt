package com.vokie.proximity

import com.vokie.location.LocationConfidence
import com.vokie.location.LocationFreshness
import com.vokie.location.LocationGuidance

enum class ProximityGuidanceState { SEARCHING, LOCATION_RECEIVED, GUIDANCE_ACTIVE, GETTING_CLOSER, GETTING_FARTHER, STABLE, SIGNAL_UNRELIABLE, LOCATION_STALE, NEARBY }
data class ProximityGuidance(val state: ProximityGuidanceState, val location: LocationGuidance, val signalTrend: RssiTrend, val confidence: LocationConfidence)

class ProximityGuidanceEngine(private val nearbyMeters: Double = 20.0) {
    fun guide(location: LocationGuidance, signal: RssiTrend): ProximityGuidance {
        if (location.freshness == LocationFreshness.STALE) return ProximityGuidance(ProximityGuidanceState.LOCATION_STALE, location, signal, LocationConfidence.UNAVAILABLE)
        if (location.freshness == LocationFreshness.UNAVAILABLE) return ProximityGuidance(ProximityGuidanceState.SEARCHING, location, signal, LocationConfidence.UNAVAILABLE)
        val state = when {
            location.distanceMeters != null && location.distanceMeters <= nearbyMeters -> ProximityGuidanceState.NEARBY
            signal == RssiTrend.UNSTABLE -> ProximityGuidanceState.SIGNAL_UNRELIABLE
            signal == RssiTrend.STRENGTHENING -> ProximityGuidanceState.GETTING_CLOSER
            signal == RssiTrend.WEAKENING -> ProximityGuidanceState.GETTING_FARTHER
            else -> ProximityGuidanceState.GUIDANCE_ACTIVE
        }
        val confidence = if (signal == RssiTrend.UNSTABLE) LocationConfidence.LOW else location.confidence
        return ProximityGuidance(state, location, signal, confidence)
    }
}
