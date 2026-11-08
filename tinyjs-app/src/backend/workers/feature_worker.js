/**
 * @wk-id tinyjs/feature-worker
 * @wk-title Sandboxed Feature Worker Isolate (feature_worker.js)
 * @wk-tags tinyjs, worker, feature, sandbox, isolate, confirm, modal
 * @wk-categories tinyjs-runtime
 *
 * # Feature Worker Isolate
 *
 * Runs user feature business logic in an isolated Web Worker QuickJS thread.
 *
 * ## Global Environment Setup (`setupGlobals`)
 * - **State Value Bindings**: Synchronous `getValue(key)`, `setValue(key, val)`, and async `subscribeValue`.
 * - **Cross-Module Proxies**: Auto-generated `${moduleName}_${fnName}` functions calling attached modules via `SyncRpcClient`.
 * - **Modal Confirmation Dialogs**: `confirm(options, title)`, `showConfirmation`, `confirmation`.
 * - **Feature Layouts**: `setLayout(layoutKey)`, `launchFeature(featureId)`.
 * - **Networking & Storage**: `httpGet`, `httpPost`, `httpRequestRaw`, `storageGet/Put`, `persistentStorageGet/Put`.
 */
import { SyncRpcClient } from '../sync_rpc.js';

let syncClient = null;
let appletContext = { appletId: '', featureId: '' };
const pendingModals = new Map();

function parseConfirmArgs(options, title, confirmText, cancelText, isDestructive) {
  if (typeof options === 'object' && options !== null) {
    return {
      title: options.title || 'Подтверждение',
      message: options.message || '',
      confirmText: options.confirmText || 'Подтвердить',
      cancelText: options.cancelText || 'Отмена',
      isDestructive: Boolean(options.isDestructive)
    };
  }
  return {
    title: title || 'Подтверждение',
    message: String(options || ''),
    confirmText: confirmText || 'Подтвердить',
    cancelText: cancelText || 'Отмена',
    isDestructive: Boolean(isDestructive)
  };
}

globalThis.onmessage = async (e) => {
  const msg = e.data;
  if (!msg) return;

  if (msg.type === 'init') {
    syncClient = new SyncRpcClient(msg.sab);
    appletContext = { appletId: msg.appletId, featureId: msg.featureId };
    setupGlobals(msg.moduleProxies);
    globalThis.postMessage({ type: 'initialized' });
    return;
  }

  if (msg.type === 'loadScript') {
    try {
      // Indirect eval to declare functions into globalThis
      const cleanScript = msg.script.replace(/^\s*["']use strict["'];?/g, '');
      (0, eval)(cleanScript);
      globalThis.postMessage({ type: 'scriptLoaded', success: true });
    } catch (err) {
      console.error(`[FeatureWorker ${appletContext.featureId}] Script eval error:`, err);
      globalThis.postMessage({ type: 'scriptLoaded', success: false, error: err.message });
    }
    return;
  }

  if (msg.type === 'invoke') {
    const { id, name, args } = msg;
    try {
      const fn = globalThis[name];
      if (typeof fn !== 'function') {
        globalThis.postMessage({ type: 'invoke_result', id, result: null });
        return;
      }
      const result = await fn.apply(null, args || []);
      globalThis.postMessage({ type: 'invoke_result', id, result: result !== undefined ? result : null });
    } catch (err) {
      console.error(`[FeatureWorker ${appletContext.featureId}] Error calling ${name}:`, err);
      globalThis.postMessage({ type: 'invoke_error', id, error: err.message });
    }
    return;
  }

  if (msg.type === 'modal_response') {
    const resolver = pendingModals.get(msg.id);
    if (resolver) {
      pendingModals.delete(msg.id);
      resolver(Boolean(msg.confirmed));
    }
    return;
  }
};

function setupGlobals(moduleProxies = []) {
  // 0. Module Proxies (${moduleName}_${functionName})
  if (Array.isArray(moduleProxies)) {
    for (const mod of moduleProxies) {
      const modName = mod.name;
      if (Array.isArray(mod.exports)) {
        for (const fnName of mod.exports) {
          const proxyName = `${modName}_${fnName}`;
          globalThis[proxyName] = function (...args) {
            return syncClient.call('callModule', [modName, fnName, args]);
          };
        }
      }
    }
  }

  // 1. State Value bindings (SYNC)
  globalThis.getValue = function (key) {
    return syncClient.call('getValue', [key]);
  };

  globalThis.setValue = function (key, value) {
    return syncClient.call('setValue', [key, value]);
  };

  globalThis.subscribeValue = function (key, callbackName) {
    return syncClient.call('subscribeValue', [key, callbackName]);
  };

  globalThis.unsubscribeValue = function (subId) {
    return syncClient.call('unsubscribeValue', [subId]);
  };

  // 2. UI / Themes & Frameworks (SYNC & ASYNC)
  globalThis.getAvailableThemes = function () {
    return syncClient.call('getAvailableThemes', []);
  };

  globalThis.getCurrentTheme = function () {
    return syncClient.call('getCurrentTheme', []);
  };

  globalThis.setCurrentTheme = function (themeId) {
    return syncClient.call('setCurrentTheme', [themeId]);
  };

  globalThis.getAvailableFrameworks = function () {
    return syncClient.call('getAvailableFrameworks', []);
  };

  globalThis.getCurrentFramework = function () {
    return syncClient.call('getCurrentFramework', []);
  };

  globalThis.setCurrentFramework = function (frameworkId) {
    return syncClient.call('setCurrentFramework', [frameworkId]);
  };

  globalThis.setLayout = function (layoutKeyOrPath) {
    return syncClient.call('setLayout', [layoutKeyOrPath]);
  };

  // 3. Navigation & Applet metadata
  globalThis.getFeatures = function () {
    return syncClient.call('getFeatures', []);
  };

  globalThis.launchFeature = function (featureId) {
    return syncClient.call('launchFeature', [featureId]);
  };

  // 4. Modal Dialogs / Confirmation (ASYNC)
  globalThis.confirm = function (options, title, confirmText, cancelText, isDestructive) {
    const modalOpts = parseConfirmArgs(options, title, confirmText, cancelText, isDestructive);
    const id = `modal_${Date.now()}_${Math.random().toString(36).substring(2, 6)}`;
    return new Promise((resolve) => {
      pendingModals.set(id, resolve);
      globalThis.postMessage({
        type: 'modal_request',
        id,
        options: modalOpts
      });
    });
  };

  globalThis.showConfirmation = globalThis.confirm;
  globalThis.confirmation = globalThis.confirm;

  // 5. Storage bindings (transient and persistent)
  globalThis.storageGet = function (scope, key) {
    return syncClient.call('storageGet', [scope, key]);
  };
  globalThis.storagePut = function (scope, key, value) {
    return syncClient.call('storagePut', [scope, key, value]);
  };
  globalThis.storageDelete = function (scope, key) {
    return syncClient.call('storageDelete', [scope, key]);
  };
  globalThis.storageGetObject = function (scope, key) {
    return syncClient.call('storageGetObject', [scope, key]);
  };
  globalThis.storagePutObject = function (scope, key, value) {
    return syncClient.call('storagePutObject', [scope, key, value]);
  };

  globalThis.persistentStorageGet = function (scope, key) {
    return syncClient.call('persistentStorageGet', [scope, key]);
  };
  globalThis.persistentStoragePut = function (scope, key, value) {
    return syncClient.call('persistentStoragePut', [scope, key, value]);
  };
  globalThis.persistentStorageDelete = function (scope, key) {
    return syncClient.call('persistentStorageDelete', [scope, key]);
  };
  globalThis.persistentStorageGetObject = function (scope, key) {
    return syncClient.call('persistentStorageGetObject', [scope, key]);
  };
  globalThis.persistentStoragePutObject = function (scope, key, value) {
    return syncClient.call('persistentStoragePutObject', [scope, key, value]);
  };

  // 5b. Centralized Cookie-Aware HTTP Client (Host Sync RPC)
  globalThis.httpGet = function (url, headers = {}) {
    return syncClient.call('httpGet', [url, headers]);
  };

  globalThis.httpPost = function (url, body = '', headers = {}) {
    return syncClient.call('httpPost', [url, body, headers]);
  };

  globalThis.httpRequestRaw = function (method, url, body = '', headers = {}) {
    return syncClient.call('httpRequestRaw', [method, url, body, headers]);
  };

  // 6. Utilities & Logging
  globalThis.consoleLog = function (...args) {
    console.log(...args);
    syncClient.call('consoleLog', args);
  };

  globalThis.consoleError = function (...args) {
    console.error(...args);
    syncClient.call('consoleError', args);
  };

  globalThis.jsonParse = function (str) {
    try {
      return JSON.parse(str);
    } catch (_) {
      return null;
    }
  };

  globalThis.xmlParse = function (str) {
    return syncClient.call('xmlParse', [str]);
  };

  // Safe console shim
  const origConsole = globalThis.console || {};
  globalThis.console = {
    log: (...args) => origConsole.log ? origConsole.log(...args) : globalThis.consoleLog(...args),
    error: (...args) => origConsole.error ? origConsole.error(...args) : globalThis.consoleError(...args),
    warn: (...args) => origConsole.warn ? origConsole.warn(...args) : globalThis.consoleLog('[WARN]', ...args),
    info: (...args) => origConsole.info ? origConsole.info(...args) : globalThis.consoleLog('[INFO]', ...args),
    debug: (...args) => origConsole.debug ? origConsole.debug(...args) : globalThis.consoleLog('[DEBUG]', ...args),
  };
}
