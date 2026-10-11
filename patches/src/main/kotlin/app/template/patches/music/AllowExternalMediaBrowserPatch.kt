package app.template.patches.music

import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.template.patches.shared.Constants.COMPATIBILITY_YOUTUBE_MUSIC
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference

@Suppress("unused")
val allowExternalMediaBrowserPatch = bytecodePatch(
    name = "Allow external media browser connections",
    description = "Allows Google Maps, Android Auto, and third-party media controllers to connect to YouTube Music.",
    default = true
) {
    compatibleWith(
        COMPATIBILITY_YOUTUBE_MUSIC
    )

    execute {
        // 1. Locate MusicBrowserService.onGetRoot, onLoadChildren, and onSearch service methods:
        val mbsClass = mutableClassDefByOrNull("Lcom/google/android/apps/youtube/music/mediabrowser/MusicBrowserService;")
            ?: classDefByOrNull { it.type.endsWith("/MusicBrowserService;") }?.let { mutableClassDefBy(it.type) }
            ?: return@execute

        val onGetRootMethod = MusicBrowserServiceFingerprint.methodOrNull
            ?: mbsClass.methods.firstOrNull { m ->
                val pCount = m.parameterTypes.size
                (pCount == 2 || pCount == 3) &&
                    m.parameterTypes[0] == "Ljava/lang/String;" &&
                    m.parameterTypes[pCount - 1] == "Landroid/os/Bundle;" &&
                    m.returnType != "V" &&
                    m.returnType != "I"
            }
            ?: return@execute

        val onLoadChildrenServiceMethod = mbsClass.methods.firstOrNull { m ->
            val pCount = m.parameterTypes.size
            (pCount == 2 || pCount == 3) &&
                m.parameterTypes[0] == "Ljava/lang/String;" &&
                m.parameterTypes[pCount - 1] == "Landroid/os/Bundle;" &&
                m.returnType == "V"
        }

        val onSearchServiceMethod = mbsClass.methods.firstOrNull { m ->
            val pCount = m.parameterTypes.size
            pCount == 3 &&
                m.parameterTypes[0] == "Ljava/lang/String;" &&
                m.parameterTypes[1] == "Landroid/os/Bundle;" &&
                m.returnType == "V"
        }

        val onGetRootImpl = onGetRootMethod.implementation ?: return@execute

        // 2. Discover AllowlistManager and CallerDetails classes from onGetRoot:
        var notAllowlistedIdx = -1
        for (i in 0 until onGetRootImpl.instructions.count()) {
            val insn = onGetRootImpl.instructions.elementAt(i)
            val str = ((insn as? ReferenceInstruction)?.reference as? StringReference)?.string
            if (str != null && str.contains("Client not allowlisted")) {
                notAllowlistedIdx = i
                break
            }
        }

        var allowlistClassName: String? = null
        var callerDetailsClassName: String? = null
        val searchLimit = if (notAllowlistedIdx != -1) notAllowlistedIdx else onGetRootImpl.instructions.count()
        for (i in 0 until searchLimit) {
            val insn = onGetRootImpl.instructions.elementAt(i)
            val ref = (insn as? ReferenceInstruction)?.reference as? MethodReference ?: continue
            if (ref.returnType == "Z" &&
                ref.parameterTypes.isNotEmpty() &&
                !ref.definingClass.startsWith("Ljava/") &&
                !ref.definingClass.startsWith("Landroid/")
            ) {
                allowlistClassName = ref.definingClass
                for (p in ref.parameterTypes) {
                    val pStr = p.toString()
                    if (pStr != "I" && pStr != "Ljava/lang/String;" && !pStr.startsWith("Landroid/")) {
                        callerDetailsClassName = pStr
                    }
                }
            }
        }

        // 3. Discover zero-arg gatekeeper classes and browsable method on AllowlistManager:
        val zeroArgGateClasses = mutableSetOf<String>()
        var browsableMethodName: String? = null
        for (i in 0 until onGetRootImpl.instructions.count()) {
            val insn = onGetRootImpl.instructions.elementAt(i)
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

        // 4. Discover MusicBrowserController from onLoadChildrenService:
        var controllerClassName: String? = null
        var controllerOnLoadChildrenName: String? = null
        if (onLoadChildrenServiceMethod != null) {
            val serviceImpl = onLoadChildrenServiceMethod.implementation
            if (serviceImpl != null) {
                for (i in 0 until serviceImpl.instructions.count()) {
                    val insn = serviceImpl.instructions.elementAt(i)
                    val ref = (insn as? ReferenceInstruction)?.reference as? MethodReference ?: continue
                    if (ref.returnType == "V" && ref.parameterTypes.size == 1 &&
                        ref.definingClass in zeroArgGateClasses
                    ) {
                        controllerClassName = ref.definingClass
                        controllerOnLoadChildrenName = ref.name
                        break
                    }
                }
            }
        }

        var controllerOnSearchName: String? = null
        if (controllerClassName != null && onSearchServiceMethod != null) {
            val searchServiceImpl = onSearchServiceMethod.implementation
            if (searchServiceImpl != null) {
                for (i in 0 until searchServiceImpl.instructions.count()) {
                    val insn = searchServiceImpl.instructions.elementAt(i)
                    val ref = (insn as? ReferenceInstruction)?.reference as? MethodReference ?: continue
                    if (ref.definingClass == controllerClassName && ref.returnType == "V" && ref.parameterTypes.size == 1) {
                        controllerOnSearchName = ref.name
                        break
                    }
                }
            }
        }

        val targetGateClasses = mutableSetOf<String>()
        if (allowlistClassName != null) targetGateClasses.add(allowlistClassName)
        targetGateClasses.addAll(zeroArgGateClasses)

        // 5. In MusicBrowserService.onGetRoot:
        // Force the result of all allowlist, browsable, and entitlement checks to true (1).
        for (i in 0 until onGetRootImpl.instructions.count()) {
            val insn = onGetRootImpl.instructions.elementAt(i)
            val ref = (insn as? ReferenceInstruction)?.reference as? MethodReference
            if (ref != null && ref.definingClass in targetGateClasses && ref.returnType == "Z") {
                if (i + 1 < onGetRootImpl.instructions.count()) {
                    val nextInsn = onGetRootImpl.instructions.elementAt(i + 1)
                    if (nextInsn.opcode == Opcode.MOVE_RESULT) {
                        val reg = (nextInsn as OneRegisterInstruction).registerA
                        onGetRootMethod.replaceInstruction(i + 1, "const/4 v$reg, 0x1")
                    }
                }
            }
        }

        // 6. In AllowlistManager:
        // Force isBrowsable (h), isClientAllowlisted (g), and any boolean gate methods without try blocks to return 1.
        // Also force consent query method (k) to return 0 so user consent is never required.
        if (allowlistClassName != null) {
            mutableClassDefByOrNull(allowlistClassName)?.let { allowClass ->
                for (m in allowClass.methods) {
                    if (m.returnType == "Z") {
                        val mImpl = m.implementation ?: continue
                        if (mImpl.tryBlocks.isEmpty()) {
                            val count = mImpl.instructions.count()
                            if (count >= 2) {
                                val isConsent = m.parameterTypes.size == 1 && m.name != browsableMethodName
                                val returnVal = if (isConsent) "0x0" else "0x1"
                                m.replaceInstruction(0, "const/4 v0, $returnVal")
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

        // 7. In Zero-arg Gatekeeper classes (kzf.g entitlement gate, khr.e premium check):
        // Replace their bodies with `return 1` to prevent MediaSession error state and grant access.
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

        // 8. In MusicBrowserController (kzf):
        if (controllerClassName != null) {
            mutableClassDefByOrNull(controllerClassName)?.let { ctrlClass ->
                // A) Make consent check method (kzf.l) always return false (0):
                for (m in ctrlClass.methods) {
                    if (m.returnType == "Z" && m.parameterTypes.size == 1 && m.implementation?.tryBlocks?.isEmpty() == true) {
                        val mImpl = m.implementation ?: continue
                        val count = mImpl.instructions.count()
                        if (count >= 2) {
                            m.replaceInstruction(0, "const/4 v0, 0x0")
                            m.replaceInstruction(1, "return v0")
                            for (idx in 2 until count) {
                                m.replaceInstruction(idx, "nop")
                            }
                        }
                    }
                }

                // B) In controller.onLoadChildren (kzf.c):
                // - Force user signed-in check (avtj.v()) to true (1) so signed-out / MicroG users can browse.
                // - Force allowlist browsable check (kxo.h) to true (1).
                // - Force empty root check (TextUtils.equals "__EMPTY_ROOT_ID__") to false (0).
                // - Force consent check (kzf.l) to false (0) so children are immediately loaded.
                ctrlClass.methods.firstOrNull { it.name == controllerOnLoadChildrenName }?.let { cMethod ->
                    val cImpl = cMethod.implementation ?: return@let
                    val count = cImpl.instructions.count()
                    for (i in 0 until count) {
                        val insn = cImpl.instructions.elementAt(i)
                        val ref = (insn as? ReferenceInstruction)?.reference
                        if (ref is MethodReference) {
                            if (ref.returnType == "Z" && i + 1 < count) {
                                val next = cImpl.instructions.elementAt(i + 1)
                                if (next.opcode == Opcode.MOVE_RESULT) {
                                    val reg = (next as OneRegisterInstruction).registerA
                                    // 1) signed-in check (avtj.v() taking 0 args in first 100 instructions)
                                    if (ref.parameterTypes.isEmpty() && i in 50..95) {
                                        cMethod.replaceInstruction(i + 1, "const/4 v$reg, 0x1")
                                    }
                                    // 2) allowlist browsable check (kxo.h)
                                    else if (allowlistClassName != null && ref.definingClass == allowlistClassName) {
                                        cMethod.replaceInstruction(i + 1, "const/4 v$reg, 0x1")
                                    }
                                    // 3) empty root check (TextUtils.equals "__EMPTY_ROOT_ID__")
                                    else if (ref.name == "equals" && i > 0) {
                                        val prevInsn = cImpl.instructions.elementAt(i - 1)
                                        val prevStr = ((prevInsn as? ReferenceInstruction)?.reference as? StringReference)?.string
                                        if (prevStr == "__EMPTY_ROOT_ID__") {
                                            cMethod.replaceInstruction(i + 1, "const/4 v$reg, 0x0")
                                        }
                                    }
                                    // 4) controller consent check (kzf.l)
                                    else if (ref.definingClass == controllerClassName) {
                                        cMethod.replaceInstruction(i + 1, "const/4 v$reg, 0x0")
                                    }
                                }
                            }
                        }
                    }
                }

                // C) In controller.onSearch (kzf.d):
                if (controllerOnSearchName != null) {
                    ctrlClass.methods.firstOrNull { it.name == controllerOnSearchName }?.let { sMethod ->
                        val sImpl = sMethod.implementation ?: return@let
                        val count = sImpl.instructions.count()
                        for (i in 0 until count) {
                            val insn = sImpl.instructions.elementAt(i)
                            val ref = (insn as? ReferenceInstruction)?.reference as? MethodReference ?: continue
                            if (ref.returnType == "Z" && i + 1 < count) {
                                val next = sImpl.instructions.elementAt(i + 1)
                                if (next.opcode == Opcode.MOVE_RESULT) {
                                    val reg = (next as OneRegisterInstruction).registerA
                                    if (allowlistClassName != null && ref.definingClass == allowlistClassName) {
                                        sMethod.replaceInstruction(i + 1, "const/4 v$reg, 0x1")
                                    } else if (ref.definingClass == controllerClassName) {
                                        sMethod.replaceInstruction(i + 1, "const/4 v$reg, 0x0")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
