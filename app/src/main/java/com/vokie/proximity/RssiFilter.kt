package com.vokie.proximity

/** Deterministic rolling median; output is radio evidence only, never distance. */
class RssiFilter(private val windowSize: Int = 5, private val stableDeltaDb: Double = 2.0) {
    init { require(windowSize > 0 && stableDeltaDb >= 0) }
    private val values = ArrayDeque<Int>()
    private var previous: Double? = null

    fun add(sample: Int): FilteredRssi {
        values.addLast(sample); if (values.size > windowSize) values.removeFirst()
        val median = values.sorted().let { it[it.size / 2].toDouble() }
        val trend = previous?.let { old ->
            when {
                median - old > stableDeltaDb -> RssiTrend.STRENGTHENING
                median - old < -stableDeltaDb -> RssiTrend.WEAKENING
                else -> RssiTrend.STABLE
            }
        } ?: RssiTrend.UNKNOWN
        previous = median
        return FilteredRssi(median, trend)
    }
}
