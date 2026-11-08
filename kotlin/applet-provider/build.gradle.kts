import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKotlinMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

group = "com.damn.aisuper"
version = "0.1.0"

kotlin {
    applyDefaultHierarchyTemplate()

    android {
        namespace = "com.damn.aisuper.appletprovider"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
        experimentalProperties["android.experimental.kmp.enableAndroidResources"] = true
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }

    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "AppletProvider"
            isStatic = true
        }
    }

    jvm()

    js { browser() }

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs { browser() }

    sourceSets {
        commonMain.dependencies {
            implementation(projects.shared)
            implementation(libs.compose.runtime)
            implementation(libs.compose.components.resources)
            implementation(libs.kotlinx.coroutines.core)
        }
        // jvmMain contains FileSystemAppletProvider, ClasspathAppletProvider, ZipAppletProvider.
        // We also add commonMain/composeResources as a JVM resources source directory so the
        // bundled applet files (files/applet.json, etc.) are accessible on the JVM classpath
        // via ClassLoader.getResourceAsStream("files/...") without maintaining a second copy.
        jvmMain {
            resources.srcDir("src/commonMain/composeResources")
        }
    }
}

compose.resources {
    // Keep the generated Res accessor in a stable, predictable package
    packageOfResClass = "aisuper.appletprovider.generated.resources"
    generateResClass = always
}
