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

        // 1. Locate the AllowlistManager class from onGetRoot.
        // In YouTube Music, AllowlistManager is called with: (callerInfo, callerDetails, clientUid) -> boolean
        // That is: 3 parameters ending in "I" (UID check) returning "Z" (boolean).
        var allowlistClassName: String? = null
        for (i in 0 until impl.instructions.count()) {
            val insn = impl.instructions.elementAt(i)
            val ref = (insn as? ReferenceInstruction)?.reference as? MethodReference
            if (ref != null &&
                ref.returnType == "Z" &&
                ref.parameterTypes.size == 3 &&
                ref.parameterTypes[2] == "I"
            ) {
                allowlistClassName = ref.definingClass
                break
            }
        }

        if (allowlistClassName == null) {
            return@execute
        }

        // 2. In MusicBrowserService.onGetRoot:
        // Replace move-result with const/4 vReg, 0x1 ONLY for calls on AllowlistManager.
        // Do NOT touch other boolean checks (e.g. Android Auto car app checks, recents checks, etc.)
        for (i in 0 until impl.instructions.count()) {
            val insn = impl.instructions.elementAt(i)
            val ref = (insn as? ReferenceInstruction)?.reference as? MethodReference
            if (ref != null && ref.definingClass == allowlistClassName && ref.returnType == "Z") {
                if (i + 1 < impl.instructions.count()) {
                    val nextInsn = impl.instructions.elementAt(i + 1)
                    if (nextInsn.opcode == Opcode.MOVE_RESULT) {
                        val reg = (nextInsn as OneRegisterInstruction).registerA
                        method.replaceInstruction(i + 1, "const/4 v$reg, 0x1")
                    }
                }
            }
        }

        // 3. In AllowlistManager itself:
        // Force all its boolean verification methods (isAllowlistedForMediaBrowser, isBrowsable,
        // partner SHA checks, signature checks, etc.) to always return true (1).
        val allowlistClass = mutableClassDefByOrNull(allowlistClassName) ?: return@execute
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
