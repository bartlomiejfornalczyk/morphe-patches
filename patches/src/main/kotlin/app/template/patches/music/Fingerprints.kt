package app.template.patches.music

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.fieldAccess
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode

/**
 * Matches MusicBrowserService.onGetRoot in YouTube Music.
 * MusicBrowserService is declared in AndroidManifest.xml and is never obfuscated.
 * onGetRoot is the entry point that authenticates connecting media clients (e.g. Google Maps, Android Auto).
 * Matches by the distinctive log string "Client not allowlisted" present across all YTM versions.
 */
object MusicBrowserServiceFingerprint : Fingerprint(
    definingClass = "Lcom/google/android/apps/youtube/music/mediabrowser/MusicBrowserService;",
    strings = listOf("Client not allowlisted")
)

/**
 * Matches LoadChildrenResult (lkh) in YouTube Music.
 * Delivers MediaBrowserCompat.MediaItem results back to connecting clients.
 */
object LoadChildrenResultFingerprint : Fingerprint(
    strings = listOf("com/google/android/apps/youtube/music/mediabrowser/results/LoadChildrenResult")
)

/**
 * Matches MusicMediaSessionCallback (lag) in YouTube Music.
 * Handles MediaSession transport controls and onPlayFromMediaId.
 */
object MediaSessionCallbackFingerprint : Fingerprint(
    strings = listOf("MSC: onPlayFromMediaId(). appPkg: '%s' mediaId: '%s'")
)

/**
 * Matches MediaBrowserItemFactory / MediaItemEncoding (mxt / ixx) in YouTube Music.
 * Distinctive constant "offline_PPSV" is uniquely present in this class across all architectures.
 */
object MediaBrowserCustomActionsFingerprint : Fingerprint(
    strings = listOf("offline_PPSV")
)

/**
 * Bypasses caller certificate verification for Google partner clients (Android Auto, Google Maps).
 */
object CheckCertificateFingerprint : Fingerprint(
    returnType = "Z",
    parameters = listOf("L"),
    strings = listOf(
        "X509",
        "isPartnerSHAFingerprint"
    )
)

/**
 * Anchors IsGoogleSignedFingerprint to the class containing Google certificates remote fetch logic.
 */
object GoogleCertificatesRemoteFingerprint : Fingerprint(
    returnType = "L",
    parameters = listOf("Ljava/lang/String;"),
    strings = listOf(
        "Failed to get Google certificates from remote"
    )
)

/**
 * Checks whether the caller is Google-signed.
 */
object IsGoogleSignedFingerprint : Fingerprint(
    classFingerprint = GoogleCertificatesRemoteFingerprint,
    returnType = "Z",
    parameters = listOf("Ljava/lang/String;")
)

/**
 * YTM's playback command builder for playlists and watch endpoints.
 * Accepts a playlist ID without a video ID, creating a WatchEndpoint command.
 */
fun playlistPlaybackCommandFingerprint(commandType: String) = Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC, AccessFlags.FINAL),
    returnType = "L",
    parameters = listOf(
        "Ljava/lang/String;", "Ljava/lang/String;", "I", "F",
        "Ljava/lang/String;", "Ljava/lang/String;", "Z"
    ),
    filters = listOf(
        fieldAccess(
            definingClass = commandType,
            type = commandType,
            opcode = Opcode.SGET_OBJECT
        )
    )
)



