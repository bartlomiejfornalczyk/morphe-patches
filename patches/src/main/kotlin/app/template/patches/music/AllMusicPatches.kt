package app.template.patches.music

import app.template.patches.music.allowExternalMediaBrowserManifestPatch
import app.template.patches.music.enhancedGridPatch
import app.template.patches.music.enhancedPatchExample
import app.template.patches.music.standardizedUIPatch

/**
 * All YouTube Music patches that provide enhanced UI layouts and grid configurations.
 */
val musicPatches = listOf(
    allowExternalMediaBrowserManifestPatch,
    enhancedGridPatch,
    enhancedPatchExample,
    standardizedUIPatch
)