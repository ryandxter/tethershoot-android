package com.rvmeur.transferin.domain.camera

/**
 * Complete representation of Fujifilm Film Simulation modes and color science profiles.
 * Corresponds to EXIF Tag 0x1401 (FilmMode) in Fuji MakerNotes and PTP property tables.
 */
enum class FujiFilmSimulation(
    val primaryCode: Int,
    val displayName: String,
    val badgeHexColor: String,
    val accentColor: String,
    val isMonochrome: Boolean = false
) {
    PROVIA(0x0000, "Provia / Standard", "#3A7BD5", "#00D2FF", false),
    ASTIA(0x0110, "Astia / Soft", "#6A82FB", "#FC5C7D", false),
    VELVIA(0x0120, "Velvia / Vivid", "#E52D27", "#B31217", false),
    PRO_NEG_STD(0x0200, "PRO Neg. Std", "#795548", "#8D6E63", false),
    PRO_NEG_HI(0x0201, "PRO Neg. Hi", "#5D4037", "#6D4C41", false),
    CLASSIC_CHROME(0x0300, "Classic Chrome", "#607D8B", "#455A64", false),
    ETERNA(0x0400, "Eterna / Cinema", "#78909C", "#37474F", false),
    CLASSIC_NEG(0x0500, "Classic Neg", "#388E3C", "#1B5E20", false),
    BLEACH_BYPASS(0x0501, "Eterna Bleach Bypass", "#546E7A", "#263238", false),
    NOSTALGIC_NEG(0x0502, "Nostalgic Neg", "#D35400", "#E67E22", false),
    REALA_ACE(0x0600, "Reala Ace", "#FF6F00", "#FF8F00", false),
    MONOCHROME(0x0700, "Monochrome", "#424242", "#212121", true),
    MONOCHROME_YE(0x0701, "Monochrome + Ye", "#F57F17", "#212121", true),
    MONOCHROME_R(0x0702, "Monochrome + R", "#C62828", "#212121", true),
    MONOCHROME_G(0x0703, "Monochrome + G", "#2E7D32", "#212121", true),
    SEPIA(0x0710, "Sepia", "#795548", "#4E342E", false),
    ACROS(0x0800, "ACROS", "#212121", "#000000", true),
    ACROS_YE(0x0801, "ACROS + Ye", "#FBC02D", "#000000", true),
    ACROS_R(0x0802, "ACROS + R", "#D32F2F", "#000000", true),
    ACROS_G(0x0803, "ACROS + G", "#388E3C", "#000000", true),
    UNKNOWN(-1, "Custom / Unknown", "#9E9E9E", "#616161", false);

    companion object {
        fun fromTagValue(tagValue: Int): FujiFilmSimulation {
            return when (tagValue) {
                0x0000, 0x0100 -> PROVIA
                0x0110 -> ASTIA
                0x0120 -> VELVIA
                0x0200 -> PRO_NEG_STD
                0x0201 -> PRO_NEG_HI
                0x0300 -> CLASSIC_CHROME
                0x0400 -> ETERNA
                0x0500 -> CLASSIC_NEG
                0x0501 -> BLEACH_BYPASS
                0x0502 -> NOSTALGIC_NEG
                0x0600 -> REALA_ACE
                0x0700 -> MONOCHROME
                0x0701 -> MONOCHROME_YE
                0x0702 -> MONOCHROME_R
                0x0703 -> MONOCHROME_G
                0x0710 -> SEPIA
                0x0800 -> ACROS
                0x0801 -> ACROS_YE
                0x0802 -> ACROS_R
                0x0803 -> ACROS_G
                else -> entries.firstOrNull { it.primaryCode == tagValue } ?: UNKNOWN
            }
        }
    }
}
