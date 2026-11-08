/**
 * @wk-id tinyjs/applet-resolver
 * @wk-title Applet Manifest Resolver & ZIP Handler (applet_resolver.js)
 * @wk-tags tinyjs, applet-resolver, manifest, zip, extraction
 * @wk-categories tinyjs-runtime
 *
 * # Applet Manifest Resolver
 *
 * Automatically resolves and loads AISuper applet manifests from multiple input formats:
 * 1. Direct path to `applet.json`.
 * 2. Directory containing `applet.json` or `files/applet.json`.
 * 3. Compressed `.zip` archives (extracts to a temporary directory via [[src:internals/tinyjs/zip-extractor]]).
 */
import { extractZip } from './zip_extractor.js';

export async function resolveAppletManifest(inputPath) {
  if (!inputPath || typeof inputPath !== 'string') {
    throw new Error('Invalid path provided to resolveAppletManifest');
  }

  let cleanPath = inputPath.trim().replace(/^"|"$/g, '').replace(/\\/g, '/');

  // 1. Check if input is a .zip archive
  if (cleanPath.toLowerCase().endsWith('.zip')) {
    console.log(`[Resolver] Detected ZIP archive: ${cleanPath}`);
    const template = `${tjs.tmpDir.replace(/\\/g, '/')}/aisuper-applet-XXXXXX`;
    const tempDir = (await tjs.makeTempDir(template)).replace(/\\/g, '/');
    console.log(`[Resolver] Extracting ZIP to temporary directory: ${tempDir}`);
    await extractZip(cleanPath, tempDir);
    return await findManifestInDirectory(tempDir);
  }

  // 2. Check if input is directly an applet.json file
  if (cleanPath.toLowerCase().endsWith('.json')) {
    try {
      const stat = await tjs.stat(cleanPath);
      if (stat && !stat.isDirectory) {
        return cleanPath;
      }
    } catch (_) {}
  }

  // 3. Assume input is a folder
  return await findManifestInDirectory(cleanPath);
}

async function findManifestInDirectory(dir) {
  const normalizedDir = dir.replace(/\/+$/, '');

  // Candidate 1: dir/applet.json
  const candidate1 = `${normalizedDir}/applet.json`;
  try {
    const s1 = await tjs.stat(candidate1);
    if (s1 && !s1.isDirectory) return candidate1;
  } catch (_) {}

  // Candidate 2: dir/files/applet.json
  const candidate2 = `${normalizedDir}/files/applet.json`;
  try {
    const s2 = await tjs.stat(candidate2);
    if (s2 && !s2.isDirectory) return candidate2;
  } catch (_) {}

  // Candidate 3: single top-level subdirectory inside dir (common in zip extractions)
  try {
    const entries = [];
    const iter = await tjs.readDir(normalizedDir);
    for await (const e of iter) {
      if (e.isDirectory) entries.push(e.name);
    }
    for (const sub of entries) {
      const subCandidate = `${normalizedDir}/${sub}/applet.json`;
      try {
        const ss = await tjs.stat(subCandidate);
        if (ss && !ss.isDirectory) return subCandidate;
      } catch (_) {}
    }
  } catch (_) {}

  throw new Error(`Could not find 'applet.json' inside '${dir}'. Ensure the folder contains an applet manifest.`);
}
