package edu.ccit.webvpn.core.webvpn

import java.security.KeyFactory
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import javax.crypto.Cipher

object WebVpnCrypto {
    private const val PublicKeyPem = """
-----BEGIN PUBLIC KEY-----
MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAvrqdXbn6tf2kabHLRoE9
IASO5fZixKK5IsFcBMJ0h1tf0WUb3HMygcC3+NecScetMSoPmSOrDLSA6sBWwGEF
LTefRM5vP/eFdkXXB0YpFjfganpBKv4ZOvzCWZGhHOUlACRHViazsZbaPHvLYhsH
Z3XTSbS8iIVDYgrQCHgzs2ULWEUau3489HTAcg7A2V2ZfDDzqaHj5BU5vopbfmjs
cXObP0Ddy4IW4Mc/fcJoJs1e7M4hZg6iTIb8OTnlssOikckenO9mV+GdxdOSG9K2
lUTCS+qxFXQ/vgd7JWi0eTOYG2duEoA2u2T3b/G5I/h8En+tOG6Ax0rztp/YtF0Q
zQIDAQAB
-----END PUBLIC KEY-----
"""

    fun encryptPassword(rawPassword: String): String {
        require(rawPassword.isNotEmpty()) { "密码不能为空" }

        val publicKeyBytes = PublicKeyPem
            .replace("-----BEGIN PUBLIC KEY-----", "")
            .replace("-----END PUBLIC KEY-----", "")
            .replace("\\s".toRegex(), "")
            .let(Base64.getDecoder()::decode)

        val publicKey = KeyFactory
            .getInstance("RSA")
            .generatePublic(X509EncodedKeySpec(publicKeyBytes))

        val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
        cipher.init(Cipher.ENCRYPT_MODE, publicKey)

        return Base64.getEncoder().encodeToString(
            cipher.doFinal(rawPassword.toByteArray(Charsets.UTF_8)),
        )
    }
}
