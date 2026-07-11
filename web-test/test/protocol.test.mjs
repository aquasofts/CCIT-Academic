import assert from "node:assert/strict";
import test from "node:test";
import { buildFinishBody, encryptPassword, KEY_FINGERPRINT } from "../server.mjs";

test("RSA output matches the successful HAR shape", () => {
  const encrypted = encryptPassword("test-password");
  assert.equal(encrypted.length, 344);
  assert.equal(Buffer.from(encrypted, "base64").length, 256);
  assert.notEqual(encrypted, "test-password");
});

test("finish request preserves the exact nested JSON contract", () => {
  const result = buildFinishBody({
    externalId: "2lZl6Ajz",
    deviceId: "0123456789abcdef0123456789abcdef",
    username: " student ",
    password: "secret",
    captchaId: "12345678901234567890",
    code: "a1b2",
  });
  const outer = JSON.parse(result.wireBody);
  assert.deepEqual(Object.keys(outer), ["externalId", "data"]);
  assert.equal(typeof outer.data, "string");
  const inner = JSON.parse(outer.data);
  assert.deepEqual(Object.keys(inner), ["deviceId", "userName", "password", "captchaId", "code"]);
  assert.equal(inner.userName, "student");
  assert.equal(result.shape.encryptedPasswordBytes, 256);
  assert.match(result.shape.deviceId, /valid/);
});

test("public key fingerprint is stable and non-secret", () => {
  assert.match(KEY_FINGERPRINT, /^[0-9a-f]{64}$/);
});
