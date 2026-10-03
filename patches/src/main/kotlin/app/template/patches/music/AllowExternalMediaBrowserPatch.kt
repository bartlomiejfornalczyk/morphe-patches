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

        // 1. Discover the gatekeeper classes and methods called by onGetRoot:
        // - AllowlistManager (gzj): called with (callerPackage, callerDetails, int clientUid) -> Z
        // - callerDetails (yyx): 2nd parameter of AllowlistManager call; MUST be excluded from gate classes
        //   so Android Auto / CarApp checks (yyx.equals) are preserved without triggering crashes.
        // - Browsable method: method on AllowlistManager with (callerDetails) -> Z
        // - Zero-arg gatekeeper classes (gzw, jff): zero-arg methods returning Z (entitlement and browse gates)
        var allowlistClassName: String? = null
        var callerDetailsClassName: String? = null
        var browsableMethodName: String? = null
        val zeroArgGateClasses = mutableSetOf<String>()

        for (i in 0 until impl.instructions.count()) {
            val insn = impl.instructions.elementAt(i)
            val ref = (insn as? ReferenceInstruction)?.reference as? MethodReference ?: continue
            if (ref.returnType == "Z") {
                if (ref.parameterTypes.size == 3 && ref.parameterTypes[2] == "I") {
                    allowlistClassName = ref.definingClass
                    callerDetailsClassName = ref.parameterTypes[1].toString()
                    break
                }
            }
        }

        for (i in 0 until impl.instructions.count()) {
            val insn = impl.instructions.elementAt(i)
            val ref = (insn as? ReferenceInstruction)?.reference as? MethodReference ?: continue
            if (ref.returnType == "Z") {
                if (ref.parameterTypes.isEmpty() &&
                    !ref.definingClass.startsWith("Ljava/") &&
                    !ref.definingClass.startsWith("Landroid/") &&
                    !ref.definingClass.startsWith("Lj$/") &&
                    ref.definingClass != callerDetailsClassName
                ) {
                    zeroArgGateClasses.add(ref.definingClass)
                }
                if (allowlistClassName != null && ref.definingClass == allowlistClassName && ref.parameterTypes.size == 1) {
                    browsableMethodName = ref.name
                }
            }
        }

        val targetClasses = mutableSetOf<String>()
        if (allowlistClassName != null) targetClasses.add(allowlistClassName)
        targetClasses.addAll(zeroArgGateClasses)

        // 2. In MusicBrowserService.onGetRoot:
        // Force the result of all allowlist, browsable, and entitlement checks to true (1).
        for (i in 0 until impl.instructions.count()) {
            val insn = impl.instructions.elementAt(i)
            val ref = (insn as? ReferenceInstruction)?.reference as? MethodReference
            if (ref != null && ref.definingClass in targetClasses && ref.returnType == "Z") {
                if (i + 1 < impl.instructions.count()) {
                    val nextInsn = impl.instructions.elementAt(i + 1)
                    if (nextInsn.opcode == Opcode.MOVE_RESULT) {
                        val reg = (nextInsn as OneRegisterInstruction).registerA
                        method.replaceInstruction(i + 1, "const/4 v$reg, 0x1")
                    }
                }
            }
        }

        // 3. Patch the browsable method on AllowlistManager (gzj.h(yyx)) to always return true (1).
        // This method has 0 try blocks and is called by onLoadChildren (gzw.c) and onSearch (gzw.d).
        if (allowlistClassName != null && browsableMethodName != null) {
            mutableClassDefByOrNull(allowlistClassName)?.let { allowClass ->
                allowClass.methods.firstOrNull { it.name == browsableMethodName && it.returnType == "Z" }?.let { bMethod ->
                    val bImpl = bMethod.implementation
                    if (bImpl != null && bImpl.tryBlocks.isEmpty()) {
                        val count = bImpl.instructions.count()
                        bMethod.replaceInstruction(0, "const/4 v0, 0x1")
                        bMethod.replaceInstruction(1, "return v0")
                        for (idx in 2 until count) {
                            bMethod.replaceInstruction(idx, "nop")
                        }
                    }
                }
            }
        }

        // 4. Patch zero-arg entitlement gate methods (gzw.g() and jff.D()) to always return true (1).
        // CRITICAL: gzw.g() has 0 try blocks and normally calls gxr.c() on non-premium users, which
        // pushes an error state (PlaybackState.STATE_ERROR = 7) onto the MediaSession, causing
        // Google Maps to report "Unable to connect". Replacing its body with `return 1` prevents the
        // error state from ever being set and unconditionally grants browsing access to Google Maps.
        for (gateClass in zeroArgGateClasses) {
            mutableClassDefByOrNull(gateClass)?.let { clazz ->
                for (m in clazz.methods) {
                    if (m.returnType == "Z" && m.parameterTypes.isEmpty()) {
                        val mImpl = m.implementation ?: continue
                        if (mImpl.tryBlocks.isEmpty()) {
                            val count = mImpl.instructions.count()
                            if (count >= 2) {
                                m.replaceInstruction(0, "const/4 v0, 0x1")
                                m.replaceInstruction(1, "return v0")
                                for (idx in 2 until count) {
                                    m.replaceInstruction(idx, "nop")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
