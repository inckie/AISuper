package com.damn.aisuper.applet

import com.damn.aisuper.runtime.AppletResourceLoader
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * [AppletProvider] backed by the JVM filesystem.
 *
 * Resolves paths relative to [root].  If a path is not found under [root] the
 * provider walks [fallbackRoots] in order, allowing the caller to chain e.g.
 * a project source tree next to a deployment directory.
 *
 * This replaces the ad-hoc `FileSystemAppletResourceLoader` that previously
 * lived privately inside `ServerMain.kt`.
 */
class FileSystemAppletProvider(
    private val root: Path = Paths.get("").toAbsolutePath(),
    private val fallbackRoots: List<Path> = emptyList()
) : AppletProvider {

    override fun createLoader(): AppletResourceLoader = AppletResourceLoader { path ->
        val candidate = resolve(path)
        Files.readAllBytes(candidate)
    }

    private fun resolve(path: String): Path {
        val requested = Paths.get(path)
        val primary = if (requested.isAbsolute) requested else root.resolve(path)
        if (Files.exists(primary)) return primary.normalize()

        for (fallback in fallbackRoots) {
            val alt = fallback.resolve(path)
            if (Files.exists(alt)) return alt.normalize()
        }

        // Return the primary path so the caller gets a useful NoSuchFileException.
        return primary.normalize()
    }

    companion object {
        /**
         * Convenience builder: searches [roots] in order, no strict primary root.
         */
        fun fromRoots(vararg roots: Path): FileSystemAppletProvider {
            require(roots.isNotEmpty()) { "At least one root path is required" }
            return FileSystemAppletProvider(
                root = roots.first(),
                fallbackRoots = roots.drop(1)
            )
        }
    }
}

