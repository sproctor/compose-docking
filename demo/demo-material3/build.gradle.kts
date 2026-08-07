plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    jvmToolchain(21)
    jvm()

    sourceSets {
        commonMain.dependencies {
            implementation(project(":docking-core"))
            implementation(project(":docking-material3"))
            implementation(compose.material3)
            implementation(compose.materialIconsExtended)
        }
        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
        }
    }
}

compose.desktop {
    application {
        mainClass = "com.seanproctor.docking.demo.MainKt"
    }
}
