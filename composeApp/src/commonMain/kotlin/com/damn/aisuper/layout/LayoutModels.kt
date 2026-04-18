package com.damn.aisuper.layout

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator
import kotlinx.serialization.json.JsonElement

@Serializable
data class LayoutRoot(
    val layout: Widget
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonClassDiscriminator("type")
sealed class Widget {
    abstract val id: String?
    abstract val fillMaxWidth: Boolean
    abstract val fillMaxSize: Boolean
    abstract val weight: Float?
}

@Serializable
@SerialName("Column")
data class ColumnWidget(
    override val id: String? = null,
    override val fillMaxWidth: Boolean = false,
    override val fillMaxSize: Boolean = false,
    override val weight: Float? = null,
    val children: List<Widget> = emptyList(),
    val dynamicChildrenId: String? = null,
    val isScrollable: Boolean = false
) : Widget()

@Serializable
@SerialName("Row")
data class RowWidget(
    override val id: String? = null,
    override val fillMaxWidth: Boolean = false,
    override val fillMaxSize: Boolean = false,
    override val weight: Float? = null,
    val children: List<Widget> = emptyList(),
    val isScrollable: Boolean = false
) : Widget()

@Serializable
@SerialName("Text")
data class TextWidget(
    override val id: String? = null,
    override val fillMaxWidth: Boolean = false,
    override val fillMaxSize: Boolean = false,
    override val weight: Float? = null,
    val text: String = ""
) : Widget()

@Serializable
@SerialName("TextField")
data class TextFieldWidget(
    override val id: String? = null,
    override val fillMaxWidth: Boolean = false,
    override val fillMaxSize: Boolean = false,
    override val weight: Float? = null,
    val hint: String = ""
) : Widget()

@Serializable
@SerialName("Button")
data class ButtonWidget(
    override val id: String? = null,
    override val fillMaxWidth: Boolean = false,
    override val fillMaxSize: Boolean = false,
    override val weight: Float? = null,
    val text: String = "",
    val action: String = "",
    val actionArgs: List<JsonElement> = emptyList()
) : Widget()

@Serializable
@SerialName("Image")
data class ImageWidget(
    override val id: String? = null,
    override val fillMaxWidth: Boolean = false,
    override val fillMaxSize: Boolean = false,
    override val weight: Float? = null,
    val url: String = "",
    val description: String = ""
) : Widget()

@Serializable
@SerialName("AudioPlayer")
data class AudioPlayerWidget(
    override val id: String? = null,
    override val fillMaxWidth: Boolean = false,
    override val fillMaxSize: Boolean = false,
    override val weight: Float? = null,
    val player: String,
    val title: String = "Audio Player"
) : Widget()

