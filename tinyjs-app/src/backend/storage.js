// storage.js - Scoped transient & persistent state storage

export class StateStorage {
  constructor(persistentFilePath = null) {
    this.memory = new Map();
    this.persistent = new Map();
    this.persistentFilePath = persistentFilePath;
    this.loadPersistent();
  }

  async init() {
    if (!this.persistentFilePath || typeof tjs === 'undefined') return;
    try {
      const data = await tjs.readFile(this.persistentFilePath);
      const str = new TextDecoder().decode(data);
      const obj = JSON.parse(str);
      for (const [k, v] of Object.entries(obj)) {
        this.persistent.set(k, v);
      }
    } catch (_) {}
  }

  loadPersistent() {
    this.init().catch(() => {});
  }

  savePersistent() {
    if (!this.persistentFilePath || typeof tjs === 'undefined') return;
    try {
      const obj = {};
      for (const [k, v] of this.persistent.entries()) {
        obj[k] = v;
      }
      const data = new TextEncoder().encode(JSON.stringify(obj, null, 2));
      tjs.writeFile(this.persistentFilePath, data).catch(() => {});
    } catch (_) {}
  }

  buildKey(scope, appletId, featureId, moduleName, key) {
    switch (scope) {
      case 'applet':
        return `applet:${appletId}:${key}`;
      case 'feature':
        return `feature:${appletId}:${featureId}:${key}`;
      case 'module':
        return `module:${appletId}:${featureId}:${moduleName || 'default'}:${key}`;
      case 'module.global':
        return `mod_global:${appletId}:${moduleName || 'default'}:${key}`;
      default:
        return `global:${key}`;
    }
  }

  getString(isPersistent, scope, ctx, key) {
    const fullKey = this.buildKey(scope, ctx.appletId, ctx.featureId, ctx.moduleName, key);
    const store = isPersistent ? this.persistent : this.memory;
    const val = store.get(fullKey);
    return typeof val === 'string' ? val : (val != null ? JSON.stringify(val) : null);
  }

  putString(isPersistent, scope, ctx, key, value) {
    const fullKey = this.buildKey(scope, ctx.appletId, ctx.featureId, ctx.moduleName, key);
    const store = isPersistent ? this.persistent : this.memory;
    store.set(fullKey, String(value));
    if (isPersistent) this.savePersistent();
    return value;
  }

  delete(isPersistent, scope, ctx, key) {
    const fullKey = this.buildKey(scope, ctx.appletId, ctx.featureId, ctx.moduleName, key);
    const store = isPersistent ? this.persistent : this.memory;
    store.delete(fullKey);
    if (isPersistent) this.savePersistent();
  }

  getObject(isPersistent, scope, ctx, key) {
    const fullKey = this.buildKey(scope, ctx.appletId, ctx.featureId, ctx.moduleName, key);
    const store = isPersistent ? this.persistent : this.memory;
    const val = store.get(fullKey);
    return val !== undefined ? val : null;
  }

  putObject(isPersistent, scope, ctx, key, value) {
    const fullKey = this.buildKey(scope, ctx.appletId, ctx.featureId, ctx.moduleName, key);
    const store = isPersistent ? this.persistent : this.memory;
    store.set(fullKey, value);
    if (isPersistent) this.savePersistent();
    return value;
  }
}
