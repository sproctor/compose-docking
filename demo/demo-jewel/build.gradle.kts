plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    // Jewel's `-262.*` builds (IntelliJ platform 2026.2) ship Java 25 bytecode - every
    // class in jewel-ui/-foundation is major version 69 - so this demo needs a 25
    // toolchain. Its `-261.*` builds are Java 21 if that ever needs to come back down.
    jvmToolchain(25)
}

dependencies {
    implementation(project(":docking-core"))
    implementation(project(":docking-jewel"))
    implementation(project(":demo:demo-shared"))
    implementation(compose.desktop.currentOs) {
        exclude(group = "org.jetbrains.compose.material")
    }
}

// The Compose run/package tasks default to the daemon JVM, but Jewel needs 25+. Prefer a
// JetBrains Runtime: other 25 builds scale the Swing MenuBar font twice on HiDPI screens.
// This resolves during configuration, which every task in the build triggers, so it falls
// back to any 25 rather than failing the whole build where no JBR can be provisioned.
val jewelRuntime: String? = sequenceOf(JvmVendorSpec.JETBRAINS, null)
    .mapNotNull { preferredVendor ->
        runCatching {
            javaToolchains.launcherFor {
                languageVersion = JavaLanguageVersion.of(25)
                preferredVendor?.let { vendor = it }
            }.get().metadata.installationPath.asFile.absolutePath
        }.getOrNull()
    }
    .firstOrNull()

compose.desktop {
    application {
        mainClass = "com.seanproctor.docking.demo.MainKt"
        jewelRuntime?.let { javaHome = it }
    }
}
