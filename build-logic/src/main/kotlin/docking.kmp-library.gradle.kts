plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.library")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

kotlin {
    explicitApi()
    jvmToolchain(21)

    jvm()
    androidTarget {
        publishLibraryVariants("release")
    }
    iosArm64()
    iosSimulatorArm64()
    wasmJs {
        browser()
    }
}

android {
    namespace = "com.seanproctor." + project.name.replace("-", ".")
    compileSdk = 36
    defaultConfig {
        minSdk = 21
    }
}
