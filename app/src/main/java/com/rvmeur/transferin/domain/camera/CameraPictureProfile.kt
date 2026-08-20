package com.rvmeur.transferin.domain.camera

/**
 * Unified representation of camera picture profiles and film simulations
 * used for dynamic exposure and luminance calibration.
 */
enum class CameraPictureProfile(
    val displayName: String,
    val isMonochrome: Boolean = false,
    val isVividHighContrast: Boolean = false
) {
    STANDARD_SRGB("Standard sRGB Baseline", isMonochrome = false, isVividHighContrast = false),
    FUJI_ACROS("Fujifilm ACROS", isMonochrome = true, isVividHighContrast = true),
    FUJI_VELVIA("Fujifilm Velvia / Vivid", isMonochrome = false, isVividHighContrast = true),
    FUJI_MONOCHROME("Fujifilm Monochrome", isMonochrome = true, isVividHighContrast = true),
    FUJI_CLASSIC_CHROME("Fujifilm Classic Chrome", isMonochrome = false, isVividHighContrast = false),
    CANON_MONOCHROME("Canon Monochrome", isMonochrome = true, isVividHighContrast = true),
    CANON_PORTRAIT("Canon Portrait", isMonochrome = false, isVividHighContrast = false),
    SONY_BW("Sony B&W", isMonochrome = true, isVividHighContrast = true),
    SONY_VIVID("Sony Vivid", isMonochrome = false, isVividHighContrast = true),
    NIKON_MONOCHROME("Nikon Monochrome", isMonochrome = true, isVividHighContrast = true),
    CUSTOM("Custom Profile", isMonochrome = false, isVividHighContrast = false);

    companion object {
        /**
         * Maps a [FujiFilmSimulation] to the corresponding [CameraPictureProfile].
         */
        fun fromFujiFilmSimulation(simulation: FujiFilmSimulation): CameraPictureProfile {
            return when (simulation) {
                FujiFilmSimulation.ACROS,
                FujiFilmSimulation.ACROS_YE,
                FujiFilmSimulation.ACROS_R,
                FujiFilmSimulation.ACROS_G -> FUJI_ACROS

                FujiFilmSimulation.VELVIA -> FUJI_VELVIA

                FujiFilmSimulation.MONOCHROME,
                FujiFilmSimulation.MONOCHROME_YE,
                FujiFilmSimulation.MONOCHROME_R,
                FujiFilmSimulation.MONOCHROME_G -> FUJI_MONOCHROME

                FujiFilmSimulation.CLASSIC_CHROME -> FUJI_CLASSIC_CHROME

                FujiFilmSimulation.PROVIA,
                FujiFilmSimulation.ASTIA,
                FujiFilmSimulation.PRO_NEG_STD,
                FujiFilmSimulation.PRO_NEG_HI,
                FujiFilmSimulation.ETERNA,
                FujiFilmSimulation.CLASSIC_NEG,
                FujiFilmSimulation.BLEACH_BYPASS,
                FujiFilmSimulation.NOSTALGIC_NEG,
                FujiFilmSimulation.REALA_ACE,
                FujiFilmSimulation.SEPIA,
                FujiFilmSimulation.UNKNOWN -> STANDARD_SRGB
            }
        }
    }
}
