package app.template.patches.maps.renaming

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.removeInstruction
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.smali.ExternalLabel
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction10t

private const val GENUINE_PACKAGE = "com.google.android.apps.maps"
private const val GENUINE_CERT = "38918A453D07199354F8B19AF05EC6562CED5788"

@Suppress("unused")
val restoreMapDataPatch = bytecodePatch(
    name = "Restore map data",
    description = "Lets a re-signed Maps load tiles, search and routing, by sending Google's own package and certificate.",
    default = true,
) {
    compatibleWith(app.template.patches.shared.Constants.COMPATIBILITY_GOOGLE_MAPS)

    execute {
        val headers = IdentityHeadersFingerprint.instructionMatches
        val method = IdentityHeadersFingerprint.method
        val impl = method.implementation!!
        
        listOf(
            headers[1] to GENUINE_PACKAGE,
            headers[3] to GENUINE_CERT,
        ).forEach { (invokeMatch, genuineValue) ->
            val invokeIndex = invokeMatch.index
            val moveResultInsn = impl.instructions.elementAt(invokeIndex + 1) as com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
            val destReg = moveResultInsn.registerA
            method.replaceInstruction(
                invokeIndex + 1,
                "const-string v$destReg, \"$genuineValue\""
            )
        }

        GetRemoteServiceFingerprint.method.apply {
            val tryBlocks = implementation!!.tryBlocks
            fun handlerFor(type: String) = tryBlocks
                .firstOrNull { it.exceptionHandler.exceptionType == type }
                ?.exceptionHandler?.handler?.location
                ?: throw PatchException("getRemoteService has no $type handler")

            val securityHandler = handlerFor("Ljava/lang/SecurityException;")
            val degradeHandler = handlerFor("Landroid/os/RemoteException;").instruction
                ?: throw PatchException("RemoteException handler has no instruction")

            val throwIndex = securityHandler.index + 1
            if (getInstruction(throwIndex).opcode != Opcode.THROW) {
                throw PatchException("SecurityException handler no longer just rethrows")
            }
            removeInstruction(throwIndex)
            addInstructionsWithLabels(
                throwIndex,
                "goto :degrade",
                ExternalLabel("degrade", degradeHandler),
            )
        }

        ViewPropertyBinderFingerprint.method.apply {
            val tryBlock = implementation!!.tryBlocks.singleOrNull { it.exceptionHandler.exceptionType == "Ljava/lang/Exception;" }
                ?: throw PatchException("property binder no longer has exactly one Exception handler")
            val handler = tryBlock.exceptionHandler.handler.location.index
            val increment = tryBlock.end.location.index
            val instructions = implementation!!.instructions
            if (instructions[handler].opcode != Opcode.MOVE_EXCEPTION) throw PatchException("property binder's handler no longer starts with move-exception")
            if (instructions[increment].opcode != Opcode.ADD_INT_LIT8) throw PatchException("property binder's try range is no longer followed by the loop increment")
            addInstructionsWithLabels(
                handler + 1,
                "goto :increment",
                ExternalLabel("increment", instructions[increment]),
            )
        }

        val apiKeyMethod = ApiKeyReaderFingerprint.method
        val isStatic = com.android.tools.smali.dexlib2.AccessFlags.STATIC.isSet(apiKeyMethod.accessFlags)
        val contextReg = if (isStatic) "p0" else "p1"
        val stringReg = if (isStatic) "p1" else "p2"
        apiKeyMethod.addInstructions(
            0,
            """
                invoke-virtual { $contextReg }, Landroid/content/Context;->getPackageName()Ljava/lang/String;
                move-result-object $stringReg
            """
        )
    }
}
