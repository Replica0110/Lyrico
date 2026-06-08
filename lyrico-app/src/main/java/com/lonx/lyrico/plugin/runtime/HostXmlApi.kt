package com.lonx.lyrico.plugin.runtime

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import org.xmlpull.v1.XmlSerializer
import java.io.StringReader
import java.io.StringWriter

object HostXmlApi {

    fun getRootAttributes(xml: String): JsonObject {
        val root = parse(xml)
        return JsonObject(root.attributes.mapValues { JsonPrimitive(it.value) })
    }

    fun findElements(
        xml: String,
        query: JsonObject,
        json: Json
    ): JsonArray {
        val root = parse(xml)
        val tag = query.string("tag")
        val attrs = query.obj("attrs") ?: JsonObject(emptyMap())

        val results = mutableListOf<JsonObject>()

        root.walk { node ->
            if (node.type != XmlNodeType.Element) return@walk
            if (tag.isNotBlank() && node.name != tag) return@walk
            if (!attrsMatch(node, attrs)) return@walk

            results += node.toJsonObject()
        }

        return JsonArray(results)
    }

    fun replaceChildrenByAttr(
        xml: String,
        options: JsonObject
    ): String {
        val root = parse(xml)

        val targetTag = options.string("targetTag")
        val keyAttr = options.string("keyAttr")
        val replacementsObj = options.obj("replacements") ?: JsonObject(emptyMap())
        val rootAttributes = options.obj("rootAttributes") ?: JsonObject(emptyMap())

        if (targetTag.isBlank() || keyAttr.isBlank()) {
            return xml
        }

        rootAttributes.forEach { (name, value) ->
            root.attributes[name] = value.jsonPrimitive.contentOrNull.orEmpty()
        }

        root.walk { node ->
            if (node.type != XmlNodeType.Element) return@walk
            if (node.name != targetTag) return@walk

            val key = node.attributes[keyAttr].orEmpty()
            if (key.isBlank()) return@walk

            val replacement = replacementsObj[key]?.jsonObject ?: return@walk
            val mode = replacement.string("mode").ifBlank { "text" }
            val value = replacement.string("value")

            node.children.clear()

            if (mode == "xml") {
                node.children += parseFragment(value)
            } else {
                node.children += XmlNode.text(value)
            }
        }

        return serialize(root)
    }

    fun removeElements(
        xml: String,
        query: JsonObject
    ): String {
        val root = parse(xml)

        val tag = query.string("tag")
        val attrs = query.obj("attrs") ?: JsonObject(emptyMap())

        fun removeIn(node: XmlNode) {
            val iterator = node.children.iterator()
            while (iterator.hasNext()) {
                val child = iterator.next()

                if (
                    child.type == XmlNodeType.Element &&
                    (tag.isBlank() || child.name == tag) &&
                    attrsMatch(child, attrs)
                ) {
                    iterator.remove()
                } else {
                    removeIn(child)
                }
            }
        }

        removeIn(root)
        collapseEmptyTranslations(root)

        return serialize(root)
    }

    private fun parse(xml: String): XmlNode {
        val parser = XmlPullParserFactory.newInstance().newPullParser()
        parser.setInput(StringReader(xml))

        val stack = ArrayDeque<XmlNode>()
        var root: XmlNode? = null

        while (true) {
            when (parser.eventType) {
                XmlPullParser.START_TAG -> {
                    val node = XmlNode.element(parser.name)

                    for (i in 0 until parser.attributeCount) {
                        val prefix = parser.getAttributePrefix(i)
                        val name = parser.getAttributeName(i)
                        val attrName = if (!prefix.isNullOrBlank()) "$prefix:$name" else name
                        node.attributes[attrName] = parser.getAttributeValue(i)
                    }

                    if (stack.isEmpty()) {
                        root = node
                    } else {
                        stack.last().children += node
                    }

                    stack.addLast(node)
                }

                XmlPullParser.TEXT, XmlPullParser.CDSECT -> {
                    val text = parser.text.orEmpty()
                    if (text.isNotEmpty() && stack.isNotEmpty()) {
                        stack.last().children += XmlNode.text(text)
                    }
                }

                XmlPullParser.END_TAG -> {
                    if (stack.isNotEmpty()) {
                        stack.removeLast()
                    }
                }

                XmlPullParser.END_DOCUMENT -> break
            }

            parser.next()
        }

        return root ?: XmlNode.element("root")
    }

    private fun parseFragment(fragment: String): List<XmlNode> {
        val wrapped = "<root>$fragment</root>"
        return parse(wrapped).children
    }

    private fun serialize(node: XmlNode): String {
        val writer = StringWriter()
        val serializer = XmlPullParserFactory.newInstance().newSerializer()

        serializer.setOutput(writer)
        writeNode(serializer, node)
        serializer.flush()

        return writer.toString()
    }

    private fun writeNode(serializer: XmlSerializer, node: XmlNode) {
        when (node.type) {
            XmlNodeType.Text -> {
                serializer.text(node.text.orEmpty())
            }

            XmlNodeType.Element -> {
                serializer.startTag("", node.name.orEmpty())

                node.attributes.forEach { (name, value) ->
                    serializer.attribute("", name, value)
                }

                node.children.forEach { child ->
                    writeNode(serializer, child)
                }

                serializer.endTag("", node.name.orEmpty())
            }
        }
    }

    private fun attrsMatch(node: XmlNode, attrs: JsonObject): Boolean {
        return attrs.all { (name, value) ->
            val expected = value.jsonPrimitive.contentOrNull.orEmpty()
            node.attributes[name] == expected
        }
    }

    private fun collapseEmptyTranslations(node: XmlNode) {
        node.walk { current ->
            if (current.type != XmlNodeType.Element) return@walk
            if (current.name != "translations") return@walk
            if (current.children.none { it.type == XmlNodeType.Element }) {
                current.children.clear()
            }
        }
    }

    private fun XmlNode.walk(block: (XmlNode) -> Unit) {
        block(this)
        children.forEach { it.walk(block) }
    }

    private fun XmlNode.toJsonObject(): JsonObject {
        return buildJsonObject {
            put("tag", JsonPrimitive(name.orEmpty()))
            put(
                "attrs",
                JsonObject(attributes.mapValues { JsonPrimitive(it.value) })
            )
            put("text", JsonPrimitive(textContent()))
            put("innerXml", JsonPrimitive(children.joinToString("") { serialize(it) }))
            put(
                "children",
                JsonArray(
                    children
                        .filter { it.type == XmlNodeType.Element }
                        .map { it.toJsonObject() }
                )
            )
        }
    }

    private fun XmlNode.textContent(): String {
        return when (type) {
            XmlNodeType.Text -> text.orEmpty()
            XmlNodeType.Element -> children.joinToString("") { it.textContent() }
        }
    }

    private fun JsonObject.string(key: String): String {
        return this[key]?.jsonPrimitive?.contentOrNull.orEmpty()
    }

    private fun JsonObject.obj(key: String): JsonObject? {
        return this[key] as? JsonObject
    }
}

private enum class XmlNodeType {
    Element,
    Text
}

private data class XmlNode(
    val type: XmlNodeType,
    val name: String? = null,
    val attributes: MutableMap<String, String> = linkedMapOf(),
    val children: MutableList<XmlNode> = mutableListOf(),
    val text: String? = null
) {
    companion object {
        fun element(name: String): XmlNode {
            return XmlNode(
                type = XmlNodeType.Element,
                name = name
            )
        }

        fun text(value: String): XmlNode {
            return XmlNode(
                type = XmlNodeType.Text,
                text = value
            )
        }
    }
}