package org.gipsybuho.recetasfamiliares.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppThemeTest {

    private val newThemes = listOf(
        AppTheme.RUBI_NOCTURNO,
        AppTheme.AURORA_BOREAL,
        AppTheme.JADE_IMPERIAL,
        AppTheme.COBRE_LUNAR,
        AppTheme.CIRUELA_SOLAR,
        AppTheme.CORAL_ABISAL,
    )

    @Test
    fun catalogContainsSixteenThemes() {
        assertEquals(16, AppTheme.entries.size)
    }

    @Test
    fun legacyThemeIdsAndOrderRemainFrozen() {
        assertEquals(
            listOf(
                "BOSQUE",
                "TERRACOTA",
                "OCASO",
                "MEDITERRANEO",
                "LAVANDA",
                "OLIVA",
                "CANELA",
                "MENTA",
                "FRAMBUESA",
                "NOCHE_VERANO",
            ),
            AppTheme.entries.take(10).map(AppTheme::name),
        )
    }

    @Test
    fun rubiNocturnoIsTheOnlyFeaturedDarkRecommendation() {
        assertEquals(listOf(AppTheme.RUBI_NOCTURNO), AppTheme.entries.filter(AppTheme::isFeatured))
        assertEquals(listOf(AppTheme.RUBI_NOCTURNO), AppTheme.entries.filter(AppTheme::recommendedDark))
    }

    @Test
    fun newThemeMetadataMatchesProductNames() {
        assertEquals(
            listOf(
                "Rubí Nocturno" to "Carbón, borgoña y luz rubí",
                "Aurora Boreal" to "Índigo, menta y violeta",
                "Jade Imperial" to "Jade, celadón y cobre",
                "Cobre Lunar" to "Grafito, cobre y amatista",
                "Ciruela Solar" to "Ciruela, ámbar y seda",
                "Coral Abisal" to "Océano profundo y coral",
            ),
            newThemes.map { it.displayName to it.description },
        )
    }

    @Test
    fun newThemeAnchorsRemainExact() {
        val expected = mapOf(
            AppTheme.RUBI_NOCTURNO to AnchorSet(0xFF8F1236, 0xFFFFF8F8, 0xFFFF9EB4, 0xFF100A0D),
            AppTheme.AURORA_BOREAL to AnchorSet(0xFF3555A5, 0xFFFAF8FF, 0xFFB8C4FF, 0xFF0C1020),
            AppTheme.JADE_IMPERIAL to AnchorSet(0xFF006B52, 0xFFF5FBF6, 0xFF70DBB5, 0xFF091713),
            AppTheme.COBRE_LUNAR to AnchorSet(0xFF8B4A1A, 0xFFFFF8F4, 0xFFFFB783, 0xFF111319),
            AppTheme.CIRUELA_SOLAR to AnchorSet(0xFF7B2B64, 0xFFFFF7FC, 0xFFF4AFDB, 0xFF180E17),
            AppTheme.CORAL_ABISAL to AnchorSet(0xFF006875, 0xFFF5FAFC, 0xFF4FD8E9, 0xFF06161A),
        )

        expected.forEach { (theme, anchors) ->
            assertEquals("${theme.name} light primary", Color(anchors.lightPrimary), theme.lightColors().primary)
            assertEquals("${theme.name} light background", Color(anchors.lightBackground), theme.lightColors().background)
            assertEquals("${theme.name} dark primary", Color(anchors.darkPrimary), theme.darkColors().primary)
            assertEquals("${theme.name} dark background", Color(anchors.darkBackground), theme.darkColors().background)
        }
    }

    @Test
    fun allNewThemeSemanticPairsMeetAaContrast() {
        newThemes.forEach { theme ->
            assertSemanticContrast(theme, "light", theme.lightColors())
            assertSemanticContrast(theme, "dark", theme.darkColors())
        }
    }

    @Test
    fun transitionsWithinTheSameModeKeepSemanticContrast() {
        listOf(false, true).forEach { dark ->
            AppTheme.entries.forEach { fromTheme ->
                AppTheme.entries.forEach { toTheme ->
                    val fromPairs = semanticPairs(
                        if (dark) fromTheme.darkColors() else fromTheme.lightColors()
                    )
                    val toPairs = semanticPairs(
                        if (dark) toTheme.darkColors() else toTheme.lightColors()
                    )
                    (0..10).forEach { step ->
                        val fraction = step / 10f
                        fromPairs.zip(toPairs).forEach { (from, to) ->
                            val ratio = contrastRatio(
                                lerp(from.second.first, to.second.first, fraction),
                                lerp(from.second.second, to.second.second, fraction),
                            )
                            assertTrue(
                                "${fromTheme.name}->${toTheme.name} " +
                                    "${if (dark) "dark" else "light"} ${from.first} " +
                                    "at $fraction contrast was $ratio",
                                ratio >= 4.5f,
                            )
                        }
                    }
                }
            }
        }
    }

    private fun assertSemanticContrast(theme: AppTheme, mode: String, colors: ColorScheme) {
        semanticPairs(colors).forEach { (label, pair) ->
            val ratio = contrastRatio(pair.first, pair.second)
            assertTrue(
                "${theme.name} $mode $label contrast was $ratio",
                ratio >= 4.5f,
            )
        }
    }

    private fun semanticPairs(colors: ColorScheme) = listOf(
            "onPrimary/primary" to (colors.onPrimary to colors.primary),
            "onPrimaryContainer/primaryContainer" to (colors.onPrimaryContainer to colors.primaryContainer),
            "onSecondary/secondary" to (colors.onSecondary to colors.secondary),
            "onSecondaryContainer/secondaryContainer" to (colors.onSecondaryContainer to colors.secondaryContainer),
            "onTertiary/tertiary" to (colors.onTertiary to colors.tertiary),
            "onTertiaryContainer/tertiaryContainer" to (colors.onTertiaryContainer to colors.tertiaryContainer),
            "onBackground/background" to (colors.onBackground to colors.background),
            "onSurface/surface" to (colors.onSurface to colors.surface),
            "onSurfaceVariant/surfaceVariant" to (colors.onSurfaceVariant to colors.surfaceVariant),
            "onError/error" to (colors.onError to colors.error),
            "onErrorContainer/errorContainer" to (colors.onErrorContainer to colors.errorContainer),
        )

    private fun contrastRatio(first: Color, second: Color): Float {
        val firstLuminance = first.luminance()
        val secondLuminance = second.luminance()
        val lighter = maxOf(firstLuminance, secondLuminance)
        val darker = minOf(firstLuminance, secondLuminance)
        return (lighter + 0.05f) / (darker + 0.05f)
    }

    private data class AnchorSet(
        val lightPrimary: Long,
        val lightBackground: Long,
        val darkPrimary: Long,
        val darkBackground: Long,
    )
}
