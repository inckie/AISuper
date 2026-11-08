// applet_session.js - Manages applet lifecycle, workers, layouts, themes, and snapshots
import { SyncRpcBuffer, SyncRpcHost } from './sync_rpc.js';
import { StateStorage } from './storage.js';

export class AppletSession {
  constructor(sessionId, manifestPath, onStateChange = null) {
    this.sessionId = sessionId;
    this.manifestPath = manifestPath;
    this.onStateChange = onStateChange;

    // Normalize baseDir with forward slashes
    const normalized = manifestPath.replace(/\\/g, '/');
    const lastSlash = normalized.lastIndexOf('/');
    this.baseDir = lastSlash >= 0 ? normalized.substring(0, lastSlash) : '.';

    this.manifest = null;
    this.storage = new StateStorage();
    this.values = {};
    this.stylesById = {};
    this.currentStyleId = null;
    this.currentFramework = 'Rikka';
    this.currentFeatureId = null;
    this.currentLayout = null;

    this.activeWorker = null;
    this.syncRpcHost = null;
    this.subscriptionCounter = 0;
    this.valueSubscriptions = new Map(); // key -> Array<{ id, callbackName }>
    this.pendingInvocations = new Map(); // id -> { resolve, reject }
    this.activeModal = null; // { id, title, message, confirmText, cancelText, isDestructive }
  }

  resolvePath(relativePath) {
    if (!relativePath) return '';
    const clean = relativePath.replace(/\\/g, '/');
    if (clean.startsWith('/') || /^[a-zA-Z]:\//.test(clean)) {
      return clean;
    }
    return `${this.baseDir}/${clean}`;
  }

  async readTextFile(path) {
    const fullPath = this.resolvePath(path);
    const bytes = await tjs.readFile(fullPath);
    return new TextDecoder().decode(bytes);
  }

  async load() {
    // 1. Read and parse manifest
    const manifestJson = await this.readTextFile(this.manifestPath);
    this.manifest = JSON.parse(manifestJson);

    // 2. Load all style sheets
    this.stylesById = {};
    if (this.manifest.styles) {
      for (const [styleId, styleDef] of Object.entries(this.manifest.styles)) {
        try {
          const styleText = await this.readTextFile(styleDef.file);
          const sheet = JSON.parse(styleText);
          const name = styleDef.name || sheet.name || styleId;
          this.stylesById[styleId] = { id: styleId, name, sheet };
        } catch (err) {
          console.error(`[AppletSession] Failed to load style '${styleId}':`, err);
        }
      }
    }

    // 3. Set default style
    const defaultStyle = this.manifest.defaultStyle;
    if (defaultStyle && this.stylesById[defaultStyle]) {
      this.currentStyleId = defaultStyle;
    } else {
      const firstStyle = Object.keys(this.stylesById)[0];
      this.currentStyleId = firstStyle || null;
    }

    // 4. Launch entry feature
    const entryFeature = this.manifest.entryFeature;
    if (entryFeature) {
      await this.launchFeature(entryFeature);
    }
  }

  async launchFeature(featureId) {
    const featureDef = this.manifest?.features?.[featureId];
    if (!featureDef) {
      console.warn(`[AppletSession] Feature '${featureId}' not found in manifest.`);
      return;
    }

    // Terminate existing worker
    if (this.activeWorker) {
      try {
        this.activeWorker.terminate();
      } catch (_) {}
      this.activeWorker = null;
      this.syncRpcHost = null;
    }

    this.currentFeatureId = featureId;
    this.valueSubscriptions.clear();
    this.activeModal = null;

    // Load initial layout for feature
    const initialLayoutPath = featureDef.layout || (featureDef.layouts ? Object.values(featureDef.layouts)[0] : null);
    if (initialLayoutPath) {
      try {
        const layoutText = await this.readTextFile(initialLayoutPath);
        const parsed = JSON.parse(layoutText);
        // Expecting { layout: Widget } or direct Widget
        this.currentLayout = parsed.layout ? parsed : { layout: parsed };
      } catch (err) {
        console.error(`[AppletSession] Failed to load layout '${initialLayoutPath}':`, err);
        this.currentLayout = null;
      }
    } else {
      this.currentLayout = null;
    }

    // Allocate SharedArrayBuffer and setup sync RPC host
    const sab = SyncRpcBuffer.create(64 * 1024);
    this.syncRpcHost = new SyncRpcHost(sab, (method, args) => this.handleSyncRpc(method, args));

    // Spawn Feature Worker Isolate
    const workerUrl = new URL('./workers/feature_worker.js', import.meta.url);
    this.activeWorker = new Worker(workerUrl);

    this.activeWorker.onmessage = (e) => {
      const msg = e.data;
      if (!msg) return;

      if (msg.type === '__sync_rpc__') {
        this.syncRpcHost.handleRpc();
        return;
      }

      if (msg.type === 'modal_request') {
        this.activeModal = {
          id: msg.id,
          ...msg.options
        };
        this.notifyStateChange('modal_request');
        return;
      }

      if (msg.type === 'invoke_result' || msg.type === 'invoke_error') {
        const pending = this.pendingInvocations.get(msg.id);
        if (pending) {
          this.pendingInvocations.delete(msg.id);
          if (msg.type === 'invoke_error') {
            pending.reject(new Error(msg.error));
          } else {
            pending.resolve(msg.result);
          }
        }
        return;
      }
    };

    // Initialize worker isolate
    this.activeWorker.postMessage({
      type: 'init',
      sab,
      appletId: this.manifest.id,
      featureId
    });

    // Load and evaluate script
    if (featureDef.script) {
      try {
        const scriptContent = await this.readTextFile(featureDef.script);
        this.activeWorker.postMessage({
          type: 'loadScript',
          script: scriptContent
        });

        // Call initialize()
        await this.invokeWorker('initialize', []);
      } catch (err) {
        console.error(`[AppletSession] Failed to load script for feature '${featureId}':`, err);
      }
    }

    this.notifyStateChange(`launch:${featureId}`);
  }

  handleSyncRpc(method, args) {
    const ctx = {
      appletId: this.manifest?.id || 'unknown',
      featureId: this.currentFeatureId || 'unknown'
    };

    switch (method) {
      case 'getValue': {
        const key = args[0];
        return this.values[key] !== undefined ? this.values[key] : '';
      }

      case 'setValue': {
        const key = args[0];
        const val = args[1];
        this.updateValueInternal(key, val);
        return true;
      }

      case 'subscribeValue': {
        const [key, callbackName] = args;
        const subId = ++this.subscriptionCounter;
        if (!this.valueSubscriptions.has(key)) {
          this.valueSubscriptions.set(key, []);
        }
        this.valueSubscriptions.get(key).push({ id: subId, callbackName });
        return subId;
      }

      case 'unsubscribeValue': {
        const [subId] = args;
        for (const [key, subs] of this.valueSubscriptions.entries()) {
          const idx = subs.findIndex(s => s.id === subId);
          if (idx >= 0) {
            subs.splice(idx, 1);
            break;
          }
        }
        return true;
      }

      case 'getAvailableThemes': {
        return Object.values(this.stylesById).map(s => ({ id: s.id, name: s.name }));
      }

      case 'getCurrentTheme': {
        return this.currentStyleId || '';
      }

      case 'setCurrentTheme': {
        const [themeId] = args;
        if (themeId && this.stylesById[themeId]) {
          this.currentStyleId = themeId;
          this.notifyStateChange(`theme:${themeId}`);
          return true;
        }
        return false;
      }

      case 'getAvailableFrameworks': {
        return [
          { id: 'Rikka', name: 'Rikka' },
          { id: 'Material3', name: 'Material3' }
        ];
      }

      case 'getCurrentFramework': {
        return this.currentFramework;
      }

      case 'setCurrentFramework': {
        const [frameworkId] = args;
        if (frameworkId) {
          this.currentFramework = frameworkId;
          this.notifyStateChange(`framework:${frameworkId}`);
          return true;
        }
        return false;
      }

      case 'getFeatures': {
        if (!this.manifest?.features) return [];
        return Object.entries(this.manifest.features).map(([id, def]) => ({
          id,
          name: def.name || id
        }));
      }

      case 'launchFeature': {
        const [featureId] = args;
        setTimeout(() => this.launchFeature(featureId), 0);
        return true;
      }

      case 'setLayout': {
        const [layoutKeyOrPath] = args;
        const featureDef = this.manifest?.features?.[this.currentFeatureId];
        const actualPath = featureDef?.layouts?.[layoutKeyOrPath] || layoutKeyOrPath;
        if (actualPath) {
          setTimeout(async () => {
            try {
              const layoutText = await this.readTextFile(actualPath);
              const parsed = JSON.parse(layoutText);
              this.currentLayout = parsed.layout ? parsed : { layout: parsed };
              this.notifyStateChange(`setLayout:${layoutKeyOrPath}`);
            } catch (err) {
              console.error(`[AppletSession] setLayout error:`, err);
            }
          }, 0);
        }
        return true;
      }

      // Storage bindings
      case 'storageGet':
        return this.storage.getString(false, args[0], ctx, args[1]);
      case 'storagePut':
        return this.storage.putString(false, args[0], ctx, args[1], args[2]);
      case 'storageDelete':
        this.storage.delete(false, args[0], ctx, args[1]);
        return true;
      case 'storageGetObject':
        return this.storage.getObject(false, args[0], ctx, args[1]);
      case 'storagePutObject':
        return this.storage.putObject(false, args[0], ctx, args[1], args[2]);

      case 'persistentStorageGet':
        return this.storage.getString(true, args[0], ctx, args[1]);
      case 'persistentStoragePut':
        return this.storage.putString(true, args[0], ctx, args[1], args[2]);
      case 'persistentStorageDelete':
        this.storage.delete(true, args[0], ctx, args[1]);
        return true;
      case 'persistentStorageGetObject':
        return this.storage.getObject(true, args[0], ctx, args[1]);
      case 'persistentStoragePutObject':
        return this.storage.putObject(true, args[0], ctx, args[1], args[2]);

      case 'consoleLog':
      case 'consoleError':
        return null;

      default:
        console.warn(`[AppletSession] Unhandled sync RPC method: ${method}`);
        return null;
    }
  }

  updateValueInternal(key, value) {
    this.values[key] = value;

    // Trigger value subscriptions asynchronously in worker
    const subs = this.valueSubscriptions.get(key);
    if (subs && subs.length > 0 && this.activeWorker) {
      for (const { callbackName } of subs) {
        this.invokeWorker(callbackName, [key, value]).catch(() => {});
      }
    }

    this.notifyStateChange(`value:${key}`);
  }

  async invokeWorker(name, args = []) {
    if (!this.activeWorker) return null;
    const id = `${Date.now()}_${Math.random().toString(36).substring(2, 7)}`;
    return new Promise((resolve, reject) => {
      this.pendingInvocations.set(id, { resolve, reject });
      this.activeWorker.postMessage({
        type: 'invoke',
        id,
        name,
        args
      });
    });
  }

  async sendAction(action, args = []) {
    if (!action) return this.getSnapshot('empty_action');

    if (action.startsWith('launch:')) {
      const targetFeature = action.substring('launch:'.length);
      await this.launchFeature(targetFeature);
      return this.getSnapshot(`action:${action}`);
    }

    try {
      await this.invokeWorker(action, args);
    } catch (err) {
      console.error(`[AppletSession] Action execution failed for '${action}':`, err);
    }

    return this.getSnapshot(`action:${action}`);
  }

  async setValue(id, value) {
    this.updateValueInternal(id, value);
    return this.getSnapshot(`setValue:${id}`);
  }

  respondToModal(id, confirmed) {
    if (this.activeModal && this.activeModal.id === id && this.activeWorker) {
      this.activeWorker.postMessage({
        type: 'modal_response',
        id,
        confirmed: Boolean(confirmed)
      });
      this.activeModal = null;
      this.notifyStateChange('modal_response');
      return true;
    }
    return false;
  }

  notifyStateChange(reason) {
    if (this.onStateChange) {
      this.onStateChange(this.getSnapshot(reason));
    }
  }

  getSnapshot(reason = 'state') {
    const activeStyle = this.stylesById[this.currentStyleId]?.sheet || null;
    return {
      sessionId: this.sessionId,
      reason,
      featureId: this.currentFeatureId || undefined,
      values: { ...this.values },
      layout: this.currentLayout,
      styleSheet: activeStyle,
      framework: this.currentFramework,
      modal: this.activeModal ? { ...this.activeModal } : null
    };
  }

  close() {
    if (this.activeWorker) {
      try {
        this.activeWorker.terminate();
      } catch (_) {}
      this.activeWorker = null;
    }
    this.activeModal = null;
    this.pendingInvocations.clear();
    this.valueSubscriptions.clear();
  }
}
