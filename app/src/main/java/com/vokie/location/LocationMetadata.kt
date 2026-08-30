package com.vokie.location

enum class LocationAvailability { AVAILABLE, UNAVAILABLE, PERMISSION_DENIED, INVALID }

data class LocationMetadata(
    val latitude: Double? = null,
    val longitude: Double? = null,
    val accuracyMeters: Float? = null,
    val timestamp: Long? = null,
    val locationSequence: Long = 0,
    val availability: LocationAvailability,
) {
    init {
        require(locationSequence >= 0)
        if (availability == LocationAvailability.AVAILABLE) {
            require(latitude != null && latitude in -90.0..90.0 && longitude != null && longitude in -180.0..180.0)
            require(accuracyMeters != null && accuracyMeters >= 0f && timestamp != null && timestamp > 0)
        }
    }
}
