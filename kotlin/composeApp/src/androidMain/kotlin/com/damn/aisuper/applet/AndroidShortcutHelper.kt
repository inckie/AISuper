package com.damn.aisuper.applet

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.net.Uri
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.damn.aisuper.MainActivity
import java.io.File
import kotlin.math.abs

object AndroidShortcutHelper {
    const val ACTION_LAUNCH_APPLET = "com.damn.aisuper.ACTION_LAUNCH_APPLET"
    const val EXTRA_APPLET_REF = "com.damn.aisuper.EXTRA_APPLET_REF"

    private val palette = intArrayOf(
        Color.rgb(37, 99, 235),
        Color.rgb(16, 185, 129),
        Color.rgb(245, 158, 11),
        Color.rgb(239, 68, 68),
        Color.rgb(139, 92, 246),
        Color.rgb(20, 184, 166),
        Color.rgb(236, 72, 153)
    )

    fun canPinShortcuts(context: Context): Boolean =
        ShortcutManagerCompat.isRequestPinShortcutSupported(context)

    suspend fun pushDynamicLauncherShortcut(
        context: Context,
        entry: AppletEntry,
        provider: AppletProvider?
    ) {
        runCatching {
            val persistentRef = ensurePersistentSourceRef(context, entry)
            val info = buildShortcutInfo(context, entry, persistentRef, provider)
            ShortcutManagerCompat.pushDynamicShortcut(context, info)
        }
    }

    suspend fun pinToHomeScreen(
        context: Context,
        entry: AppletEntry,
        provider: AppletProvider?
    ): Result<String> {
        if (!canPinShortcuts(context)) {
            return Result.failure(IllegalStateException("Current launcher does not support pinned shortcuts."))
        }
        return runCatching {
            val persistentRef = ensurePersistentSourceRef(context, entry)
            val info = buildShortcutInfo(context, entry, persistentRef, provider)
            runCatching { ShortcutManagerCompat.pushDynamicShortcut(context, info) }
            val requested = ShortcutManagerCompat.requestPinShortcut(context, info, null)
            if (!requested) {
                error("Launcher rejected shortcut request for ${entry.name}.")
            }
            "Shortcut requested for ${entry.name}"
        }
    }

    private suspend fun buildShortcutInfo(
        context: Context,
        entry: AppletEntry,
        persistentRef: String,
        provider: AppletProvider?
    ): ShortcutInfoCompat {
        val shortcutId = "applet_${entry.id}_${persistentRef.hashCode().toUInt()}"
        val icon = createAppletIcon(context, entry, provider)
        val launchIntent = Intent(context, MainActivity::class.java).apply {
            action = ACTION_LAUNCH_APPLET
            putExtra(EXTRA_APPLET_REF, persistentRef)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        return ShortcutInfoCompat.Builder(context, shortcutId)
            .setShortLabel(entry.name.take(25))
            .setLongLabel(entry.name.take(64))
            .setIcon(icon)
            .setIntent(launchIntent)
            .build()
    }

    suspend fun ensurePersistentSourceRef(context: Context, entry: AppletEntry): String {
        val ref = runCatching { decodeAndroidAppletRef(entry.sourceRef) }.getOrNull() ?: return entry.sourceRef
        if (ref is AndroidAppletRef.SafZip) {
            val uri = Uri.parse(ref.documentUri)
            val hasPersisted = context.contentResolver.persistedUriPermissions.any {
                it.uri == uri && it.isReadPermission
            }
            if (!hasPersisted) {
                val pinnedDir = File(context.filesDir, "pinned_applets").apply { mkdirs() }
                val dest = File(pinnedDir, "${entry.id.hashCode().toUInt()}_${uri.toString().hashCode().toUInt()}.zip")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    dest.outputStream().use { output -> input.copyTo(output) }
                }
                return AndroidAppletRef.LocalZip(
                    path = dest.absolutePath,
                    entryPath = ref.entryPath,
                    rootPrefix = ref.rootPrefix
                ).encodeToJson()
            }
        }
        return entry.sourceRef
    }

    suspend fun createAppletIcon(
        context: Context,
        entry: AppletEntry,
        provider: AppletProvider?
    ): IconCompat {
        val iconPath = entry.iconPath
        if (!iconPath.isNullOrBlank() && provider != null) {
            val bitmap = runCatching {
                val bytes = provider.createLoader().readBytes(iconPath)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }.getOrNull()
            if (bitmap != null) {
                return IconCompat.createWithAdaptiveBitmap(bitmap)
            }
        }
        return IconCompat.createWithAdaptiveBitmap(createMonogramBitmap(entry))
    }

    private fun createMonogramBitmap(entry: AppletEntry): Bitmap {
        val size = 192
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val bgColor = palette[abs(entry.id.hashCode()) % palette.size]
        canvas.drawColor(bgColor)

        val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(38, 255, 255, 255)
        }
        canvas.drawCircle(size / 2f, size / 2f, size * 0.30f, circlePaint)

        val initials = entry.name
            .trim()
            .split(Regex("\\s+"))
            .filter { it.isNotEmpty() }
            .take(2)
            .joinToString("") { it.first().uppercaseChar().toString() }
            .ifEmpty { "A" }

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = if (initials.length == 1) 72f else 56f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val bounds = Rect()
        textPaint.getTextBounds(initials, 0, initials.length, bounds)
        val y = size / 2f - bounds.exactCenterY()
        canvas.drawText(initials, size / 2f, y, textPaint)
        return bitmap
    }
}
