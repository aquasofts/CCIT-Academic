package edu.ccit.webvpn.core.webvpn

import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WebVpnCryptoTest {
    @Test
    fun encryptPassword_usesRsa2048AndBase64Output() {
        val encrypted = WebVpnCrypto.encryptPassword("test-password")

        assertNotEquals("test-password", encrypted)
        assertEquals(256, Base64.getDecoder().decode(encrypted).size)
    }

    @Test
    fun deviceId_matchesOfficialFingerprintVisitorIdShape() {
        val deviceId = WebVpnDeviceId.create()

        assertEquals(32, deviceId.length)
        assertTrue(WebVpnDeviceId.isCompatible(deviceId))
        assertFalse(WebVpnDeviceId.isCompatible("01234567-89ab-cdef-0123-456789abcdef"))
    }
}
