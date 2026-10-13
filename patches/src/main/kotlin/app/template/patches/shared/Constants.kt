package app.template.patches.shared

import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.template.patches.shared.StandardizedUIComponents.GRID_3X3
import app.template.patches.shared.StandardizedUIComponents.GRID_4X4

object Constants {
    val COMPATIBILITY_GOOGLE_MAPS = Compatibility(
        name = "Google Maps",
        packageName = "com.google.android.apps.maps",
        appIconColor = 0x1A73E8,
        targets = listOf(
            AppTarget(
                version = "26.36.04.973607363"
            )
        )
    )

    val COMPATIBILITY_YOUTUBE_MUSIC = Compatibility(
        name = "YouTube Music",
        packageName = "com.google.android.apps.youtube.music",
        appIconColor = 0xFF0000
    )
    
    // Standardized grid configurations
    val STANDARD_GRID_CONFIGURATIONS = mapOf(
        GRID_3X3 to 3,
        GRID_4X4 to 4
    )
}

