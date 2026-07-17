# 发布 APK

向 GitHub 推送纯版本号 Tag 后，`.github/workflows/release.yml` 会自动构建并发布两个经过 R8 优化、正式签名的 APK。

例如推送 `1.1.0` 后将创建：

- Tag：`1.1.0`
- Release 标题：`Release 1.1.0`
- Full 安装包：`Full-Cithub-V1.1.0.apk`（包含本机验证码识别和自动回填）
- Lite 安装包：`Lite-Cithub-V1.1.0.apk`（手动输入验证码，不包含 ML Kit）

## 首次配置

在 GitHub 仓库的 **Settings → Secrets and variables → Actions** 中添加以下 Repository secrets：

- `ANDROID_SIGNING_KEYSTORE_BASE64`：正式签名 keystore 文件的 Base64 内容。
- `ANDROID_SIGNING_STORE_PASSWORD`：keystore 密码。
- `ANDROID_SIGNING_KEY_ALIAS`：签名密钥别名。
- `ANDROID_SIGNING_KEY_PASSWORD`：签名密钥密码。

keystore 和密码不得提交到仓库。发布工作流会校验签名、不可调试标记和 Baseline Profile，任一校验失败都不会创建 Release。

## 发布新版本

确认目标提交已推送后执行：

```bash
git tag 1.1.0
git push origin 1.1.0
```

Tag 必须使用 `主版本.次版本.修订号` 格式，不加 `v` 前缀；次版本号和修订号的取值范围均为 `0` 到 `999`。
