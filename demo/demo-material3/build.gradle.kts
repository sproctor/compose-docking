plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    jvmToolchain(21)
    jvm()
    wasmJs {
        browser()
        binaries.executable()
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":docking-core"))
            implementation(project(":docking-material3"))
            implementation(project(":demo:demo-shared"))
            implementation(compose.material3)
            implementation(compose.materialIconsExtended)
            implementation(compose.components.resources)
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

compose.resources {
    publicResClass = false
    packageOfResClass = "com.seanproctor.docking.demo.generated.resources"
    generateResClass = auto
}
