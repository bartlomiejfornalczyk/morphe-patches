package app.template.patches.music

import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.resourcePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod.Companion.toMutable
import app.template.patches.shared.Constants.COMPATIBILITY_YOUTUBE_MUSIC
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.MutableMethodImplementation
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodParameter
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
        // 0. Bypass certificate checks (Google Maps, Android Auto partner certs & Google signed check)
        CheckCertificateFingerprint.methodOrNull?.let { m ->
            val count = m.implementation?.instructions?.count() ?: 0
            if (count >= 2) {
                m.replaceInstruction(0, "const/4 v0, 0x1")
                m.replaceInstruction(1, "return v0")
                for (idx in 2 until count) {
                    m.replaceInstruction(idx, "nop")
                }
            }
        }
        IsGoogleSignedFingerprint.methodOrNull?.let { m ->
            val count = m.implementation?.instructions?.count() ?: 0
            if (count >= 2) {
                m.replaceInstruction(0, "const/4 v0, 0x0")
                m.replaceInstruction(1, "return v0")
                for (idx in 2 until count) {
                    m.replaceInstruction(idx, "nop")
                }
            }
        }

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
        val mxtMethod = mxtClass?.methods?.firstOrNull { m ->
            (m.accessFlags and AccessFlags.STATIC.value != 0) &&
                m.parameterTypes == listOf("Ljava/lang/String;") &&
                m.returnType == "Ljava/lang/String;"
        }

        val encodeCommandMediaIdMethod = mxtMethod?.implementation?.instructions
            ?.mapNotNull { (it as? ReferenceInstruction)?.reference as? MethodReference }
            ?.firstOrNull { ref ->
                ref.returnType == "Ljava/lang/String;" &&
                    ref.parameterTypes.size == 1 &&
                    ref.parameterTypes[0] != "Ljava/lang/String;"
            }
        val commandType = encodeCommandMediaIdMethod?.parameterTypes?.single()?.toString()
        val createPlaybackCommandMethod = commandType?.let { cmdType ->
            playlistPlaybackCommandFingerprint(cmdType).methodOrNull
                ?: classDefByOrNull { clazz ->
                    clazz.methods.any { m ->
                        m.parameterTypes.map { p -> p.toString() } == listOf(
                            "Ljava/lang/String;", "Ljava/lang/String;", "I", "F",
                            "Ljava/lang/String;", "Ljava/lang/String;", "Z"
                        ) &&
                        m.returnType.startsWith("L") &&
                        m.returnType.endsWith(";")
                    }
                }?.methods?.firstOrNull { m ->
                    m.parameterTypes.map { p -> p.toString() } == listOf(
                        "Ljava/lang/String;", "Ljava/lang/String;", "I", "F",
                        "Ljava/lang/String;", "Ljava/lang/String;", "Z"
                    ) &&
                    m.returnType.startsWith("L") &&
                    m.returnType.endsWith(";")
                }
        }
        val buildCommandMethod = createPlaybackCommandMethod?.implementation?.instructions
            ?.mapNotNull { (it as? ReferenceInstruction)?.reference as? MethodReference }
            ?.distinct()
            ?.firstOrNull { method -> method.name == "build" && method.parameterTypes.isEmpty() }

        val lkhClass = LoadChildrenResultFingerprint.classDefOrNull
            ?: classDefByStrings("com/google/android/apps/youtube/music/mediabrowser/results/LoadChildrenResult").firstOrNull()
        var hasEncoderHelper = false
        if (lkhClass != null) {
            val lkhMutableClass = mutableClassDefBy(lkhClass)
            if (encodeCommandMediaIdMethod != null && createPlaybackCommandMethod != null && buildCommandMethod != null) {
                val encodeHelperSmali = """
                    const/4 v0, 0x0
                    move-object v1, p0
                    const/4 v2, 0x0
                    const/4 v3, 0x0
                    const/4 v4, 0x0
                    const/4 v5, 0x0
                    const/4 v6, 0x0
                    invoke-static/range { v0 .. v6 }, ${createPlaybackCommandMethod.definingClass}->${createPlaybackCommandMethod.name}(Ljava/lang/String;Ljava/lang/String;IFLjava/lang/String;Ljava/lang/String;Z)${createPlaybackCommandMethod.returnType}
                    move-result-object v0
                    invoke-virtual { v0 }, ${buildCommandMethod.definingClass}->${buildCommandMethod.name}()${buildCommandMethod.returnType}
                    move-result-object v0
                    check-cast v0, $commandType
                    invoke-static { v0 }, ${encodeCommandMediaIdMethod.definingClass}->${encodeCommandMediaIdMethod.name}($commandType)Ljava/lang/String;
                    move-result-object v0
                    return-object v0
                """.trimIndent()

                lkhMutableClass.methods.add(
                    ImmutableMethod(
                        lkhMutableClass.type,
                        "encodePlaylistMediaId",
                        listOf(ImmutableMethodParameter("Ljava/lang/String;", null, null)),
                        "Ljava/lang/String;",
                        AccessFlags.PUBLIC.value or AccessFlags.STATIC.value,
                        null,
                        null,
                        MutableMethodImplementation(8)
                    ).toMutable().apply {
                        addInstructionsWithLabels(0, encodeHelperSmali)
                    }
                )
                hasEncoderHelper = true
            }

            val lkhCMethod = lkhMutableClass.methods.firstOrNull { m ->
                m.parameterTypes.size == 2 &&
                    m.parameterTypes[0] == "Ljava/util/List;" &&
                    m.returnType == "V"
            }

            if (lkhCMethod != null && lkhCMethod.implementation != null) {
                fun playlistIdSmali(playlistId: String): String = if (hasEncoderHelper) {
                    """
                        const-string v1, "$playlistId"
                        invoke-static { v1 }, ${lkhClass.type}->encodePlaylistMediaId(Ljava/lang/String;)Ljava/lang/String;
                        move-result-object v1
                    """.trimIndent()
                } else if (mxtClass != null && mxtMethod != null) {
                    """
                        const-string v1, "$playlistId"
                        invoke-static { v1 }, ${mxtClass.type}->${mxtMethod.name}(Ljava/lang/String;)Ljava/lang/String;
                        move-result-object v1
                    """.trimIndent()
                } else {
                    """const-string v1, "$playlistId""""
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

                    ${makeItemSmali(playlistIdSmali("RDTM"), "My Supermix", "Personalized endless radio")}
                    ${makeItemSmali(playlistIdSmali("LM"), "Liked Music", "Your favorites")}
                    ${makeItemSmali(playlistIdSmali("PPSV"), "Downloaded Songs", "Offline library")}
                    ${makeItemSmali(playlistIdSmali("RDEV"), "Discover Mix", "New music for you")}
                    ${makeItemSmali(playlistIdSmali("RDAMVM"), "New Releases", "Latest music & albums")}
                    ${makeItemSmali(playlistIdSmali("RDMM"), "Mixed for You", "Popular hit songs")}
                    ${makeItemSmali("""const-string v1, "MBS_ACTION_PLAY"""", "Resume Playback", "Resume current track")}
                    ${makeItemSmali("""const-string v1, "MBS_ACTION_NEXT"""", "Next Track", "Skip to next song")}
                    ${makeItemSmali("""const-string v1, "MBS_ACTION_PREV"""", "Previous Track", "Skip to previous song")}

                    :cond_list_done
                    nop
                """.trimIndent()

                lkhCMethod.addInstructionsWithLabels(0, fallbackSmali)
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

                if (onPlayMethod != null && onSkipNextMethod != null && onSkipPrevMethod != null) {
                    fun encodeActionSmali(actionName: String, rawId: String, nextLabel: String): String {
                        return if (hasEncoderHelper && lkhClass != null) {
                            """
                                const-string v0, "$actionName"
                                invoke-static { p1, v0 }, Landroid/text/TextUtils;->equals(Ljava/lang/CharSequence;Ljava/lang/CharSequence;)Z
                                move-result v0
                                if-nez v0, :cond_encode_$rawId
                                const-string v0, "$rawId"
                                invoke-static { p1, v0 }, Landroid/text/TextUtils;->equals(Ljava/lang/CharSequence;Ljava/lang/CharSequence;)Z
                                move-result v0
                                if-eqz v0, :$nextLabel
                                :cond_encode_$rawId
                                const-string p1, "$rawId"
                                invoke-static { p1 }, ${lkhClass.type}->encodePlaylistMediaId(Ljava/lang/String;)Ljava/lang/String;
                                move-result-object p1
                                goto :cond_actions_done
                            """.trimIndent()
                        } else if (mxtClass != null && mxtMethod != null) {
                            """
                                const-string v0, "$actionName"
                                invoke-static { p1, v0 }, Landroid/text/TextUtils;->equals(Ljava/lang/CharSequence;Ljava/lang/CharSequence;)Z
                                move-result v0
                                if-nez v0, :cond_encode_$rawId
                                const-string v0, "$rawId"
                                invoke-static { p1, v0 }, Landroid/text/TextUtils;->equals(Ljava/lang/CharSequence;Ljava/lang/CharSequence;)Z
                                move-result v0
                                if-eqz v0, :$nextLabel
                                :cond_encode_$rawId
                                const-string p1, "$rawId"
                                invoke-static { p1 }, ${mxtClass.type}->${mxtMethod.name}(Ljava/lang/String;)Ljava/lang/String;
                                move-result-object p1
                                goto :cond_actions_done
                            """.trimIndent()
                        } else {
                            """
                                const-string v0, "$actionName"
                                invoke-static { p1, v0 }, Landroid/text/TextUtils;->equals(Ljava/lang/CharSequence;Ljava/lang/CharSequence;)Z
                                move-result v0
                                if-eqz v0, :$nextLabel
                                invoke-virtual { p0 }, ${lagClass.type}->${onPlayMethod.name}()V
                                return-void
                            """.trimIndent()
                        }
                    }

                    val actionSmali = buildString {
                        appendLine("""
                            const-string v0, "MBS_ACTION_PLAY"
                            invoke-static { p1, v0 }, Landroid/text/TextUtils;->equals(Ljava/lang/CharSequence;Ljava/lang/CharSequence;)Z
                            move-result v0
                            if-eqz v0, :cond_check_next
                            invoke-virtual { p0 }, ${lagClass.type}->${onPlayMethod.name}()V
                            return-void

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
                            if-eqz v0, :cond_check_supermix
                            invoke-virtual { p0 }, ${lagClass.type}->${onSkipPrevMethod.name}()V
                            return-void

                            :cond_check_supermix
                        """.trimIndent())

                        appendLine(encodeActionSmali("MBS_ACTION_SUPERMIX", "RDTM", "cond_check_liked"))
                        appendLine(":cond_check_liked")
                        appendLine(encodeActionSmali("MBS_ACTION_LIKED", "LM", "cond_check_offline"))
                        appendLine(":cond_check_offline")
                        appendLine(encodeActionSmali("MBS_ACTION_OFFLINE", "PPSV", "cond_check_discover"))
                        appendLine(":cond_check_discover")
                        appendLine(encodeActionSmali("MBS_ACTION_DISCOVER", "RDEV", "cond_check_new"))
                        appendLine(":cond_check_new")
                        appendLine(encodeActionSmali("MBS_ACTION_NEW", "RDAMVM", "cond_check_charts"))
                        appendLine(":cond_check_charts")
                        appendLine(encodeActionSmali("MBS_ACTION_CHARTS", "RDMM", "cond_actions_done"))

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
                hadBMethod.addInstructionsWithLabels(0, """
                    if-nez p1, :cond_bundle_not_null
                    new-instance p1, Landroid/os/Bundle;
                    invoke-direct { p1 }, Landroid/os/Bundle;-><init>()V
                    :cond_bundle_not_null
                    const-string v0, "skip_entitlement_check"
                    const/4 v1, 0x1
                    invoke-virtual { p1, v0, v1 }, Landroid/os/Bundle;->putBoolean(Ljava/lang/String;Z)V
                    return-object p1
                """.trimIndent())
            }
        }

        // 8. In MusicMediaSessionPlayerController (lbg.i / hak.h):
        // Bypass account check (isSignedIn) and entitlement check (skip_entitlement_check)
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

                // B. Bypass AccountIdentityManager.isSignedIn() and omt.i() checks:
                // Find all return-object instructions in the first 50 instructions and force
                // the move-result instructions before them to 1 (true) so early error returns are bypassed.
                val updatedImpl = lbgPlayMethod.implementation!!
                val count = updatedImpl.instructions.count()
                val returnIndices = mutableListOf<Int>()
                for (i in 0 until minOf(50, count)) {
                    if (updatedImpl.instructions.elementAt(i).opcode == Opcode.RETURN_OBJECT) {
                        returnIndices.add(i)
                    }
                }
                for (retIdx in returnIndices) {
                    for (i in (retIdx - 1) downTo maxOf(0, retIdx - 10)) {
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

