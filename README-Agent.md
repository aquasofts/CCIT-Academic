# CCIT Academic App

面向长春工程学院 WebVPN 的 Android 客户端。目前已完成本地账号登录、图形验证码、会话恢复、过期检测、加密保存多账号密码和退出登录，可作为后续教务、门户等模块的统一认证基础。

## 当前实现

```text
app            Compose 登录页、学生端登录、成绩筛选与原生成绩卡片、会话监控
core-academic  教务验证码、表单登录、学期与成绩 HTML 解析
core-ui        Material 3 主题与通用组件
core-webvpn    接口模型、RSA 加密、CookieJar、Keystore 会话、仓库层
docs           WebVPN 协议核对记录
```

登录流程：

```text
authentication/list
  -> graph-captcha/validate-code
  -> RSA 加密密码
  -> auth/finish
  -> 保存响应 Cookie
  -> user/info 仅携带 Cookie 验证登录态
```

学校当前公开前端把加密后的本地登录数据提交到 `auth/finish`，由该接口建立同源 Cookie 会话，随后调用 `user/info` 验证登录态。浏览器、网页探针和独立 Python 探针均已完成真实登录验证。客户端以 `user/info` 为最终登录成功判据，不发送 `Authorization`；升级时会删除旧版 App 留下的 legacy token。

登录态会在以下时机调用 `user/info` 复检：

```text
冷启动恢复会话时
应用从后台回到前台时（30 秒内不重复请求）
应用持续位于前台时每 5 分钟
后续业务模块可在访问受保护接口前调用 requireActiveSession()
```

只有服务端明确返回 400/401/402 才清除无效 Cookie 并回到登录页；断网、超时或服务端临时故障不会误删本地会话。

用户可主动勾选“在本机加密保存此账号和密码”。保存后可从登录页选择账号，只输入新验证码即可重新登录。最多保留最近使用的 10 个账号，可随时单独删除。

## 安全约束

- 不提交真实账号、密码、Cookie、JWT、token、手机号、姓名、学号或验证码图片。
- 密码只在内存中使用学校公开 RSA 公钥和 `RSA/ECB/PKCS1Padding` 加密后提交。
- Cookie 和用户主动保存的账号密码均使用 Android Keystore 的 AES-GCM 密钥加密后写入 DataStore；不再保存认证 token。
- 默认不保存账号密码，只有用户明确勾选后才保存；UI 状态和账号列表中不包含密码明文。
- 网络日志默认关闭；退出登录或验证到会话过期时清除本地凭据。
- 不识别或绕过验证码，不绕过二次认证、改密、绑定账号等安全流程。

## 构建与测试

需要 JDK 17+、Android SDK 35。

```text
./gradlew :core:webvpn:testDebugUnitTest
./gradlew :core:academic:testDebugUnitTest
./gradlew :app:assembleDebug
```

## 学生成绩流程

WebVPN 登录成功后，应用会使用同一个加密 Cookie 会话访问学校教学一体化服务平台。学生端登录有独立验证码；若用户此前主动保存过同名 WebVPN 账号密码，可选择直接使用本机加密凭据，否则需要再次输入教务密码。

登录学生端后可选择学期和“只显示最好成绩”，应用会解析学校返回的成绩 HTML 表格并使用现有 Material 3 主题展示原生课程卡片。协议字段、页面变更策略与安全约束见 `docs/academic-grade-notes.md`。

如果 Windows 仓库路径包含中文，项目已通过 `android.overridePathCheck=true` 允许 AGP 构建；若 JVM 单元测试仍出现类存在但 `ClassNotFoundException`，请从纯 ASCII 路径或临时盘符运行 Gradle。

## 仍需真机确认

- 使用本人授权账号完整走一次验证码和登录流程。
- 验证学校实际过期时 `user/info` 返回的状态码仍为 401/402。
- 遇到 TFA、首次改密或绑定本地账号时，当前客户端会明确提示改用学校官方 WebVPN 页面完成，不会尝试绕过。
