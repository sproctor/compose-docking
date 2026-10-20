plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    // Jewel's `-262.*` builds ship Java 25 bytecode, as in demo-jewel.
    jvmToolchain(25)
}

dependencies {
    implementation(project(":docking-core"))
    implementation(project(":docking-jewel"))
    implementation(project(":docking-tao"))
    implementation(project(":demo:demo-shared"))
    implementation(compose.desktop.currentOs) {
        exclude(group = "org.jetbrains.compose.material")
    }
    implementation(libs.nucleus.application)
    implementation(libs.nucleus.decorated.window.tao)
}

// Nucleus windows need no JetBrains Runtime (no AWT in the process), but Jewel's classes
// are Java 25, so any 25 will do.
val runtime25: String? =
    runCatching {
        javaToolchains.launcherFor { languageVersion = JavaLanguageVersion.of(25) }.get()
            .metadata.installationPath.asFile.absolutePath
    }.getOrNull()

compose.desktop {
    application {
        mainClass = "com.seanproctor.docking.demo.MainKt"
        runtime25?.let { javaHome = it }
    }
}
