package com.lonx.lyrico.domain.lyrics.document

import com.lonx.lyrico.domain.lyrics.LyricsTimestamp
import java.io.StringReader
import java.io.StringWriter
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.InputSource

/** Secure XML access shared by TTML parsing and loss-preserving timestamp edits. */
internal object TtmlXml {
    fun parse(raw: String): Document {
        require(!raw.contains("<!DOCTYPE", ignoreCase = true)) {
            "TTML documents containing DOCTYPE are not supported"
        }
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            isIgnoringComments = false
            isExpandEntityReferences = false
            fun feature(name: String, value: Boolean) { runCatching { setFeature(name, value) } }
            feature("http://apache.org/xml/features/disallow-doctype-decl", true)
            feature("http://xml.org/sax/features/external-general-entities", false)
            feature("http://xml.org/sax/features/external-parameter-entities", false)
            feature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
        }
        return factory.newDocumentBuilder().parse(InputSource(StringReader(raw)))
    }

    fun shift(document: Document, offset: Long) {
        // Lyrico's AMLL/structured contract stores div, line and word times in one absolute
        // media timeline, rather than interpreting div begin as a relative-time origin.
        fun visit(node: Node) {
            if (node is Element && node.namespaceURI in timedNamespaces && node.localName in timedElements) {
                for (name in listOf("begin", "end")) {
                    val attribute = node.getAttributeNode(name) ?: continue
                    val time = TtmlTime.parseMsOrNull(attribute.value) ?: continue
                    attribute.value = LyricsTimestamp.formatTtml(LyricsTimestamp.shift(time, offset))
                }
            }
            for (index in 0 until node.childNodes.length) visit(node.childNodes.item(index))
        }
        visit(document.documentElement)
    }

    fun write(document: Document): String {
        val factory = TransformerFactory.newInstance()
        runCatching { factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true) }
        runCatching { factory.setAttribute("http://javax.xml.XMLConstants/property/accessExternalDTD", "") }
        runCatching { factory.setAttribute("http://javax.xml.XMLConstants/property/accessExternalStylesheet", "") }
        val transformer = factory.newTransformer().apply {
            setOutputProperty(OutputKeys.ENCODING, "UTF-8")
            setOutputProperty(OutputKeys.INDENT, "no")
        }
        return StringWriter().also { transformer.transform(DOMSource(document), StreamResult(it)) }.toString()
    }

    private val timedNamespaces = setOf(null, "", "http://www.w3.org/ns/ttml",
        "http://music.apple.com/lyric-ttml-internal", "http://music.apple.com/itunes/ttml")
    private val timedElements = setOf("tt", "body", "div", "p", "span", "text")
}
