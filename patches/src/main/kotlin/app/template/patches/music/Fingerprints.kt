package app.template.patches.music

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.string

/**
 * Matches AllowlistManager in YouTube Music's MediaBrowser implementation.
 * Method g is isAllowlistedForMediaBrowser which verifies if the connecting client is allowed.
 */
object AllowlistManagerFingerprint : Fingerprint(
    returnType = "Z",
    filters = listOf(
        string("isAllowlistedForMediaBrowser failed UID check. Package: %s, UID: %d")
    )
)
