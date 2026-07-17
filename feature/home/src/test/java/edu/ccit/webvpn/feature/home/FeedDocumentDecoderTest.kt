package edu.ccit.webvpn.feature.home

import java.nio.charset.StandardCharsets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedDocumentDecoderTest {
    @Test
    fun decodesUtf8BomAndNormalizesDeclaration() {
        val xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><rss><channel><title>中文</title></channel></rss>"
        val bytes = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + xml.toByteArray()

        val decoded = FeedDocumentDecoder.decode(bytes)

        assertEquals(xml, decoded)
    }

    @Test
    fun xmlDeclarationSelectsLegacyCharset() {
        val charset = charset("ISO-8859-1")
        val xml = "<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?><rss><channel><title>Café</title></channel></rss>"

        val decoded = FeedDocumentDecoder.decode(xml.toByteArray(charset))

        assertTrue(decoded.contains("encoding=\"UTF-8\""))
        assertTrue(decoded.contains("Café"))
    }

    @Test
    fun bomTakesPriorityAndUtf16WithoutBomIsDetected() {
        val xml = "<?xml version=\"1.0\"?><rss><channel><title>宽字符</title></channel></rss>"
        val bomBytes = byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + xml.toByteArray(StandardCharsets.UTF_16LE)

        assertEquals(xml, FeedDocumentDecoder.decode(bomBytes, StandardCharsets.UTF_8))
        assertEquals(xml, FeedDocumentDecoder.decode(xml.toByteArray(StandardCharsets.UTF_16BE)))
    }

    @Test
    fun invalidEncodingIsReported() {
        val error = assertThrows(FeedLoadException::class.java) {
            FeedDocumentDecoder.decode(byteArrayOf(0xC3.toByte(), 0x28))
        }

        assertEquals(FeedFailureKind.ENCODING, error.reason)
    }
}
