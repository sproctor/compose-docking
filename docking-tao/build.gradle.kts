plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    id("docking.published-library")
}

kotlin {
    explicitApi()
    jvmToolchain(21)
    compilerOptions {
        optIn.add("com.seanproctor.docking.spi.DockingWindowHostApi")
        // The module's own experimental API, which it uses throughout.
        optIn.add("com.seanproctor.docking.tao.ExperimentalDockingTaoApi")
    }

    // Same validation the KMP modules get from docking.kmp-library; this module is
    // JVM-only, so it does not use that convention plugin.
    @OptIn(org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation::class)
    abiValidation {
        filters {
            exclude {
                byNames.add("**.ComposableSingletons**")
            }
        }
    }
}

dependencies {
    api(project(":docking-core"))
    // NucleusApplicationScope and NucleusDecoratedWindowScope are in the public API.
    api(libs.nucleus.application)
    implementation(libs.nucleus.decorated.window.tao)
    implementation(compose.desktop.common)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.core)
    testImplementation(compose.desktop.currentOs)
    testImplementation(compose.desktop.uiTestJUnit4)
}
