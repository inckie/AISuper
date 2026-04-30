# JS Module Template (TypeScript)

This directory is a dedicated TypeScript workspace for AISuper JS modules.

## Structure

- `modules/<module-name>/index.ts` - module source file.
- `modules/<module-name>/module.config.json` - module build metadata.
- `types/runtime-globals.d.ts` - host runtime globals (`registerExports`, `httpGet`).
- `scripts/build-modules.mjs` - builds every module to one JS file.

## module.config.json

```json
{
  "id": "moduleIdUsedInAppletJson",
  "entry": "index.ts",
  "output": "module_file_name.js"
}
```

- `id` is informational and used in build logs.
- `entry` is relative to the module directory.
- `output` is written to `composeApp/src/commonMain/composeResources/files/`.

## Runtime contract

Each module must call:

```ts
registerExports("myModule", ["fn1", "fn2"]);
```

The host will expose them to feature scripts as:

- `myModule_fn1(...)`
- `myModule_fn2(...)`

## Global Runtime Functions

### `xmlParse(xmlString: string): Record<string, unknown>`

Parses XML strings into JSON objects. Provided by the Kotlin `XmlJsonParser` through the JS engine.

**Output contract:**
- Root element becomes a top-level key: `{ "RootTag": { /* contents */ } }`
- Attributes are stored under `"@attributes"`
- Text content is stored under `"#text"`
- Repeated child tags become arrays

**Example:**
```typescript
const parsed = xmlParse('<root><item>value</item></root>');
// Result: { root: { item: { "#text": "value" } } }
```

### `httpGet(url: string): Promise<string>`

Fetches HTTP content. Must be called as an async function.

### `registerExports(moduleName: string, functionNames: string[]): void`

Registers module functions to be exposed to the host application.

## Commands

```bash
npm install
npm run typecheck
npm run build
```

Watch mode:

```bash
npm run watch
```

## Included examples

- `modules/template` - minimal starter module.
- `modules/miami_metromover` - TypeScript JS implementation of the MetroMover backend APIs.

## Documentation

- **[JS Module Development Guide](../docs/JS_MODULE_DEVELOPMENT.md)** - Complete guide for writing JS modules
- **[Testing JS Modules](../docs/TESTING_JS_MODULES.md)** - How to test modules with polyfills
- **[XML Parsing Architecture](../docs/ARCHITECTURE_XML_PARSING.md)** - How XML parsing works with Kotlin/JS integration

## Development Environment

This workspace includes polyfills for testing:

- **`polyfills.ts`** - `xmlParse`, `httpGet`, `registerExports` implementations
- **`test-setup.ts`** - Test utilities and mock data helpers

Use these for unit testing your modules in Node.js with full IDE debugging support.

## Module Development Quick Start

```typescript
// modules/my_module/index.ts
async function getData() {
  const xml = await httpGet("http://example.com/data.xml");
  const parsed = xmlParse(xml);  // Uses Kotlin parser via bridge
  return parseCustom(parsed);
}

function parseCustom(data: any) {
  // Extract values from parsed JSON
  return null;
}

registerExports("myModule", ["getData"]);
```

The `xmlParse()` function is provided by Kotlin's `XmlJsonParser` through the JS engine, ensuring:
- Debuggable (IDE breakpoints work in Kotlin)
- Consistent XML parsing across all modules
- Proper entity decoding and CDATA support
