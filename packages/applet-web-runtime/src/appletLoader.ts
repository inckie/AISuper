import type {
  AppletBundle,
  AppletManifest,
  LayoutRoot,
  StyleSheet,
} from './types';

export interface AppletSourceDescriptor {
  id: string;
  baseUrl: string;
  manifestUrl: string;
  installedAt?: number;
}

const bundleCache = new Map<string, Promise<AppletBundle>>();

export function invalidateAppletBundleCache(appId?: string): void {
  if (!appId) {
    bundleCache.clear();
    return;
  }
  for (const key of Array.from(bundleCache.keys())) {
    if (key === appId || key.startsWith(`${appId}@`)) {
      bundleCache.delete(key);
    }
  }
}

function joinUrl(baseUrl: string, relPath: string): string {
  if (/^https?:\/\//i.test(relPath) || relPath.startsWith('/')) {
    return relPath;
  }
  const base = baseUrl.replace(/\/+$/, '');
  const rel = relPath.replace(/^\/+/, '');
  return `${base}/${rel}`;
}

async function fetchTextWithFallback(baseUrl: string, relPath: string): Promise<string> {
  const primaryUrl = joinUrl(baseUrl, relPath);
  const res = await fetch(primaryUrl, { cache: 'no-store' });
  if (res.ok) {
    return res.text();
  }

  // Fallback between "files/<name>" and "<name>" for legacy applet packages
  const altPath = relPath.startsWith('files/')
    ? relPath.slice('files/'.length)
    : `files/${relPath}`;
  const altRes = await fetch(joinUrl(baseUrl, altPath), { cache: 'no-store' });
  if (altRes.ok) {
    return altRes.text();
  }

  throw new Error(`Failed to fetch applet resource '${relPath}' (HTTP ${res.status})`);
}

async function fetchJsonWithFallback<T>(baseUrl: string, relPath: string): Promise<T> {
  const text = await fetchTextWithFallback(baseUrl, relPath);
  return JSON.parse(text) as T;
}

async function loadBundleInternal(app: AppletSourceDescriptor): Promise<AppletBundle> {
  const manifestRes = await fetch(app.manifestUrl, { cache: 'no-store' });
  if (!manifestRes.ok) {
    throw new Error(`Failed to load applet manifest '${app.manifestUrl}' (HTTP ${manifestRes.status})`);
  }
  const manifest = (await manifestRes.json()) as AppletManifest;
  const baseUrl = app.baseUrl;

  const styles: Record<string, StyleSheet> = {};
  const layouts: Record<string, LayoutRoot> = {};
  const scripts: Record<string, string> = {};
  const modules: Record<string, string> = {};

  const tasks: Promise<void>[] = [];

  // 1. Styles (load light & dark plus any declared defaultStyle)
  if (manifest.styles) {
    for (const [styleKey, styleDef] of Object.entries(manifest.styles)) {
      if (!styleDef?.file) continue;
      // Load light, dark, and defaultStyle eagerly; others can be fetched if needed
      if (styleKey === 'light' || styleKey === 'dark' || styleKey === manifest.defaultStyle) {
        tasks.push(
          fetchJsonWithFallback<StyleSheet>(baseUrl, styleDef.file)
            .then((sheet) => {
              styles[styleKey] = sheet;
            })
            .catch(() => {
              // Optional style sheet missing
            })
        );
      }
    }
  }

  // 2. Feature layouts, scripts, and jsModules
  const fetchedFiles = new Map<string, Promise<string>>();
  const fetchOnce = (filePath: string): Promise<string> => {
    let p = fetchedFiles.get(filePath);
    if (!p) {
      p = fetchTextWithFallback(baseUrl, filePath);
      fetchedFiles.set(filePath, p);
    }
    return p;
  };

  for (const [featureKey, featureDef] of Object.entries(manifest.features || {})) {
    if (featureDef.layout) {
      const path = featureDef.layout;
      tasks.push(
        fetchOnce(path).then((raw) => {
          const parsed = JSON.parse(raw) as LayoutRoot;
          layouts[path] = parsed;
          layouts[featureKey] = parsed;
        })
      );
    }
    if (featureDef.layouts) {
      const layoutEntries = Object.entries(featureDef.layouts);
      for (const [layoutAlias, path] of layoutEntries) {
        tasks.push(
          fetchOnce(path).then((raw) => {
            const parsed = JSON.parse(raw) as LayoutRoot;
            layouts[path] = parsed;
            layouts[layoutAlias] = parsed;
            if (layoutAlias === 'main' || layoutAlias === 'list' || layoutAlias === 'dashboard') {
              layouts[featureKey] = parsed;
            }
          })
        );
      }
    }
    if (featureDef.script) {
      const path = featureDef.script;
      tasks.push(
        fetchOnce(path).then((code) => {
          scripts[path] = code;
          scripts[featureKey] = code;
        })
      );
    }
    if (Array.isArray(featureDef.modules)) {
      for (const mod of featureDef.modules) {
        if (mod.type === 'jsModule' && mod.script) {
          const path = mod.script;
          tasks.push(
            fetchOnce(path).then((code) => {
              modules[path] = code;
              if (mod.name) {
                modules[mod.name] = code;
              }
            })
          );
        }
      }
    }
  }

  await Promise.all(tasks);

  return {
    manifest,
    styles,
    layouts,
    scripts,
    modules,
  };
}

export function fetchAppletBundle(
  app: AppletSourceDescriptor,
  forceReload = false
): Promise<AppletBundle> {
  const cacheKey = `${app.id}@${app.installedAt || 0}`;
  if (forceReload) {
    bundleCache.delete(cacheKey);
  }
  let cached = bundleCache.get(cacheKey);
  if (!cached) {
    cached = loadBundleInternal(app).catch((err) => {
      bundleCache.delete(cacheKey);
      throw err;
    });
    bundleCache.set(cacheKey, cached);
  }
  return cached;
}
