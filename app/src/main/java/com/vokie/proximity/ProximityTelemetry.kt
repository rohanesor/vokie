package com.vokie.proximity

import com.vokie.domain.model.TransportType

data class ProximityTelemetry(val sourceDeviceId: String, val transportType: TransportType, val rssi: Int, val timestamp: Long) {
    init { require(sourceDeviceId.isNotBlank() && timestamp > 0) }
}
enum class RssiTrend { STRENGTHENING, WEAKENING, STABLE, UNSTABLE, UNKNOWN }
data class FilteredRssi(val rssi: Double?, val trend: RssiTrend)
