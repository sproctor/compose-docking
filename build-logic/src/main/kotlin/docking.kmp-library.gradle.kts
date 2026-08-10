plugins {
    id("org.jetbrains.kotlin.multiplatform")
    // AGP 9 made com.android.library incompatible with the KMP plugin in one subproject;
    // this is its KMP replacement. It is single-variant, so there is no release/debug
    // split to select and `publishLibraryVariants` no longer exists.
    id("com.android.kotlin.multiplatform.library")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

kotlin {
    explicitApi()
    jvmToolchain(21)

    jvm()
    android {
        namespace = "com.seanproctor." + project.name.replace("-", ".")
        compileSdk = 36
        minSdk = 21
    }
    iosArm64()
    iosSimulatorArm64()
    wasmJs {
        browser()
    }
}
