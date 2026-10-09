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

/** org.ungoogled.ui.Screens: opens the extension's screens, inside a host when they are not declared. */
private const val SCREENS = "Lorg/ungoogled/ui/Screens;"
/** androidx.core's AppComponentFactory, which Maps' manifest names: it creates every Activity of the app. */
private const val COMPONENT_FACTORY = "androidx.core.app.CoreComponentFactory"
/** Stock Maps' open-source licences screen, Screens.HOST. */
private const val SCREEN_HOST = "com.google.android.libraries.social.licenses.LicenseActivity"

/**
 * The screen host works from stock Maps' manifest, so it relies on two things in it: the
 * component factory it names, and the licences screen declared as a plain Activity private
 * to the app.
 */
private val screenHostManifestPatch = app.morphe.patcher.patch.resourcePatch(
    description = "Checks the component factory and the screen host in Maps' manifest.",
) {
    execute {
        document("AndroidManifest.xml").use { manifest ->
            val application = manifest.getElementsByTagName("application").item(0) as org.w3c.dom.Element
            val factory = application.getAttribute("android:appComponentFactory")
            if (factory != COMPONENT_FACTORY) throw PatchException("the manifest's component factory is '$factory', not $COMPONENT_FACTORY")
            val activities = manifest.getElementsByTagName("activity")
            val host = (0 until activities.length).map { activities.item(it) as org.w3c.dom.Element }
                .singleOrNull { it.getAttribute("android:name") == SCREEN_HOST }
                ?: throw PatchException("the manifest no longer declares $SCREEN_HOST")
            val unexpected = listOf(
                "android:process", "android:launchMode", "android:taskAffinity", "android:enabled",
                "android:noHistory", "android:excludeFromRecents", "android:screenOrientation", "android:permission",
            ).filter(host::hasAttribute)
            if (host.getAttribute("android:exported") != "false" || unexpected.isNotEmpty()) {
                throw PatchException("$SCREEN_HOST is no longer a plain private Activity ($unexpected)")
            }
        }
    }
}

/**
 * Root "mount" installs (issue #25): Morphe Manager bind-mounts the patched APK over stock
 * Maps' base.apk, and Android keeps the manifest it parsed from the stock one, so none of the
 * extension's Activities exist there. Screens then opens them through stock Maps' licences
 * screen, with an action naming the screen, and the app's component factory -- hooked here --
 * creates the extension's class in the host's place:
 *
 *     instantiateActivity(classLoader, className, intent)
 *  -> className = Screens.activityFor(className, intent), then as before
 *
 * Any other Activity, the licences screen opened by Maps included, keeps its class.
 */
internal val screenHostPatch = bytecodePatch(
    description = "Opens the extension's screens inside an Activity stock Maps declares, for root mount installs.",
) {
    dependsOn(sharedExtensionPatch, screenHostManifestPatch)

    execute {
        val factory = "L${COMPONENT_FACTORY.replace('.', '/')};"
        val method = mutableClassDefBy(factory).methods.singleOrNull {
            it.name == "instantiateActivity" && it.returnType == "Landroid/app/Activity;" &&
                it.parameterTypes.map(CharSequence::toString) ==
                listOf("Ljava/lang/ClassLoader;", "Ljava/lang/String;", "Landroid/content/Intent;")
        } ?: throw PatchException("$factory->instantiateActivity not found")
        if (method.implementation!!.instructions.first().location.labels.isNotEmpty()) {
            throw PatchException("instantiateActivity starts at a branch target")
        }
        method.addInstructions(
            0,
            """
                invoke-static { p2, p3 }, $SCREENS->activityFor(Ljava/lang/String;Landroid/content/Intent;)Ljava/lang/String;
                move-result-object p2
            """,
        )
    }
}

