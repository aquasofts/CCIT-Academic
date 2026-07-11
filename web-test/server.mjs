import { createHash, createPublicKey, publicEncrypt, randomBytes, constants } from "node:crypto";
import { createServer } from "node:http";
import { request as httpsRequest } from "node:https";
import { readFile } from "node:fs/promises";
import { extname, join } from "node:path";
import { fileURLToPath } from "node:url";

const HOST = "127.0.0.1";
const PORT = Number(process.env.PORT || 4173);
const OFFICIAL_HOST = "webvpn.ccit.edu.cn";
const PUBLIC_DIR = fileURLToPath(new URL("./public/", import.meta.url));
const PUBLIC_KEY = `-----BEGIN PUBLIC KEY-----
MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAvrqdXbn6tf2kabHLRoE9
IASO5fZixKK5IsFcBMJ0h1tf0WUb3HMygcC3+NecScetMSoPmSOrDLSA6sBWwGEF
LTefRM5vP/eFdkXXB0YpFjfganpBKv4ZOvzCWZGhHOUlACRHViazsZbaPHvLYhsH
Z3XTSbS8iIVDYgrQCHgzs2ULWEUau3489HTAcg7A2V2ZfDDzqaHj5BU5vopbfmjs
cXObP0Ddy4IW4Mc/fcJoJs1e7M4hZg6iTIb8OTnlssOikckenO9mV+GdxdOSG9K2
lUTCS+qxFXQ/vgd7JWi0eTOYG2duEoA2u2T3b/G5I/h8En+tOG6Ax0rztp/YtF0Q
zQIDAQAB
-----END PUBLIC KEY-----`;
const KEY_FINGERPRINT = createHash("sha256")
  .update(createPublicKey(PUBLIC_KEY).export({ type: "spki", format: "der" }))
  .digest("hex");
const sessions = new Map();

export function encryptPassword(password) {
  return publicEncrypt(
    { key: PUBLIC_KEY, padding: constants.RSA_PKCS1_PADDING },
    Buffer.from(password, "utf8"),
  ).toString("base64");
}

export function buildFinishBody({ externalId, deviceId, username, password, captchaId, code }) {
  const encryptedPassword = encryptPassword(password);
  const inner = {
    deviceId,
    userName: username.trim(),
    password: encryptedPassword,
    captchaId,
    code: code.trim(),
  };
  return {
    wireBody: JSON.stringify({ externalId, data: JSON.stringify(inner) }),
    shape: {
      outerFields: ["externalId", "data"],
      dataType: "string",
      innerFields: Object.keys(inner),
      deviceId: `${deviceId.length} chars / ${/^[0-9a-f]{32}$/.test(deviceId) ? "valid" : "invalid"}`,
      usernameLength: inner.userName.length,
      encryptedPasswordLength: encryptedPassword.length,
      encryptedPasswordBytes: Buffer.from(encryptedPassword, "base64").length,
      captchaIdLength: captchaId.length,
      captchaCodeLength: inner.code.length,
    },
  };
}

function parseCookies(header = "") {
  return Object.fromEntries(header.split(";").map((part) => part.trim().split(/=(.*)/s).slice(0, 2)).filter(([key]) => key));
}

function sessionFor(req, res) {
  const requestedId = parseCookies(req.headers.cookie).probe_sid;
  const id = requestedId && sessions.has(requestedId) ? requestedId : randomBytes(18).toString("hex");
  if (!sessions.has(id)) sessions.set(id, { cookies: new Map(), events: [] });
  if (id !== requestedId) {
    res.setHeader("Set-Cookie", `probe_sid=${id}; Path=/; HttpOnly; SameSite=Strict`);
  }
  return sessions.get(id);
}

function rememberUpstreamCookies(session, rawHeaders) {
  for (let i = 0; i < rawHeaders.length; i += 2) {
    if (rawHeaders[i].toLowerCase() !== "set-cookie") continue;
    const pair = rawHeaders[i + 1].split(";", 1)[0];
    const separator = pair.indexOf("=");
    if (separator > 0) session.cookies.set(pair.slice(0, separator), pair);
  }
}

function officialRequest(session, path, { method = "GET", body, origin = false } = {}) {
  return new Promise((resolve, reject) => {
    const started = Date.now();
    const headers = {
      Accept: "application/json, text/plain, */*",
      "Accept-Language": "zh-CN,zh;q=0.9",
      "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150.0.0.0 Safari/537.36 Edg/150.0.0.0",
    };
    if (session.cookies.size) headers.Cookie = [...session.cookies.values()].join("; ");
    if (origin) headers.Origin = `https://${OFFICIAL_HOST}`;
    if (body !== undefined) {
      headers["Content-Type"] = "application/json";
      headers["Content-Length"] = Buffer.byteLength(body);
    }
    const upstream = httpsRequest(
      { hostname: OFFICIAL_HOST, port: 443, path, method, headers, timeout: 20_000 },
      (response) => {
        const chunks = [];
        response.on("data", (chunk) => chunks.push(chunk));
        response.on("end", () => {
          rememberUpstreamCookies(session, response.rawHeaders);
          const text = Buffer.concat(chunks).toString("utf8");
          let json = null;
          try { json = JSON.parse(text); } catch { /* Keep the non-JSON response out of diagnostics. */ }
          resolve({
            status: response.statusCode || 0,
            json,
            durationMs: Date.now() - started,
            receivedCookies: response.rawHeaders.some((value, index) => index % 2 === 0 && value.toLowerCase() === "set-cookie"),
          });
        });
      },
    );
    upstream.on("timeout", () => upstream.destroy(new Error("连接学校 WebVPN 超时")));
    upstream.on("error", reject);
    if (body !== undefined) upstream.write(body);
    upstream.end();
  });
}

function safeResponse(result) {
  return {
    httpStatus: result.status,
    apiCode: typeof result.json?.code === "number" ? result.json.code : null,
    message: typeof result.json?.message === "string" ? result.json.message : "上游未返回 JSON 消息",
    durationMs: result.durationMs,
    receivedCookies: result.receivedCookies,
  };
}

function writeJson(res, status, value) {
  const body = JSON.stringify(value);
  res.writeHead(status, { "Content-Type": "application/json; charset=utf-8", "Cache-Control": "no-store" });
  res.end(body);
}

async function readJson(req) {
  const chunks = [];
  let size = 0;
  for await (const chunk of req) {
    size += chunk.length;
    if (size > 32_768) throw new Error("请求体过大");
    chunks.push(chunk);
  }
  return JSON.parse(Buffer.concat(chunks).toString("utf8"));
}

async function bootstrap(session) {
  const methods = await officialRequest(session, "/api/access/authentication/list");
  const local = methods.json?.data?.list?.find((item) => item.authType === 1);
  if (methods.status !== 200 || methods.json?.code !== 0 || !local) {
    return { ok: false, stage: "authentication/list", result: safeResponse(methods) };
  }
  const captcha = await officialRequest(session, "/api/access/graph-captcha/validate-code?width=150&height=50");
  if (captcha.status !== 200 || captcha.json?.code !== 0 || !captcha.json?.data?.id || !captcha.json?.data?.captcha) {
    return { ok: false, stage: "graph-captcha/validate-code", result: safeResponse(captcha) };
  }
  return {
    ok: true,
    externalId: local.externalId,
    captcha: captcha.json.data,
    configuration: {
      authType: local.authType,
      staticVerification: local.authOptions?.staticVerification,
      useGraphValidateCode: local.authOptions?.useGraphValidateCode,
      dynamicVerification: local.authOptions?.dynamicVerification || [],
    },
    diagnostics: [
      { stage: "authentication/list", ...safeResponse(methods) },
      { stage: "graph-captcha/validate-code", ...safeResponse(captcha) },
    ],
    keyFingerprint: KEY_FINGERPRINT,
  };
}

async function login(session, input) {
  for (const field of ["externalId", "deviceId", "username", "password", "captchaId", "code"]) {
    if (typeof input[field] !== "string" || !input[field]) throw new Error(`缺少字段：${field}`);
  }
  if (!/^[0-9a-f]{32}$/.test(input.deviceId)) throw new Error("deviceId 必须是 32 位小写十六进制");
  const { wireBody, shape } = buildFinishBody(input);
  const finish = await officialRequest(session, "/api/access/auth/finish", { method: "POST", body: wireBody, origin: true });
  const diagnostics = [{ stage: "auth/finish", ...safeResponse(finish), requestShape: shape }];
  if (finish.status !== 200 || finish.json?.code !== 0) {
    return { ok: false, stage: "auth/finish", result: safeResponse(finish), diagnostics, keyFingerprint: KEY_FINGERPRINT };
  }
  const userInfo = await officialRequest(session, "/api/access/user/info");
  diagnostics.push({ stage: "user/info", ...safeResponse(userInfo) });
  return {
    ok: userInfo.status === 200 && userInfo.json?.code === 0,
    stage: "user/info",
    result: safeResponse(userInfo),
    diagnostics,
    keyFingerprint: KEY_FINGERPRINT,
  };
}

const mimeTypes = { ".html": "text/html; charset=utf-8", ".css": "text/css; charset=utf-8", ".js": "text/javascript; charset=utf-8" };
async function staticFile(pathname, res) {
  const name = pathname === "/" ? "index.html" : pathname.slice(1);
  if (!/^[a-zA-Z0-9._-]+$/.test(name)) return false;
  try {
    const body = await readFile(join(PUBLIC_DIR, name));
    res.writeHead(200, { "Content-Type": mimeTypes[extname(name)] || "application/octet-stream", "Cache-Control": "no-store" });
    res.end(body);
    return true;
  } catch { return false; }
}

const server = createServer(async (req, res) => {
  const url = new URL(req.url || "/", `http://${HOST}:${PORT}`);
  const session = sessionFor(req, res);
  try {
    if (req.method === "GET" && url.pathname === "/probe/bootstrap") return writeJson(res, 200, await bootstrap(session));
    if (req.method === "POST" && url.pathname === "/probe/login") return writeJson(res, 200, await login(session, await readJson(req)));
    if (req.method === "GET" && await staticFile(url.pathname, res)) return;
    writeJson(res, 404, { ok: false, message: "Not found" });
  } catch (error) {
    writeJson(res, 500, { ok: false, stage: "local-probe", result: { message: error instanceof Error ? error.message : "未知错误" } });
  }
});

if (process.argv[1] === fileURLToPath(import.meta.url)) {
  server.listen(PORT, HOST, () => {
    console.log(`WebVPN 登录测试页：http://${HOST}:${PORT}`);
    console.log("安全提示：服务只监听本机，且不会打印凭据或上游 Cookie。按 Ctrl+C 停止。");
  });
}

export { KEY_FINGERPRINT, PUBLIC_KEY, server };
