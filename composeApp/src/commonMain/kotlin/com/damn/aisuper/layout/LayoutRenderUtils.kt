package com.damn.aisuper.layout

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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

fun ColumnScope.childModifier(widget: Widget): Modifier {
    return widget.weight?.let { Modifier.weight(it) } ?: Modifier
}

fun RowScope.childModifier(widget: Widget): Modifier {
    return widget.weight?.let { Modifier.weight(it) } ?: Modifier
}
