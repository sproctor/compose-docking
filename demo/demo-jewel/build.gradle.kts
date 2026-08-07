plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    jvmToolchain(21)
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
    }
}
