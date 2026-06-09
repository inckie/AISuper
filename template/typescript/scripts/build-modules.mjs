import { promises as fs } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { build } from "esbuild";

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const modulesRoot = path.resolve(__dirname, "..");
const sourceModulesDir = path.join(modulesRoot, "modules");
const outputDir = path.join(modulesRoot, "..", "applet", "files");

async function readModuleConfigs() {
  const entries = await fs.readdir(sourceModulesDir, { withFileTypes: true });
  const result = [];

  for (const entry of entries) {
    if (!entry.isDirectory()) continue;

    const moduleDir = path.join(sourceModulesDir, entry.name);
    const configPath = path.join(moduleDir, "module.config.json");

    try {
      const raw = await fs.readFile(configPath, "utf-8");
      const config = JSON.parse(raw);
      result.push({
        id: String(config.id),
        moduleDir,
        entryFile: path.join(moduleDir, String(config.entry)),
        outputFile: path.join(outputDir, String(config.output))
      });
    } catch (error) {
      if (error && error.code === "ENOENT") continue;
      throw new Error(`Failed to load ${configPath}: ${error}`);
    }
  }

  return result;
}

async function main() {
  await fs.mkdir(outputDir, { recursive: true });
  const modules = await readModuleConfigs();
  for (const moduleDef of modules) {
    await build({
      entryPoints: [moduleDef.entryFile],
      outfile: moduleDef.outputFile,
      bundle: false,
      platform: "browser",
      target: ["es2019"],
      legalComments: "none",
      minify: false,
      treeShaking: false,
      charset: "ascii",
    });
    console.log(`Built ${moduleDef.id} -> ${moduleDef.outputFile}`);
  }
}

main().catch(console.error);
