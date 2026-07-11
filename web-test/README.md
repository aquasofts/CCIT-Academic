# WebVPN 登录协议测试页

这个测试页用于在修改 Android App 之前，单独验证学校 WebVPN 当前登录协议。

## 启动

在 `web-test` 目录运行：

```powershell
node server.mjs
```

也可以直接运行：

```powershell
.\start.ps1
```

然后手动打开 `http://127.0.0.1:4173`。

如果系统找不到 Node，可使用 Codex 工作区自带的 Node：

```powershell
& 'C:\Users\mosior\.cache\codex-runtimes\codex-primary-runtime\dependencies\node\bin\node.exe' server.mjs
```

## 测试与日志

输入自己的账号、密码和图片验证码后点击“开始协议测试”。把页面底部“脱敏诊断结果”复制回来即可。

诊断日志不会包含账号、明文或密文密码、验证码、captchaId、deviceId、Cookie、token 和用户信息，只保留字段长度、状态码、API code、服务器 message、耗时及是否收到 Cookie。

## 与成功 HAR 对齐的协议

- `POST /api/access/auth/finish`
- 外层字段：`externalId`, `data`
- `data` 是 JSON 字符串，不是 JSON 对象
- 内层字段：`deviceId`, `userName`, `password`, `captchaId`, `code`
- `deviceId` 为 32 位小写十六进制
- 密码使用 RSA-2048 PKCS#1 v1.5，加密结果为 344 字符 Base64 / 256 字节密文
- 登录成功后以 `GET /api/access/user/info` 作为最终判定

本地服务仅监听 `127.0.0.1`，凭据只在内存中短暂使用并直接转发到 `webvpn.ccit.edu.cn`。

## 独立 Python 测试

不启动网页和 Node 服务，直接运行：

```powershell
python .\webvpn_probe.py
```

如果系统 `python` 不可用，直接运行自动选择解释器的启动脚本：

```powershell
.\run-python-probe.ps1
```

脚本不依赖第三方包。它会下载验证码到系统临时目录并尝试用系统图片查看器打开；随后在终端读取账号、隐藏输入密码、读取验证码，执行与成功 HAR 相同的 `auth/finish → user/info` 流程。终端最后输出可回传的脱敏日志。
