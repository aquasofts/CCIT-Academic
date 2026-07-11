import base64
import json
import unittest

from webvpn_probe import encrypt_password, parse_rsa_public_key


class WebVpnProbeTest(unittest.TestCase):
    def test_public_key_is_rsa_2048(self):
        modulus, exponent = parse_rsa_public_key()
        self.assertEqual(2048, modulus.bit_length())
        self.assertEqual(65537, exponent)

    def test_encryption_matches_har_shape(self):
        encrypted = encrypt_password("test-password")
        self.assertEqual(344, len(encrypted))
        self.assertEqual(256, len(base64.b64decode(encrypted)))

    def test_encryption_uses_random_padding(self):
        first = encrypt_password("test-password")
        second = encrypt_password("test-password")
        self.assertNotEqual(first, second)


if __name__ == "__main__":
    unittest.main()
