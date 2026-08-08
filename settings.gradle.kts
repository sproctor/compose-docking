pluginManagement {
    includeBuild("build-logic")
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        // Jewel's IntelliJ platform dependencies (icons, UI data)
        maven("https://www.jetbrains.com/intellij-repository/releases")
        maven("https://packages.jetbrains.team/maven/p/kpm/public")
    }
}

rootProject.name = "compose-docking"

include(":docking-core")
include(":docking-material3")
include(":docking-jewel")
include(":demo:demo-shared")
include(":demo:demo-material3")
include(":demo:demo-jewel")
