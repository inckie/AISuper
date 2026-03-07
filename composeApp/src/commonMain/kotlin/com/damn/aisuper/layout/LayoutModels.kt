package com.damn.aisuper.layout

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator

@Serializable
data class LayoutRoot(
    val layout: Widget
)

@Serializable
@JsonClassDiscriminator("type")
sealed class Widget {
    abstract val id: String?
}

@Serializable
@SerialName("Column")
data class ColumnWidget(
    override val id: String? = null,
    val children: List<Widget> = emptyList(),
    val dynamicChildrenId: String? = null,
    val isScrollable: Boolean = false
) : Widget()

@Serializable
@SerialName("Text")
data class TextWidget(
    override val id: String? = null,
    val text: String = ""
) : Widget()

@Serializable
@SerialName("TextField")
data class TextFieldWidget(
    override val id: String? = null,
    val hint: String = ""
) : Widget()

@Serializable
@SerialName("Button")
data class ButtonWidget(
    override val id: String? = null,
    val text: String = "",
    val action: String = ""
) : Widget()

@Serializable
@SerialName("Image")
data class ImageWidget(
    override val id: String? = null,
    val url: String = "",
    val description: String = ""
) : Widget()
