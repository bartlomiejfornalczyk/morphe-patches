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
    custom = { method, _ ->
        val pCount = method.parameterTypes.size
        (pCount == 2 || pCount == 3) &&
            method.parameterTypes[0] == "Ljava/lang/String;" &&
            method.parameterTypes[pCount - 1] == "Landroid/os/Bundle;" &&
            method.returnType != "V" &&
            method.returnType != "I"
    }
)
