package app.template.patches.maps.navigation

import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.booleanOption
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.resourcePatch
import app.morphe.patcher.patch.stringOption
import app.template.patches.shared.Constants.COMPATIBILITY_GOOGLE_MAPS
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.NarrowLiteralInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import org.w3c.dom.Element

val allowMorpheMusicManifestPatch = resourcePatch(
    name = "Allow Morphe YouTube Music package visibility",
    description = "Adds package queries and permission to AndroidManifest.xml for full media apps visibility.",
    default = true
) {
    compatibleWith(COMPATIBILITY_GOOGLE_MAPS)

    execute {
        document("AndroidManifest.xml").use { doc ->
            val manifest = doc.documentElement
            val androidNs = "http://schemas.android.com/apk/res/android"

            // 1. Grant QUERY_ALL_PACKAGES so Android OS never hides any media apps
            val queryAllPerm = doc.createElement("uses-permission").apply {
                setAttribute("android:name", "android.permission.QUERY_ALL_PACKAGES")
                setAttributeNS(androidNs, "android:name", "android.permission.QUERY_ALL_PACKAGES")
            }
            manifest.appendChild(queryAllPerm)

            // 2. Add queries for all YouTube Music variants + generic MediaBrowserService intent
            val queriesNodes = doc.getElementsByTagName("queries")
            val queries: Element = if (queriesNodes.length > 0) {
                queriesNodes.item(0) as Element
            } else {
                val newQueries = doc.createElement("queries")
                manifest.appendChild(newQueries)
                newQueries
            }

            val packagesToAdd = listOf(
                "app.morphe.android.apps.youtube.music",
                "app.revanced.android.apps.youtube.music",
                "app.rvx.android.apps.youtube.music",
                "com.google.android.apps.youtube.music",
                "com.spotify.music"
            )

            for (pkg in packagesToAdd) {
                val pkgElement = doc.createElement("package").apply {
                    setAttribute("android:name", pkg)
                    setAttributeNS(androidNs, "android:name", pkg)
                }
                queries.appendChild(pkgElement)
            }

            // Also add generic MediaBrowserService intent filter query
            val intentElem = doc.createElement("intent")
            val actionElem = doc.createElement("action").apply {
                setAttribute("android:name", "android.media.browse.MediaBrowserService")
                setAttributeNS(androidNs, "android:name", "android.media.browse.MediaBrowserService")
            }
            intentElem.appendChild(actionElem)
            queries.appendChild(intentElem)
        }
    }
}

@Suppress("unused")
val allowMorpheMusicPatch = bytecodePatch(
    name = "Allow Morphe YouTube Music mini player",
    description = "Enables YouTube Music and modded media apps as the navigation mini player.",
    default = true
) {
    compatibleWith(COMPATIBILITY_GOOGLE_MAPS)
    dependsOn(allowMorpheMusicManifestPatch)

    val targetPackage by stringOption(
        key = "targetPackage",
        default = "app.morphe.android.apps.youtube.music",
        title = "YouTube Music package name",
        description = "Package name of your modded YouTube Music app."
    )

    val enable4x4Grid by booleanOption(
        key = "enable4x4Grid",
        default = true,
        title = "Enable 4x4 grid layout (16 tiles)",
        description = "Increases the media browser tile grid layout from the default 3x3 (9 tiles) to 4x4 (16 tiles)."
    )

    execute {
        val instructionMatch = NavigationMediaProvidersFingerprint.instructionMatches.first()
        val register = instructionMatch.getInstruction<OneRegisterInstruction>().registerA

        NavigationMediaProvidersFingerprint.method.replaceInstruction(
            instructionMatch.index,
            "const-string v$register, \"$targetPackage\""
        )

        if (enable4x4Grid == true) {
            // 1. Update MediaBrowserMediaProvider (ampr.s) & abstract base provider (amqs.ac, amqs.ae)
            // to request and display up to 16 tiles (0x10) instead of truncating at 9 tiles (0x9):
            val amprClass = MediaBrowserProviderFingerprint.classDefOrNull
            if (amprClass != null) {
                val amprMutable = mutableClassDefBy(amprClass)
                for (method in amprMutable.methods) {
                    if (method.parameterTypes.isEmpty() && method.returnType == "V" && method.implementation != null) {
                        val insns = method.implementation!!.instructions.toList()
                        for (idx in insns.indices) {
                            val insn = insns[idx]
                            if (insn.opcode == Opcode.CONST_16 && (insn as? NarrowLiteralInstruction)?.narrowLiteral == 9) {
                                val reg = (insn as OneRegisterInstruction).registerA
                                method.replaceInstruction(idx, "const/16 v$reg, 0x10")
                            }
                        }
                    }
                }

                val amqsType = amprClass.superclass
                if (amqsType != null && amqsType != "Ljava/lang/Object;") {
                    val amqsMutable = mutableClassDefBy(amqsType)
                    for (method in amqsMutable.methods) {
                        if (method.parameterTypes.isEmpty() &&
                            (method.returnType == "Ljava/lang/Integer;" || method.returnType == "Ljava/util/List;") &&
                            method.implementation != null
                        ) {
                            val insns = method.implementation!!.instructions.toList()
                            for (idx in insns.indices) {
                                val insn = insns[idx]
                                if (insn.opcode == Opcode.CONST_16 && (insn as? NarrowLiteralInstruction)?.narrowLiteral == 9) {
                                    val reg = (insn as OneRegisterInstruction).registerA
                                    method.replaceInstruction(idx, "const/16 v$reg, 0x10")
                                }
                            }
                        }
                    }
                }
            }

            // 2. Update MediaBrowserBrowseSheetLayout (amoy) from 3 columns to 4 columns
            // and standardize tile square dimensions to 64dp (0x40) so 4x4 fits cleanly:
            val amoyClass = MediaBrowserBrowseSheetFingerprint.classDefOrNull
            if (amoyClass != null) {
                val amoyMutable = mutableClassDefBy(amoyClass)

                // A. In <clinit>, adjust tile square size from 80dp (0x50) to 64dp (0x40)
                // and label top spacing from 12dp (0xc) to 6dp (0x6):
                val clinitMethod = amoyMutable.methods.firstOrNull { it.name == "<clinit>" }
                if (clinitMethod?.implementation != null) {
                    val insns = clinitMethod.implementation!!.instructions.toList()
                    for (idx in insns.indices) {
                        val insn = insns[idx]
                        val lit = (insn as? NarrowLiteralInstruction)?.narrowLiteral ?: continue
                        if (insn.opcode == Opcode.CONST_16 && lit == 80) {
                            val reg = (insn as OneRegisterInstruction).registerA
                            clinitMethod.replaceInstruction(idx, "const/16 v$reg, 0x40")
                        } else if (insn.opcode == Opcode.CONST_16 && lit == 12) {
                            val reg = (insn as OneRegisterInstruction).registerA
                            clinitMethod.replaceInstruction(idx, "const/4 v$reg, 0x6")
                        }
                    }
                }

                // B. In a() (layout builder), change GridLayoutManager column count from 3 to 4:
                val layoutMethod = amoyMutable.methods.firstOrNull { m ->
                    m.parameterTypes.isEmpty() && m.returnType.startsWith("L") && m.name != "<init>"
                }
                if (layoutMethod?.implementation != null) {
                    val insns = layoutMethod.implementation!!.instructions.toList()
                    // Identify the GridLayoutManager factory method (ayyl.aR(I)) which is called with 4:
                    val gridFactoryMethods = mutableSetOf<String>()
                    for (idx in 0 until insns.size - 1) {
                        val insn = insns[idx]
                        val next = insns[idx + 1]
                        if (insn.opcode == Opcode.CONST_4 &&
                            (insn as? NarrowLiteralInstruction)?.narrowLiteral == 4 &&
                            next.opcode == Opcode.INVOKE_STATIC
                        ) {
                            val ref = (next as? ReferenceInstruction)?.reference as? MethodReference
                            if (ref != null && ref.parameterTypes == listOf("I")) {
                                gridFactoryMethods.add("${ref.definingClass}->${ref.name}")
                            }
                        }
                    }

                    // Replace the preceding 3-column call (ayyl.aR(3)) with 4 columns (ayyl.aR(4)):
                    for (idx in 0 until insns.size - 1) {
                        val insn = insns[idx]
                        val next = insns[idx + 1]
                        if (insn.opcode == Opcode.CONST_4 &&
                            (insn as? NarrowLiteralInstruction)?.narrowLiteral == 3 &&
                            next.opcode == Opcode.INVOKE_STATIC
                        ) {
                            val ref = (next as? ReferenceInstruction)?.reference as? MethodReference
                            if (ref != null && "${ref.definingClass}->${ref.name}" in gridFactoryMethods) {
                                val reg = (insn as OneRegisterInstruction).registerA
                                layoutMethod.replaceInstruction(idx, "const/4 v$reg, 0x4")
                            }
                        }
                    }
                }
            }
        }
    }
}

