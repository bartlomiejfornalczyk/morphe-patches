package app.template.patches.music

import app.morphe.patcher.Fingerprint

/**
 * Matches MusicBrowserService.onGetRoot in YouTube Music.
 * MusicBrowserService is declared in AndroidManifest.xml and is never obfuscated.
 * onGetRoot is the entry point that authenticates connecting media clients (e.g. Google Maps, Android Auto).
 */
object MusicBrowserServiceFingerprint : Fingerprint(
    definingClass = "Lcom/google/android/apps/youtube/music/mediabrowser/MusicBrowserService;",
    parameters = listOf("Ljava/lang/String;", "I", "Landroid/os/Bundle;")
)
