package com.damn.aisuper.widget

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import java.io.OutputStream
import java.util.concurrent.Executors

/**
 * Serves locally cached widget images to RemoteViews via content:// scheme.
 * If the image is not yet cached, it is downloaded asynchronously via a pipe
 * so that openFile() never blocks the calling thread.
 *
 * Authority: <packageName>.widget_images
 */
class WidgetImageContentProvider : ContentProvider() {

    private val executor = Executors.newCachedThreadPool()

    override fun onCreate(): Boolean = true

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
        val context = context ?: return null
        val imageUrl = WidgetImageCache.urlFromUri(uri) ?: return null
        val file = WidgetImageCache.cacheFile(context, imageUrl)

        // If already cached, serve the file directly (fast path)
        if (file.exists()) {
            return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        }

        // Slow path: create a pipe and download into the write end on a background thread
        val pipe = ParcelFileDescriptor.createPipe()
        val readEnd  = pipe[0]
        val writeEnd = pipe[1]

        executor.submit {
            try {
                // Try to also write to the cache file so next request is fast
                file.parentFile?.mkdirs()
                val bytes = java.net.URL(imageUrl).openStream().use { it.readBytes() }
                file.writeBytes(bytes)
                ParcelFileDescriptor.AutoCloseOutputStream(writeEnd).use { out: OutputStream ->
                    out.write(bytes)
                }
            } catch (e: Exception) {
                Log.e("WidgetImageProvider", "Failed to download: $imageUrl", e)
                writeEnd.closeWithError(e.message ?: "download failed")
            }
        }

        return readEnd
    }

    override fun getType(uri: Uri): String = "image/*"

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?,
                       selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?,
                        selectionArgs: Array<out String>?): Int = 0
}
