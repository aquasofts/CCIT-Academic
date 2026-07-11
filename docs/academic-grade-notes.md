# 学生端与成绩查询协议记录

更新时间：2026-07-11

## 数据来源与脱敏

本实现依据用户授权提供的两份 HAR，对 WebVPN 登录后访问教学一体化服务平台的流程进行本地分析。仓库不包含 HAR 原文件，也不记录真实账号、密码、验证码、Cookie、token、姓名、学号或成绩。

分析时只保留请求方法、路径、字段名、跳转状态和 HTML 表结构。所有凭据值与个人数据均不写入文档或测试样例。

## 请求链

学生端经 WebVPN 代理后的基地址：

```text
https://http-10-198-47-148-8080.webvpn.ccit.edu.cn/jsxsd/
```

主要流程：

```text
WebVPN 登录并恢复父域 Cookie
  -> GET  verifycode.servlet
  -> POST xk/LoginToXk
  -> 302  framework/xsMain.jsp（学生端登录成功）
  -> GET  kscj/cjcx_query（学期筛选配置）
  -> GET  kscj/cjcx_list?kksj=...&xsfs=...（成绩表）
```

学生端登录表单字段：

```text
userAccount  空字符串
userPassword 空字符串
RANDOMCODE   用户手工输入的验证码
encoded      Base64(账号) + "%%%" + Base64(密码)
```

`FormBody` 会按 `application/x-www-form-urlencoded` 对百分号进行转义，服务端解码后得到三个百分号分隔符。客户端不识别或绕过验证码。

## 成绩解析

学期来自 `cjcx_query` 页面中 `name="kksj"` 的下拉选项，保留服务端标记的默认学期。

成绩来自 `cjcx_list` 页面中 `id="dataList"` 的表格。HAR 中观察到 20 列：

```text
序号、开课学期、课程编号、课程名称、分组名、总成绩、成绩标识、学分、总学时、绩点、
是否通选课、原始总成绩、说明、备注、补重学期、考核方式、考试性质、课程属性、课程性质、课程类别
```

原生界面优先展示课程名称、总成绩、学分、绩点、考核方式和课程属性；说明、备注与成绩标识非空时作为提示展示。

## 会话与安全

- WebVPN 和代理后的学生端共用一个内存 CookieJar。
- WebVPN 当前只使用 Cookie 会话，所有请求都不发送 Authorization Bearer。
- WebVPN 父域 Cookie 与学生端 `JSESSIONID` 一起使用 Android Keystore AES-GCM 加密后保存。
- Cookie 持久化记录原始域、路径、HostOnly、Secure、HttpOnly 和过期时间，确保代理子域会话可以正确恢复。
- 退出 WebVPN 或确认会话过期时清除全部会话 Cookie；用户主动保存的账号密码单独管理。
- 网络日志默认关闭，错误信息不包含请求体、Cookie 或页面中的个人信息。

## 页面变更检测

若学校升级教务系统并改变 HTML 结构，客户端会在无法识别学期列表时明确提示“页面结构可能已更新”，而不是返回错误成绩。需要重新抓取已授权、已脱敏的 HAR 后更新解析器和测试。
