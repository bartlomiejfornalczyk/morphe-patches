package app.template.patches.music

import app.morphe.patcher.Fingerprint
import com.android.tools.smali.dexlib2.AccessFlags

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
 * Static method g encodes WatchEndpoint commands into playable mediaId strings.
 */
object MediaBrowserCustomActionsFingerprint : Fingerprint(
    name = "g",
    returnType = "Ljava/lang/String;",
    parameters = listOf("Ljava/lang/String;"),
    strings = listOf("com.google.android.apps.youtube.music.mediabrowser.custom_actions"),
    custom = { method, _ ->
        (method.accessFlags and AccessFlags.STATIC.value != 0)
    }
)

