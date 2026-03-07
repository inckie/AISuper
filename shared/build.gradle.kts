plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKotlinMultiplatformLibrary)
}

kotlin {
    android {
        namespace = "com.damn.aisuper.shared"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
    }

    jvm()
    js { browser() }

    sourceSets {
        val commonMain by getting {
            dependencies {
                // Re-export the existing composeApp module so consumers depend on :shared
                api(projects.composeApp)
            }
        }
    }
}

