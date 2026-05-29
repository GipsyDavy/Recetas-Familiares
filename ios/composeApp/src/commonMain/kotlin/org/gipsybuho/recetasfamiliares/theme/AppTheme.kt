package org.gipsybuho.recetasfamiliares.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

enum class AppTheme {
    BOSQUE, TERRACOTA, OCASO, MEDITERRANEO, LAVANDA,
    OLIVA, CANELA, MENTA, FRAMBUESA, NOCHE_VERANO;

    val displayName: String get() = when (this) {
        BOSQUE        -> "Bosque"
        TERRACOTA     -> "Terracota"
        OCASO         -> "Ocaso"
        MEDITERRANEO  -> "Mediterráneo"
        LAVANDA       -> "Lavanda"
        OLIVA         -> "Oliva"
        CANELA        -> "Canela"
        MENTA         -> "Menta"
        FRAMBUESA     -> "Frambuesa"
        NOCHE_VERANO  -> "Noche de Verano"
    }
}

enum class ThemeMode { LIGHT, DARK, SYSTEM }

fun AppTheme.lightColors(): ColorScheme = when (this) {
    AppTheme.BOSQUE -> lightColorScheme(
        primary = Color(0xFF3E5F45), onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFC0EDCA), onPrimaryContainer = Color(0xFF002111),
        secondary = Color(0xFF8C4A2F), onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFFFDBCF), onSecondaryContainer = Color(0xFF3A0A00),
        tertiary = Color(0xFF7B5E35), onTertiary = Color(0xFFFFFFFF),
        tertiaryContainer = Color(0xFFFFDDB3), onTertiaryContainer = Color(0xFF2C1700),
        background = Color(0xFFFFFBF6), onBackground = Color(0xFF1A1C19),
        surface = Color(0xFFFFFBF6), onSurface = Color(0xFF1A1C19),
        surfaceVariant = Color(0xFFEDE8E0), onSurfaceVariant = Color(0xFF44483E),
        outline = Color(0xFF7C7669), error = Color(0xFFBA1A1A), onError = Color(0xFFFFFFFF),
        errorContainer = Color(0xFFFFDAD6), onErrorContainer = Color(0xFF410002),
    )
    AppTheme.TERRACOTA -> lightColorScheme(
        primary = Color(0xFF8B3A2A), onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFFFDBCF), onPrimaryContainer = Color(0xFF3A0900),
        secondary = Color(0xFF785849), onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFFFDBCF), onSecondaryContainer = Color(0xFF2D1509),
        tertiary = Color(0xFF6B5E2F), onTertiary = Color(0xFFFFFFFF),
        tertiaryContainer = Color(0xFFF6E2A7), onTertiaryContainer = Color(0xFF221B00),
        background = Color(0xFFFFF8F5), onBackground = Color(0xFF201A18),
        surface = Color(0xFFFFF8F5), onSurface = Color(0xFF201A18),
        surfaceVariant = Color(0xFFF4DDD6), onSurfaceVariant = Color(0xFF534340),
        outline = Color(0xFFA08C85), error = Color(0xFFBA1A1A), onError = Color(0xFFFFFFFF),
        errorContainer = Color(0xFFFFDAD6), onErrorContainer = Color(0xFF410002),
    )
    AppTheme.OCASO -> lightColorScheme(
        primary = Color(0xFFC84B2B), onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFFFDBCC), onPrimaryContainer = Color(0xFF3E0700),
        secondary = Color(0xFF9E5228), onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFFFDBCA), onSecondaryContainer = Color(0xFF360F00),
        tertiary = Color(0xFF7B5800), onTertiary = Color(0xFFFFFFFF),
        tertiaryContainer = Color(0xFFFFDE9D), onTertiaryContainer = Color(0xFF271900),
        background = Color(0xFFFFFBF8), onBackground = Color(0xFF201A16),
        surface = Color(0xFFFFFBF8), onSurface = Color(0xFF201A16),
        surfaceVariant = Color(0xFFF5E0D5), onSurfaceVariant = Color(0xFF534440),
        outline = Color(0xFF9C7B6F), error = Color(0xFFBA1A1A), onError = Color(0xFFFFFFFF),
        errorContainer = Color(0xFFFFDAD6), onErrorContainer = Color(0xFF410002),
    )
    AppTheme.MEDITERRANEO -> lightColorScheme(
        primary = Color(0xFF1B5E8A), onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFCFE5FF), onPrimaryContainer = Color(0xFF001D35),
        secondary = Color(0xFF4A7C59), onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFCCE8D5), onSecondaryContainer = Color(0xFF07210F),
        tertiary = Color(0xFF6D5E0F), onTertiary = Color(0xFFFFFFFF),
        tertiaryContainer = Color(0xFFF7E48B), onTertiaryContainer = Color(0xFF211B00),
        background = Color(0xFFF6FBFF), onBackground = Color(0xFF181C20),
        surface = Color(0xFFF6FBFF), onSurface = Color(0xFF181C20),
        surfaceVariant = Color(0xFFDDE4ED), onSurfaceVariant = Color(0xFF41484F),
        outline = Color(0xFF6E7E8A), error = Color(0xFFBA1A1A), onError = Color(0xFFFFFFFF),
        errorContainer = Color(0xFFFFDAD6), onErrorContainer = Color(0xFF410002),
    )
    AppTheme.LAVANDA -> lightColorScheme(
        primary = Color(0xFF6750A4), onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFEADDFF), onPrimaryContainer = Color(0xFF21005D),
        secondary = Color(0xFF9C4D80), onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFFFD7F2), onSecondaryContainer = Color(0xFF38003A),
        tertiary = Color(0xFF7E5700), onTertiary = Color(0xFFFFFFFF),
        tertiaryContainer = Color(0xFFFFDEA0), onTertiaryContainer = Color(0xFF281900),
        background = Color(0xFFFEFBFF), onBackground = Color(0xFF1C1B1F),
        surface = Color(0xFFFEFBFF), onSurface = Color(0xFF1C1B1F),
        surfaceVariant = Color(0xFFE7E0EC), onSurfaceVariant = Color(0xFF49454F),
        outline = Color(0xFF79757F), error = Color(0xFFB3261E), onError = Color(0xFFFFFFFF),
        errorContainer = Color(0xFFF9DEDC), onErrorContainer = Color(0xFF410E0B),
    )
    AppTheme.OLIVA -> lightColorScheme(
        primary = Color(0xFF4A5C2F), onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFCAEAA6), onPrimaryContainer = Color(0xFF0D1E00),
        secondary = Color(0xFF7B6D3F), onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFF6E5BB), onSecondaryContainer = Color(0xFF271A00),
        tertiary = Color(0xFF386663), onTertiary = Color(0xFFFFFFFF),
        tertiaryContainer = Color(0xFFBCECE8), onTertiaryContainer = Color(0xFF00201E),
        background = Color(0xFFFAFDF0), onBackground = Color(0xFF1A1D14),
        surface = Color(0xFFFAFDF0), onSurface = Color(0xFF1A1D14),
        surfaceVariant = Color(0xFFE2E6D3), onSurfaceVariant = Color(0xFF43493C),
        outline = Color(0xFF73796B), error = Color(0xFFBA1A1A), onError = Color(0xFFFFFFFF),
        errorContainer = Color(0xFFFFDAD6), onErrorContainer = Color(0xFF410002),
    )
    AppTheme.CANELA -> lightColorScheme(
        primary = Color(0xFF7B4F2A), onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFFDDCBB), onPrimaryContainer = Color(0xFF2B1500),
        secondary = Color(0xFF6C5944), onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFF7DCBF), onSecondaryContainer = Color(0xFF261506),
        tertiary = Color(0xFF4E6B3F), onTertiary = Color(0xFFFFFFFF),
        tertiaryContainer = Color(0xFFCEEDB8), onTertiaryContainer = Color(0xFF0A2100),
        background = Color(0xFFFFF8F2), onBackground = Color(0xFF201A13),
        surface = Color(0xFFFFF8F2), onSurface = Color(0xFF201A13),
        surfaceVariant = Color(0xFFEDE0D4), onSurfaceVariant = Color(0xFF504540),
        outline = Color(0xFF8E7B6C), error = Color(0xFFBA1A1A), onError = Color(0xFFFFFFFF),
        errorContainer = Color(0xFFFFDAD6), onErrorContainer = Color(0xFF410002),
    )
    AppTheme.MENTA -> lightColorScheme(
        primary = Color(0xFF00695C), onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFA7F3EB), onPrimaryContainer = Color(0xFF00201C),
        secondary = Color(0xFF4A6360), onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFCCE8E5), onSecondaryContainer = Color(0xFF051F1D),
        tertiary = Color(0xFF5C7A1F), onTertiary = Color(0xFFFFFFFF),
        tertiaryContainer = Color(0xFFD9F0A0), onTertiaryContainer = Color(0xFF192400),
        background = Color(0xFFF5FFFE), onBackground = Color(0xFF161D1C),
        surface = Color(0xFFF5FFFE), onSurface = Color(0xFF161D1C),
        surfaceVariant = Color(0xFFD8E5E3), onSurfaceVariant = Color(0xFF3D4947),
        outline = Color(0xFF6A7A79), error = Color(0xFFBA1A1A), onError = Color(0xFFFFFFFF),
        errorContainer = Color(0xFFFFDAD6), onErrorContainer = Color(0xFF410002),
    )
    AppTheme.FRAMBUESA -> lightColorScheme(
        primary = Color(0xFF9E1B4A), onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFFFD9E3), onPrimaryContainer = Color(0xFF3E0015),
        secondary = Color(0xFF7A525B), onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFFFD9E3), onSecondaryContainer = Color(0xFF30111C),
        tertiary = Color(0xFF795900), onTertiary = Color(0xFFFFFFFF),
        tertiaryContainer = Color(0xFFFFDE9D), onTertiaryContainer = Color(0xFF271900),
        background = Color(0xFFFFF8F8), onBackground = Color(0xFF201A1B),
        surface = Color(0xFFFFF8F8), onSurface = Color(0xFF201A1B),
        surfaceVariant = Color(0xFFECDFDF), onSurfaceVariant = Color(0xFF504446),
        outline = Color(0xFF85727B), error = Color(0xFFBA1A1A), onError = Color(0xFFFFFFFF),
        errorContainer = Color(0xFFFFDAD6), onErrorContainer = Color(0xFF410002),
    )
    AppTheme.NOCHE_VERANO -> lightColorScheme(
        primary = Color(0xFF2C5282), onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFD6E4FF), onPrimaryContainer = Color(0xFF001944),
        secondary = Color(0xFF4A5C8A), onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFD8E2FF), onSecondaryContainer = Color(0xFF0D1B40),
        tertiary = Color(0xFF7B5800), onTertiary = Color(0xFFFFFFFF),
        tertiaryContainer = Color(0xFFFFDE9D), onTertiaryContainer = Color(0xFF271900),
        background = Color(0xFFF8FAFE), onBackground = Color(0xFF191C20),
        surface = Color(0xFFF8FAFE), onSurface = Color(0xFF191C20),
        surfaceVariant = Color(0xFFE0E2F0), onSurfaceVariant = Color(0xFF44464E),
        outline = Color(0xFF72748B), error = Color(0xFFBA1A1A), onError = Color(0xFFFFFFFF),
        errorContainer = Color(0xFFFFDAD6), onErrorContainer = Color(0xFF410002),
    )
}

fun AppTheme.darkColors(): ColorScheme = when (this) {
    AppTheme.BOSQUE -> darkColorScheme(
        primary = Color(0xFFAED6B4), onPrimary = Color(0xFF103720),
        primaryContainer = Color(0xFF284F33), onPrimaryContainer = Color(0xFFC0EDCA),
        secondary = Color(0xFFE6B89C), onSecondary = Color(0xFF531F06),
        secondaryContainer = Color(0xFF703519), onSecondaryContainer = Color(0xFFFFDBCF),
        tertiary = Color(0xFFEFBD7B), onTertiary = Color(0xFF432C04),
        tertiaryContainer = Color(0xFF5C4118), onTertiaryContainer = Color(0xFFFFDDB3),
        background = Color(0xFF161915), onBackground = Color(0xFFE2E3DC),
        surface = Color(0xFF1D211C), onSurface = Color(0xFFE2E3DC),
        surfaceVariant = Color(0xFF3F4A40), onSurfaceVariant = Color(0xFFBFC9BF),
        outline = Color(0xFF8E9B8F), error = Color(0xFFFFB4AB), onError = Color(0xFF690005),
        errorContainer = Color(0xFF93000A), onErrorContainer = Color(0xFFFFDAD6),
    )
    AppTheme.TERRACOTA -> darkColorScheme(
        primary = Color(0xFFFFB4A0), onPrimary = Color(0xFF551500),
        primaryContainer = Color(0xFF733220), onPrimaryContainer = Color(0xFFFFDBCF),
        secondary = Color(0xFFE7BDB1), onSecondary = Color(0xFF442922),
        secondaryContainer = Color(0xFF5D3F37), onSecondaryContainer = Color(0xFFFFDBCF),
        tertiary = Color(0xFFD4C491), onTertiary = Color(0xFF382E07),
        tertiaryContainer = Color(0xFF50461B), onTertiaryContainer = Color(0xFFF6E2A7),
        background = Color(0xFF211410), onBackground = Color(0xFFEEDED9),
        surface = Color(0xFF291A16), onSurface = Color(0xFFEEDED9),
        surfaceVariant = Color(0xFF53403B), onSurfaceVariant = Color(0xFFD9BDB8),
        outline = Color(0xFFA08C85), error = Color(0xFFFFB4AB), onError = Color(0xFF690005),
        errorContainer = Color(0xFF93000A), onErrorContainer = Color(0xFFFFDAD6),
    )
    AppTheme.OCASO -> darkColorScheme(
        primary = Color(0xFFFFB59E), onPrimary = Color(0xFF5E1300),
        primaryContainer = Color(0xFF7F2310), onPrimaryContainer = Color(0xFFFFDBCC),
        secondary = Color(0xFFFFBA9D), onSecondary = Color(0xFF551F00),
        secondaryContainer = Color(0xFF762D0A), onSecondaryContainer = Color(0xFFFFDBCA),
        tertiary = Color(0xFFFAC55A), onTertiary = Color(0xFF3F2D00),
        tertiaryContainer = Color(0xFF5B4100), onTertiaryContainer = Color(0xFFFFDE9D),
        background = Color(0xFF201009), onBackground = Color(0xFFEFDFD7),
        surface = Color(0xFF291612), onSurface = Color(0xFFEFDFD7),
        surfaceVariant = Color(0xFF52332A), onSurfaceVariant = Color(0xFFD7B9B1),
        outline = Color(0xFFA68277), error = Color(0xFFFFB4AB), onError = Color(0xFF690005),
        errorContainer = Color(0xFF93000A), onErrorContainer = Color(0xFFFFDAD6),
    )
    AppTheme.MEDITERRANEO -> darkColorScheme(
        primary = Color(0xFF98CBFF), onPrimary = Color(0xFF003258),
        primaryContainer = Color(0xFF004880), onPrimaryContainer = Color(0xFFCFE5FF),
        secondary = Color(0xFFA1D1AE), onSecondary = Color(0xFF0B3820),
        secondaryContainer = Color(0xFF2B5236), onSecondaryContainer = Color(0xFFCCE8D5),
        tertiary = Color(0xFFEFC84A), onTertiary = Color(0xFF382E00),
        tertiaryContainer = Color(0xFF514500), onTertiaryContainer = Color(0xFFF7E48B),
        background = Color(0xFF0D1A26), onBackground = Color(0xFFDDE3EA),
        surface = Color(0xFF15222F), onSurface = Color(0xFFDDE3EA),
        surfaceVariant = Color(0xFF2E3B46), onSurfaceVariant = Color(0xFFBDC8D1),
        outline = Color(0xFF8C9BAA), error = Color(0xFFFFB4AB), onError = Color(0xFF690005),
        errorContainer = Color(0xFF93000A), onErrorContainer = Color(0xFFFFDAD6),
    )
    AppTheme.LAVANDA -> darkColorScheme(
        primary = Color(0xFFD0BCFF), onPrimary = Color(0xFF381E72),
        primaryContainer = Color(0xFF4F378B), onPrimaryContainer = Color(0xFFEADDFF),
        secondary = Color(0xFFF1AADB), onSecondary = Color(0xFF5C1158),
        secondaryContainer = Color(0xFF75256E), onSecondaryContainer = Color(0xFFFFD7F2),
        tertiary = Color(0xFFF0BF48), onTertiary = Color(0xFF3E2E00),
        tertiaryContainer = Color(0xFF594300), onTertiaryContainer = Color(0xFFFFDEA0),
        background = Color(0xFF1C1B1F), onBackground = Color(0xFFE6E1E5),
        surface = Color(0xFF141218), onSurface = Color(0xFFE6E1E5),
        surfaceVariant = Color(0xFF49454F), onSurfaceVariant = Color(0xFFCAC4D0),
        outline = Color(0xFF938F99), error = Color(0xFFF2B8B5), onError = Color(0xFF601410),
        errorContainer = Color(0xFF8C1D18), onErrorContainer = Color(0xFFF9DEDC),
    )
    AppTheme.OLIVA -> darkColorScheme(
        primary = Color(0xFFAFCE8D), onPrimary = Color(0xFF203200),
        primaryContainer = Color(0xFF324700), onPrimaryContainer = Color(0xFFCAEAA6),
        secondary = Color(0xFFD9CA99), onSecondary = Color(0xFF3E2F00),
        secondaryContainer = Color(0xFF574500), onSecondaryContainer = Color(0xFFF6E5BB),
        tertiary = Color(0xFF79CEC8), onTertiary = Color(0xFF003734),
        tertiaryContainer = Color(0xFF1D4E4B), onTertiaryContainer = Color(0xFFBCECE8),
        background = Color(0xFF191D11), onBackground = Color(0xFFE2E5D7),
        surface = Color(0xFF1E2218), onSurface = Color(0xFFE2E5D7),
        surfaceVariant = Color(0xFF3D4436), onSurfaceVariant = Color(0xFFBFC8B4),
        outline = Color(0xFF8D9387), error = Color(0xFFFFB4AB), onError = Color(0xFF690005),
        errorContainer = Color(0xFF93000A), onErrorContainer = Color(0xFFFFDAD6),
    )
    AppTheme.CANELA -> darkColorScheme(
        primary = Color(0xFFF9BA82), onPrimary = Color(0xFF452200),
        primaryContainer = Color(0xFF623300), onPrimaryContainer = Color(0xFFFDDCBB),
        secondary = Color(0xFFDABDA0), onSecondary = Color(0xFF3D2B15),
        secondaryContainer = Color(0xFF554229), onSecondaryContainer = Color(0xFFF7DCBF),
        tertiary = Color(0xFFA4D18C), onTertiary = Color(0xFF1A380A),
        tertiaryContainer = Color(0xFF30521E), onTertiaryContainer = Color(0xFFCEEDB8),
        background = Color(0xFF201309), onBackground = Color(0xFFECDED4),
        surface = Color(0xFF281B0F), onSurface = Color(0xFFECDED4),
        surfaceVariant = Color(0xFF4E3B2C), onSurfaceVariant = Color(0xFFD3BFB2),
        outline = Color(0xFFA1887A), error = Color(0xFFFFB4AB), onError = Color(0xFF690005),
        errorContainer = Color(0xFF93000A), onErrorContainer = Color(0xFFFFDAD6),
    )
    AppTheme.MENTA -> darkColorScheme(
        primary = Color(0xFF80D8D0), onPrimary = Color(0xFF003732),
        primaryContainer = Color(0xFF00504A), onPrimaryContainer = Color(0xFFA7F3EB),
        secondary = Color(0xFFAED0CD), onSecondary = Color(0xFF1B3432),
        secondaryContainer = Color(0xFF314B49), onSecondaryContainer = Color(0xFFCCE8E5),
        tertiary = Color(0xFFB3D06F), onTertiary = Color(0xFF2B3D00),
        tertiaryContainer = Color(0xFF405700), onTertiaryContainer = Color(0xFFD9F0A0),
        background = Color(0xFF0F1F1E), onBackground = Color(0xFFDDE4E3),
        surface = Color(0xFF162625), onSurface = Color(0xFFDDE4E3),
        surfaceVariant = Color(0xFF3B4947), onSurfaceVariant = Color(0xFFBBC9C7),
        outline = Color(0xFF89A4A2), error = Color(0xFFFFB4AB), onError = Color(0xFF690005),
        errorContainer = Color(0xFF93000A), onErrorContainer = Color(0xFFFFDAD6),
    )
    AppTheme.FRAMBUESA -> darkColorScheme(
        primary = Color(0xFFFFB1C5), onPrimary = Color(0xFF5F0028),
        primaryContainer = Color(0xFF800040), onPrimaryContainer = Color(0xFFFFD9E3),
        secondary = Color(0xFFE6B7C2), onSecondary = Color(0xFF47252F),
        secondaryContainer = Color(0xFF603B45), onSecondaryContainer = Color(0xFFFFD9E3),
        tertiary = Color(0xFFF3BC44), onTertiary = Color(0xFF3F2D00),
        tertiaryContainer = Color(0xFF5B4100), onTertiaryContainer = Color(0xFFFFDE9D),
        background = Color(0xFF211018), onBackground = Color(0xFFEEDEE2),
        surface = Color(0xFF291520), onSurface = Color(0xFFEEDEE2),
        surfaceVariant = Color(0xFF503743), onSurfaceVariant = Color(0xFFD4BBC4),
        outline = Color(0xFFA98891), error = Color(0xFFF2B8B5), onError = Color(0xFF601410),
        errorContainer = Color(0xFF8C1D18), onErrorContainer = Color(0xFFF9DEDC),
    )
    AppTheme.NOCHE_VERANO -> darkColorScheme(
        primary = Color(0xFFADC8FF), onPrimary = Color(0xFF002D6E),
        primaryContainer = Color(0xFF0A449A), onPrimaryContainer = Color(0xFFD6E4FF),
        secondary = Color(0xFFBAC3FF), onSecondary = Color(0xFF222D62),
        secondaryContainer = Color(0xFF394479), onSecondaryContainer = Color(0xFFD8E2FF),
        tertiary = Color(0xFFFFBA52), onTertiary = Color(0xFF3F2D00),
        tertiaryContainer = Color(0xFF5B4100), onTertiaryContainer = Color(0xFFFFDE9D),
        background = Color(0xFF0E1118), onBackground = Color(0xFFE2E2E8),
        surface = Color(0xFF141823), onSurface = Color(0xFFE2E2E8),
        surfaceVariant = Color(0xFF33374A), onSurfaceVariant = Color(0xFFBDBFC9),
        outline = Color(0xFF8C8FA4), error = Color(0xFFFFB4AB), onError = Color(0xFF690005),
        errorContainer = Color(0xFF93000A), onErrorContainer = Color(0xFFFFDAD6),
    )
}

val AppTypography = Typography(
    displaySmall   = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Bold,    fontSize = 36.sp, lineHeight = 44.sp),
    headlineLarge  = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Bold,    fontSize = 32.sp, lineHeight = 40.sp),
    headlineMedium = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold, fontSize = 28.sp, lineHeight = 36.sp),
    headlineSmall  = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, lineHeight = 32.sp),
    titleLarge     = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp),
    titleMedium    = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 24.sp),
    titleSmall     = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
    bodyLarge      = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal,   fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium     = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal,   fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall      = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal,   fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge     = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium    = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, lineHeight = 16.sp),
    labelSmall     = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal,   fontSize = 11.sp, lineHeight = 16.sp),
)
