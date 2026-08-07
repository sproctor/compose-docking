plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    // Jewel 0.39+ ships Java 25 bytecode; compile and run this demo on a 25 toolchain
    // (JetBrains Runtime 25 recommended at runtime for correct font rendering).
    jvmToolchain(25)
}

dependencies {
    implementation(project(":docking-core"))
    implementation(project(":docking-jewel"))
    implementation(compose.desktop.currentOs) {
        exclude(group = "org.jetbrains.compose.material")
    }
}

compose.desktop {
    application {
        mainClass = "com.seanproctor.docking.demo.MainKt"
        // The Compose run/package tasks default to the daemon JVM; Jewel needs 25+.
        javaHome = javaToolchains.launcherFor {
            languageVersion = JavaLanguageVersion.of(25)
        }.get().metadata.installationPath.asFile.absolutePath
    }
}
