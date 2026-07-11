const state = {
  externalId: "",
  captchaId: "",
  diagnostics: [],
  deviceId: getDeviceId(),
};

const elements = {
  form: document.querySelector("#login-form"),
  username: document.querySelector("#username"),
  password: document.querySelector("#password"),
  code: document.querySelector("#code"),
  captcha: document.querySelector("#captcha"),
  refresh: document.querySelector("#refresh"),
  submit: document.querySelector("#submit"),
  bootstrap: document.querySelector("#bootstrap-state"),
  result: document.querySelector("#result"),
  diagnostics: document.querySelector("#diagnostics"),
  copy: document.querySelector("#copy-log"),
};

function getDeviceId() {
  const existing = localStorage.getItem("webvpn-probe-device-id");
  if (/^[0-9a-f]{32}$/.test(existing || "")) return existing;
  const next = Array.from(crypto.getRandomValues(new Uint8Array(16)), (byte) => byte.toString(16).padStart(2, "0")).join("");
  localStorage.setItem("webvpn-probe-device-id", next);
  return next;
}

function show(element, kind, text) {
  element.className = `state ${kind}`;
  element.textContent = text;
}

function renderDiagnostics(extra = {}) {
  const log = {
    generatedAt: new Date().toISOString(),
    probeVersion: 1,
    deviceIdShape: `${state.deviceId.length} chars / ${/^[0-9a-f]{32}$/.test(state.deviceId) ? "valid" : "invalid"}`,
    events: state.diagnostics,
    ...extra,
  };
  elements.diagnostics.textContent = JSON.stringify(log, null, 2);
}

async function request(path, options) {
  const response = await fetch(path, { cache: "no-store", ...options });
  return response.json();
}

async function bootstrap() {
  elements.refresh.disabled = true;
  elements.submit.disabled = true;
  show(elements.bootstrap, "working", "正在读取官方登录配置…");
  try {
    const data = await request("/probe/bootstrap");
    if (!data.ok) throw new Error(`${data.stage}: ${data.result?.message || "失败"}`);
    state.externalId = data.externalId;
    state.captchaId = data.captcha.id;
    state.diagnostics = data.diagnostics || [];
    elements.captcha.src = data.captcha.captcha;
    elements.code.value = "";
    show(elements.bootstrap, "success", `配置正常 · externalId=${data.externalId} · captchaId 长度=${data.captcha.id.length}`);
    renderDiagnostics({ keyFingerprint: data.keyFingerprint, configuration: data.configuration });
    elements.submit.disabled = false;
  } catch (error) {
    show(elements.bootstrap, "error", error.message);
    renderDiagnostics({ bootstrapError: error.message });
  } finally {
    elements.refresh.disabled = false;
  }
}

elements.form.addEventListener("submit", async (event) => {
  event.preventDefault();
  if (!state.externalId || !state.captchaId) return bootstrap();
  elements.submit.disabled = true;
  show(elements.result, "working", "正在执行 auth/finish → user/info…");
  try {
    const data = await request("/probe/login", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        externalId: state.externalId,
        deviceId: state.deviceId,
        username: elements.username.value,
        password: elements.password.value,
        captchaId: state.captchaId,
        code: elements.code.value,
      }),
    });
    state.diagnostics.push(...(data.diagnostics || []));
    renderDiagnostics({ keyFingerprint: data.keyFingerprint });
    if (data.ok) {
      show(elements.result, "success", "协议验证成功：auth/finish 与 user/info 均通过");
      elements.password.value = "";
      elements.code.value = "";
    } else {
      show(elements.result, "error", `${data.stage || "unknown"} · HTTP ${data.result?.httpStatus ?? "?"} · API ${data.result?.apiCode ?? "?"} · ${data.result?.message || "请求失败"}`);
      await bootstrap();
    }
  } catch (error) {
    show(elements.result, "error", error.message);
    renderDiagnostics({ localError: error.message });
  } finally {
    elements.submit.disabled = false;
  }
});

elements.refresh.addEventListener("click", bootstrap);
elements.copy.addEventListener("click", async () => {
  await navigator.clipboard.writeText(elements.diagnostics.textContent);
  const previous = elements.copy.textContent;
  elements.copy.textContent = "已复制";
  setTimeout(() => { elements.copy.textContent = previous; }, 1200);
});

bootstrap();
