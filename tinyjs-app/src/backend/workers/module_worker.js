// module_worker.js - Sandboxed isolate worker for JS Modules (applet-scoped)
let appletContext = { appletId: '', moduleName: '' };
const pendingHostRpcs = new Map();

function sendHostRpc(method, args) {
  const id = `rpc_${Date.now()}_${Math.random().toString(36).substring(2, 7)}`;
  return new Promise((resolve, reject) => {
    pendingHostRpcs.set(id, { resolve, reject });
    globalThis.postMessage({
      type: 'host_rpc',
      id,
      method,
      args
    });
  });
}

globalThis.onmessage = async (e) => {
  const msg = e.data;
  if (!msg) return;

  if (msg.type === 'init') {
    appletContext = { appletId: msg.appletId, moduleName: msg.moduleName };
    setupGlobals();
    globalThis.postMessage({ type: 'initialized' });
    return;
  }

  if (msg.type === 'loadScript') {
    try {
      const cleanScript = msg.script.replace(/^\s*["']use strict["'];?/g, '');
      (0, eval)(cleanScript);
      globalThis.postMessage({ type: 'scriptLoaded', success: true });
    } catch (err) {
      console.error(`[ModuleWorker ${appletContext.moduleName}] Script eval error:`, err);
      globalThis.postMessage({ type: 'scriptLoaded', success: false, error: err.message });
    }
    return;
  }

  if (msg.type === 'invoke') {
    const { id, name, args } = msg;
    try {
      const fn = globalThis[name];
      if (typeof fn !== 'function') {
        globalThis.postMessage({
          type: 'invoke_error',
          id,
          error: `Function '${name}' not defined in module '${appletContext.moduleName}'`
        });
        return;
      }
      const result = await fn.apply(null, args || []);
      globalThis.postMessage({
        type: 'invoke_result',
        id,
        result: result !== undefined ? result : null
      });
    } catch (err) {
      console.error(`[ModuleWorker ${appletContext.moduleName}] Error executing '${name}':`, err);
      globalThis.postMessage({
        type: 'invoke_error',
        id,
        error: err instanceof Error ? err.message : String(err)
      });
    }
    return;
  }

  if (msg.type === 'host_rpc_result') {
    const pending = pendingHostRpcs.get(msg.id);
    if (pending) {
      pendingHostRpcs.delete(msg.id);
      pending.resolve(msg.result);
    }
    return;
  }

  if (msg.type === 'host_rpc_error') {
    const pending = pendingHostRpcs.get(msg.id);
    if (pending) {
      pendingHostRpcs.delete(msg.id);
      pending.reject(new Error(msg.error));
    }
    return;
  }
};

function setupGlobals() {
  // 1. Module Export Declaration
  globalThis.registerExports = function (moduleName, exportsArray) {
    const mod = moduleName || appletContext.moduleName;
    const fns = Array.isArray(exportsArray) ? exportsArray : [];
    globalThis.postMessage({
      type: 'exports_registered',
      moduleName: mod,
      exports: fns
    });
  };

  // 2. Storage APIs (scoped, async)
  globalThis.storageGet = function (scope, key) {
    return sendHostRpc('storageGet', [scope, key]);
  };
  globalThis.storagePut = function (scope, key, value) {
    return sendHostRpc('storagePut', [scope, key, value]);
  };
  globalThis.storageDelete = function (scope, key) {
    return sendHostRpc('storageDelete', [scope, key]);
  };
  globalThis.storageGetObject = function (scope, key) {
    return sendHostRpc('storageGetObject', [scope, key]);
  };
  globalThis.storagePutObject = function (scope, key, value) {
    return sendHostRpc('storagePutObject', [scope, key, value]);
  };

  globalThis.persistentStorageGet = function (scope, key) {
    return sendHostRpc('persistentStorageGet', [scope, key]);
  };
  globalThis.persistentStoragePut = function (scope, key, value) {
    return sendHostRpc('persistentStoragePut', [scope, key, value]);
  };
  globalThis.persistentStorageDelete = function (scope, key) {
    return sendHostRpc('persistentStorageDelete', [scope, key]);
  };
  globalThis.persistentStorageGetObject = function (scope, key) {
    return sendHostRpc('persistentStorageGetObject', [scope, key]);
  };
  globalThis.persistentStoragePutObject = function (scope, key, value) {
    return sendHostRpc('persistentStoragePutObject', [scope, key, value]);
  };

  // 3. Centralized Cookie-Aware HTTP Client (Host RPC)
  globalThis.httpGet = function (url, headers = {}) {
    return sendHostRpc('httpGet', [url, headers]);
  };

  globalThis.httpPost = function (url, body = '', headers = {}) {
    return sendHostRpc('httpPost', [url, body, headers]);
  };

  globalThis.httpRequestRaw = function (method, url, body = '', headers = {}) {
    return sendHostRpc('httpRequestRaw', [method, url, body, headers]);
  };

  // 4. Utilities & Logging
  globalThis.jsonParse = function (str) {
    try {
      return JSON.parse(str);
    } catch (_) {
      return null;
    }
  };

  globalThis.xmlParse = function (str) {
    return sendHostRpc('xmlParse', [str]);
  };

  globalThis.consoleLog = function (...args) {
    console.log(...args);
    sendHostRpc('consoleLog', args).catch(() => {});
  };

  globalThis.consoleError = function (...args) {
    console.error(...args);
    sendHostRpc('consoleError', args).catch(() => {});
  };
}
