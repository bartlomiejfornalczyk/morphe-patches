package app.template.patches.music

import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.resourcePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod.Companion.toMutable
import app.template.patches.shared.Constants.COMPATIBILITY_YOUTUBE_MUSIC
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.MutableMethodImplementation
import com.android.tools.smali.dexib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexib2.iface.reference.FieldReference
import com.android.tools.smali.dexib2.iface.reference.MethodReference

/**
 * Example patch for enhanced functionality that modifies the UI.
 * This demonstrates a complex patch implementation.
 */
val enhancedPatchExample = bytecodePatch("Enhanced patch example") {
    // Add basic compatibility for YouTube Music
    compatibleWith(COMPATIBILITY_YOUTUBE_MUSIC)

    // Simple example that shows correct working code instead of comments
    before("com.google.android.apps.youtube.music.ui.musicbrowser.MusicBrowserActivity;", "onCreate", "Landroid/os/Bundle;") {
        val method = toMutable()
        val implementation = method.implementation as MutableMethodImplementation
        implementation.addInstructionsWithLabels(Opcode.RETURN_VOID, 0)
        method.implementation = implementation
    }
}