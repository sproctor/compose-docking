plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    jvmToolchain(21)
    jvm()
    wasmJs {
        browser()
        // Compose 1.12 checks that a wasmJs target carrying Compose UI tests bundles an
        // executable, or the Skiko runtime the tests need is never loaded (CMP-4906).
        binaries.executable()
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":docking-core"))
            implementation(compose.runtime)
            implementation(compose.ui)
            // api: PropsDemoState's JSON round-trip is part of the shared surface
            api(libs.kotlinx.serialization.json)
        }
    }
}

// Same as the libraries (see docking.kmp-library.gradle.kts): the executable is declared
// for the Compose test check, but this module is shared demo code rather than the browser
// application. demo-material3 is what builds a bundle anyone loads.
tasks.matching {
    it.name in setOf(
        "compileProductionExecutableKotlinWasmJsOptimize",
        "wasmJsProductionExecutableCompileSync",
        "wasmJsBrowserProductionWebpack",
        "wasmJsBrowserDistribution",
    )
}.configureEach { enabled = false }
