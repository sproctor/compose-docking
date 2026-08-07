plugins {
    id("docking.kmp-library")
    id("docking.published-library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":docking-core"))
            implementation(compose.material3)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}
