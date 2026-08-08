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

// ABI validation is KGP's built-in `abiValidation`, enabled per-project by the
// docking.kmp-library convention plugin — so the published libraries opt in and the
// demos and docking-jewel simply never do. (Jewel offers no binary-compatibility
// guarantees between its own releases, so pinning our adapter's ABI would be churn.)
