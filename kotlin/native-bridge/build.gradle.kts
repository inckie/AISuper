import org.jetbrains.kotlin.gradle.plugin.mpp.NativeBuildType
import java.io.File

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    applyDefaultHierarchyTemplate()

    listOf(
        mingwX64(),
        linuxX64(),
        macosArm64()
    ).forEach { target ->
        val targetName = target.name
        target.binaries.sharedLib {
            baseName = "aisuper_core"
            if (buildType == NativeBuildType.RELEASE) {
                linkTaskProvider.configure {
                    val outFileProvider = outputFile
                    doLast {
                        val binary = outFileProvider.get()
                        val stripFlag = if (targetName.startsWith("macos")) "-x" else "--strip-unneeded"
                        val stripCmd = if (targetName == "mingwX64") {
                            val konanDeps = File(System.getProperty("user.home"), ".konan/dependencies")
                            konanDeps.listFiles()
                                ?.firstOrNull { it.isDirectory && it.name.startsWith("msys2-mingw-w64-") }
                                ?.let { File(it, "bin/strip.exe") }
                                ?.takeIf { it.isFile }
                                ?.absolutePath
                                ?: "strip"
                        } else {
                            "strip"
                        }
                        val proc = ProcessBuilder(stripCmd, stripFlag, binary.absolutePath)
                            .inheritIO()
                            .start()
                        val code = proc.waitFor()
                        if (code != 0) {
                            throw GradleException("strip failed with exit code $code for ${binary.absolutePath}")
                        }
                    }
                }
            }
        }
    }

    sourceSets {
        nativeMain.dependencies {
            implementation(project(":shared"))
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
        }
    }
}
