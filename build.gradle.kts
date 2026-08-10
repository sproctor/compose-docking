plugins {
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.kotlinJvm) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.kotlinSerialization) apply false
    // Not applied here, but required on the root classpath so the convention plugin can
    // resolve AGP's KotlinMultiplatformAndroidComponentsExtension.
    alias(libs.plugins.androidKmpLibrary) apply false
    alias(libs.plugins.mavenPublish) apply false
}

// No ABI validation while the library is unreleased and has no users - see TODO.md for
// turning KGP's built-in `abiValidation` back on before the first published release.
