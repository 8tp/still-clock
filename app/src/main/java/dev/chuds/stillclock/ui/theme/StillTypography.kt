package dev.chuds.stillclock.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import dev.chuds.stillclock.data.FontPreset

/**
 * Concrete typography values for the active font preset. Read via [StillTypography] inside
 * a Composable; provide via [LocalStillTypography] at the composition root.
 *
 * Roles tuned for a clock surface:
 *   Kicker    — uppercase mono labels (the four bottom-tab labels, section accents)
 *   ClockFace — the live clock on the clock tab and the alarm-fires screen (huge serif,
 *               descended from still-launcher's Clock style and pushed larger)
 *   Time      — alarm rows, lap rows, the running timer/stopwatch readout (medium serif)
 *   Title     — labels in alarm rows, settings rows, button-row text
 *   Menu      — settings row labels (sans, light, large)
 *   Caption   — counts, mono accents
 *   Small     — secondary metadata (sans, small)
 */
data class StillTypographyValues(
    val Kicker: TextStyle,
    val ClockFace: TextStyle,
    val Time: TextStyle,
    val Title: TextStyle,
    val Menu: TextStyle,
    val Caption: TextStyle,
    val Small: TextStyle,
)

fun stillTypographyValues(
    serifFont: FontFamily,
    sansFont: FontFamily,
    monoFont: FontFamily,
): StillTypographyValues = StillTypographyValues(
    Kicker = TextStyle(
        fontFamily = monoFont,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 1.8.sp,
        fontWeight = FontWeight.Normal,
    ),
    ClockFace = TextStyle(
        fontFamily = serifFont,
        fontSize = 88.sp,
        lineHeight = 96.sp,
        letterSpacing = (-2.0).sp,
        fontWeight = FontWeight.Light,
    ),
    Time = TextStyle(
        fontFamily = serifFont,
        fontSize = 32.sp,
        lineHeight = 38.sp,
        letterSpacing = (-0.4).sp,
        fontWeight = FontWeight.Light,
    ),
    Title = TextStyle(
        fontFamily = sansFont,
        fontSize = 18.sp,
        lineHeight = 26.sp,
        letterSpacing = 0.2.sp,
        fontWeight = FontWeight.Normal,
    ),
    Menu = TextStyle(
        fontFamily = sansFont,
        fontSize = 22.sp,
        lineHeight = 32.sp,
        letterSpacing = 0.2.sp,
        fontWeight = FontWeight.Light,
    ),
    Caption = TextStyle(
        fontFamily = monoFont,
        fontSize = 12.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.7.sp,
        fontWeight = FontWeight.Normal,
    ),
    Small = TextStyle(
        fontFamily = sansFont,
        fontSize = 13.sp,
        lineHeight = 19.sp,
        letterSpacing = 0.2.sp,
        fontWeight = FontWeight.Light,
    ),
)

fun stillTypographyFor(preset: FontPreset): StillTypographyValues = when (preset) {
    FontPreset.System -> stillTypographyValues(
        serifFont = FontFamily.Serif,
        sansFont = FontFamily.SansSerif,
        monoFont = FontFamily.Monospace,
    )
    FontPreset.Editorial -> stillTypographyValues(
        serifFont = StillFontFamilies.CormorantGaramond,
        sansFont = StillFontFamilies.Inter,
        monoFont = StillFontFamilies.IbmPlexMono,
    )
    FontPreset.Terminal -> stillTypographyValues(
        serifFont = StillFontFamilies.IbmPlexMono,
        sansFont = StillFontFamilies.IbmPlexMono,
        monoFont = StillFontFamilies.IbmPlexMono,
    )
    FontPreset.Grotesk -> stillTypographyValues(
        serifFont = StillFontFamilies.InstrumentSerif,
        sansFont = StillFontFamilies.SpaceGrotesk,
        monoFont = StillFontFamilies.IbmPlexMono,
    )
}

val LocalStillTypography = staticCompositionLocalOf {
    stillTypographyFor(FontPreset.System)
}

object StillTypography {
    val Kicker: TextStyle
        @Composable @ReadOnlyComposable
        get() = LocalStillTypography.current.Kicker

    val ClockFace: TextStyle
        @Composable @ReadOnlyComposable
        get() = LocalStillTypography.current.ClockFace

    val Time: TextStyle
        @Composable @ReadOnlyComposable
        get() = LocalStillTypography.current.Time

    val Title: TextStyle
        @Composable @ReadOnlyComposable
        get() = LocalStillTypography.current.Title

    val Menu: TextStyle
        @Composable @ReadOnlyComposable
        get() = LocalStillTypography.current.Menu

    val Caption: TextStyle
        @Composable @ReadOnlyComposable
        get() = LocalStillTypography.current.Caption

    val Small: TextStyle
        @Composable @ReadOnlyComposable
        get() = LocalStillTypography.current.Small
}
