package app.template.patches.music

import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.booleanOption
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
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
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

    val tileSupermix by booleanOption(
        key = "tileSupermix",
        default = true,
        title = "Tile: My Supermix",
        description = "Personalized endless radio mix in external media browsers (Google Maps, Android Auto)."
    )
    val tileLikedMusic by booleanOption(
        key = "tileLikedMusic",
        default = true,
        title = "Tile: Liked Music",
        description = "Your favorite liked songs library in external media browsers."
    )
    val tileLikeCurrentSong by booleanOption(
        key = "tileLikeCurrentSong",
        default = true,
        title = "Tile: Like Current Song",
        description = "Quick thumbs-up action tile for the currently playing track in external media browsers."
    )
    val tileResumePlayback by booleanOption(
        key = "tileResumePlayback",
        default = true,
        title = "Tile: Resume Playback",
        description = "Quick resume / unpause action tile in external media browsers."
    )
    val tileDownloadedSongs by booleanOption(
        key = "tileDownloadedSongs",
        default = true,
        title = "Tile: Downloaded Songs",
        description = "Offline downloaded music library in external media browsers."
    )
    val tileMixedForYou by booleanOption(
        key = "tileMixedForYou",
        default = true,
        title = "Tile: Mixed for You",
        description = "Mixed for you road trip radio in external media browsers."
    )
    val tilePopCertified by booleanOption(
        key = "tilePopCertified",
        default = true,
        title = "Tile: Pop Certified",
        description = "Certified pop hits playlist in external media browsers."
    )
    val tileDancePartyHits by booleanOption(
        key = "tileDancePartyHits",
        default = true,
        title = "Tile: Dance Party Hits",
        description = "Upbeat dance & EDM anthems in external media browsers."
    )
    val tilePumpUpEnergy by booleanOption(
        key = "tilePumpUpEnergy",
        default = true,
        title = "Tile: Pump-Up Energy",
        description = "High energy workout & driving hits in external media browsers."
    )
    val tileTrending20 by booleanOption(
        key = "tileTrending20",
        default = false,
        title = "Tile: Trending 20",
        description = "Top 20 trending chart playlist in external media browsers."
    )
    val tileTrendingMusicVideos by booleanOption(
        key = "tileTrendingMusicVideos",
        default = false,
        title = "Tile: Trending Music Videos",
        description = "Top trending music videos playlist in external media browsers."
    )
    val tilePopularRightNow by booleanOption(
        key = "tilePopularRightNow",
        default = false,
        title = "Tile: Popular Right Now",
        description = "Popular hit songs playlist in external media browsers."
    )

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
                        const-string v1, "${resolvePlaylistId(playlistId)}"
                        invoke-static { v1 }, ${lkhClass.type}->encodePlaylistMediaId(Ljava/lang/String;)Ljava/lang/String;
                        move-result-object v1
                    """.trimIndent()
                } else if (mxtClass != null && mxtMethod != null) {
                    """
                        const-string v1, "${resolvePlaylistId(playlistId)}"
                        invoke-static { v1 }, ${mxtClass.type}->${mxtMethod.name}(Ljava/lang/String;)Ljava/lang/String;
                        move-result-object v1
                    """.trimIndent()
                } else {
                    """const-string v1, "${resolvePlaylistId(playlistId)}""""
                }

                fun makeItemSmali(mediaIdSmali: String, title: String, subtitle: String, iconUrl: String? = null): String {
                    val iconSmali = if (iconUrl != null) {
                        """
                            const-string v6, "$iconUrl"
                            invoke-static { v6 }, Landroid/net/Uri;->parse(Ljava/lang/String;)Landroid/net/Uri;
                            move-result-object v6
                        """.trimIndent()
                    } else {
                        "const/4 v6, 0x0"
                    }

                    return """
                        $mediaIdSmali
                        $iconSmali
                        new-instance v0, Landroid/support/v4/media/MediaDescriptionCompat;
                        const-string v2, "$title"
                        const-string v3, "$subtitle"
                        const/4 v4, 0x0
                        const/4 v5, 0x0
                        const/4 v7, 0x0
                        const/4 v8, 0x0
                        invoke-direct/range { v0 .. v8 }, Landroid/support/v4/media/MediaDescriptionCompat;-><init>(Ljava/lang/String;Ljava/lang/CharSequence;Ljava/lang/CharSequence;Ljava/lang/CharSequence;Landroid/graphics/Bitmap;Landroid/net/Uri;Landroid/os/Bundle;Landroid/net/Uri;)V
                        new-instance v1, Landroid/support/v4/media/MediaBrowserCompat${'$'}MediaItem;
                        const/4 v2, 2
                        invoke-direct { v1, v0, v2 }, Landroid/support/v4/media/MediaBrowserCompat${'$'}MediaItem;-><init>(Landroid/support/v4/media/MediaDescriptionCompat;I)V
                        invoke-virtual { p1, v1 }, Ljava/util/ArrayList;->add(Ljava/lang/Object;)Z
                    """.trimIndent()
                }

                val itemsSmali = buildString {
                    if (tileSupermix == true) appendLine(makeItemSmali(playlistIdSmali("RDTM"), "My Supermix", "Personalized endless radio", "https://yt3.googleusercontent.com/a_Psx_TQyLsZ5S5ybBCtyK2ArHwLUN4p8LjmWL3wvtNc-xQaG447M3nKViimyjAsW3pQ2GdBO8L4Jj4=w544-h544-l90-rj"))
                    if (tileLikedMusic == true) appendLine(makeItemSmali(playlistIdSmali("LM"), "Liked Music", "Your favorites", "https://play-lh.googleusercontent.com/JX8RAoUFybCYzCLp5bNhqeXCmhqqzXf4sX81-XsokkOJXqZyw5N83wpzrtHlILhFBkFmuhhM51z9wSLXQrEa=w512-h512"))
                    if (tileLikeCurrentSong == true) appendLine(makeItemSmali("""const-string v1, "MBS_ACTION_LIKE"""", "Like Current Song", "Thumbs up this track", "https://play-lh.googleusercontent.com/UvyJtlgvDLs2VIcOeg-aFDjB3XETm5Li0YZA6JaUOtUgcM_9ehzovpbLuof618tWfkIxW65vJcTFmFc6ak7uMw=w512-h512"))
                    if (tileResumePlayback == true) appendLine(makeItemSmali("""const-string v1, "MBS_ACTION_PLAY"""", "Resume Playback", "Resume current track", "https://play-lh.googleusercontent.com/MyptZXmcCyF59phIWPsNCfHq6zSO_ojEdw_4rMYYWPOGdikSjqeOXcoszBZ053VYpINe5K7ZR9KV1nkFWgxdOw=w512-h512"))
                    if (tileDownloadedSongs == true) appendLine(makeItemSmali(playlistIdSmali("PPSV"), "Downloaded Songs", "Offline library", "https://play-lh.googleusercontent.com/cjyFg0ABFwOFdLf1GsCGRvOoAANqCnIsFCgwGe8g-FPVaQ5STxoqZAKQOph44Hlx-gO0GMx8AZC9AY1VUBiO=w512-h512"))
                    if (tileMixedForYou == true) appendLine(makeItemSmali(playlistIdSmali("RDMM"), "Mixed for You", "Rock on the road hits", "https://yt3.googleusercontent.com/BSIKjtCY-sMw8NpepPWv7gGwk8ij7TvDH785SAdfEsQ6Zf1AVT1FKOYbSB3FlEAeeiKPwvfLNNkIktU=w544-h544-l90-rj"))
                    if (tilePopCertified == true) appendLine(makeItemSmali(playlistIdSmali("RDCLAK5uy_lBNUteBRencHzKelu5iDHwLF6mYqjL-JU"), "Pop Certified", "Certified pop hits", "https://yt3.googleusercontent.com/H-a23V_iN1LA7Vi8r8H5grE9FmjUTIsy_WjzUO_5hYxNT4TRZNkBXSFKlNHLab3kKYpBlsqqhcCuhWE=w544-h544-l90-rj"))
                    if (tileDancePartyHits == true) appendLine(makeItemSmali(playlistIdSmali("RDCLAK5uy_mkXiaMATud8GJQ3aX7f0nCeCqvehA9wTQ"), "Dance Party Hits", "Upbeat dance anthems", "https://yt3.googleusercontent.com/fcBazTj_SCl0s9rsgsI2x8d0UKr_vxHEuu35GYcua25DbjK4HpqV2ZxoykjwhdAVH34GJVq-5bEADJM=w544-h544-l90-rj"))
                    if (tilePumpUpEnergy == true) appendLine(makeItemSmali(playlistIdSmali("RDCLAK5uy_mVJ3RRi_YBfUJnZnQxLAedQQcXHujbUcg"), "Pump-Up Energy", "High energy favorites", "https://yt3.googleusercontent.com/PiHeLTzGupMv0PkTI0dg-sstkzOx8yNkBOEfV67yP_sUkJtXLRk4JO8dwhz18rZAz9gHJSAGHeztsJ4=w544-h544-l90-rj"))
                    if (tileTrending20 == true) appendLine(makeItemSmali(playlistIdSmali("PLFgquLnL59alW3xmYiWRaoz0oM3H17Lth"), "Trending 20", "Top charts right now", "https://yt3.googleusercontent.com/Fesjpk0-6-rFF02i0bnutv--ILcObw-4y8hhKqjBbfZteZkIEFcJKXJgFVcILo-9TewdC67rTJ8=s576"))
                    if (tileTrendingMusicVideos == true) appendLine(makeItemSmali(playlistIdSmali("PLFgquLnL59alCl_2TQvOiD5Vgm1hCaGSI"), "Trending Music Videos", "Popular video hits", "https://yt3.googleusercontent.com/12Z2lesRDLk4t_Pd-lMs-TlvEJBFLIOv1Uh9mJUUneRqYVRRSjAkjcKkCNF7f3dejleGz77F7xN5=s576"))
                    if (tilePopularRightNow == true) appendLine(makeItemSmali(playlistIdSmali("PLrEnWoR732-BHrPp_Pm8_VleD68f9s14-"), "Popular Right Now", "Trending hit songs", "https://yt3.googleusercontent.com/i22CwRBnI9UZYAPf3Cuxcx0qCrzb_G5lzdIjYSiHtPNuIHPe8DQizfMmY6FX1-PI6sLMG1Sy9yY=s576"))
                }.ifBlank {
                    makeItemSmali(playlistIdSmali("RDTM"), "My Supermix", "Personalized endless radio", "https://yt3.googleusercontent.com/a_Psx_TQyLsZ5S5ybBCtyK2ArHwLUN4p8LjmWL3wvtNc-xQaG447M3nKViimyjAsW3pQ2GdBO8L4Jj4=w544-h544-l90-rj")
                }

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

                    $itemsSmali

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

                val onSetRatingMethod = lagMutableClass.methods.firstOrNull { m ->
                    m.parameterTypes == listOf("Landroid/support/v4/media/RatingCompat;") && m.returnType == "V"
                }
                val onCustomActionMethod = lagMutableClass.methods.firstOrNull { m ->
                    m.parameterTypes == listOf("Ljava/lang/String;", "Landroid/os/Bundle;") &&
                        m.returnType == "V" &&
                        m.name != playFromMediaIdMethod.name
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
                                const-string p1, "${resolvePlaylistId(rawId)}"
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
                                const-string p1, "${resolvePlaylistId(rawId)}"
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
                        if (onSetRatingMethod != null) {
                            appendLine("""
                                const-string v0, "MBS_ACTION_LIKE"
                                invoke-static { p1, v0 }, Landroid/text/TextUtils;->equals(Ljava/lang/CharSequence;Ljava/lang/CharSequence;)Z
                                move-result v0
                                if-eqz v0, :cond_check_play
                                new-instance v0, Landroid/support/v4/media/RatingCompat;
                                const/4 v1, 0x2
                                const/high16 v2, 0x3f800000    # 1.0f
                                invoke-direct { v0, v1, v2 }, Landroid/support/v4/media/RatingCompat;-><init>(IF)V
                                invoke-virtual { p0, v0 }, ${lagClass.type}->${onSetRatingMethod.name}(Landroid/support/v4/media/RatingCompat;)V
                                return-void

                                :cond_check_play
                            """.trimIndent())
                        } else if (onCustomActionMethod != null) {
                            appendLine("""
                                const-string v0, "MBS_ACTION_LIKE"
                                invoke-static { p1, v0 }, Landroid/text/TextUtils;->equals(Ljava/lang/CharSequence;Ljava/lang/CharSequence;)Z
                                move-result v0
                                if-eqz v0, :cond_check_play
                                const-string v0, "thumbs_up_action"
                                invoke-virtual { p0, v0, p2 }, ${lagClass.type}->${onCustomActionMethod.name}(Ljava/lang/String;Landroid/os/Bundle;)V
                                return-void

                                :cond_check_play
                            """.trimIndent())
                        }

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
                            if-nez p2, :cond_bundle_has_bundle
                            new-instance p2, Landroid/os/Bundle;
                            invoke-direct { p2 }, Landroid/os/Bundle;-><init>()V
                            :cond_bundle_has_bundle
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
        // Bypass entitlement check (onPlayEntitlementCheck / hak.a / lbg.b) and player state routing (hak.h / lbg.i)
        // so that external playback requests unconditionally enqueue and trigger playback.
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
                val returnType = lbgPlayMethod.returnType

                // A. Patch entitlement check method (hak.a / lbg.b) called by onPlay():
                // Find resultClass (gzs / kym) that defines the success/error MediaSessionResult fields:
                val resultClass = lbgPlayMethod.implementation?.instructions?.mapNotNull {
                    ((it as? ReferenceInstruction)?.reference as? FieldReference)
                }?.firstOrNull { it.type == returnType && it.name == "a" }?.definingClass

                if (resultClass != null) {
                    val entitlementCheckMethod = lbgMutableClass.methods.firstOrNull { m ->
                        m.parameterTypes.isEmpty() && m.returnType == returnType &&
                            m.implementation?.instructions?.any { insn ->
                                val fr = (insn as? ReferenceInstruction)?.reference as? FieldReference
                                fr?.definingClass == resultClass && fr.name == "d"
                            } == true
                    }

                    if (entitlementCheckMethod?.implementation != null) {
                        val count = entitlementCheckMethod.implementation!!.instructions.count()
                        if (count >= 2) {
                            entitlementCheckMethod.replaceInstruction(0, "sget-object v0, $resultClass->a:$returnType")
                            entitlementCheckMethod.replaceInstruction(1, "return-object v0")
                            for (idx in 2 until count) {
                                entitlementCheckMethod.replaceInstruction(idx, "nop")
                            }
                        }
                    }
                }

                // B. In playMethod (hak.h / lbg.i):
                val impl = lbgPlayMethod.implementation!!
                val insns = impl.instructions.toList()

                // 1. Bypass sign-in check (move-result before first return-object):
                val firstReturnIdx = insns.indexOfFirst { it.opcode == Opcode.RETURN_OBJECT }
                if (firstReturnIdx in 1..50) {
                    for (i in (firstReturnIdx - 1) downTo maxOf(0, firstReturnIdx - 10)) {
                        val insn = insns[i]
                        if (insn.opcode == Opcode.MOVE_RESULT) {
                            val reg = (insn as OneRegisterInstruction).registerA
                            lbgPlayMethod.replaceInstruction(i, "const/4 v$reg, 0x1")
                            break
                        }
                    }
                }

                // 2. Bypass active-player jump to 402 and route directly to skip_entitlement_check branch:
                var skipStrIdx = -1
                for (i in insns.indices) {
                    val strRef = ((insns[i] as? ReferenceInstruction)?.reference as? StringReference)?.string
                    if (strRef == "skip_entitlement_check") {
                        skipStrIdx = i
                        break
                    }
                }

                if (skipStrIdx != -1) {
                    // Preceding 2 MOVE_RESULT instructions are player active/playing checks (p() and o()):
                    var mr2Idx = -1
                    var mr1Idx = -1
                    for (i in (skipStrIdx - 1) downTo 0) {
                        if (insns[i].opcode == Opcode.MOVE_RESULT) {
                            if (mr2Idx == -1) {
                                mr2Idx = i
                            } else if (mr1Idx == -1) {
                                mr1Idx = i
                                break
                            }
                        }
                    }

                    if (mr1Idx != -1) {
                        val reg1 = (insns[mr1Idx] as OneRegisterInstruction).registerA
                        lbgPlayMethod.replaceInstruction(mr1Idx, "const/4 v$reg1, 0x0")
                    }
                    if (mr2Idx != -1) {
                        val reg2 = (insns[mr2Idx] as OneRegisterInstruction).registerA
                        lbgPlayMethod.replaceInstruction(mr2Idx, "const/4 v$reg2, 0x0")
                    }

                    // 3. Force move-result after getBoolean("skip_entitlement_check") to 1 (true)
                    // so it jumps directly to playlist enqueue (Labgu.g) and play (Labhc.ab):
                    for (j in (skipStrIdx + 1) until minOf(skipStrIdx + 6, insns.size)) {
                        if (insns[j].opcode == Opcode.MOVE_RESULT) {
                            val regSkip = (insns[j] as OneRegisterInstruction).registerA
                            lbgPlayMethod.replaceInstruction(j, "const/4 v$regSkip, 0x1")
                            break
                        }
                    }
                }
            }
        }

        // 9. Enable Like / Thumbs Up button in PlaybackStateCompat:
        // YouTube Music natively supports liking songs from external controllers via CustomAction("thumbs_up_action")
        // and PlaybackStateCompat.ACTION_SET_RATING (512), but suppresses both in external/driving mode.
        // A. Patch h() on ThumbsUpAction (liv) to return true for thumbs_up_action,
        // and suppress Waze action subclass (lkd).
        val livClass = classDefByStrings("thumbs_up_action").firstOrNull { clazz ->
            clazz.methods.any { m ->
                m.returnType == "Ljava/lang/String;" &&
                    m.parameterTypes.isEmpty() &&
                    m.implementation?.instructions?.any { insn ->
                        ((insn as? ReferenceInstruction)?.reference as? StringReference)?.string == "thumbs_up_action"
                    } == true
            }
        }

        if (livClass != null) {
            val livMutableClass = mutableClassDefBy(livClass)
            val hMethod = livMutableClass.methods.firstOrNull { m ->
                m.returnType == "Z" && m.parameterTypes.isEmpty()
            }

            if (hMethod?.implementation != null) {
                val count = hMethod.implementation!!.instructions.count()
                if (count >= 2) {
                    hMethod.replaceInstruction(0, "const/4 v0, 0x1")
                    hMethod.replaceInstruction(1, "return v0")
                    for (idx in 2 until count) {
                        hMethod.replaceInstruction(idx, "nop")
                    }
                }

                // If Waze action subclass (lkd) exists, suppress it by having its h() return false
                val lkdClass = classDefByStrings("waze.thumbUp").firstOrNull { clazz ->
                    clazz.superclass == livClass.type
                }
                if (lkdClass != null) {
                    hMethod.accessFlags = hMethod.accessFlags and AccessFlags.FINAL.value.inv()
                    val lkdMutableClass = mutableClassDefBy(lkdClass)
                    lkdMutableClass.methods.add(
                        ImmutableMethod(
                            lkdMutableClass.type,
                            hMethod.name,
                            emptyList(),
                            "Z",
                            AccessFlags.PUBLIC.value,
                            null,
                            null,
                            MutableMethodImplementation(2)
                        ).toMutable().apply {
                            addInstructionsWithLabels(0, "const/4 v0, 0x0\nreturn v0")
                        }
                    )
                }
            }
        }

        // B. In MediaSessionDrivingModeActions (kzr) & MediaSessionActions (ljg):
        // Ensure ACTION_SET_RATING (512) is unconditionally added to PlaybackStateCompat actions bitmask,
        // and ensure kzr.e() always returns the actions list containing thumbs_up_action (field k)
        // rather than the driving list that omits it (field l).
        val kzrClass = classDefByStrings("com.google.android.gms.car.media.ALWAYS_RESERVE_SPACE_FOR.ACTION_SKIP_TO_PREVIOUS").firstOrNull()
            ?: classDefByStrings("waze.state.isThumbUp").firstOrNull()

        val ljgClass = classDefByStrings("MEDIA_BUTTON_PLACEMENT_SWAP_LIKE_FOR_SHUFFLE").firstOrNull()
            ?: classDefByStrings("MEDIA_BUTTON_PLACEMENT_SEEK_FOCUSED_SWAP_SKIP").firstOrNull()

        if (kzrClass != null) {
            val kzrMutableClass = mutableClassDefBy(kzrClass)

            // Force ACTION_SET_RATING (512 / 0x200) into actions in kzr.a(baxh, long):
            val kzrAMethod = kzrMutableClass.methods.firstOrNull { m ->
                m.parameterTypes.size == 2 &&
                    m.parameterTypes[1] == "J" &&
                    m.returnType == "J"
            }
            if (kzrAMethod?.implementation != null) {
                kzrAMethod.addInstructionsWithLabels(0, """
                    const-wide/16 v0, 0x200
                    or-long/2addr p2, v0
                """.trimIndent())
            }

            // In kzr.e(): ensure field k (containing thumbs_up_action) is returned instead of field l
            if (ljgClass != null) {
                val kzrEMethod = kzrMutableClass.methods.firstOrNull { m ->
                    m.parameterTypes.isEmpty() && m.returnType == "Ljava/util/List;"
                }
                if (kzrEMethod?.implementation != null) {
                    val eImpl = kzrEMethod.implementation!!
                    val kField = ljgClass.fields.firstOrNull { it.name == "k" }
                    val lField = ljgClass.fields.firstOrNull { it.name == "l" }

                    if (kField != null && lField != null) {
                        for (idx in 0 until eImpl.instructions.count()) {
                            val insn = eImpl.instructions.elementAt(idx)
                            val ref = (insn as? ReferenceInstruction)?.reference as? FieldReference ?: continue
                            if (ref.definingClass == ljgClass.type && ref.name == lField.name) {
                                val regA = (insn as TwoRegisterInstruction).registerA
                                val regB = insn.registerB
                                kzrEMethod.replaceInstruction(idx, "iget-object v$regA, v$regB, ${ljgClass.type}->${kField.name}:${kField.type}")
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * YouTube Music rejects bare radio prefixes such as "RDTM". Map them to complete, playable playlist IDs.
 */
private fun resolvePlaylistId(id: String): String = when (id) {
    "RDTM" -> "RDTMAK5uy_kset8DisdE7LSD4TNjEVvrKRTmG7a56sY"
    else -> id
}
