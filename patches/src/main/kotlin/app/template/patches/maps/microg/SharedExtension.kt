package app.template.patches.maps.microg

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.template.patches.shared.addInstructionsAtLabel
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.methodCall
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.string
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.NarrowLiteralInstruction
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction35c

private const val SHAPES = "Lorg/ungoogled/ui/Shapes;"

val sharedExtensionPatch = bytecodePatch(
    description = "Adds the MicroG runtime extension classes.",
) {
    extendWith("extensions/extension.mpe")
}

internal fun BytecodePatchContext.markPatched(marker: String) {
    val method = mutableClassDefBy(SHAPES).methods.singleOrNull {
        it.name == marker && it.parameterTypes.isEmpty() && it.returnType == "Z"
    } ?: throw PatchException("extension marker $SHAPES->$marker() not found")
    val first = method.implementation!!.instructions.first()
    if (first.opcode != Opcode.CONST_4 || (first as NarrowLiteralInstruction).narrowLiteral != 0) {
        throw PatchException("$marker() no longer starts with const/4 v0, 0x0")
    }
    method.replaceInstruction(0, "const/4 v0, 0x1")
}

internal object AppCompatAttachBaseContextFingerprint : Fingerprint(
    name = "attachBaseContext",
    returnType = "V",
    parameters = listOf("Landroid/content/Context;"),
    filters = listOf(
        string("rebase"),
        methodCall(opcode = Opcode.INVOKE_SUPER, name = "attachBaseContext"),
    ),
)

internal val activityContextHookPatch = bytecodePatch(
    description = "Routes every Activity's base context through the extension.",
) {
    dependsOn(sharedExtensionPatch)

    execute {
        AppCompatAttachBaseContextFingerprint.let { fp ->
            val superCall = fp.instructionMatches.last().index
            val registers = (fp.method.implementation!!.instructions[superCall] as Instruction35c)
            if (registers.registerCount != 2) throw PatchException("super.attachBaseContext takes ${registers.registerCount} registers")
            val context = "v${registers.registerD}"
            fp.method.addInstructionsAtLabel(
                superCall,
                """
                    invoke-static { $context }, $SHAPES->wrap(Landroid/content/Context;)Landroid/content/Context;
                    move-result-object $context
                """,
            )
        }
    }
}

internal object ApplicationAttachBaseContextFingerprint : Fingerprint(
    name = "attachBaseContext",
    returnType = "V",
    parameters = listOf("Landroid/content/Context;"),
    filters = listOf(string("CommonGoogleMapsApplication.attachBaseContext")),
)

internal val applicationStartHookPatch = bytecodePatch(
    description = "Applies the extension's process-wide settings when the app starts.",
) {
    dependsOn(sharedExtensionPatch)

    execute {
        ApplicationAttachBaseContextFingerprint.method.apply {
            val first = implementation!!.instructions.first()
            if (first.location.labels.isNotEmpty()) throw PatchException("Application.attachBaseContext starts at a branch target")
            val context = "p1"
            addInstructions(
                0,
                "invoke-static { $context }, $SHAPES->processStart(Landroid/content/Context;)V",
            )
        }
    }
}
