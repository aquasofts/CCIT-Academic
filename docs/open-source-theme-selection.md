# 开源主题选择

更新时间：2026-06-29

## 结论

第一版采用 Android 官方开源项目的组合方式：

```text
基础架构与 Material 3 主题方向：Now in Android
自定义 Design System 组织方式：Android Compose Samples / Jetsnack
具体视觉语言：本项目按参考图重新定义颜色、圆角、字体层级和卡片密度
```

不直接复制上游 UI 文件，避免把新闻类或食品类产品风格原样带入学校 WebVPN 客户端；只复用开源框架、模块组织和 Compose 主题搭建方式。

上游来源：

```text
Now in Android: https://github.com/android/nowinandroid
Android Compose Samples / Jetsnack: https://github.com/android/compose-samples
Now in Android License: https://github.com/android/nowinandroid/blob/main/LICENSE
Android Compose Samples License: https://github.com/android/compose-samples/blob/main/LICENSE
```

## 选择理由

```text
1. 两个来源均为 Android 官方维护或官方示例，长期可维护。
2. 许可证为 Apache-2.0，适合开源客户端二次开发。
3. 技术栈与当前规范一致：Kotlin、Jetpack Compose、Material 3。
4. Jetsnack 的自定义主题方式适合把参考图里的柔和浅色、低对比卡片、圆角和粗字体抽成 core-ui。
5. Now in Android 的模块化方式适合后续扩展 feature-auth、core-network、core-data、core-webvpn。
```

## 第一版落地

```text
core-ui
  WebVpnTheme
  WebVpnColors
  WebVpnTypography
  WebVpnShapes
  WebVpnCard
  WebVpnPrimaryButton

core-webvpn
  WebVpnApi
  WebVpnAuthRepository
  WebVpnSessionManager
  WebVpnCrypto
  WebVpnCookieJar

app
  登录页
  验证码展示与刷新
  RSA 加密登录
  token + Cookie 双保险网络策略
  登录成功后的仪表盘占位
```
