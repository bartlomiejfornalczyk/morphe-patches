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
 * Matches MusicBrowserController.onLoadChildren (kzf.c) in YouTube Music.
 * Distinctive constant "PLACEHOLDER_RECENTLY_PLAYED_MEDIA_ID" is used to build
 * local playable items for recents playback.
 */
object MusicBrowserControllerFingerprint : Fingerprint(
    strings = listOf("PLACEHOLDER_RECENTLY_PLAYED_MEDIA_ID")
)

/**
 * Matches MusicMediaSessionPlayerController.playFromMediaItem (lbg.i) in YouTube Music.
 * Handles playback execution for media session callbacks (including onPlayFromMediaId).
 */
object MusicMediaSessionPlayerFingerprint : Fingerprint(
    strings = listOf("EXTRA_START_PAUSED", "android.media.session.extra.LEGACY_STREAM_TYPE")
)

