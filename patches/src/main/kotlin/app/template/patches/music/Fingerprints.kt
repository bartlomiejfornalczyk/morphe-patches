package app.template.patches.music

import app.morphe.patcher.Fingerprint

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
 * Matches MediaBrowserItemFactory / MediaItemEncoding (mxt) in YouTube Music.
 * Encodes WatchEndpoint commands into playable mediaId strings.
 */
object MediaBrowserCustomActionsFingerprint : Fingerprint(
    strings = listOf("com.google.android.apps.youtube.music.mediabrowser.custom_actions")
)
