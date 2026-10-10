package app.template.patches.maps.navigation

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.methodCall
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.intOption
import app.template.patches.shared.Constants.COMPATIBILITY_GOOGLE_MAPS
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction

/**
 * Fingerprint matching the navigation FAB layout margin calculation method.
 * In Google Maps, this method evaluates the bottom margin for the right-side
 * navigation buttons (Search, Mute, Report, Media) in NavFabContainerLayout.
 *
 * It calculates: Math.round(insetPx / density) dp.
 * However, the inset calculation is gated behind an unreleased Phenotype flag
 * (auqo.s()) that evaluates to false, returning 0dp by default.
 */
internal object NavFabBottomMarginFingerprint : Fingerprint(
    returnType = "Ljava/lang/Object;",
    filters = listOf(
        fieldAccess(
            definingClass = "Landroid/util/DisplayMetrics;",
            name = "density",
            type = "F",
        ),
        methodCall(
            opcode = Opcode.INVOKE_STATIC,
            definingClass = "Ljava/lang/Math;",
            name = "round",
            returnType = "I",
            parameters = listOf("F"),
        ),
    ),
)

@Suppress("unused")
val elevateRightNavButtonsPatch = bytecodePatch(
    name = "Elevate right navigation buttons",
    description = "Elevates the right-side navigation buttons (Search, Sound, Report, Media) " +
        "above the media player and bottom panel during turn-by-turn navigation in portrait mode.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_GOOGLE_MAPS)

    val elevationDp by intOption(
        key = "elevationDp",
        default = 165,
        title = "Elevation (dp)",
        description = "Height in dp to elevate the right navigation buttons above the media player.",
    )

    execute {
        val method = NavFabBottomMarginFingerprint.method
        val instructions = method.implementation!!.instructions
        val densityIndex = NavFabBottomMarginFingerprint.instructionMatches[0].index
        val roundIndex = NavFabBottomMarginFingerprint.instructionMatches[1].index

        // 1. Locate the Phenotype experiment flag check before DisplayMetrics.density.
        // There are two if-eqz instructions preceding density:
        //   - the orientation check (bekv.ao(context) for landscape)
        //   - the experiment check (auqo.s() gating the inset calculation)
        // The second if-eqz backwards from density is the experiment flag branch.
        val ifEqzIndices = (densityIndex - 1 downTo 0)
            .filter { instructions[it].opcode == Opcode.IF_EQZ }
            .take(2)

        if (ifEqzIndices.size < 2) {
            throw PatchException("Could not find flag branch in NavFabBottomMargin")
        }

        val flagBranchIndex = ifEqzIndices[1]

        // Replace the flag branch with nop so that portrait orientation proceeds to inset calculation.
        method.replaceInstruction(flagBranchIndex, "nop")

        // 2. Locate the move-result after Math.round(F)I to get the register holding the calculated dp.
        val moveResultIndex = roundIndex + 1
        val moveResult = instructions[moveResultIndex]
        if (moveResult.opcode != Opcode.MOVE_RESULT) {
            throw PatchException("Expected move-result after Math.round(F) at index $moveResultIndex, found ${moveResult.opcode}")
        }
        val targetReg = (moveResult as OneRegisterInstruction).registerA
        val tempReg = if (targetReg == 0) 1 else 0

        val elevation = elevationDp ?: 165

        // Elevate the bottom margin by at least elevationDp in portrait mode:
        // Math.max(calculatedDp, elevationDp)
        method.addInstructions(
            roundIndex + 2,
            """
                const/16 v$tempReg, $elevation
                invoke-static { v$targetReg, v$tempReg }, Ljava/lang/Math;->max(II)I
                move-result v$targetReg
            """.trimIndent(),
        )
    }
}
