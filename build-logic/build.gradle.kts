plugins {
    `kotlin-dsl`
}

dependencies {
    implementation(libs.gradlePlugin.kotlin)
    implementation(libs.gradlePlugin.composeCompiler)
    implementation(libs.gradlePlugin.compose)
    implementation(libs.gradlePlugin.android)
    implementation(libs.gradlePlugin.mavenPublish)
}
