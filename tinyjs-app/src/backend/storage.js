/**
 * @wk-id tinyjs/storage
 * @wk-title Scoped Transient and Persistent Storage (storage.js)
 * @wk-tags tinyjs, storage, persistence, scoped-state
 * @wk-categories tinyjs-runtime
 *
 * # Scoped Transient and Persistent Storage
 *
 * Implements the AISuper storage contract supporting both in-memory transient storage
 * (`storageGet`, `storagePut`) and file-backed persistent storage (`persistentStorageGet`, `persistentStoragePut`).
 *
 * ## Scoping Rules (`buildKey`)
 * - `applet`: Scoped across the entire applet (`applet:${appletId}:${key}`). Used for auth sessions and tokens.
 * - `feature`: Scoped to a specific feature (`feature:${appletId}:${featureId}:${key}`).
 * - `module`: Scoped to a feature's module (`module:${appletId}:${featureId}:${moduleName}:${key}`).
 * - `module.global`: Shared across features for a given module (`mod_global:${appletId}:${moduleName}:${key}`).
 * - `global`: Unscoped key (`global:${key}`).
 *
 * ## Persistence
 * When configured with a file path (defaults to `${tjs.tmpDir}/aisuper_storage.json`), all persistent puts/deletes
 * write JSON to disk, while `init()` ensures existing data is fully loaded prior to applet script execution.
 */

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
