package app.template.patches.music

import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.template.patches.shared.Constants.COMPATIBILITY_YOUTUBE_MUSIC
import com.android.tools.smali.dexlib2.AccessFlags
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

        // 5. In LoadChildrenResult (lkh.c):
        // When online browse items are empty or null (e.g. non-premium account or external client),
        // provide a set of playable MediaBrowserCompat$MediaItem entries so Google Maps navigation
        // browse view renders rich playable tiles instead of "Could not load content from YT Music Morphe".
        val lkhClass = LoadChildrenResultFingerprint.classDefOrNull
            ?: classDefByStrings("com/google/android/apps/youtube/music/mediabrowser/results/LoadChildrenResult").firstOrNull()
        if (lkhClass != null) {
            val lkhMutableClass = mutableClassDefBy(lkhClass)
            val lkhCMethod = lkhMutableClass.methods.firstOrNull { m ->
                m.parameterTypes.size == 2 &&
                    m.parameterTypes[0] == "Ljava/util/List;" &&
                    m.returnType == "V"
            }

            if (lkhCMethod != null) {
                if (lkhCMethod.implementation != null) {
                    // Discover the native media item encoder (mxt.g / ixx.g) if present
                    val mxtClass = MediaBrowserCustomActionsFingerprint.classDefOrNull
                        ?: classDefByOrNull { clazz ->
                            clazz.methods.any { m ->
                                m.name == "g" &&
                                    m.parameterTypes == listOf("Ljava/lang/String;") &&
                                    m.returnType == "Ljava/lang/String;" &&
                                    (m.accessFlags and AccessFlags.STATIC.value != 0)
                            }
                        }
                    val mxtEncodeMethod = MediaBrowserCustomActionsFingerprint.methodOrNull
                        ?: mxtClass?.methods?.firstOrNull { m ->
                            m.name == "g" &&
                                m.parameterTypes == listOf("Ljava/lang/String;") &&
                                m.returnType == "Ljava/lang/String;" &&
                                (m.accessFlags and AccessFlags.STATIC.value != 0)
                        }

                    val lmMediaIdSmali = if (mxtClass != null && mxtEncodeMethod != null) {
                        """
                            const-string v1, "LM"
                            invoke-static { v1 }, ${mxtClass.type}->${mxtEncodeMethod.name}(Ljava/lang/String;)Ljava/lang/String;
                            move-result-object v1
                        """.trimIndent()
                    } else {
                        """const-string v1, "MBS_ACTION_LIKED""""
                    }

                    val supermixMediaIdSmali = if (mxtClass != null && mxtEncodeMethod != null) {
                        """
                            const-string v1, "RDTM"
                            invoke-static { v1 }, ${mxtClass.type}->${mxtEncodeMethod.name}(Ljava/lang/String;)Ljava/lang/String;
                            move-result-object v1
                        """.trimIndent()
                    } else {
                        """const-string v1, "MBS_ACTION_SUPERMIX""""
                    }

                    val offlineMediaIdSmali = if (mxtClass != null && mxtEncodeMethod != null) {
                        """
                            const-string v1, "PPSV"
                            invoke-static { v1 }, ${mxtClass.type}->${mxtEncodeMethod.name}(Ljava/lang/String;)Ljava/lang/String;
                            move-result-object v1
                        """.trimIndent()
                    } else {
                        """const-string v1, "MBS_ACTION_OFFLINE""""
                    }

                    val discoverMediaIdSmali = if (mxtClass != null && mxtEncodeMethod != null) {
                        """
                            const-string v1, "RDEV"
                            invoke-static { v1 }, ${mxtClass.type}->${mxtEncodeMethod.name}(Ljava/lang/String;)Ljava/lang/String;
                            move-result-object v1
                        """.trimIndent()
                    } else {
                        """const-string v1, "MBS_ACTION_DISCOVER""""
                    }

                    val newReleaseMediaIdSmali = if (mxtClass != null && mxtEncodeMethod != null) {
                        """
                            const-string v1, "RDAMVM"
                            invoke-static { v1 }, ${mxtClass.type}->${mxtEncodeMethod.name}(Ljava/lang/String;)Ljava/lang/String;
                            move-result-object v1
                        """.trimIndent()
                    } else {
                        """const-string v1, "MBS_ACTION_NEW""""
                    }

                    val chartsMediaIdSmali = if (mxtClass != null && mxtEncodeMethod != null) {
                        """
                            const-string v1, "RDMM"
                            invoke-static { v1 }, ${mxtClass.type}->${mxtEncodeMethod.name}(Ljava/lang/String;)Ljava/lang/String;
                            move-result-object v1
                        """.trimIndent()
                    } else {
                        """const-string v1, "MBS_ACTION_CHARTS""""
                    }

                    fun makeItemSmali(mediaIdSmali: String, title: String, subtitle: String): String = """
                        $mediaIdSmali
                        new-instance v0, Landroid/support/v4/media/MediaDescriptionCompat;
                        const-string v2, "$title"
                        const-string v3, "$subtitle"
                        const/4 v4, 0x0
                        const/4 v5, 0x0
                        const/4 v6, 0x0
                        const/4 v7, 0x0
                        const/4 v8, 0x0
                        invoke-direct/range { v0 .. v8 }, Landroid/support/v4/media/MediaDescriptionCompat;-><init>(Ljava/lang/String;Ljava/lang/CharSequence;Ljava/lang/CharSequence;Ljava/lang/CharSequence;Landroid/graphics/Bitmap;Landroid/net/Uri;Landroid/os/Bundle;Landroid/net/Uri;)V
                        new-instance v1, Landroid/support/v4/media/MediaBrowserCompat${'$'}MediaItem;
                        const/4 v2, 0x2
                        invoke-direct { v1, v0, v2 }, Landroid/support/v4/media/MediaBrowserCompat${'$'}MediaItem;-><init>(Landroid/support/v4/media/MediaDescriptionCompat;I)V
                        invoke-virtual { p1, v1 }, Ljava/util/ArrayList;->add(Ljava/lang/Object;)Z
                    """.trimIndent()

                    val fallbackSmali = """
                        if-nez p1, :cond_check_empty
                        goto :cond_create_fallback

                        :cond_check_empty
                        invoke-interface { p1 }, Ljava/util/List;->isEmpty()Z
                        move-result v0
                        if-eqz v0, :cond_list_done

                        :cond_create_fallback
                        new-instance p1, Ljava/util/ArrayList;
                        invoke-direct { p1 }, Ljava/util/ArrayList;-><init>()V

                        ${makeItemSmali(supermixMediaIdSmali, "My Supermix", "Personalized endless radio")}
                        ${makeItemSmali(lmMediaIdSmali, "Liked Music", "Your favorites")}
                        ${makeItemSmali(offlineMediaIdSmali, "Downloaded Songs", "Offline library")}
                        ${makeItemSmali(discoverMediaIdSmali, "Discover Mix", "New music for you")}
                        ${makeItemSmali(newReleaseMediaIdSmali, "New Releases", "Latest music & albums")}
                        ${makeItemSmali(chartsMediaIdSmali, "Mixed for You", "Popular hit songs")}
                        ${makeItemSmali("""const-string v1, "MBS_ACTION_PLAY"""", "Resume Playback", "Resume current track")}
                        ${makeItemSmali("""const-string v1, "MBS_ACTION_NEXT"""", "Next Track", "Skip to next song")}
                        ${makeItemSmali("""const-string v1, "MBS_ACTION_PREV"""", "Previous Track", "Skip to previous song")}

                        :cond_list_done
                        nop
                    """.trimIndent()

                    lkhCMethod.addInstructionsWithLabels(0, fallbackSmali)
                }
            }
        }

        // 6. In MusicMediaSessionCallback (lag.g):
        // Translate custom action mediaIds ("MBS_ACTION_PLAY", "MBS_ACTION_NEXT", "MBS_ACTION_PREV", etc.)
        // into native playable playlist endpoints or invoke transport callbacks.
        val lagClass = MediaSessionCallbackFingerprint.classDefOrNull
            ?: classDefByStrings("MSC: onPlayFromMediaId(). appPkg: '%s' mediaId: '%s'").firstOrNull()
        if (lagClass != null) {
            val lagMutableClass = mutableClassDefBy(lagClass)
            val playFromMediaIdMethod = lagMutableClass.methods.firstOrNull { m ->
                m.parameterTypes.size == 2 &&
                    m.parameterTypes[0] == "Ljava/lang/String;" &&
                    m.parameterTypes[1] == "Landroid/os/Bundle;" &&
                    m.implementation?.instructions?.any { insn ->
                        ((insn as? ReferenceInstruction)?.reference as? StringReference)?.string?.contains("MSC: onPlayFromMediaId()") == true
                    } == true
            }

            if (playFromMediaIdMethod != null) {
                val onPlayMethod = lagMutableClass.methods.firstOrNull { m ->
                    m.parameterTypes.isEmpty() && m.returnType == "V" &&
                        m.implementation?.instructions?.any { insn ->
                            ((insn as? ReferenceInstruction)?.reference as? StringReference)?.string == "onPlay()"
                        } == true
                }
                val onSkipNextMethod = lagMutableClass.methods.firstOrNull { m ->
                    m.parameterTypes.isEmpty() && m.returnType == "V" &&
                        m.implementation?.instructions?.any { insn ->
                            ((insn as? ReferenceInstruction)?.reference as? StringReference)?.string == "onSkipToNext()"
                        } == true
                }
                val onSkipPrevMethod = lagMutableClass.methods.firstOrNull { m ->
                    m.parameterTypes.isEmpty() && m.returnType == "V" &&
                        m.implementation?.instructions?.any { insn ->
                            ((insn as? ReferenceInstruction)?.reference as? StringReference)?.string == "onSkipToPrevious()"
                        } == true
                }

                val mxtClass = MediaBrowserCustomActionsFingerprint.classDefOrNull
                    ?: classDefByOrNull { clazz ->
                        clazz.methods.any { m ->
                            m.name == "g" &&
                                m.parameterTypes == listOf("Ljava/lang/String;") &&
                                m.returnType == "Ljava/lang/String;" &&
                                (m.accessFlags and AccessFlags.STATIC.value != 0)
                        }
                    }
                val mxtEncodeMethod = MediaBrowserCustomActionsFingerprint.methodOrNull
                    ?: mxtClass?.methods?.firstOrNull { m ->
                        m.name == "g" &&
                            m.parameterTypes == listOf("Ljava/lang/String;") &&
                            m.returnType == "Ljava/lang/String;" &&
                            (m.accessFlags and AccessFlags.STATIC.value != 0)
                    }

                if (onPlayMethod != null && onSkipNextMethod != null && onSkipPrevMethod != null) {
                    val actionSmali = buildString {
                        appendLine("""
                            const-string v0, "MBS_ACTION_PLAY"
                            invoke-static { p1, v0 }, Landroid/text/TextUtils;->equals(Ljava/lang/CharSequence;Ljava/lang/CharSequence;)Z
                            move-result v0
                            if-eqz v0, :cond_check_next
                            invoke-virtual { p0 }, ${lagClass.type}->${onPlayMethod.name}()V
                            return-void
                        """.trimIndent())
                        appendLine("""
                            :cond_check_next
                            const-string v0, "MBS_ACTION_NEXT"
                            invoke-static { p1, v0 }, Landroid/text/TextUtils;->equals(Ljava/lang/CharSequence;Ljava/lang/CharSequence;)Z
                            move-result v0
                            if-eqz v0, :cond_check_prev
                            invoke-virtual { p0 }, ${lagClass.type}->${onSkipNextMethod.name}()V
                            return-void

                            :cond_check_prev
                            const-string v0, "MBS_ACTION_PREV"
                            invoke-static { p1, v0 }, Landroid/text/TextUtils;->equals(Ljava/lang/CharSequence;Ljava/lang/CharSequence;)Z
                            move-result v0
                            if-eqz v0, :cond_check_liked
                            invoke-virtual { p0 }, ${lagClass.type}->${onSkipPrevMethod.name}()V
                            return-void

                            :cond_check_liked
                            const-string v0, "MBS_ACTION_LIKED"
                            invoke-static { p1, v0 }, Landroid/text/TextUtils;->equals(Ljava/lang/CharSequence;Ljava/lang/CharSequence;)Z
                            move-result v0
                            if-eqz v0, :cond_check_supermix
                        """.trimIndent())
                        if (mxtClass != null && mxtEncodeMethod != null) {
                            appendLine("""
                                const-string p1, "LM"
                                invoke-static { p1 }, ${mxtClass.type}->${mxtEncodeMethod.name}(Ljava/lang/String;)Ljava/lang/String;
                                move-result-object p1
                                goto :cond_actions_done
                            """.trimIndent())
                        } else {
                            appendLine("return-void")
                        }
                        appendLine("""
                            :cond_check_supermix
                            const-string v0, "MBS_ACTION_SUPERMIX"
                            invoke-static { p1, v0 }, Landroid/text/TextUtils;->equals(Ljava/lang/CharSequence;Ljava/lang/CharSequence;)Z
                            move-result v0
                            if-eqz v0, :cond_check_offline
                        """.trimIndent())
                        if (mxtClass != null && mxtEncodeMethod != null) {
                            appendLine("""
                                const-string p1, "RDTM"
                                invoke-static { p1 }, ${mxtClass.type}->${mxtEncodeMethod.name}(Ljava/lang/String;)Ljava/lang/String;
                                move-result-object p1
                                goto :cond_actions_done
                            """.trimIndent())
                        } else {
                            appendLine("return-void")
                        }
                        appendLine("""
                            :cond_check_offline
                            const-string v0, "MBS_ACTION_OFFLINE"
                            invoke-static { p1, v0 }, Landroid/text/TextUtils;->equals(Ljava/lang/CharSequence;Ljava/lang/CharSequence;)Z
                            move-result v0
                            if-eqz v0, :cond_check_discover
                        """.trimIndent())
                        if (mxtClass != null && mxtEncodeMethod != null) {
                            appendLine("""
                                const-string p1, "PPSV"
                                invoke-static { p1 }, ${mxtClass.type}->${mxtEncodeMethod.name}(Ljava/lang/String;)Ljava/lang/String;
                                move-result-object p1
                                goto :cond_actions_done
                            """.trimIndent())
                        } else {
                            appendLine("return-void")
                        }
                        appendLine("""
                            :cond_check_discover
                            const-string v0, "MBS_ACTION_DISCOVER"
                            invoke-static { p1, v0 }, Landroid/text/TextUtils;->equals(Ljava/lang/CharSequence;Ljava/lang/CharSequence;)Z
                            move-result v0
                            if-eqz v0, :cond_check_new
                        """.trimIndent())
                        if (mxtClass != null && mxtEncodeMethod != null) {
                            appendLine("""
                                const-string p1, "RDEV"
                                invoke-static { p1 }, ${mxtClass.type}->${mxtEncodeMethod.name}(Ljava/lang/String;)Ljava/lang/String;
                                move-result-object p1
                                goto :cond_actions_done
                            """.trimIndent())
                        } else {
                            appendLine("return-void")
                        }
                        appendLine("""
                            :cond_check_new
                            const-string v0, "MBS_ACTION_NEW"
                            invoke-static { p1, v0 }, Landroid/text/TextUtils;->equals(Ljava/lang/CharSequence;Ljava/lang/CharSequence;)Z
                            move-result v0
                            if-eqz v0, :cond_check_charts
                        """.trimIndent())
                        if (mxtClass != null && mxtEncodeMethod != null) {
                            appendLine("""
                                const-string p1, "RDAMVM"
                                invoke-static { p1 }, ${mxtClass.type}->${mxtEncodeMethod.name}(Ljava/lang/String;)Ljava/lang/String;
                                move-result-object p1
                                goto :cond_actions_done
                            """.trimIndent())
                        } else {
                            appendLine("return-void")
                        }
                        appendLine("""
                            :cond_check_charts
                            const-string v0, "MBS_ACTION_CHARTS"
                            invoke-static { p1, v0 }, Landroid/text/TextUtils;->equals(Ljava/lang/CharSequence;Ljava/lang/CharSequence;)Z
                            move-result v0
                            if-eqz v0, :cond_actions_done
                        """.trimIndent())
                        if (mxtClass != null && mxtEncodeMethod != null) {
                            appendLine("""
                                const-string p1, "RDMM"
                                invoke-static { p1 }, ${mxtClass.type}->${mxtEncodeMethod.name}(Ljava/lang/String;)Ljava/lang/String;
                                move-result-object p1
                                goto :cond_actions_done
                            """.trimIndent())
                        } else {
                            appendLine("return-void")
                        }
                        appendLine(":cond_actions_done")
                        appendLine("nop")
                    }
                    playFromMediaIdMethod.addInstructionsWithLabels(0, actionSmali)
                }
            }
        }
    }
}

