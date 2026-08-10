plugins {
    id("org.jetbrains.kotlin.multiplatform")
    // AGP 9 made com.android.library incompatible with the KMP plugin in one subproject;
    // this is its KMP replacement. It is single-variant, so there is no release/debug
    // split to select and `publishLibraryVariants` no longer exists.
    id("com.android.kotlin.multiplatform.library")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

kotlin {
    explicitApi()
    jvmToolchain(21)

    // KGP's built-in ABI validation. The standalone binary-compatibility-validator does
    // not recognize the AGP 9 KMP Android target, so it silently stopped dumping it.
    @OptIn(org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation::class)
    abiValidation {
        filters {
            exclude {
                // Compose's generated lambda holders. Their names carry a hash that moves
                // whenever a @Composable lambda is added or reordered, so dumping them
                // would mean regenerating the reference files after unrelated edits.
                byNames.add("**.ComposableSingletons**")
            }
        }
    }

    jvm()
    android {
        namespace = "com.seanproctor." + project.name.replace("-", ".")
        compileSdk = 36
        minSdk = 21
    }
    iosArm64()
    iosSimulatorArm64()
    wasmJs {
        browser()
    }
}
