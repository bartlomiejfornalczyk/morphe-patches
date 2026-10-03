package app.template.patches.music

import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.template.patches.shared.Constants.COMPATIBILITY_MORPHE_YOUTUBE_MUSIC
import app.template.patches.shared.Constants.COMPATIBILITY_REVANCED_YOUTUBE_MUSIC
import app.template.patches.shared.Constants.COMPATIBILITY_YOUTUBE_MUSIC
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

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
        val method = MusicBrowserServiceFingerprint.methodOrNull
            ?: classDefByOrNull { it.type.endsWith("/MusicBrowserService;") }
                ?.methods?.firstOrNull {
                    it.parameterTypes.size == 3 &&
                        it.parameterTypes[0] == "Ljava/lang/String;" &&
                        it.parameterTypes[1] == "I" &&
                        it.parameterTypes[2] == "Landroid/os/Bundle;"
                }
                ?.let { fallbackMethod ->
                    mutableClassDefBy(fallbackMethod.definingClass).methods.first { m ->
                        m.name == fallbackMethod.name && m.parameterTypes == fallbackMethod.parameterTypes
                    }
                }
            ?: return@execute

        val impl = method.implementation ?: return@execute
        val allowlistClassNames = mutableSetOf<String>()

        // 1. In MusicBrowserService.onGetRoot:
        // Force the results of all boolean verification checks (isAllowlistedForMediaBrowser, isBrowsable)
        // to 1 (true) by replacing the move-result instruction following their invocation.
        for (i in 0 until impl.instructions.count()) {
            val insn = impl.instructions.elementAt(i)
            val ref = (insn as? ReferenceInstruction)?.reference as? MethodReference
            if (ref != null && ref.returnType == "Z") {
                allowlistClassNames.add(ref.definingClass)

                if (i + 1 < impl.instructions.count()) {
                    val nextInsn = impl.instructions.elementAt(i + 1)
                    if (nextInsn.opcode == Opcode.MOVE_RESULT) {
                        val reg = (nextInsn as OneRegisterInstruction).registerA
                        method.replaceInstruction(i + 1, "const/4 v$reg, 0x1")
                    }
                }
            }
        }

        // 2. Also patch the AllowlistManager class directly:
        // Force all its boolean methods (UID check, package name allowlist, browsable check, etc.)
        // to return true (1).
        for (className in allowlistClassNames) {
            val allowlistClass = mutableClassDefByOrNull(className) ?: continue
            for (m in allowlistClass.methods) {
                if (m.returnType == "Z") {
                    val totalInsn = m.implementation?.instructions?.count() ?: 0
                    if (totalInsn >= 2) {
                        for (idx in 2 until totalInsn) {
                            m.replaceInstruction(idx, "nop")
                        }
                        m.replaceInstruction(0, "const/4 v0, 0x1")
                        m.replaceInstruction(1, "return v0")
                    }
                }
            }
        }
    }
}
