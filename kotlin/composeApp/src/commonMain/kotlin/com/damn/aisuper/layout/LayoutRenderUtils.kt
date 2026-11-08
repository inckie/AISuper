package com.damn.aisuper.layout

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

fun Modifier.applyStyleRule(style: StyleRule): Modifier {
    var next = this

    val radius = (style.cornerRadius ?: 0).dp
    val hasBackground = !style.backgroundColor.isNullOrBlank()
    val backgroundColor = parseColorOrNull(style.backgroundColor)
    if (hasBackground && backgroundColor != null) {
        if (style.cornerRadius != null) {
            next = next.clip(RoundedCornerShape(radius))
        }
        next = next.background(backgroundColor)
    } else if (style.cornerRadius != null) {
        next = next.clip(RoundedCornerShape(radius))
    }

    val paddingAll = style.padding
    val padH = style.paddingHorizontal
    val padV = style.paddingVertical
    next = when {
        paddingAll != null -> next.padding(paddingAll.dp)
        padH != null || padV != null -> next.padding(horizontal = (padH ?: 0).dp, vertical = (padV ?: 0).dp)
        else -> next
    }

    return next
}

fun Widget.layoutModifier(): Modifier {
    return Modifier
        .let { if (fillMaxSize) it.fillMaxSize() else it }
        .let { if (fillMaxWidth) it.fillMaxWidth() else it }
}

fun ColumnScope.childModifier(widget: Widget, style: StyleRule? = null): Modifier {
    var m = widget.weight?.let { Modifier.weight(it) } ?: Modifier
    style?.alignment?.lowercase()?.let { align ->
        val alignment = when (align) {
            "start", "left" -> Alignment.Start
            "center" -> Alignment.CenterHorizontally
            "end", "right" -> Alignment.End
            else -> null
        }
        if (alignment != null) {
            m = m.align(alignment)
        }
    }
    return m
}

fun RowScope.childModifier(widget: Widget, style: StyleRule? = null): Modifier {
    var m = widget.weight?.let { Modifier.weight(it) } ?: Modifier
    style?.alignment?.lowercase()?.let { align ->
        val alignment = when (align) {
            "top" -> Alignment.Top
            "center" -> Alignment.CenterVertically
            "bottom" -> Alignment.Bottom
            else -> null
        }
        if (alignment != null) {
            m = m.align(alignment)
        }
    }
    return m
}
@OptIn(kotlin.io.encoding.ExperimentalEncodingApi::class)
fun normalizeImageDataUri(uri: String?): String? {
    if (uri == null) return null
    if (uri.startsWith("data:image/svg+xml") && !uri.contains(";base64,")) {
        val commaIndex = uri.indexOf(',')
        if (commaIndex != -1) {
            val rawData = uri.substring(commaIndex + 1)
            val decoded = decodePercentEncoding(rawData)
            val base64 = kotlin.io.encoding.Base64.encode(decoded.encodeToByteArray())
            return "data:image/svg+xml;base64,$base64"
        }
    }
    return uri
}

private fun decodePercentEncoding(input: String): String {
    if (!input.contains('%')) return input
    val sb = StringBuilder(input.length)
    var i = 0
    while (i < input.length) {
        val c = input[i]
        if (c == '%' && i + 2 < input.length) {
            val hex = input.substring(i + 1, i + 3)
            val code = hex.toIntOrNull(16)
            if (code != null) {
                sb.append(code.toChar())
                i += 3
                continue
            }
        }
        sb.append(c)
        i++
    }
    return sb.toString()
}

