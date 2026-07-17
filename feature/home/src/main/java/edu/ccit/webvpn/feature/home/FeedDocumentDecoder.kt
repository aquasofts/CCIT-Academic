package edu.ccit.webvpn.feature.home

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

internal object FeedDocumentDecoder {
    fun decode(bytes: ByteArray, httpCharset: Charset? = null): String {
        if (bytes.isEmpty()) throw FeedLoadException(FeedFailureKind.INVALID_FEED)
        val bom = detectBom(bytes)
        val charset = bom?.charset
            ?: httpCharset
            ?: detectUtf16WithoutBom(bytes)
            ?: declaredCharset(bytes)
            ?: StandardCharsets.UTF_8
        val offset = bom?.length ?: 0
        val decoded = try {
            charset.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes, offset, bytes.size - offset))
                .toString()
        } catch (error: CharacterCodingException) {
            throw FeedLoadException(FeedFailureKind.ENCODING, cause = error)
        }
        return normalizeXmlDeclaration(decoded.removePrefix("\uFEFF"))
    }

    private fun detectBom(bytes: ByteArray): Bom? = when {
        bytes.startsWith(0x00, 0x00, 0xFE, 0xFF) -> Bom(Charset.forName("UTF-32BE"), 4)
        bytes.startsWith(0xFF, 0xFE, 0x00, 0x00) -> Bom(Charset.forName("UTF-32LE"), 4)
        bytes.startsWith(0xEF, 0xBB, 0xBF) -> Bom(StandardCharsets.UTF_8, 3)
        bytes.startsWith(0xFE, 0xFF) -> Bom(StandardCharsets.UTF_16BE, 2)
        bytes.startsWith(0xFF, 0xFE) -> Bom(StandardCharsets.UTF_16LE, 2)
        else -> null
    }

    private fun detectUtf16WithoutBom(bytes: ByteArray): Charset? = when {
        bytes.startsWith(0x00, 0x3C, 0x00, 0x3F) -> StandardCharsets.UTF_16BE
        bytes.startsWith(0x3C, 0x00, 0x3F, 0x00) -> StandardCharsets.UTF_16LE
        else -> null
    }

    private fun declaredCharset(bytes: ByteArray): Charset? {
        val prefix = bytes.copyOfRange(0, minOf(bytes.size, DECLARATION_SCAN_BYTES))
            .toString(StandardCharsets.ISO_8859_1)
        val name = XML_DECLARATION_ENCODING.find(prefix)?.groupValues?.get(1) ?: return null
        return try {
            Charset.forName(name)
        } catch (error: IllegalArgumentException) {
            throw FeedLoadException(FeedFailureKind.ENCODING, cause = error)
        }
    }

    private fun normalizeXmlDeclaration(xml: String): String {
        val match = XML_DECLARATION_ENCODING.find(xml) ?: return xml
        val normalized = match.value.replace(match.groupValues[1], "UTF-8")
        return xml.replaceRange(match.range, normalized)
    }

    private fun ByteArray.startsWith(vararg expected: Int): Boolean =
        size >= expected.size && expected.indices.all { index -> this[index].toInt() and 0xFF == expected[index] }

    private data class Bom(val charset: Charset, val length: Int)

    private val XML_DECLARATION_ENCODING = Regex(
        """(?i)<\?xml[^>]*\bencoding\s*=\s*[\"']([^\"']+)[\"']""",
    )
    private const val DECLARATION_SCAN_BYTES = 1024
}
