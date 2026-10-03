package app.template.patches.music

import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.template.patches.shared.Constants.COMPATIBILITY_MORPHE_YOUTUBE_MUSIC
import app.template.patches.shared.Constants.COMPATIBILITY_REVANCED_YOUTUBE_MUSIC
import app.template.patches.shared.Constants.COMPATIBILITY_YOUTUBE_MUSIC

@Suppress("unused")
val allowExternalMediaBrowserPatch = bytecodePatch(
    name = "Allow external media browser connections",
    description = "Allows Google Maps, Android Auto, and third-party media controllers to connect to YouTube Music.",
    default = true
) {
    compatibleWith(
        COMPATIBILITY_YOUTUBE_MUSIC,
        COMPATIBILITY_MORPHE_YOUTUBE_MUSIC,
        COMPATIBILITY_REVANCED_YOUTUBE_MUSIC
    )

    execute {
        val classDef = AllowlistManagerFingerprint.classDef

        // Patch all boolean methods in AllowlistManager (isAllowlistedForMediaBrowser, isBrowsable,
        // partner SHA checks, signature checks, etc.) to always return true (1).
        // This unconditionally accepts all connecting media clients like Google Maps and Android Auto.
        for (m in classDef.methods) {
            if (m.returnType == "Z") {
                val totalInsn = m.implementation?.instructions?.count() ?: 0
                if (totalInsn >= 2) {
                    for (i in 2 until totalInsn) {
                        m.replaceInstruction(i, "nop")
                    }
                    m.replaceInstruction(0, "const/4 v0, 0x1")
                    m.replaceInstruction(1, "return v0")
                }
            }
        }
    }
}
