plugins {
    kotlin("jvm")
    application
}

group = "com.damn.aisuper"
version = "1.0.0"

application {
    mainClass.set("com.damn.aisuper.lanterna.MainKt")
}

// Globally exclude Compose and Androidx UI libraries from the Lanterna TUI runtime classpath
configurations.runtimeClasspath {
    exclude(group = "org.jetbrains.compose.runtime")
    exclude(group = "org.jetbrains.compose.components")
    exclude(group = "org.jetbrains.compose.foundation")
    exclude(group = "org.jetbrains.compose.ui")
    exclude(group = "org.jetbrains.compose.animation")
    exclude(group = "androidx.compose.runtime")
    exclude(group = "androidx.compose.foundation")
    exclude(group = "androidx.compose.ui")
    exclude(group = "androidx.compose.animation")
    exclude(group = "org.jetbrains.skiko") // Skia native bindings used by Compose Desktop
}

dependencies {
    implementation(projects.shared)
    implementation(projects.appletProvider)
    
    // Lanterna for the terminal UI
    implementation(libs.lanterna)
    
    // Coroutines for collecting state flows
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
}

tasks.register("createReleaseDistributable") {
    // Release version is identical to the debug one but relies on the stripped runtimeClasspath
    dependsOn("createDistributable")
}

tasks.register<Exec>("createDistributable") {
    dependsOn("installDist")
    
    val inputDir = file("build/install/lanternaApp/lib")
    val outputDir = file("build/distributions/app-image")
    
    doFirst {
        outputDir.deleteRecursively()
        outputDir.mkdirs()
    }
    
    val jpackagePath = if (System.getProperty("os.name").lowercase().contains("win")) {
        "${System.getProperty("java.home")}/bin/jpackage.exe"
    } else {
        "${System.getProperty("java.home")}/bin/jpackage"
    }
    
    commandLine(
        jpackagePath,
        "--name", "lanternaApp",
        "--input", inputDir.absolutePath,
        "--main-jar", "lanternaApp-1.0.0.jar",
        "--main-class", "com.damn.aisuper.lanterna.MainKt",
        "--type", "app-image",
        "--dest", outputDir.absolutePath
    )
}
