package app.template.patches.maps.navigation

import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.stringOption
import app.template.patches.shared.Constants.COMPATIBILITY_GOOGLE_MAPS
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction

@Suppress("unused")
val allowMorpheMusicPatch = bytecodePatch(
    name = "Allow Morphe YouTube Music mini player",
    description = "Allows modded YouTube Music (app.morphe.android.apps.youtube.music) as the navigation mini player.",
    default = true
) {
    compatibleWith(COMPATIBILITY_GOOGLE_MAPS)

    val targetPackage by stringOption(
        key = "targetPackage",
        default = "app.morphe.android.apps.youtube.music",
        title = "YouTube Music package name",
        description = "Package name of your modded YouTube Music app."
    )

    execute {
        val instructionMatch = NavigationMediaProvidersFingerprint.instructionMatches.first()
        val register = instructionMatch.getInstruction<OneRegisterInstruction>().registerA

        NavigationMediaProvidersFingerprint.method.replaceInstruction(
            instructionMatch.index,
            "const-string v$register, \"$targetPackage\""
        )
    }
}
