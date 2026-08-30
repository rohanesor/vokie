package com.vokie.location

import kotlin.math.*

enum class CardinalDirection { N, NE, E, SE, S, SW, W, NW }
enum class LocationFreshness { CURRENT, STALE, UNAVAILABLE }
enum class LocationConfidence { HIGH, LOW, UNAVAILABLE }

data class LocationGuidance(
    val distanceMeters: Double?, val absoluteBearingDegrees: Double?, val relativeBearingDegrees: Double?,
    val cardinalDirection: CardinalDirection?, val confidence: LocationConfidence, val freshness: LocationFreshness,
)

class LocationGuidanceEngine(private val staleAfterMs: Long = 60_000L, private val lowAccuracyMeters: Float = 50f) {
    fun guide(sender: LocationMetadata, receiver: LocationMetadata, receiverHeadingDegrees: Double?, now: Long): LocationGuidance {
        if (sender.availability != LocationAvailability.AVAILABLE || receiver.availability != LocationAvailability.AVAILABLE) return unavailable()
        val timestamp = requireNotNull(sender.timestamp)
        if (now < timestamp || now - timestamp > staleAfterMs) return LocationGuidance(null, null, null, null, LocationConfidence.UNAVAILABLE, LocationFreshness.STALE)
        val distance = haversine(requireNotNull(receiver.latitude), requireNotNull(receiver.longitude), requireNotNull(sender.latitude), requireNotNull(sender.longitude))
        val bearing = initialBearing(requireNotNull(receiver.latitude), requireNotNull(receiver.longitude), requireNotNull(sender.latitude), requireNotNull(sender.longitude))
        val relative = receiverHeadingDegrees?.let { normalize(bearing - it) }
        val accuracy = maxOf(requireNotNull(sender.accuracyMeters), requireNotNull(receiver.accuracyMeters))
        return LocationGuidance(distance, bearing, relative, cardinal(bearing), if (accuracy > lowAccuracyMeters) LocationConfidence.LOW else LocationConfidence.HIGH, LocationFreshness.CURRENT)
    }

    fun haversine(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1); val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        return 6_371_000.0 * 2 * atan2(sqrt(a), sqrt(1 - a))
    }
    fun initialBearing(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLon = Math.toRadians(lon2 - lon1); val p1 = Math.toRadians(lat1); val p2 = Math.toRadians(lat2)
        return normalize(Math.toDegrees(atan2(sin(dLon) * cos(p2), cos(p1) * sin(p2) - sin(p1) * cos(p2) * cos(dLon))))
    }
    fun normalize(degrees: Double) = ((degrees % 360) + 360) % 360
    fun cardinal(bearing: Double): CardinalDirection = CardinalDirection.entries[((normalize(bearing) + 22.5) / 45).toInt() % 8]
    private fun unavailable() = LocationGuidance(null, null, null, null, LocationConfidence.UNAVAILABLE, LocationFreshness.UNAVAILABLE)
}
