package app.template.patches.music

import app.template.patches.music.AllowExternalMediaBrowserPatch
import app.template.patches.music.EnhancedGridPatch
import app.template.patches.music.EnhancedPatchExample
import app.template.patches.music.StandardizedUIPatch

/**
 * All YouTube Music patches that provide enhanced UI layouts and grid configurations.
 */
val musicPatches = listOf(
    AllowExternalMediaBrowserPatch,
    EnhancedGridPatch,
    EnhancedPatchExample,
    StandardizedUIPatch
)