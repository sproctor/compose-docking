plugins {
    id("docking.kmp-library")
    id("docking.published-library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":docking-core"))
            implementation(compose.material3)
            implementation(compose.components.resources)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}

compose.resources {
    // Icons ship with the adapter, so the generated Res class is internal to it.
    publicResClass = false
    packageOfResClass = "com.seanproctor.docking.material3.generated.resources"
    generateResClass = auto
}
