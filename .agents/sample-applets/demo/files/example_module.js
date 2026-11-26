// Generated from modules-ts (exampleModule). Do not edit manually.
"use strict";
async function ping(name) {
  const normalized = (name || "").trim();
  if (!normalized) {
    return { ok: false, message: "name is required" };
  }
  return { ok: true, message: `Hello, ${normalized}!` };
}
registerExports("exampleModule", ["ping"]);
