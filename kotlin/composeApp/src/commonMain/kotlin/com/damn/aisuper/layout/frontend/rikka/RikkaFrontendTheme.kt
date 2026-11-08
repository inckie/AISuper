package com.damn.aisuper.layout.frontend.rikka

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.damn.aisuper.layout.StyleSheet
import com.damn.aisuper.layout.parseColorOrNull
import zed.rainxch.rikkaui.foundation.RikkaAccentPreset
import zed.rainxch.rikkaui.foundation.RikkaPalette
import zed.rainxch.rikkaui.foundation.RikkaStylePreset
import zed.rainxch.rikkaui.foundation.RikkaTheme

@Composable
fun RikkaFrontendTheme(
    styleSheet: StyleSheet?,
    content: @Composable () -> Unit
) {
    val isDark = styleSheet?.scheme.equals("dark", ignoreCase = true)
    val accent = resolveRikkaAccentPreset(styleSheet?.tokens?.accentColor)

    RikkaTheme(
        palette = RikkaPalette.Zinc,
        accent = accent,
        isDark = isDark,
        preset = RikkaStylePreset.Default,
        content = content
    )
}

private fun resolveRikkaAccentPreset(hex: String?): RikkaAccentPreset {
    val color = parseColorOrNull(hex) ?: return RikkaAccentPreset.Default

    val presets = listOf(
        RikkaAccentPreset.Blue,
        RikkaAccentPreset.Green,
        RikkaAccentPreset.Orange,
        RikkaAccentPreset.Red,
        RikkaAccentPreset.Rose,
        RikkaAccentPreset.Violet,
        RikkaAccentPreset.Yellow
    )

    return presets.minByOrNull { preset ->
        val sample = preset.previewColor ?: return@minByOrNull Float.MAX_VALUE
        colorDistanceSquared(color, sample)
    } ?: RikkaAccentPreset.Default
}

private fun colorDistanceSquared(a: Color, b: Color): Float {
    val dr = a.red - b.red
    val dg = a.green - b.green
    val db = a.blue - b.blue
    return dr * dr + dg * dg + db * db
}

