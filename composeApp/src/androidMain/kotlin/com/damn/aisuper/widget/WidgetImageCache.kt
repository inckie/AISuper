package com.damn.aisuper.widget

import android.content.Context
import android.net.Uri
import android.util.Base64
import com.damn.aisuper.widget.WidgetImageCache.contentUri
import java.io.File
import java.security.MessageDigest

/**
 * Caches images from URLs into app-private storage so they can be
 * served on demand by [WidgetImageContentProvider] using a content:// URI.
 *
 * Files are cached by a SHA-1 hash of the URL to avoid re-downloads.
 * The original URL is encoded as a query parameter in the content URI so
 * the provider can locate or download the image without any extra state.
 */
object WidgetImageCache {

    private const val DIR = "widget_image_cache"

    fun cacheDir(context: Context): File =
        File(context.filesDir, DIR).also { it.mkdirs() }

    /** Cache file for a given URL (keyed by SHA-1 hash). */
    fun cacheFile(context: Context, url: String): File {
        val hash = sha1(url)
        val ext = url.substringAfterLast('.', "").takeIf { it.length in 2..4 } ?: "img"
        return File(cacheDir(context), "$hash.$ext")
    }

    /**
     * Build the content:// URI for [imageUrl].
     * The original URL is Base64-encoded as the path segment so the provider can
     * decode it without relying on query parameters (which RemoteViews may strip).
     */
    fun contentUri(context: Context, imageUrl: String): Uri {
        val encoded = Base64.encodeToString(imageUrl.toByteArray(), Base64.URL_SAFE or Base64.NO_WRAP)
        return Uri.Builder()
            .scheme("content")
            .authority("${context.packageName}.widget_images")
            .appendPath(encoded)
            .build()
    }

    /** Decode the image URL from a content URI built by [contentUri]. */
    fun urlFromUri(uri: Uri): String? {
        val encoded = uri.lastPathSegment ?: return null
        return try {
            Base64.decode(encoded, Base64.URL_SAFE or Base64.NO_WRAP).toString(Charsets.UTF_8)
        } catch (_: Exception) { null }
    }

    private fun sha1(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-1").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
