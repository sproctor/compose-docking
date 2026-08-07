plugins {
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.kotlinJvm) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.kotlinSerialization) apply false
    alias(libs.plugins.androidLibrary) apply false
    alias(libs.plugins.mavenPublish) apply false
    alias(libs.plugins.binaryCompat)
}

apiValidation {
    // Jewel offers no binary-compatibility guarantees between its own releases,
    // so pinning our adapter's ABI would be churn without benefit.
    ignoredProjects += listOf("docking-jewel", "demo-material3", "demo-jewel")
}
