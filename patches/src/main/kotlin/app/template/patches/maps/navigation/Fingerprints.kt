package app.template.patches.maps.navigation

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.string
import com.android.tools.smali.dexlib2.iface.instruction.NarrowLiteralInstruction

/**
 * Fingerprint matching the navigation media provider resolution method (xzt.ux()).
 * Matches based on the hardcoded YouTube Music package and MediaBrowserService intent strings.
 */
object NavigationMediaProvidersFingerprint : Fingerprint(
    returnType = "Ljava/lang/Object;",
    filters = listOf(
        string("com.google.android.apps.youtube.music"),
        string("android.media.browse.MediaBrowserService")
    )
)

/**
 * Matches MediaBrowserMediaProvider (ampr) in Google Maps.
 * Handles fetching MediaBrowser items (default limit 9) and parsing artwork URIs.
 */
object MediaBrowserProviderFingerprint : Fingerprint(
    strings = listOf(
        "android.media.metadata.ART_URI",
        "android.media.metadata.ALBUM_ART_URI"
    )
)

/**
 * Matches MediaBrowserBrowseSheetLayout (amoy) in Google Maps.
 * Controls the 3-column GridLayoutManager and 80dp tile dimensions for the media browser grid.
 */
object MediaBrowserBrowseSheetFingerprint : Fingerprint(
    name = "<clinit>",
    returnType = "V",
    parameters = emptyList(),
    custom = { method, _ ->
        val literals = method.implementation?.instructions
            ?.filterIsInstance<NarrowLiteralInstruction>()
            ?.map { it.narrowLiteral }
            ?.toSet() ?: emptySet()
        literals.contains(167) && literals.contains(333) && literals.contains(500) && literals.contains(80)
    }
)

