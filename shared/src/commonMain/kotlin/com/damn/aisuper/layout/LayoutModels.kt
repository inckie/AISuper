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
    abstract val classes: List<String>
}

@Serializable
@SerialName("Column")
data class ColumnWidget(
    override val id: String? = null,
    override val fillMaxWidth: Boolean = false,
    override val fillMaxSize: Boolean = false,
    override val weight: Float? = null,
    override val classes: List<String> = emptyList(),
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
    override val classes: List<String> = emptyList(),
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
    override val classes: List<String> = emptyList(),
    val text: String = ""
) : Widget()

@Serializable
@SerialName("TextField")
data class TextFieldWidget(
    override val id: String? = null,
    override val fillMaxWidth: Boolean = false,
    override val fillMaxSize: Boolean = false,
    override val weight: Float? = null,
    override val classes: List<String> = emptyList(),
    val hint: String = "",
    // single line input (default true)
    val singleLine: Boolean = true,
    // IME action to request (e.g. "Search", "Next", "Done")
    val imeAction: String? = null,
    // action to call when IME action is triggered (JS action name)
    val onImeAction: String? = null,
    // id of the next focusable widget for Next action
    val nextFocusId: String? = null
) : Widget()

@Serializable
@SerialName("Button")
data class ButtonWidget(
    override val id: String? = null,
    override val fillMaxWidth: Boolean = false,
    override val fillMaxSize: Boolean = false,
    override val weight: Float? = null,
    override val classes: List<String> = emptyList(),
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
    override val classes: List<String> = emptyList(),
    val url: String = "",
    val data: String? = null,
    val description: String = ""
) : Widget()

@Serializable
@SerialName("AudioPlayer")
data class AudioPlayerWidget(
    override val id: String? = null,
    override val fillMaxWidth: Boolean = false,
    override val fillMaxSize: Boolean = false,
    override val weight: Float? = null,
    override val classes: List<String> = emptyList(),
    val player: String,
    val title: String = "Audio Player"
) : Widget()

@Serializable
data class DropdownOption(
    val value: String,
    val label: String = value
)

@Serializable
@SerialName("Dropdown")
data class DropdownWidget(
    override val id: String? = null,
    override val fillMaxWidth: Boolean = false,
    override val fillMaxSize: Boolean = false,
    override val weight: Float? = null,
    override val classes: List<String> = emptyList(),
    val hint: String = "Select",
    val options: List<DropdownOption> = emptyList(),
    val optionsValueId: String? = null,
    val onChangeAction: String? = null
) : Widget()

@Serializable
@SerialName("Switch")
data class SwitchWidget(
    override val id: String? = null,
    override val fillMaxWidth: Boolean = false,
    override val fillMaxSize: Boolean = false,
    override val weight: Float? = null,
    override val classes: List<String> = emptyList(),
    val text: String = "",
    val checked: Boolean = false
) : Widget()

@Serializable
@SerialName("Spinner")
data class SpinnerWidget(
    override val id: String? = null,
    override val fillMaxWidth: Boolean = false,
    override val fillMaxSize: Boolean = false,
    override val weight: Float? = null,
    override val classes: List<String> = emptyList(),
    val visibilityId: String? = null
) : Widget()

@Serializable
@SerialName("Progress")
data class ProgressWidget(
    override val id: String? = null,
    override val fillMaxWidth: Boolean = false,
    override val fillMaxSize: Boolean = false,
    override val weight: Float? = null,
    override val classes: List<String> = emptyList(),
    val progress: Float? = null,
    val progressId: String? = null,
    val indeterminate: Boolean = false
) : Widget()

