rootProject.name = "AISuper"

pluginManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}


includeBuild("../packages/rpc-core")
include(":composeApp")
include(":androidApp")
include(":shared")
include(":server")
include(":applet-provider")
include(":lanternaApp")
include(":native-bridge")

includeBuild("../externals/keight") {
    name = "keightBuild"
    dependencySubstitution {
        substitute(module("io.github.alexzhirkevich:keight")).using(project(":keight"))
        substitute(module("io.github.alexzhirkevich:keight-core")).using(project(":keight-core"))
    }
}
