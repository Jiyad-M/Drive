package com.example.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

data class WallpaperOption(
    val id: String,
    val name: String,
    val previewColors: List<Color>,
    val brush: Brush
)

object WallpaperProvider {
    val options = listOf(
        WallpaperOption(
            id = "map",
            name = "Live Road Map (Default)",
            previewColors = listOf(Color(0xFF0284C7), Color(0xFF0F172A)),
            brush = Brush.linearGradient(
                colors = listOf(
                    Color(0xFF070A13),
                    Color(0xFF0B192C),
                    Color(0xFF0F2A4A),
                    Color(0xFF070A13)
                )
            )
        ),
        WallpaperOption(
            id = "carbon",
            name = "Carbon Sport",
            previewColors = listOf(Color(0xFF0F172A), Color(0xFF1E293B)),
            brush = Brush.linearGradient(
                colors = listOf(
                    Color(0xFF0B0F19),
                    Color(0xFF0F172A),
                    Color(0xFF1E293B),
                    Color(0xFF0B0F19)
                )
            )
        ),
        WallpaperOption(
            id = "midnight",
            name = "Midnight Cobalt",
            previewColors = listOf(Color(0xFF021B38), Color(0xFF0369A1)),
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color(0xFF020E22),
                    Color(0xFF03284A),
                    Color(0xFF0284C7).copy(alpha = 0.4f),
                    Color(0xFF020E22)
                )
            )
        ),
        WallpaperOption(
            id = "amber",
            name = "Cyber Amber",
            previewColors = listOf(Color(0xFF2E1502), Color(0xFFD97706)),
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color(0xFF1C0E05),
                    Color(0xFF381B09),
                    Color(0xFFB45309).copy(alpha = 0.3f),
                    Color(0xFF1C0E05)
                )
            )
        ),
        WallpaperOption(
            id = "emerald",
            name = "Racing Emerald",
            previewColors = listOf(Color(0xFF062319), Color(0xFF059669)),
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color(0xFF041811),
                    Color(0xFF063525),
                    Color(0xFF10B981).copy(alpha = 0.3f),
                    Color(0xFF041811)
                )
            )
        ),
        WallpaperOption(
            id = "sunset",
            name = "Twilight Red",
            previewColors = listOf(Color(0xFF280718), Color(0xFFE11D48)),
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color(0xFF170410),
                    Color(0xFF3B0B23),
                    Color(0xFFE11D48).copy(alpha = 0.35f),
                    Color(0xFF170410)
                )
            )
        )
    )

    fun getBrushForTheme(themeId: String, customColor: Long = 0xFF0B132BL): Brush {
        val found = options.find { it.id == themeId }
        if (found != null) return found.brush

        // Fallback or custom
        val baseColor = Color(customColor)
        return Brush.verticalGradient(
            colors = listOf(
                baseColor.copy(alpha = 0.8f),
                baseColor,
                Color(0xFF0B0F19)
            )
        )
    }
}
