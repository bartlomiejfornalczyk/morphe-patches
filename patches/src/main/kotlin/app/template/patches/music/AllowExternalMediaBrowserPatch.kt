package app.template.patches.music

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.template.patches.shared.Constants.COMPATIBILITY_YOUTUBE_MUSIC
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction35c
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction3rc
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
        val method = MusicBrowserServiceFingerprint.methodOrNull
            ?: classDefByOrNull { it.type.endsWith("/MusicBrowserService;") }
                ?.methods?.firstOrNull { m ->
                    val pCount = m.parameterTypes.size
                    (pCount == 2 || pCount == 3) &&
                        m.parameterTypes[0] == "Ljava/lang/String;" &&
                        m.parameterTypes[pCount - 1] == "Landroid/os/Bundle;" &&
                        m.returnType != "V" &&
                        m.returnType != "I"
                }
                ?.let { fallbackMethod ->
                    mutableClassDefBy(fallbackMethod.definingClass).methods.first { m ->
                        m.name == fallbackMethod.name && m.parameterTypes == fallbackMethod.parameterTypes
                    }
                }
            ?: return@execute

        val impl = method.implementation ?: return@execute

        // 1. Locate the AllowlistManager class from onGetRoot:
        // Find the index of string "Client not allowlisted" or "MBS: getRoot() failed."
        var notAllowlistedIdx = -1
        for (i in 0 until impl.instructions.count()) {
            val insn = impl.instructions.elementAt(i)
            val str = ((insn as? ReferenceInstruction)?.reference as? StringReference)?.string
            if (str != null && str.contains("Client not allowlisted")) {
                notAllowlistedIdx = i
                break
            }
        }

        var allowlistClassName: String? = null
        var callerDetailsClassName: String? = null
        var browsableMethodName: String? = null
        val zeroArgGateClasses = mutableSetOf<String>()

        // Find the boolean method invocation before the "Client not allowlisted" log
        val searchLimit = if (notAllowlistedIdx != -1) notAllowlistedIdx else impl.instructions.count()
        for (i in 0 until searchLimit) {
            val insn = impl.instructions.elementAt(i)
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

        // Find browsable method on AllowlistManager and all zero-arg gatekeeper classes
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

        // 3. Patch the browsable method on AllowlistManager (gzj.h / kxo.h) to always return true (1).
        // This method has 0 try blocks and is called by onLoadChildren and onSearch.
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

        // 4. Patch zero-arg entitlement gate methods (gzw.g / kzf.g and jff.D / khr.e) to always return true (1).
        // CRITICAL: Gatekeeper methods have 0 try blocks and normally call error reporting on non-premium users, which
        // pushes an error state (PlaybackState.STATE_ERROR = 7) onto the MediaSession, causing
        // Google Maps to report "Unable to connect". Replacing their bodies with `return 1` prevents the
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

        // 5. In MusicBrowserService.onGetRoot:
        // Set BrowserRoot ID to "__MY_RECENTS_ROOT_ID__" so connecting media clients (e.g. Google Maps)
        // receive a browse root that points directly to recents/playback instead of failing RPCs.
        val returnType = method.returnType
        var lastInitIdx = -1
        var rootIdReg = -1
        for (i in 0 until impl.instructions.count()) {
            val insn = impl.instructions.elementAt(i)
            if (insn is Instruction35c) {
                val ref = insn.reference as? MethodReference
                if (ref?.definingClass == returnType && ref.name == "<init>" &&
                    ref.parameterTypes.size == 2 && ref.parameterTypes[0] == "Ljava/lang/String;"
                ) {
                    lastInitIdx = i
                    rootIdReg = insn.registerD
                }
            }
        }
        if (lastInitIdx != -1 && rootIdReg != -1) {
            for (i in (lastInitIdx - 1) downTo 0) {
                val insn = impl.instructions.elementAt(i)
                if (insn.opcode == Opcode.IGET_OBJECT && (insn as? OneRegisterInstruction)?.registerA == rootIdReg) {
                    method.replaceInstruction(i, "const-string v$rootIdReg, \"__MY_RECENTS_ROOT_ID__\"")
                    break
                }
            }
        }

        // 6. In MusicBrowserController.onLoadChildren (kzf.c / gzw.c):
        // Allow Google Maps to enter recents builder block, force recents availability check to true (0),
        // and provide fallback Title/Subtitle so browse item is always playable.
        val controllerMethod = MusicBrowserControllerFingerprint.methodOrNull
        if (controllerMethod != null) {
            val cImpl = controllerMethod.implementation
            if (cImpl != null) {
                var recentsStringIdx = -1
                var placeholderIdx = -1
                for (i in 0 until cImpl.instructions.count()) {
                    val insn = cImpl.instructions.elementAt(i)
                    val str = ((insn as? ReferenceInstruction)?.reference as? StringReference)?.string
                    if (str == "__MY_RECENTS_ROOT_ID__") recentsStringIdx = i
                    if (str == "PLACEHOLDER_RECENTLY_PLAYED_MEDIA_ID") placeholderIdx = i
                }

                if (recentsStringIdx != -1 && placeholderIdx != -1) {
                    // Step A: Find MediaDescriptionCompat.<init> to identify title and subtitle registers
                    var mediaDescInitIdx = -1
                    var titleReg = -1
                    var subtitleReg = -1
                    for (i in placeholderIdx until (placeholderIdx + 10).coerceAtMost(cImpl.instructions.count())) {
                        val insn = cImpl.instructions.elementAt(i)
                        if (insn is Instruction3rc) {
                            val ref = insn.reference as? MethodReference
                            if (ref?.name == "<init>" && ref.definingClass.contains("MediaDescriptionCompat")) {
                                mediaDescInitIdx = i
                                titleReg = insn.startRegister + 2
                                subtitleReg = insn.startRegister + 3
                                break
                            }
                        }
                    }

                    // Step B: Find the recents-check move-result instruction between recentsStringIdx and placeholderIdx
                    var booleanCheckMoveResultIdx = -1
                    for (i in (recentsStringIdx + 3) until placeholderIdx) {
                        val insn = cImpl.instructions.elementAt(i)
                        val ref = (insn as? ReferenceInstruction)?.reference as? MethodReference
                        if (ref?.returnType == "Z") {
                            val next = cImpl.instructions.elementAt(i + 1)
                            if (next.opcode == Opcode.MOVE_RESULT) {
                                booleanCheckMoveResultIdx = i + 1
                                break
                            }
                        }
                    }

                    // Step C: Identify parentIdReg and resultReg from TextUtils.equals check
                    val insnInvoke = cImpl.instructions.elementAt(recentsStringIdx + 1) as? Instruction35c
                    val insnMove = cImpl.instructions.elementAt(recentsStringIdx + 2) as? OneRegisterInstruction
                    val parentIdReg = insnInvoke?.registerC
                    val resultReg = insnMove?.registerA

                    // Apply modifications back-to-front to preserve instruction indices
                    if (mediaDescInitIdx != -1 && titleReg != -1 && subtitleReg != -1) {
                        controllerMethod.addInstructions(
                            mediaDescInitIdx,
                            """
                                invoke-static { v$titleReg }, Landroid/text/TextUtils;->isEmpty(Ljava/lang/CharSequence;)Z
                                move-result v0
                                if-eqz v0, :cond_has_title
                                const-string v$titleReg, "YouTube Music"
                                const-string v$subtitleReg, "Resume playback"
                                :cond_has_title
                            """
                        )
                    }

                    if (booleanCheckMoveResultIdx != -1) {
                        val boolReg = (cImpl.instructions.elementAt(booleanCheckMoveResultIdx) as OneRegisterInstruction).registerA
                        controllerMethod.replaceInstruction(booleanCheckMoveResultIdx, "const/4 v$boolReg, 0x0")
                    }

                    if (parentIdReg != null && resultReg != null) {
                        controllerMethod.addInstructions(
                            recentsStringIdx + 3,
                            """
                                if-nez v$resultReg, :cond_recents
                                const-string v$resultReg, "com.google.android.apps.maps"
                                invoke-static { v$parentIdReg, v$resultReg }, Landroid/text/TextUtils;->equals(Ljava/lang/CharSequence;Ljava/lang/CharSequence;)Z
                                move-result v$resultReg
                                :cond_recents
                            """
                        )
                    }
                }
            }
        }

        // 7. In MusicMediaSessionPlayerController.playFromMediaItem (lbg.i / hak.h):
        // When boht/item is null (e.g. PLACEHOLDER_RECENTLY_PLAYED_MEDIA_ID clicked from Maps),
        // immediately delegate to onPlay() to unpause/start playback and return success.
        val playerMethod = MusicMediaSessionPlayerFingerprint.methodOrNull
        if (playerMethod != null) {
            val playerClass = mutableClassDefBy(playerMethod.definingClass)
            val onPlayMethod = playerClass.methods.firstOrNull { m ->
                m.parameterTypes.isEmpty() && m.returnType == playerMethod.returnType &&
                    m.implementation?.let { pImpl ->
                        var hasGetMode = false
                        var selfCalls = 0
                        for (insn in pImpl.instructions) {
                            val ref = (insn as? ReferenceInstruction)?.reference as? MethodReference
                            if (ref != null) {
                                if (ref.name == "getMode") hasGetMode = true
                                if (ref.definingClass == playerClass.type) selfCalls++
                            }
                        }
                        hasGetMode && selfCalls >= 4
                    } == true
            }

            if (onPlayMethod != null) {
                playerMethod.addInstructions(
                    0,
                    """
                        if-nez p1, :cond_has_boht
                        invoke-virtual { p0 }, ${playerClass.type}->${onPlayMethod.name}()${playerMethod.returnType}
                        move-result-object p1
                        return-object p1
                        :cond_has_boht
                    """
                )
            }
        }
    }
}

