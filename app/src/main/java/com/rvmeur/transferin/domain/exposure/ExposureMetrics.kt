package com.rvmeur.transferin.domain.exposure

/**
 * Statistical metrics calculated from a 256-bin luminance histogram.
 */
data class ExposureMetrics(
    val meanLuminance: Float,
    val blackFloorDensity: Float,
    val shadowClippingPercent: Float,
    val highlightSaturationPercent: Float,
    val shannonEntropy: Float,
    val histogramBins: IntArray,
    val totalPixels: Int
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ExposureMetrics

        if (meanLuminance != other.meanLuminance) return false
        if (blackFloorDensity != other.blackFloorDensity) return false
        if (shadowClippingPercent != other.shadowClippingPercent) return false
        if (highlightSaturationPercent != other.highlightSaturationPercent) return false
        if (shannonEntropy != other.shannonEntropy) return false
        if (totalPixels != other.totalPixels) return false
        if (!histogramBins.contentEquals(other.histogramBins)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = meanLuminance.hashCode()
        result = 31 * result + blackFloorDensity.hashCode()
        result = 31 * result + shadowClippingPercent.hashCode()
        result = 31 * result + highlightSaturationPercent.hashCode()
        result = 31 * result + shannonEntropy.hashCode()
        result = 31 * result + totalPixels
        result = 31 * result + histogramBins.contentHashCode()
        return result
    }

    companion object {
        private const val INV_LN_2 = 1.4426950408889634

        /**
         * Computes [ExposureMetrics] from a [LuminanceHistogram] without unnecessary heap allocations.
         * [destinationBins] can be provided to avoid creating a new [IntArray].
         */
        fun compute(
            histogram: LuminanceHistogram,
            destinationBins: IntArray? = null
        ): ExposureMetrics {
            val total = histogram.totalPixels
            if (total == 0) {
                val bins = destinationBins ?: IntArray(LuminanceHistogram.BIN_COUNT)
                histogram.copyInto(bins)
                return ExposureMetrics(
                    meanLuminance = 0.0f,
                    blackFloorDensity = 0.0f,
                    shadowClippingPercent = 0.0f,
                    highlightSaturationPercent = 0.0f,
                    shannonEntropy = 0.0f,
                    histogramBins = bins,
                    totalPixels = 0
                )
            }

            val bins = destinationBins ?: IntArray(LuminanceHistogram.BIN_COUNT)
            histogram.copyInto(bins)

            var lumaSum = 0L
            var blackFloorCount = 0
            var shadowClipCount = 0
            var highlightSatCount = 0
            var sumCountLogCount = 0.0

            for (i in 0 until LuminanceHistogram.BIN_COUNT) {
                val count = bins[i]
                if (count > 0) {
                    lumaSum += (i.toLong() * count)
                    val countD = count.toDouble()
                    sumCountLogCount += countD * (Math.log(countD) * INV_LN_2)
                }
                if (i <= 3) {
                    blackFloorCount += count
                }
                if (i <= 15) {
                    shadowClipCount += count
                }
                if (i >= 248) {
                    highlightSatCount += count
                }
            }

            val totalD = total.toDouble()
            val totalF = total.toFloat()
            val log2Total = Math.log(totalD) * INV_LN_2
            val entropy = (log2Total - (sumCountLogCount / totalD)).coerceAtLeast(0.0).toFloat()

            return ExposureMetrics(
                meanLuminance = (lumaSum.toFloat() / totalF),
                blackFloorDensity = (blackFloorCount.toFloat() / totalF),
                shadowClippingPercent = (shadowClipCount.toFloat() / totalF),
                highlightSaturationPercent = (highlightSatCount.toFloat() / totalF),
                shannonEntropy = entropy,
                histogramBins = bins,
                totalPixels = total
            )
        }
    }
}
