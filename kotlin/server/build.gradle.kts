plugins {
    id("org.jetbrains.kotlin.jvm")
    alias(libs.plugins.kotlinSerialization)
    application
}

group = "com.damn.aisuper"
version = "0.1.0"

kotlin {
    jvmToolchain(21)
}

application {
    mainClass = "com.damn.aisuper.server.ServerMainKt"
}

dependencies {
    implementation(projects.shared)
    implementation(projects.appletProvider)

    implementation(libs.kotlinx.serialization.json)

    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.cors)
    implementation(libs.ktor.server.sse)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.server.status.pages)
    implementation(libs.ktor.server.call.logging)
    implementation(libs.ktor.serialization.kotlinx.json)
}



