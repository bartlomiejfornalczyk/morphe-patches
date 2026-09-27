package app.template.patches.maps.navigation

import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.resourcePatch
import app.morphe.patcher.patch.stringOption
import app.template.patches.shared.Constants.COMPATIBILITY_GOOGLE_MAPS
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import org.w3c.dom.Element

val allowMorpheMusicManifestPatch = resourcePatch(
    name = "Allow Morphe YouTube Music package visibility",
    description = "Adds Morphe YouTube Music to queries in AndroidManifest.xml for Android 11+ visibility.",
    default = true
) {
    compatibleWith(COMPATIBILITY_GOOGLE_MAPS)

    execute {
        document("AndroidManifest.xml").use { doc ->
            val queriesNodes = doc.getElementsByTagName("queries")
            if (queriesNodes.length > 0) {
                val queries = queriesNodes.item(0) as Element
                
                // Add Morphe YouTube Music
                val pkgMorphe = doc.createElement("package")
                pkgMorphe.setAttribute("android:name", "app.morphe.android.apps.youtube.music")
                queries.appendChild(pkgMorphe)

                // Also add ReVanced YouTube Music package for compatibility
                val pkgRevanced = doc.createElement("package")
                pkgRevanced.setAttribute("android:name", "app.revanced.android.apps.youtube.music")
                queries.appendChild(pkgRevanced)
            }
        }
    }
}

@Suppress("unused")
val allowMorpheMusicPatch = bytecodePatch(
    name = "Allow Morphe YouTube Music mini player",
    description = "Allows modded YouTube Music (app.morphe.android.apps.youtube.music) as the navigation mini player.",
    default = true
) {
    compatibleWith(COMPATIBILITY_GOOGLE_MAPS)
    dependsOn(allowMorpheMusicManifestPatch)

    val targetPackage by stringOption(
        key = "targetPackage",
        default = "app.morphe.android.apps.youtube.music",
        title = "YouTube Music package name",
        description = "Package name of your modded YouTube Music app."
    )

    execute {
        // 1. Force the Phenotype media feature flag (apww.l()) to always return true (1).
        // This is necessary because cloned Google Maps (different package name) fails to sync
        // Phenotype server flags, which otherwise causes Maps to hide the media playback
        // preference and return an empty media providers list.
        val mediaClass = MediaControllerFingerprint.classDef
        val flagMethod = mediaClass.methods.firstOrNull { it.name == "l" && it.returnType == "Z" }
        if (flagMethod != null) {
            flagMethod.replaceInstruction(0, "const/4 v0, 0x1")
            flagMethod.replaceInstruction(1, "return v0")
        }

        // 2. Replace the YouTube Music package name string with the target package in navigation media resolution (xzt.ux())
        val instructionMatch = NavigationMediaProvidersFingerprint.instructionMatches.first()
        val register = instructionMatch.getInstruction<OneRegisterInstruction>().registerA

        NavigationMediaProvidersFingerprint.method.replaceInstruction(
            instructionMatch.index,
            "const-string v$register, \"$targetPackage\""
        )

        // 3. Bypass the server-side feature flag check (if-eqz v4, :cond_203)
        // This ensures YouTube Music is always added to the allowed media list even if disabled by Google config
        for (i in 1..8) {
            val checkIndex = instructionMatch.index - i
            if (checkIndex >= 0) {
                val insn = NavigationMediaProvidersFingerprint.method.getInstruction(checkIndex)
                if (insn.opcode == Opcode.IF_EQZ) {
                    NavigationMediaProvidersFingerprint.method.replaceInstruction(
                        checkIndex,
                        "nop"
                    )
                    break
                }
            }
        }
    }
}
