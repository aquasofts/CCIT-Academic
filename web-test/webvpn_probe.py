#!/usr/bin/env python3
"""Interactive, dependency-free CCIT WebVPN login protocol probe.

Credentials are read interactively, kept only in memory, and sent only to
https://webvpn.ccit.edu.cn. Output is deliberately sanitized.
"""

from __future__ import annotations

import base64
import getpass
import http.cookiejar
import json
import os
import secrets
import sys
import tempfile
import time
import urllib.error
import urllib.parse
import urllib.request
from dataclasses import dataclass
from typing import Any


BASE_URL = "https://webvpn.ccit.edu.cn"
PUBLIC_KEY_PEM = """-----BEGIN PUBLIC KEY-----
MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAvrqdXbn6tf2kabHLRoE9
IASO5fZixKK5IsFcBMJ0h1tf0WUb3HMygcC3+NecScetMSoPmSOrDLSA6sBWwGEF
LTefRM5vP/eFdkXXB0YpFjfganpBKv4ZOvzCWZGhHOUlACRHViazsZbaPHvLYhsH
Z3XTSbS8iIVDYgrQCHgzs2ULWEUau3489HTAcg7A2V2ZfDDzqaHj5BU5vopbfmjs
cXObP0Ddy4IW4Mc/fcJoJs1e7M4hZg6iTIb8OTnlssOikckenO9mV+GdxdOSG9K2
lUTCS+qxFXQ/vgd7JWi0eTOYG2duEoA2u2T3b/G5I/h8En+tOG6Ax0rztp/YtF0Q
zQIDAQAB
-----END PUBLIC KEY-----"""
BROWSER_USER_AGENT = (
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
    "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150.0.0.0 "
    "Safari/537.36 Edg/150.0.0.0"
)


class ProbeError(Exception):
    pass


def _read_der_length(data: bytes, offset: int) -> tuple[int, int]:
    first = data[offset]
    offset += 1
    if first < 0x80:
        return first, offset
    count = first & 0x7F
    if count == 0 or count > 4:
        raise ProbeError("不支持的 DER 长度")
    return int.from_bytes(data[offset : offset + count], "big"), offset + count


def _read_der_tlv(data: bytes, offset: int, expected_tag: int) -> tuple[bytes, int]:
    if offset >= len(data) or data[offset] != expected_tag:
        raise ProbeError(f"RSA 公钥 DER 结构错误，期望 tag 0x{expected_tag:02x}")
    length, value_offset = _read_der_length(data, offset + 1)
    end = value_offset + length
    if end > len(data):
        raise ProbeError("RSA 公钥 DER 长度越界")
    return data[value_offset:end], end


def parse_rsa_public_key(pem: str = PUBLIC_KEY_PEM) -> tuple[int, int]:
    encoded = "".join(line for line in pem.splitlines() if not line.startswith("-----"))
    der = base64.b64decode(encoded)
    spki, end = _read_der_tlv(der, 0, 0x30)
    if end != len(der):
        raise ProbeError("RSA 公钥包含多余数据")

    _, offset = _read_der_tlv(spki, 0, 0x30)  # AlgorithmIdentifier
    bit_string, offset = _read_der_tlv(spki, offset, 0x03)
    if offset != len(spki) or not bit_string or bit_string[0] != 0:
        raise ProbeError("RSA 公钥 BIT STRING 错误")

    rsa_sequence, end = _read_der_tlv(bit_string[1:], 0, 0x30)
    if end != len(bit_string) - 1:
        raise ProbeError("RSA 公钥主体错误")
    modulus_bytes, offset = _read_der_tlv(rsa_sequence, 0, 0x02)
    exponent_bytes, offset = _read_der_tlv(rsa_sequence, offset, 0x02)
    if offset != len(rsa_sequence):
        raise ProbeError("RSA 公钥整数结构错误")
    return int.from_bytes(modulus_bytes, "big"), int.from_bytes(exponent_bytes, "big")


def encrypt_password(password: str) -> str:
    modulus, exponent = parse_rsa_public_key()
    key_size = (modulus.bit_length() + 7) // 8
    message = password.encode("utf-8")
    if len(message) > key_size - 11:
        raise ProbeError("密码过长，无法使用 RSA PKCS#1 v1.5 加密")

    padding_length = key_size - len(message) - 3
    padding = bytearray()
    while len(padding) < padding_length:
        padding.extend(byte for byte in secrets.token_bytes(padding_length - len(padding)) if byte)
    encoded_message = b"\x00\x02" + bytes(padding) + b"\x00" + message
    encrypted = pow(int.from_bytes(encoded_message, "big"), exponent, modulus)
    return base64.b64encode(encrypted.to_bytes(key_size, "big")).decode("ascii")


@dataclass
class ApiResult:
    stage: str
    http_status: int
    body: dict[str, Any] | None
    duration_ms: int
    received_cookie: bool

    @property
    def api_code(self) -> int | None:
        value = self.body.get("code") if self.body else None
        return value if isinstance(value, int) else None

    @property
    def message(self) -> str:
        value = self.body.get("message") if self.body else None
        return value if isinstance(value, str) else "上游未返回 JSON 消息"

    @property
    def ok(self) -> bool:
        return self.http_status == 200 and self.api_code == 0

    def sanitized(self) -> dict[str, Any]:
        return {
            "stage": self.stage,
            "httpStatus": self.http_status,
            "apiCode": self.api_code,
            "message": self.message,
            "durationMs": self.duration_ms,
            "receivedCookies": self.received_cookie,
        }


class WebVpnProbe:
    def __init__(self) -> None:
        self.cookie_jar = http.cookiejar.CookieJar()
        self.opener = urllib.request.build_opener(
            urllib.request.HTTPCookieProcessor(self.cookie_jar)
        )

    def request(
        self,
        stage: str,
        path: str,
        *,
        method: str = "GET",
        body: dict[str, Any] | None = None,
        origin: bool = False,
    ) -> ApiResult:
        encoded_body = None
        headers = {
            "Accept": "application/json, text/plain, */*",
            "Accept-Language": "zh-CN,zh;q=0.9",
            "User-Agent": BROWSER_USER_AGENT,
            "Sec-Fetch-Dest": "empty",
            "Sec-Fetch-Mode": "cors",
            "Sec-Fetch-Site": "same-origin",
            "DNT": "1",
        }
        if body is not None:
            encoded_body = json.dumps(body, ensure_ascii=False, separators=(",", ":")).encode("utf-8")
            headers["Content-Type"] = "application/json"
        if origin:
            headers["Origin"] = BASE_URL

        before = {cookie.name for cookie in self.cookie_jar}
        request = urllib.request.Request(
            urllib.parse.urljoin(BASE_URL, path),
            data=encoded_body,
            headers=headers,
            method=method,
        )
        started = time.perf_counter()
        try:
            response = self.opener.open(request, timeout=20)
            status = response.status
            raw = response.read()
        except urllib.error.HTTPError as error:
            status = error.code
            raw = error.read()
        except urllib.error.URLError as error:
            raise ProbeError(f"{stage} 网络错误：{error.reason}") from error

        try:
            parsed = json.loads(raw.decode("utf-8"))
            parsed = parsed if isinstance(parsed, dict) else None
        except (UnicodeDecodeError, json.JSONDecodeError):
            parsed = None
        after = {cookie.name for cookie in self.cookie_jar}
        return ApiResult(
            stage=stage,
            http_status=status,
            body=parsed,
            duration_ms=round((time.perf_counter() - started) * 1000),
            received_cookie=bool(after - before),
        )


def save_and_open_captcha(data_uri: str) -> str:
    try:
        prefix, encoded = data_uri.split(",", 1)
        if ";base64" not in prefix:
            raise ValueError
        image = base64.b64decode(encoded, validate=True)
    except (ValueError, base64.binascii.Error) as error:
        raise ProbeError("验证码图片格式错误") from error

    with tempfile.NamedTemporaryFile(prefix="webvpn-captcha-", suffix=".png", delete=False) as file:
        file.write(image)
        path = file.name
    if sys.platform == "win32":
        try:
            os.startfile(path)  # type: ignore[attr-defined]
        except OSError:
            pass
    return path


def require_ok(result: ApiResult) -> None:
    print(json.dumps(result.sanitized(), ensure_ascii=False))
    if not result.ok:
        raise ProbeError(
            f"{result.stage} 失败：HTTP {result.http_status} / API {result.api_code} / {result.message}"
        )


def main() -> int:
    print("CCIT WebVPN Python 协议测试")
    print("凭据仅保存在内存中，并且只发送到 webvpn.ccit.edu.cn。\n")
    probe = WebVpnProbe()
    events: list[dict[str, Any]] = []

    try:
        methods = probe.request("authentication/list", "/api/access/authentication/list")
        events.append(methods.sanitized())
        require_ok(methods)
        method_list = methods.body.get("data", {}).get("list", []) if methods.body else []
        local = next((item for item in method_list if item.get("authType") == 1), None)
        if not local:
            raise ProbeError("没有找到本地账号登录配置")

        captcha = probe.request(
            "graph-captcha/validate-code",
            "/api/access/graph-captcha/validate-code?width=150&height=50",
        )
        events.append(captcha.sanitized())
        require_ok(captcha)
        captcha_data = captcha.body.get("data", {}) if captcha.body else {}
        captcha_id = captcha_data.get("id")
        captcha_uri = captcha_data.get("captcha")
        if not isinstance(captcha_id, str) or not isinstance(captcha_uri, str):
            raise ProbeError("验证码响应缺少 id 或图片")
        captcha_path = save_and_open_captcha(captcha_uri)
        print(f"验证码图片：{captcha_path}")

        username = input("账号：").strip()
        password = getpass.getpass("密码（输入时不显示）：")
        code = input("图片验证码：").strip()
        if not username or not password or not code:
            raise ProbeError("账号、密码和验证码都不能为空")

        device_id = secrets.token_hex(16)
        encrypted_password = encrypt_password(password)
        inner = {
            "deviceId": device_id,
            "userName": username,
            "password": encrypted_password,
            "captchaId": captcha_id,
            "code": code,
        }
        finish_body = {
            "externalId": local["externalId"],
            "data": json.dumps(inner, ensure_ascii=False, separators=(",", ":")),
        }
        request_shape = {
            "outerFields": list(finish_body),
            "dataType": type(finish_body["data"]).__name__,
            "innerFields": list(inner),
            "deviceId": "32 chars / valid",
            "usernameLength": len(username),
            "encryptedPasswordLength": len(encrypted_password),
            "encryptedPasswordBytes": len(base64.b64decode(encrypted_password)),
            "captchaIdLength": len(captcha_id),
            "captchaCodeLength": len(code),
        }

        finish = probe.request(
            "auth/finish",
            "/api/access/auth/finish",
            method="POST",
            body=finish_body,
            origin=True,
        )
        finish_event = finish.sanitized() | {"requestShape": request_shape}
        events.append(finish_event)
        require_ok(finish)

        user_info = probe.request("user/info", "/api/access/user/info")
        events.append(user_info.sanitized())
        require_ok(user_info)
        print("\n协议测试成功：auth/finish 与 user/info 均通过。")
        return 0
    except (ProbeError, KeyError) as error:
        print(f"\n测试失败：{error}", file=sys.stderr)
        return 1
    finally:
        print("\n脱敏日志：")
        print(json.dumps({"probe": "python", "events": events}, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    raise SystemExit(main())
