package com.damn.aisuper.runtime

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Minimal XML to JSON converter for JS runtime utilities.
 *
 * Output contract:
 * - Root object is wrapped with root tag name: { "RootTag": { ... } }
 * - Attributes are stored under "@attributes"
 * - Text content is stored under "#text"
 * - Repeated child tags become arrays
 */
object XmlJsonParser {

    fun parse(xmlInput: String): JsonElement {
        val xml = xmlInput.trim()
        require(xml.isNotEmpty()) { "XML input is empty" }

        val root = parseRootNode(xml)
        return JsonObject(mapOf(root.name to nodeToJson(root)))
    }

    private data class Node(
        val name: String,
        val attributes: MutableMap<String, String> = linkedMapOf(),
        val children: MutableList<Node> = mutableListOf(),
        val text: StringBuilder = StringBuilder()
    )

    private fun parseRootNode(xml: String): Node {
        val stack = mutableListOf<Node>()
        var root: Node? = null
        var index = 0

        while (index < xml.length) {
            if (xml[index] != '<') {
                val nextTag = xml.indexOf('<', index).let { if (it == -1) xml.length else it }
                val chunk = xml.substring(index, nextTag)
                if (stack.isNotEmpty() && chunk.isNotBlank()) {
                    stack.last().text.append(decodeEntities(chunk))
                }
                index = nextTag
                continue
            }

            when {
                xml.startsWith("<!--", index) -> {
                    val end = xml.indexOf("-->", index + 4)
                    require(end >= 0) { "Unclosed XML comment" }
                    index = end + 3
                }

                xml.startsWith("<![CDATA[", index) -> {
                    val end = xml.indexOf("]]>", index + 9)
                    require(end >= 0) { "Unclosed CDATA block" }
                    if (stack.isNotEmpty()) {
                        stack.last().text.append(xml.substring(index + 9, end))
                    }
                    index = end + 3
                }

                xml.startsWith("<?", index) -> {
                    val end = xml.indexOf("?>", index + 2)
                    require(end >= 0) { "Unclosed processing instruction" }
                    index = end + 2
                }

                xml.startsWith("</", index) -> {
                    val end = xml.indexOf('>', index + 2)
                    require(end >= 0) { "Unclosed closing tag" }
                    val closeName = xml.substring(index + 2, end).trim()
                    require(stack.isNotEmpty()) { "Unexpected closing tag: $closeName" }
                    val current = stack.removeAt(stack.lastIndex)
                    require(current.name == closeName) {
                        "Mismatched closing tag: expected </${current.name}> but found </$closeName>"
                    }
                    index = end + 1
                }

                else -> {
                    val end = xml.indexOf('>', index + 1)
                    require(end >= 0) { "Unclosed start tag" }

                    var inner = xml.substring(index + 1, end).trim()
                    val selfClosing = inner.endsWith("/")
                    if (selfClosing) {
                        inner = inner.dropLast(1).trim()
                    }

                    val name = readTagName(inner)
                    require(name.isNotEmpty()) { "Invalid tag name" }

                    val attributesPart = inner.substring(name.length).trim()
                    val node = Node(name = name)
                    node.attributes.putAll(parseAttributes(attributesPart))

                    if (stack.isNotEmpty()) {
                        stack.last().children.add(node)
                    } else {
                        root = node
                    }

                    if (!selfClosing) {
                        stack.add(node)
                    }

                    index = end + 1
                }
            }
        }

        require(stack.isEmpty()) { "Unclosed tags remain" }
        return requireNotNull(root) { "No root XML node found" }
    }

    private fun readTagName(inner: String): String {
        val sb = StringBuilder()
        for (ch in inner) {
            if (ch.isWhitespace() || ch == '/') break
            sb.append(ch)
        }
        return sb.toString()
    }

    private fun parseAttributes(text: String): Map<String, String> {
        if (text.isBlank()) return emptyMap()

        val attributes = linkedMapOf<String, String>()
        var index = 0

        fun skipSpaces() {
            while (index < text.length && text[index].isWhitespace()) index++
        }

        while (index < text.length) {
            skipSpaces()
            if (index >= text.length) break

            val keyStart = index
            while (index < text.length && !text[index].isWhitespace() && text[index] != '=') index++
            val key = text.substring(keyStart, index)
            if (key.isBlank()) break

            skipSpaces()
            require(index < text.length && text[index] == '=') { "Expected '=' after attribute '$key'" }
            index++
            skipSpaces()

            require(index < text.length) { "Missing value for attribute '$key'" }
            val quote = text[index]
            require(quote == '"' || quote == '\'') { "Attribute '$key' value must be quoted" }
            index++

            val valueStart = index
            while (index < text.length && text[index] != quote) index++
            require(index < text.length) { "Unclosed quoted attribute value for '$key'" }

            val value = text.substring(valueStart, index)
            attributes[key] = decodeEntities(value)
            index++
        }

        return attributes
    }

    private fun nodeToJson(node: Node): JsonElement {
        val out = linkedMapOf<String, JsonElement>()

        if (node.attributes.isNotEmpty()) {
            out["@attributes"] = JsonObject(node.attributes.mapValues { JsonPrimitive(it.value) })
        }

        val text = node.text.toString().trim()
        if (text.isNotEmpty()) {
            out["#text"] = JsonPrimitive(text)
        }

        if (node.children.isNotEmpty()) {
            val grouped = linkedMapOf<String, MutableList<Node>>()
            for (child in node.children) {
                grouped.getOrPut(child.name) { mutableListOf() }.add(child)
            }

            for ((name, siblings) in grouped) {
                out[name] = if (siblings.size == 1) {
                    nodeToJson(siblings[0])
                } else {
                    JsonArray(siblings.map { nodeToJson(it) })
                }
            }
        }

        return JsonObject(out)
    }

    private fun decodeEntities(value: String): String {
        return value
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
    }
}

