async function ping(name: string): Promise<{ ok: boolean; message: string }> {
  const normalized = (name || "").trim();
  if (!normalized) {
    return { ok: false, message: "name is required" };
  }
  return { ok: true, message: `Hello, ${normalized}!` };
}

registerExports("exampleModule", ["ping"]);

