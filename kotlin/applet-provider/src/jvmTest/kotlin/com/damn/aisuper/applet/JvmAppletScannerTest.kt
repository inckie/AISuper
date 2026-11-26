package com.damn.aisuper.applet

import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class JvmAppletScannerTest {

    private fun manifestJson(id: String, name: String) = """
        {
          "id": "$id",
          "name": "$name",
          "entryFeature": "main",
          "features": {
            "main": {
              "layout": "files/main.json",
              "script": "files/main.js",
              "name": "Main"
            }
          }
        }
    """.trimIndent()

    private fun writeZip(path: Path, entries: Map<String, String>) {
        ZipOutputStream(Files.newOutputStream(path)).use { zos ->
            entries.forEach { (name, content) ->
                zos.putNextEntry(ZipEntry(name))
                zos.write(content.toByteArray())
                zos.closeEntry()
            }
        }
    }

    @Test
    fun `scanDirectory discovers folder and zip applets in all supported layouts`() = runTest {
        val tempRoot = Files.createTempDirectory("aisuper-scanner-test")
        try {
            // 1. Direct folder: app1/applet.json
            val app1 = tempRoot.resolve("app1")
            Files.createDirectories(app1.resolve("files"))
            Files.writeString(app1.resolve("applet.json"), manifestJson("id.1", "Alpha Folder"))
            Files.writeString(app1.resolve("files/main.js"), "// alpha")

            // 2. files/ folder: app2/files/applet.json
            val app2 = tempRoot.resolve("app2")
            Files.createDirectories(app2.resolve("files"))
            Files.writeString(app2.resolve("files/applet.json"), manifestJson("id.2", "Beta Files Folder"))
            Files.writeString(app2.resolve("files/main.js"), "// beta")

            // 3. Nested subfolder: app3/applet/applet.json
            val app3 = tempRoot.resolve("app3/applet")
            Files.createDirectories(app3.resolve("files"))
            Files.writeString(app3.resolve("applet.json"), manifestJson("id.3", "Gamma Nested Folder"))
            Files.writeString(app3.resolve("files/main.js"), "// gamma")

            // 4. Root zip: app4.zip
            writeZip(
                tempRoot.resolve("app4.zip"),
                mapOf(
                    "applet.json" to manifestJson("id.4", "Delta Root Zip"),
                    "files/main.js" to "// delta"
                )
            )

            // 5. Wrapped zip: app5.zip
            writeZip(
                tempRoot.resolve("app5.zip"),
                mapOf(
                    "wrapped/applet.json" to manifestJson("id.5", "Epsilon Wrapped Zip"),
                    "wrapped/files/main.js" to "// epsilon"
                )
            )

            // 6. Invalid folder and non-applet zip should be safely ignored
            val invalidDir = tempRoot.resolve("not-an-applet")
            Files.createDirectories(invalidDir)
            Files.writeString(invalidDir.resolve("readme.txt"), "hello")
            writeZip(tempRoot.resolve("empty.zip"), mapOf("readme.txt" to "nope"))

            val entries = JvmAppletScanner.scanDirectory(tempRoot)
            assertEquals(5, entries.size)
            assertEquals(
                listOf(
                    "Alpha Folder",
                    "Beta Files Folder",
                    "Delta Root Zip",
                    "Epsilon Wrapped Zip",
                    "Gamma Nested Folder"
                ),
                entries.map { it.name }
            )

            for (entry in entries) {
                val target = when (entry.sourceKind) {
                    AppletSourceKind.ZIP -> Path.of(entry.sourceRef)
                    AppletSourceKind.DIRECTORY -> Path.of(entry.sourceRef).resolve(entry.entryPath)
                }
                val resolved = JvmAppletScanner.resolveAppletPath(target)
                val loader = resolved.provider.createLoader()
                val script = loader.readBytes("files/main.js").decodeToString()
                assertTrue(script.startsWith("//"), "Expected valid script for ${entry.name}")
            }
        } finally {
            tempRoot.toFile().deleteRecursively()
        }
    }
}
