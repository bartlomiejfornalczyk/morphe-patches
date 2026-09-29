package app.template.patches.maps.navigation

import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.resourcePatch
import app.morphe.patcher.patch.stringOption
import app.template.patches.shared.Constants.COMPATIBILITY_GOOGLE_MAPS
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference
import org.w3c.dom.Element

val allowMorpheMusicManifestPatch = resourcePatch(
    name = "Allow Morphe YouTube Music package visibility",
    description = "Adds package queries and permission to AndroidManifest.xml for full media apps visibility.",
    default = true
) {
    compatibleWith(COMPATIBILITY_GOOGLE_MAPS)

    execute {
        document("AndroidManifest.xml").use { doc ->
            val manifest = doc.documentElement

            // 1. Grant QUERY_ALL_PACKAGES so Android OS never hides any media apps
            val queryAllPerm = doc.createElement("uses-permission")
            queryAllPerm.setAttribute("android:name", "android.permission.QUERY_ALL_PACKAGES")
            manifest.appendChild(queryAllPerm)

            // 2. Add queries for all YouTube Music variants + generic MediaBrowserService intent
            val queriesNodes = doc.getElementsByTagName("queries")
            val queries: Element = if (queriesNodes.length > 0) {
                queriesNodes.item(0) as Element
            } else {
                val newQueries = doc.createElement("queries")
                manifest.appendChild(newQueries)
                newQueries
            }

            val packagesToAdd = listOf(
                "app.morphe.android.apps.youtube.music",
                "app.revanced.android.apps.youtube.music",
                "app.rvx.android.apps.youtube.music",
                "com.google.android.apps.youtube.music",
                "com.spotify.music"
            )

            for (pkg in packagesToAdd) {
                val pkgElement = doc.createElement("package")
                pkgElement.setAttribute("android:name", pkg)
                queries.appendChild(pkgElement)
            }

            // Also add generic MediaBrowserService intent filter query
            val intentElem = doc.createElement("intent")
            val actionElem = doc.createElement("action")
            actionElem.setAttribute("android:name", "android.media.browse.MediaBrowserService")
            intentElem.appendChild(actionElem)
            queries.appendChild(intentElem)
        }
    }
}

@Suppress("unused")
val allowMorpheMusicPatch = bytecodePatch(
    name = "Allow Morphe YouTube Music mini player",
    description = "Enables YouTube Music and modded media apps as the navigation mini player.",
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
        // 1. Patch apww.l() so the feature flag always returns true (1)
        // Only use replaceInstruction - never addInstructions (shifts branch offsets = startup crash)
        val mediaClass = MediaControllerFingerprint.classDef
        val flagMethod = mediaClass.methods.firstOrNull { it.name == "l" && it.returnType == "Z" }
        if (flagMethod != null) {
            val totalInsn = flagMethod.implementation?.instructions?.count() ?: 0
            for (i in 2 until totalInsn) {
                flagMethod.replaceInstruction(i, "nop")
            }
            flagMethod.replaceInstruction(0, "const/4 v0, 0x1")
            flagMethod.replaceInstruction(1, "return v0")
        }

        // 2. Patch navigation media provider resolution method (xzt.ux())
        val method = NavigationMediaProvidersFingerprint.method
        val impl = method.implementation!!

        val ytmMatch = NavigationMediaProvidersFingerprint.instructionMatches.first()
        val ytmIndex = ytmMatch.index
        val register = ytmMatch.getInstruction<OneRegisterInstruction>().registerA

        // 2a. Replace the YouTube Music package string with our target package
        method.replaceInstruction(
            ytmIndex,
            "const-string v$register, \"$targetPackage\""
        )

        // 2b. Replace "com.google.android.music" (Google Play Music) with an alternate package
        val altPackage = if (targetPackage == "app.morphe.android.apps.youtube.music") {
            "app.revanced.android.apps.youtube.music"
        } else {
            "app.morphe.android.apps.youtube.music"
        }

        for (i in ytmIndex downTo (ytmIndex - 25).coerceAtLeast(0)) {
            val insn = impl.instructions.elementAt(i)
            if ((insn as? ReferenceInstruction)?.reference?.let { (it as? StringReference)?.string == "com.google.android.music" } == true) {
                val reg = (insn as OneRegisterInstruction).registerA
                method.replaceInstruction(i, "const-string v$reg, \"$altPackage\"")

                // Bypass cpwy.b flag check: find the if-eqz and nop it (replaceInstruction only, no offset shift)
                for (j in i downTo (i - 10).coerceAtLeast(0)) {
                    val checkInsn = impl.instructions.elementAt(j)
                    if (checkInsn.opcode == Opcode.IF_EQZ) {
                        method.replaceInstruction(j, "nop")
                        break
                    }
                }
                break
            }
        }

        // 2c. Bypass cpwy.d flag check: find the if-eqz before ytmIndex and nop it
        for (i in ytmIndex downTo (ytmIndex - 10).coerceAtLeast(0)) {
            val insn = impl.instructions.elementAt(i)
            if (insn.opcode == Opcode.IF_EQZ) {
                method.replaceInstruction(i, "nop")
                break
            }
        }

        // 2d. Bypass apww.l() check before ytmIndex: replace move-result with const/4 0x1
        for (i in ytmIndex downTo (ytmIndex - 40).coerceAtLeast(0)) {
            val insn = impl.instructions.elementAt(i)
            if ((insn as? ReferenceInstruction)?.reference?.let { (it as? MethodReference)?.name == "l" && (it as? MethodReference)?.returnType == "Z" } == true) {
                val nextInsn = impl.instructions.elementAt(i + 1)
                if (nextInsn.opcode == Opcode.MOVE_RESULT) {
                    val reg = (nextInsn as OneRegisterInstruction).registerA
                    method.replaceInstruction(i + 1, "const/4 v$reg, 0x1")
                }
                break
            }
        }

        // 2e. Force queryIntentServices to use MATCH_ALL (0x20000) to fix Android 11+ visibility.
        // ONLY use replaceInstruction - the smali assembler allows multi-instruction replacement atomically.
        for (i in ytmIndex until impl.instructions.count()) {
            val insn = impl.instructions.elementAt(i)
            if ((insn as? ReferenceInstruction)?.reference?.let { (it as? MethodReference)?.name == "queryIntentServices" } == true) {
                val invokeInsn = insn as com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
                val pmReg = invokeInsn.registerC
                val intentReg = invokeInsn.registerD
                val flagsReg = invokeInsn.registerE

                // Atomic replacement: the smali assembler merges this into one instruction slot
                method.replaceInstruction(
                    i,
                    """
                    const v$flagsReg, 0x20000
                    invoke-virtual {v$pmReg, v$intentReg, v$flagsReg}, Landroid/content/pm/PackageManager;->queryIntentServices(Landroid/content/Intent;I)Ljava/util/List;
                    """.trimIndent()
                )
                break
            }
        }

        // 2f. Bypass apww.l() check after ytmIndex: replace if-eqz with nop
        for (i in ytmIndex until impl.instructions.count()) {
            val insn = impl.instructions.elementAt(i)
            if ((insn as? ReferenceInstruction)?.reference?.let { (it as? MethodReference)?.name == "l" && (it as? MethodReference)?.returnType == "Z" } == true) {
                val branchInsn = impl.instructions.elementAt(i + 2)
                if (branchInsn.opcode == Opcode.IF_EQZ) {
                    method.replaceInstruction(i + 2, "nop")
                }
                break
            }
        }
    }
}
