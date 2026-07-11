# WebVPN HTTP 400 排查记录

更新日期：2026-07-11

## 已从成功 HAR 确认

成功登录链路为：

```text
GET  /api/access/authentication/list
GET  /api/access/graph-captcha/validate-code?width=150&height=50
POST /api/access/auth/finish
GET  /api/access/user/info
```

`auth/finish` 的外层 JSON 字段顺序是 `externalId`, `data`，其中 `data` 是字符串。字符串解析后的字段顺序是 `deviceId`, `userName`, `password`, `captchaId`, `code`。

成功 HAR 中各字段形态：

```text
deviceId                32 位小写十六进制
password                344 字符 Base64，解码后 256 字节
captchaId               20 字符
code                    4 字符（验证码内容可能变化，长度以实际图片为准）
```

密码算法是 RSA-2048、PKCS#1 v1.5 padding。成功 HAR 引用的官方 `/assets/encrypt-zeF0IWt4.js` 中公钥已经与 `WebVpnCrypto.kt` 和网页测试器逐字核对，三者一致，所以当前证据不支持“公钥过期”假设。

公开接口实测结果：

```text
authentication/list             HTTP 200 / API code 0
graph-captcha/validate-code     HTTP 200 / API code 0
externalId                     2lZl6Ajz
captchaId                      20 字符
以上两个请求均未收到 Set-Cookie
```

## Python 实测与 Android 修复

成功 HAR 中紧跟 `auth/finish` 的 `user/info` 请求没有 `Authorization` 请求头。

独立 Python 脚本使用与 HAR 相同的请求结构和 CookieJar 完成了真实登录：

```text
authentication/list             HTTP 200 / API code 0
graph-captcha/validate-code     HTTP 200 / API code 0
auth/finish                     HTTP 200 / API code 0 / 收到 Cookie
user/info                       HTTP 200 / API code 0 / 仅携带 Cookie
```

旧 App 会保存 `auth/finish` 响应中的兼容 token，并给随后的 `user/info` 添加 `Authorization: Bearer <token>`；这与成功的浏览器和 Python 请求均不一致。Android 已调整为：

- `auth/finish` 返回的 Cookie 是唯一会话凭据。
- 不保存新响应中的 legacy token，并迁移删除旧 token。
- 所有 WebVPN 请求主动移除 `Authorization`。
- `user/info` 仅携带 Cookie。
- API 请求补齐成功 HAR 中的 User-Agent、Accept、Accept-Language 和 Sec-Fetch 请求头。
- HTTP 错误提示增加 `auth/finish` 或 `user/info` 阶段前缀。

Android WebVPN 单元测试全部通过，Debug APK 构建成功。

## 网页测试器

测试器位于 `web-test/`。它使用与成功 HAR 相同的请求结构，且不会向诊断日志写入账号、密码、验证码、captchaId、deviceId、Cookie、token 或用户资料。

运行：

```powershell
cd web-test
node server.mjs
```

手动打开 `http://127.0.0.1:4173`，完成一次登录测试后复制页面底部日志。

独立 Python 版本位于 `web-test/webvpn_probe.py`，可用于后续协议回归对比。
