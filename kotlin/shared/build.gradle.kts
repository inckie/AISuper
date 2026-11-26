plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKotlinMultiplatformLibrary)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    applyDefaultHierarchyTemplate()

    android {
        namespace = "com.damn.aisuper.shared"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
    }

    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "AISuperCore"
            isStatic = true
        }
    }

    jvm()
    mingwX64()
    linuxX64()
    macosArm64()

    js { browser() }

    @OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
    wasmJs {
        browser()
    }

    sourceSets {
        val quickjsMain by creating {
            dependsOn(commonMain.get())
            dependencies {
                implementation(libs.quickjs.kt)
            }
        }

        val composeAudioMain by creating {
            dependsOn(commonMain.get())
            dependencies {
                implementation(libs.composemediaplayer.audio)
            }
        }

        val desktopNativeMain by creating {
            dependsOn(commonMain.get())
            dependsOn(quickjsMain)
        }

        commonMain.dependencies {
            api("com.damn.aisuper:rpc-core:0.1.0")
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.datetime)
            implementation(libs.keight)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
            implementation(libs.klibsKstorage)
        }

        androidMain {
            dependsOn(quickjsMain)
            dependsOn(composeAudioMain)
            dependencies {
                implementation(libs.androidx.annotation)
                implementation(libs.androidx.core)
                implementation(libs.androidx.activity)
                implementation(libs.ktor.client.android)
            }
        }

        jvmMain {
            dependsOn(quickjsMain)
            dependsOn(composeAudioMain)
            dependencies {
                implementation(libs.ktor.client.cio)
                implementation(libs.ktor.server.core)
                implementation(libs.ktor.server.netty)
                implementation(libs.ktor.server.content.negotiation)
                implementation(libs.ktor.server.status.pages)
                implementation(libs.ktor.server.cors)
            }
        }

        iosMain {
            dependsOn(quickjsMain)
            dependsOn(composeAudioMain)
            dependencies {
                implementation(libs.ktor.client.darwin)
            }
        }

        mingwX64Main {
            dependsOn(desktopNativeMain)
            dependencies {
                implementation(libs.ktor.client.winhttp)
            }
        }

        linuxX64Main {
            dependsOn(desktopNativeMain)
            dependencies {
                implementation(libs.ktor.client.curl)
            }
        }

        macosArm64Main {
            dependsOn(desktopNativeMain)
            dependencies {
                implementation(libs.ktor.client.darwin)
            }
        }

        jsMain {
            dependsOn(composeAudioMain)
            dependencies {
                implementation(libs.ktor.client.js)
            }
        }

        wasmJsMain {
            dependsOn(composeAudioMain)
            dependencies {
                implementation(libs.ktor.client.js)
                implementation(libs.kotlinx.browser)
            }
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
