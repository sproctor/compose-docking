plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    id("docking.published-library")
}

kotlin {
    explicitApi()
    jvmToolchain(21)

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
    api(libs.jewel.int.ui.standalone)
    api(libs.jewel.int.ui.decorated.window)
    implementation(libs.intellij.icons)
    implementation(compose.desktop.common)
}
