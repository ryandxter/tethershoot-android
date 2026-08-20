package com.rvmeur.transferin.domain.exposure

/**
 * High-performance 256-bin luminance histogram container.
 * Pre-allocates its internal [IntArray] buffer to ensure zero heap allocation during frame analysis.
 */
class LuminanceHistogram(
    val bins: IntArray = IntArray(BIN_COUNT)
) {
    companion object {
        const val BIN_COUNT = 256
    }

    var totalPixels: Int = 0
        private set

    init {
        require(bins.size == BIN_COUNT) { "Histogram bins must have size exactly $BIN_COUNT" }
        recalculateTotal()
    }

    /**
     * Resets all 256 bins to 0 and clears the total pixel count.
     */
    fun reset() {
        bins.fill(0)
        totalPixels = 0
    }

    /**
     * Increments the count for the bin corresponding to [luma].
     * Clamps [luma] to the range [0, 255].
     */
    fun record(luma: Int) {
        val bin = if (luma < 0) 0 else if (luma > 255) 255 else luma
        bins[bin]++
        totalPixels++
    }

    /**
     * Directly gets the bin count at [binIndex].
     */
    operator fun get(binIndex: Int): Int = bins[binIndex]

    /**
     * Sets the bin count at [binIndex] and adjusts totalPixels accordingly.
     */
    operator fun set(binIndex: Int, value: Int) {
        val old = bins[binIndex]
        bins[binIndex] = value
        totalPixels += (value - old)
    }

    /**
     * Copies current bin values into [destination].
     * [destination] must have at least [BIN_COUNT] elements.
     */
    fun copyInto(destination: IntArray) {
        require(destination.size >= BIN_COUNT) {
            "Destination array must have size >= $BIN_COUNT, got ${destination.size}"
        }
        System.arraycopy(bins, 0, destination, 0, BIN_COUNT)
    }

    /**
     * Copies all bin values and total count from [other].
     */
    fun copyFrom(other: LuminanceHistogram) {
        System.arraycopy(other.bins, 0, bins, 0, BIN_COUNT)
        totalPixels = other.totalPixels
    }

    /**
     * Recomputes and sets totalPixels from summing all bins.
     */
    fun recalculateTotal(): Int {
        var count = 0
        for (i in 0 until BIN_COUNT) {
            count += bins[i]
        }
        totalPixels = count
        return count
    }
}
