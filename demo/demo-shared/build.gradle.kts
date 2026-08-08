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
