import { promises as fs } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const modulesTsRoot = path.resolve(__dirname, "..");
const repoRoot = path.resolve(modulesTsRoot, "..");
const templateRoot = path.join(repoRoot, "template");

const filesToSync = [
  {
    src: path.join(modulesTsRoot, "types", "runtime-globals.d.ts"),
    dest: path.join(templateRoot, "typescript", "types", "runtime-globals.d.ts")
  },
  {
    src: path.join(modulesTsRoot, "polyfills.ts"),
    dest: path.join(templateRoot, "typescript", "polyfills.ts")
  },
  {
    src: path.join(modulesTsRoot, "test-setup.ts"),
    dest: path.join(templateRoot, "typescript", "test-setup.ts")
  },
  {
    src: path.join(modulesTsRoot, "tsconfig.json"),
    dest: path.join(templateRoot, "typescript", "tsconfig.json")
  },
  {
    src: path.join(repoRoot, "client-react", "src", "types.ts"),
    dest: path.join(templateRoot, "applet", "types", "layout-types.ts")
  }
];

async function sync() {
  console.log("[sync-template] Synchronizing files to template directory...");
  for (const entry of filesToSync) {
    try {
      await fs.mkdir(path.dirname(entry.dest), { recursive: true });
      await fs.copyFile(entry.src, entry.dest);
      console.log(`  Synced: ${path.basename(entry.src)} -> ${path.relative(repoRoot, entry.dest)}`);
    } catch (error) {
      console.error(`  Failed to sync ${entry.src}: ${error.message}`);
    }
  }
}

sync().catch(console.error);
