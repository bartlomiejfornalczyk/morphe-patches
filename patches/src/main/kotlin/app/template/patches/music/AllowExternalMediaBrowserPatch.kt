package app.template.patches.music

import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.resourcePatch
import app.template.patches.shared.Constants.COMPATIBILITY_YOUTUBE_MUSIC
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference
import org.w3c.dom.Element

val allowExternalMediaBrowserManifestPatch = resourcePatch(
    name = "Allow external media browser launch",
    description = "Adds MAIN and DEFAULT intent-filters to MusicActivity so external controllers like Google Maps can open the app.",
    default = true
) {
    compatibleWith(COMPATIBILITY_YOUTUBE_MUSIC)

    execute {
        document("AndroidManifest.xml").use { doc ->
            val manifest = doc.documentElement
            val androidNs = "http://schemas.android.com/apk/res/android"

            // 1. Ensure MusicActivity has MAIN, DEFAULT, and LAUNCHER
            val activityNodes = doc.getElementsByTagName("activity")
            for (i in 0 until activityNodes.length) {
                val activity = activityNodes.item(i) as? Element ?: continue
                val name = activity.getAttributeNS(androidNs, "name")
                    .ifEmpty { activity.getAttribute("android:name") }
                if (name == "com.google.android.apps.youtube.music.activities.MusicActivity") {
                    val filter = doc.createElement("intent-filter")

                    val action = doc.createElement("action").apply {
                        setAttribute("android:name", "android.intent.action.MAIN")
                        setAttributeNS(androidNs, "android:name", "android.intent.action.MAIN")
                    }
                    val catDefault = doc.createElement("category").apply {
                        setAttribute("android:name", "android.intent.category.DEFAULT")
                        setAttributeNS(androidNs, "android:name", "android.intent.category.DEFAULT")
                    }
                    val catLauncher = doc.createElement("category").apply {
                        setAttribute("android:name", "android.intent.category.LAUNCHER")
                        setAttributeNS(androidNs, "android:name", "android.intent.category.LAUNCHER")
                    }

                    filter.appendChild(action)
                    filter.appendChild(catDefault)
                    filter.appendChild(catLauncher)
                    activity.appendChild(filter)
                    break
                }
            }

            // 2. Also ensure all activity-aliases (especially Morphe custom branding launcher aliases)
            // with action.MAIN have category.DEFAULT so external apps (Google Maps, Android Auto)
            // can launch the active alias via new Intent(ACTION_MAIN).setPackage(...) without ActivityNotFoundException.
            val aliasNodes = doc.getElementsByTagName("activity-alias")
            for (i in 0 until aliasNodes.length) {
                val alias = aliasNodes.item(i) as? Element ?: continue
                val filterNodes = alias.getElementsByTagName("intent-filter")
                for (j in 0 until filterNodes.length) {
                    val filter = filterNodes.item(j) as? Element ?: continue
                    val actions = filter.getElementsByTagName("action")
                    var hasMain = false
                    for (k in 0 until actions.length) {
                        val act = actions.item(k) as? Element ?: continue
                        val actName = act.getAttributeNS(androidNs, "name").ifEmpty { act.getAttribute("android:name") }
                        if (actName == "android.intent.action.MAIN") {
                            hasMain = true
                            break
                        }
                    }
                    if (hasMain) {
                        val categories = filter.getElementsByTagName("category")
                        var hasDefault = false
                        for (k in 0 until categories.length) {
                            val cat = categories.item(k) as? Element ?: continue
                            val catName = cat.getAttributeNS(androidNs, "name").ifEmpty { cat.getAttribute("android:name") }
                            if (catName == "android.intent.category.DEFAULT") {
                                hasDefault = true
                                break
                            }
                        }
                        if (!hasDefault) {
                            val catDefault = doc.createElement("category").apply {
                                setAttribute("android:name", "android.intent.category.DEFAULT")
                                setAttributeNS(androidNs, "android:name", "android.intent.category.DEFAULT")
                            }
                            filter.appendChild(catDefault)
                        }
                    }
                }
            }
        }
    }
}

@Suppress("unused")
val allowExternalMediaBrowserPatch = bytecodePatch(
    name = "Allow external media browser connections",
    description = "Allows Google Maps, Android Auto, and third-party media controllers to connect to YouTube Music.",
    default = true
) {
    compatibleWith(
        COMPATIBILITY_YOUTUBE_MUSIC
    )
    dependsOn(allowExternalMediaBrowserManifestPatch)

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
                    // Discover the native media item encoder (mxt / ixx) if present
                    val mxtClass = listOfNotNull(
                        MediaBrowserCustomActionsFingerprint.classDefOrNull,
                        classDefByStrings("offline_PPSV").firstOrNull { clazz ->
                            clazz.methods.any { m ->
                                (m.accessFlags and AccessFlags.STATIC.value != 0) &&
                                    m.parameterTypes == listOf("Ljava/lang/String;") &&
                                    m.returnType == "Ljava/lang/String;"
                            }
                        },
                        classDefByStrings("com.google.android.apps.youtube.music.mediabrowser.custom_actions").firstOrNull { clazz ->
                            clazz.methods.any { m ->
                                (m.accessFlags and AccessFlags.STATIC.value != 0) &&
                                    m.parameterTypes == listOf("Ljava/lang/String;") &&
                                    m.returnType == "Ljava/lang/String;"
                            }
                        },
                        classDefByStrings("__DETAILS_HEADER_ID__").firstOrNull { clazz ->
                            clazz.methods.any { m ->
                                (m.accessFlags and AccessFlags.STATIC.value != 0) &&
                                    m.parameterTypes == listOf("Ljava/lang/String;") &&
                                    m.returnType == "Ljava/lang/String;"
                            }
                        }
                    ).firstOrNull { clazz ->
                        clazz.methods.any { m ->
                            (m.accessFlags and AccessFlags.STATIC.value != 0) &&
                                m.parameterTypes == listOf("Ljava/lang/String;") &&
                                m.returnType == "Ljava/lang/String;"
                        }
                    }
                    val mxtEncodeMethod = mxtClass?.methods?.firstOrNull { m ->
                        (m.accessFlags and AccessFlags.STATIC.value != 0) &&
                            m.parameterTypes == listOf("Ljava/lang/String;") &&
                            m.returnType == "Ljava/lang/String;"
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

                val mxtClass = listOfNotNull(
                    MediaBrowserCustomActionsFingerprint.classDefOrNull,
                    classDefByStrings("offline_PPSV").firstOrNull { clazz ->
                        clazz.methods.any { m ->
                            (m.accessFlags and AccessFlags.STATIC.value != 0) &&
                                m.parameterTypes == listOf("Ljava/lang/String;") &&
                                m.returnType == "Ljava/lang/String;"
                        }
                    },
                    classDefByStrings("com.google.android.apps.youtube.music.mediabrowser.custom_actions").firstOrNull { clazz ->
                        clazz.methods.any { m ->
                            (m.accessFlags and AccessFlags.STATIC.value != 0) &&
                                m.parameterTypes == listOf("Ljava/lang/String;") &&
                                m.returnType == "Ljava/lang/String;"
                        }
                    },
                    classDefByStrings("__DETAILS_HEADER_ID__").firstOrNull { clazz ->
                        clazz.methods.any { m ->
                            (m.accessFlags and AccessFlags.STATIC.value != 0) &&
                                m.parameterTypes == listOf("Ljava/lang/String;") &&
                                m.returnType == "Ljava/lang/String;"
                        }
                    }
                ).firstOrNull { clazz ->
                    clazz.methods.any { m ->
                        (m.accessFlags and AccessFlags.STATIC.value != 0) &&
                            m.parameterTypes == listOf("Ljava/lang/String;") &&
                            m.returnType == "Ljava/lang/String;"
                    }
                }
                val mxtEncodeMethod = mxtClass?.methods?.firstOrNull { m ->
                    (m.accessFlags and AccessFlags.STATIC.value != 0) &&
                        m.parameterTypes == listOf("Ljava/lang/String;") &&
                        m.returnType == "Ljava/lang/String;"
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
                            appendLine("""
                                invoke-virtual { p0 }, ${lagClass.type}->${onPlayMethod.name}()V
                                return-void
                            """.trimIndent())
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
                            appendLine("""
                                invoke-virtual { p0 }, ${lagClass.type}->${onPlayMethod.name}()V
                                return-void
                            """.trimIndent())
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
                            appendLine("""
                                invoke-virtual { p0 }, ${lagClass.type}->${onPlayMethod.name}()V
                                return-void
                            """.trimIndent())
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
                            appendLine("""
                                invoke-virtual { p0 }, ${lagClass.type}->${onPlayMethod.name}()V
                                return-void
                            """.trimIndent())
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
                            appendLine("""
                                invoke-virtual { p0 }, ${lagClass.type}->${onPlayMethod.name}()V
                                return-void
                            """.trimIndent())
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
                            appendLine("""
                                invoke-virtual { p0 }, ${lagClass.type}->${onPlayMethod.name}()V
                                return-void
                            """.trimIndent())
                        }
                        appendLine("""
                            :cond_actions_done
                            if-nez p2, :cond_bundle_init
                            goto :cond_bundle_set_skip
                            :cond_bundle_init
                            new-instance p2, Landroid/os/Bundle;
                            invoke-direct { p2 }, Landroid/os/Bundle;-><init>()V
                            :cond_bundle_set_skip
                            const-string v0, "skip_entitlement_check"
                            const/4 v1, 0x1
                            invoke-virtual { p2, v0, v1 }, Landroid/os/Bundle;->putBoolean(Ljava/lang/String;Z)V
                            nop
                        """.trimIndent())
                    }
                    playFromMediaIdMethod.addInstructionsWithLabels(0, actionSmali)
                }
            }
        }

        // 7. In MediaSessionHelper (had.b / lai.b):
        // Force bundle.putBoolean("skip_entitlement_check", true) so that all playback requests
        // from external controllers (Google Maps, Android Auto, Bluetooth) bypass entitlement gating.
        val hadClass = classDefByStrings("skip_entitlement_check").firstOrNull { clazz ->
            clazz.methods.any { m ->
                m.parameterTypes.size == 2 &&
                    m.parameterTypes[1] == "Landroid/os/Bundle;" &&
                    m.returnType == "Landroid/os/Bundle;" &&
                    (m.accessFlags and AccessFlags.STATIC.value != 0)
            }
        }
        if (hadClass != null) {
            val hadMutableClass = mutableClassDefBy(hadClass)
            val hadBMethod = hadMutableClass.methods.firstOrNull { m ->
                m.parameterTypes.size == 2 &&
                    m.parameterTypes[1] == "Landroid/os/Bundle;" &&
                    m.returnType == "Landroid/os/Bundle;" &&
                    (m.accessFlags and AccessFlags.STATIC.value != 0)
            }
            if (hadBMethod?.implementation != null) {
                val impl = hadBMethod.implementation!!
                for (i in 0 until impl.instructions.count()) {
                    val insn = impl.instructions.elementAt(i)
                    val strRef = ((insn as? ReferenceInstruction)?.reference as? StringReference)?.string
                    if (strRef == "skip_entitlement_check") {
                        for (j in i + 1 until minOf(i + 4, impl.instructions.count())) {
                            val nextInsn = impl.instructions.elementAt(j)
                            val mRef = (nextInsn as? ReferenceInstruction)?.reference as? MethodReference
                            if (mRef?.name == "putBoolean") {
                                val reg = (nextInsn as? FiveRegisterInstruction)?.registerE ?: 1
                                hadBMethod.addInstructionsWithLabels(j, "const/4 v$reg, 0x1")
                                break
                            }
                        }
                        break
                    }
                }
            }
        }

        // 8. In MusicMediaSessionPlayerController (lbg.i / hak.h):
        // Bypass account check (kym.c) and car-mode entitlement gating (kym.d)
        // so playback requests unconditionally execute ((bacc) this.e.gG()).b(azmi) and ((bacw) this.d.gG()).ah()
        val lbgClass = classDefByStrings("intent_extra_command_bundle").firstOrNull { clazz ->
            clazz.methods.any { m ->
                m.name == "handleSignOutEvent" || m.implementation?.instructions?.any { insn ->
                    ((insn as? ReferenceInstruction)?.reference as? StringReference)?.string == "skip_entitlement_check"
                } == true
            }
        } ?: classDefByStrings("skip_entitlement_check").firstOrNull { clazz ->
            clazz.methods.any { m -> m.name == "handleSignOutEvent" }
        }

        if (lbgClass != null) {
            val lbgMutableClass = mutableClassDefBy(lbgClass)
            val lbgPlayMethod = lbgMutableClass.methods.firstOrNull { m ->
                m.implementation?.instructions?.any { insn ->
                    ((insn as? ReferenceInstruction)?.reference as? StringReference)?.string == "skip_entitlement_check"
                } == true
            }

            if (lbgPlayMethod?.implementation != null) {
                // A. Force bundle.getBoolean("skip_entitlement_check", false) to return 1 (true):
                // This ensures car mode (driving mode in Google Maps) directly enqueues and plays the playlist.
                val impl = lbgPlayMethod.implementation!!
                for (i in 0 until impl.instructions.count()) {
                    val insn = impl.instructions.elementAt(i)
                    val strRef = ((insn as? ReferenceInstruction)?.reference as? StringReference)?.string
                    if (strRef == "skip_entitlement_check") {
                        for (j in i + 1 until minOf(i + 6, impl.instructions.count())) {
                            val nextInsn = impl.instructions.elementAt(j)
                            if (nextInsn.opcode == Opcode.MOVE_RESULT) {
                                val reg = (nextInsn as OneRegisterInstruction).registerA
                                lbgPlayMethod.addInstructionsWithLabels(j + 1, "const/4 v$reg, 0x1")
                                break
                            }
                        }
                        break
                    }
                }

                // B. Bypass AccountIdentityManager.isSignedIn() check:
                // Find the first return-object instruction (which returns AUTH_REQUIRED / kym.c)
                // and the move-result instruction before it, forcing its register to 1 (true).
                val updatedImpl = lbgPlayMethod.implementation!!
                val count = updatedImpl.instructions.count()
                var firstReturnObjectIdx = -1
                for (i in 0 until minOf(50, count)) {
                    val insn = updatedImpl.instructions.elementAt(i)
                    if (insn.opcode == Opcode.RETURN_OBJECT) {
                        firstReturnObjectIdx = i
                        break
                    }
                }
                if (firstReturnObjectIdx != -1) {
                    for (i in (firstReturnObjectIdx - 1) downTo maxOf(0, firstReturnObjectIdx - 10)) {
                        val insn = updatedImpl.instructions.elementAt(i)
                        if (insn.opcode == Opcode.MOVE_RESULT) {
                            val reg = (insn as OneRegisterInstruction).registerA
                            lbgPlayMethod.addInstructionsWithLabels(i + 1, "const/4 v$reg, 0x1")
                            break
                        }
                    }
                }
            }
        }
    }
}

