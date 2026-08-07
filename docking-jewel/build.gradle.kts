plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    id("docking.published-library")
}

kotlin {
    explicitApi()
    jvmToolchain(21)
}

dependencies {
    api(project(":docking-core"))
    api(libs.jewel.int.ui.standalone)
    api(libs.jewel.int.ui.decorated.window)
    implementation(compose.desktop.common)
}
