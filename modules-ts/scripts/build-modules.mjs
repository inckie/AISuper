import { promises as fs } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { build, context } from "esbuild";

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const modulesRoot = path.resolve(__dirname, "..");
const repoRoot = path.resolve(modulesRoot, "..");
const sourceModulesDir = path.join(modulesRoot, "modules");
const outputDir = path.join(repoRoot, "composeApp", "src", "commonMain", "composeResources", "files");
const watchMode = process.argv.includes("--watch");

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
      if (!config.id || !config.entry || !config.output) {
        throw new Error("module.config.json must include id, entry and output");
      }

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

async function buildOne(definition) {
  const options = {
    entryPoints: [definition.entryFile],
    outfile: definition.outputFile,
    bundle: false,
    platform: "browser",
    target: ["es2019"],
    legalComments: "none",
    minify: false,
    treeShaking: false,
    charset: "ascii",
    sourcemap: false,
    banner: {
      js: `// Generated from modules-ts (${definition.id}). Do not edit manually.`
    }
  };

  if (watchMode) {
    const ctx = await context(options);
    await ctx.watch();
    console.log(`[modules-ts] watching ${definition.id} -> ${definition.outputFile}`);
    return;
  }

  await build(options);
  console.log(`[modules-ts] built ${definition.id} -> ${definition.outputFile}`);
}

async function main() {
  await fs.mkdir(outputDir, { recursive: true });

  const modules = await readModuleConfigs();
  if (modules.length === 0) {
    throw new Error(`No modules found in ${sourceModulesDir}`);
  }

  for (const moduleDef of modules) {
    await buildOne(moduleDef);
  }

  if (watchMode) {
    console.log("[modules-ts] watch mode active");
  }
}

main().catch((error) => {
  console.error("[modules-ts] build failed:", error);
  process.exit(1);
});


