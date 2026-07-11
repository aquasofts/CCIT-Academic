# WebVPN 登录信息记录

更新时间：2026-07-10

## 2026-07-10 协议复核结论

> 2026-07-11 HAR 复核更新：当前学校登录页实际把本地账号、RSA 加密密码、图形验证码和 deviceId 直接提交到 `POST /api/access/auth/finish`，随后调用 `GET /api/access/user/info`。此前记录的 `auth/start` 路径不适用于当前部署。Android 客户端已改为 `auth/finish`，并保留验证码加载后产生的 Cookie，直到认证完成或明确失败。HAR 中 FingerprintJS `deviceId` 为 32 位小写十六进制，旧版 App 的 36 位 UUID 会被自动迁移。

已重新核对学校当前公开登录页及其前端脚本：

```text
登录页脚本：/assets/index-DoTxJYv5.js（文件名会随部署变化）
本地登录组件：Password-*.js
请求封装：request-*.js
```

当前前端行为如下：

```text
1. auth/finish 返回 code == 0 后不读取 data.token。
2. 前端立即调用 user/info，以同源 Cookie 验证登录态。
3. axios 请求封装没有添加 Authorization 请求头。
4. auth/finish 的 data 是 JSON 字符串，字段结构和 RSA 公钥未变化。
5. authentication/list 当前仍返回 externalId=2lZl6Ajz、authType=1、
   staticVerification=1、useGraphValidateCode=1、dynamicVerification=[]。
```

因此 Android 客户端不得把 `data.token` 作为登录成功的必要条件。实现策略已调整为：

```text
auth/finish code == 0
  -> 接收并保存 Cookie
  -> 调用 user/info
  -> user/info code == 0 才确认登录成功
```

网页和 Python 真实登录均确认当前部署使用 Cookie，且 user/info 不发送 Authorization。Android 已移除 Bearer 兼容路径，不再保存新 token，并在升级时删除旧版 token。

## 客户端会话过期策略

```text
启动恢复              读取 Cookie 后立即调用 user/info
回到前台              距上次检查超过 30 秒时调用 user/info
持续停留前台          每 5 分钟调用一次 user/info
未来受保护业务请求    可先调用 WebVpnAuthRepository.requireActiveSession()
```

处理规则：

```text
API code 或 HTTP status 为 401/402：清除 Cookie/token，回到登录页并刷新验证码
断网、超时、HTTP 5xx：保留当前会话并提示稍后重试，不能据此判定已过期
```

## 保存账号密码

保存功能必须由用户主动勾选。实现约束：

```text
最多保存最近使用的 10 个账号
密码不进入 SavedWebVpnAccount 或 Compose UI 状态
账号密码列表整体使用 Android Keystore AES-GCM 加密后写入 DataStore
选择已保存账号后，登录时短暂解密密码并立即进行 RSA 加密提交
退出 WebVPN 只清 Cookie/token，不删除用户主动保存的账号
登录页提供逐个删除已保存账号的入口
```

## 背景

目标是把学校官方网页中的常用功能接入 Android 开源客户端。WebVPN 是后续访问教务、门户、业务系统等功能的前置登录入口。

学校 WebVPN 登录页：

```text
https://webvpn.ccit.edu.cn/auth/login
```

登录成功后的 WebVPN 首页：

```text
https://webvpn.ccit.edu.cn/site-nav/home
```

## 安全边界

客户端只做用户授权登录，不做以下行为：

```text
不获取、收集、保存他人账号密码
不识别或绕过验证码
不绕过学校权限校验、二步验证、改密、绑定等安全流程
不把真实 Cookie、JWT、token、账号、手机号、姓名、学号提交到仓库
不高频请求学校接口
```

账号、密码、验证码由用户自行输入。验证码图片由客户端从官方接口拉取后展示给用户，用户手动填写。

## 已确认登录态相关信息

浏览器 Cookie 中曾观察到以下登录态 Cookie 名称：

```text
webvpn-jwt
webvpn-token
```

注意：目前原生登录阶段还没有最终确认登录成功后是否必须依赖 Cookie。实现上建议同时支持 token 和 Cookie。

## 已确认用户信息接口

登录成功后可用以下接口验证当前登录态：

```http
GET https://webvpn.ccit.edu.cn/api/access/user/info
```

未登录响应：

```json
{
  "code": 401,
  "message": "未授权",
  "data": null
}
```

已登录响应结构示例，必须脱敏：

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "userId": 0,
    "groups": ["学生", "默认"],
    "username": "2505xxxxxx",
    "email": "",
    "mobile": "155xxxx6539",
    "avatar": "https://thirdwx.qlogo.cn/...",
    "nickname": "xxx",
    "fullName": "xxx",
    "createTime": "2025-09-18 18:40:00",
    "bindWechat": false,
    "externals": [],
    "authType": 1,
    "needTriggerTFA": false,
    "needChangePwd": false,
    "needToBindLocalAccount": false,
    "passwordChanged": 0,
    "source": 18,
    "bindOtp": false
  }
}
```

关键状态字段：

```text
needTriggerTFA           是否需要二步验证
needChangePwd            是否需要修改密码
needToBindLocalAccount   是否需要绑定本地账号
bindWechat               是否已绑定微信
bindOtp                  是否已绑定 OTP
passwordChanged          密码是否已修改
```

## 已观察到的前端接口

公开前端资源中观察到以下接口：

```http
GET  /api/access/authentication/list
GET  /api/access/authentication/all
GET  /api/access/authentication/password-auth
GET  /api/access/authentication/conf

GET  /api/access/graph-captcha/validate-code
GET  /api/access/slide-captcha/info
POST /api/access/slide-captcha/check

POST /api/access/auth/start
POST /api/access/auth/finish
POST /api/access/auth/tfa
POST /api/access/auth/tfa-config
GET  /api/access/auth/user-notice-info
GET  /api/access/auth/consume-session
POST /api/access/auth/session-token
POST /api/access/auth/reset-password

GET  /api/access/user/info
POST /api/access/user/logout
POST /api/access/user/change-password
POST /api/access/user/change-info
POST /api/access/user/change-email
POST /api/access/user/change-mobile
POST /api/access/user/change-avatar
GET  /api/access/user/auth/history
GET  /api/access/access-log/list
```

## 本地登录配置

配置接口：

```http
GET https://webvpn.ccit.edu.cn/api/access/authentication/list
```

已确认返回本地登录方式：

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "list": [
      {
        "externalId": "2lZl6Ajz",
        "name": "本地登录",
        "authType": 1,
        "authOptions": {
          "dynamicVerification": [],
          "loginFrame": {
            "loginSubmitText": "",
            "passwordPlaceholder": "",
            "passwordVerifyText": "",
            "usernamePlaceholder": "用户名（学号或工号）",
            "usernameVerifyText": "请输入用户名（学号或工号）"
          },
          "mergePasswordSource": [],
          "resetPasswordSource": 1,
          "staticVerification": 1,
          "useGraphValidateCode": 1
        }
      },
      {
        "externalId": "wechat",
        "name": "微信",
        "authType": 11,
        "authOptions": null
      }
    ]
  }
}
```

结论：

```text
本地登录 externalId = 2lZl6Ajz
本地登录 authType = 1
启用静态密码 staticVerification = 1
启用图形验证码 useGraphValidateCode = 1
当前 dynamicVerification = []，暂不需要短信、邮箱、OTP
```

## 图形验证码

验证码接口：

```http
GET https://webvpn.ccit.edu.cn/api/access/graph-captcha/validate-code?width=150&height=50
```

响应结构：

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "id": "captcha-id",
    "captcha": "data:image/png;base64,..."
  }
}
```

Android 客户端处理：

```text
保存 data.id 作为 captchaId
显示 data.captcha 图片
用户手动输入验证码 code
登录失败后刷新验证码，清空验证码输入框
```

## 密码加密

WebVPN 本地账号密码登录不是明文提交。前端使用 RSA 公钥加密密码后提交。

前端加密库类似 JSEncrypt，Android 端建议使用：

```text
RSA/ECB/PKCS1Padding
Base64 输出
```

已从前端包中观察到公钥，代码实现时应作为公开配置常量保存。不要保存任何用户密码。

公钥：

```text
-----BEGIN PUBLIC KEY-----
MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAvrqdXbn6tf2kabHLRoE9
IASO5fZixKK5IsFcBMJ0h1tf0WUb3HMygcC3+NecScetMSoPmSOrDLSA6sBWwGEF
LTefRM5vP/eFdkXXB0YpFjfganpBKv4ZOvzCWZGhHOUlACRHViazsZbaPHvLYhsH
Z3XTSbS8iIVDYgrQCHgzs2ULWEUau3489HTAcg7A2V2ZfDDzqaHj5BU5vopbfmjs
cXObP0Ddy4IW4Mc/fcJoJs1e7M4hZg6iTIb8OTnlssOikckenO9mV+GdxdOSG9K2
lUTCS+qxFXQ/vgd7JWi0eTOYG2duEoA2u2T3b/G5I/h8En+tOG6Ax0rztp/YtF0Q
zQIDAQAB
-----END PUBLIC KEY-----
```

## deviceId

前端登录提交中包含：

```json
{
  "deviceId": "..."
}
```

前端来源是 FingerprintJS 的 `visitorId`。

Android 第一版建议：

```text
使用本机稳定安装 ID 作为 deviceId
例如随机 UUID 首次生成后保存到 EncryptedDataStore / DataStore
如果服务端不接受，再考虑更接近浏览器指纹的兼容方案
```

## 登录提交

登录接口：

```http
POST https://webvpn.ccit.edu.cn/api/access/auth/start
```

前端实际提交结构：

```json
{
  "externalId": "2lZl6Ajz",
  "data": "{\"deviceId\":\"...\",\"userName\":\"...\",\"password\":\"RSA加密后的密码\",\"captchaId\":\"...\",\"code\":\"...\"}"
}
```

其中 `data` 是 JSON 字符串，不是嵌套对象。

内部 `data` 字段：

```json
{
  "deviceId": "stable-device-id",
  "userName": "用户输入的账号",
  "password": "RSA加密后的密码",
  "captchaId": "验证码接口返回的 id",
  "code": "用户输入的验证码"
}
```

## 登录失败响应

错误响应示例：

```json
{
  "code": 20072,
  "message": "图形验证码错误",
  "data": null
}
```

处理规则：

```text
HTTP 200 但 code != 0 时，直接向用户展示 message 字段
刷新验证码
清空验证码输入框
保留账号
密码是否清空由 UX 决定，建议保留但不持久化
```

## 登录成功响应

登录成功响应结构：

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "token": "***"
  }
}
```

注意：

```text
真实 token 是 JWT，包含账号等敏感信息
文档、仓库、截图中必须脱敏
```

处理规则：

```text
code == 0 且 data.token 非空时，认为 auth/start 成功
保存 data.token 到本机安全存储
随后调用 /api/access/user/info 验证登录态
验证成功后进入 App
验证失败则提示“登录态验证失败，请重新登录”
```

## token 与 Cookie 策略

目前还没有最终确认：

```text
/api/access/user/info 是否只需要 Cookie
是否需要 Authorization: Bearer token
auth/start 成功响应是否下发 HttpOnly Cookie
```

为了降低第一版实现难度，Android 网络层建议做双保险：

```text
1. OkHttp 配置 CookieJar，自动保存服务端下发 Cookie
2. auth/start 成功后保存 data.token
3. 调用 /api/access/user/info 时优先添加 Authorization: Bearer <token>
4. 同时让 OkHttp 自动携带 Cookie
5. 哪个机制生效就沿用哪个
```

如果 `/user/info` 仍返回 401，再补充抓包确认。

## 第一版 Android 登录流程

```text
1. 启动登录页
2. GET /api/access/authentication/list
3. 找到 authType = 1 的本地登录配置
4. GET /api/access/graph-captcha/validate-code?width=150&height=50
5. 显示验证码图片
6. 用户输入账号、密码、验证码
7. 生成或读取本机 deviceId
8. 使用 RSA 公钥加密密码
9. POST /api/access/auth/start
10. 如果 code != 0，显示 message 并刷新验证码
11. 如果 code == 0，保存 data.token
12. GET /api/access/user/info 验证登录态
13. 如果 user/info code == 0，进入原生客户端首页
14. 如果需要 TFA、改密、绑定账号等状态，进入对应处理页或提示用户使用 WebView 完成
```

## 推荐 Android 模块

```text
feature-auth      登录 UI、验证码、登录状态
core-webvpn       WebVpnSessionManager、登录态校验、Cookie/token 管理
core-network      OkHttp、Retrofit、CookieJar、认证拦截器
core-data         DataStore、Room、本地缓存
core-ui           主题、圆角、字体、按钮、通用组件
```

核心类建议：

```text
WebVpnAuthRepository
  - loadAuthMethods()
  - loadCaptcha()
  - login(username, password, captchaId, code)
  - validateSession()
  - logout()

WebVpnSessionManager
  - saveToken()
  - getToken()
  - clearSession()
  - isLoggedIn()

WebVpnCrypto
  - encryptPassword()

WebVpnDeviceIdProvider
  - getOrCreateDeviceId()

WebVpnApi
  - getAuthenticationList()
  - getGraphCaptcha()
  - startAuth()
  - getUserInfo()
  - logout()
```

## UI 风格记录

抽取参考图的视觉语言：

```text
圆角
字体层级
按钮形态
低对比卡片
图标风格
留白
浅色柔和配色
```

建议用 Kotlin + Jetpack Compose + Material 3，在已有开源框架基础上建立自己的 Design System。

用户说明只能使用开源框架与开源主题，在上面进行功能实现。

可参考：

```text
Now in Android：架构参考
Android Compose Samples / Jetsnack：自定义设计系统参考
```

## 开源注意事项

不要提交：

```text
真实 Cookie 值
JWT / token
真实账号
真实手机号
真实姓名
真实学号
验证码图片
密码
微信 openid
包含个人信息的头像 URL
HAR 文件中的敏感请求头
```

建议：

```text
WebVPN 登录态只保存在本机安全存储中
密码只在内存中短暂使用
登录请求不要打印明文日志
接口访问加限流和缓存
开源仓库只提供脱敏示例数据
```

## 待确认

后续如果能力允许，再确认：

```text
auth/start 成功响应是否有 Set-Cookie
/user/info 仅携带 Cookie 是否成功
/user/info 仅携带 Authorization: Bearer token 是否成功
deviceId 使用 Android 稳定安装 ID 是否会被服务端接受
需要二步验证、首次改密、绑定本地账号时的完整处理流程
```
