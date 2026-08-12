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
        compileSdk = 37
        minSdk = 21
    }
    iosArm64()
    iosSimulatorArm64()
    wasmJs {
        browser()
        // Compose 1.12 checks that a wasmJs target carrying Compose UI tests bundles an
        // executable, or the Skiko runtime the tests need is never loaded (CMP-4906).
        binaries.executable()
    }
}

// Declaring that executable also hangs the browser bundling chain off `assemble` -
// Binaryen's optimizer and then a webpack run - whose output is a browser application
// built out of a library. Nothing consumes it: dependents resolve the klib, and the demo
// that is a real browser application builds its own bundle. So the chain is turned off
// here, leaving the executable itself declared, which is all the test check above wants.
//
// The whole chain has to go, not just the distribution at the end of it: a disabled task
// is skipped but its dependencies still run, so switching off only the last one would
// leave the two expensive steps behind it running for nothing.
tasks.matching {
    it.name in setOf(
        "compileProductionExecutableKotlinWasmJsOptimize",
        "wasmJsProductionExecutableCompileSync",
        "wasmJsBrowserProductionWebpack",
        "wasmJsBrowserDistribution",
    )
}.configureEach { enabled = false }
