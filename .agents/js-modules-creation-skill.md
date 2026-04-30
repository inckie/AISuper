# JS Modules Creation Skill (AISuper)

This note captures the repeatable workflow for adding or debugging JS modules in AISuper.

## 1) Module contract

- A module script must call:
  - `registerExports("<moduleId>", ["fn1", "fn2", ...])`
- `JsModuleRuntime` creates feature-callable proxies as:
  - `<moduleId>_<exportName>`
- Example:
  - export `list_stations` from module `miamiMetromover`
  - feature calls `miamiMetromover_list_stations()`

## 2) Files to touch

- JS module declaration:
  - `composeApp/src/commonMain/composeResources/files/applet.json`
  - `jsModules.<moduleId>.script`
- Feature script:
  - `composeApp/src/commonMain/composeResources/files/<feature>_script.js`
- Module script asset:
  - `composeApp/src/commonMain/composeResources/files/<module>.js`

## 3) Host bridges and feature modules

- If module needs HTTP, feature must include native module:
  - `{ "type": "http", "name": "httpMain" }`
- `JsModuleRuntime` bridges `httpGet` when `http` module is present.
- Missing bridge symptoms:
  - module loads, but calls fail/return empty due to fetch path not executing correctly.

## 4) Keight compatibility rules (important)

Use conservative JS for Keight runtime:

- Prefer `var` + function declarations
- Avoid `Map`, `Set`, advanced regex-heavy parsing, complex transpiled helpers
- Avoid reliance on `String.indexOf(substr, fromIndex)` in parser loops
  - use helper: `indexOfFrom(text, needle, fromIndex)` via `substring(fromIndex).indexOf(...)`
- Prefer plain object caches over ES collections
- Keep parsing string-based and explicit

## 5) API parity rule

Keep runtime API names stable between TS/source and bundled runtime asset.

- If feature expects `get_trains`, do not export `list_trains` only.
- Keep export list in sync with feature calls.

## 6) Current known-good runtime module setup

- Active module script in `applet.json`:
  - `files/miami_metromover_keight_module.js`
- Legacy incompatible asset removed:
  - `files/miami_metromover_module.js`

## 7) Test strategy

- Parser/unit checks (Node):
  - `modules-ts/tests/*.test.mjs`
- Keight behavior (JVM common tests):
  - `composeApp/src/commonTest/kotlin/com/damn/aisuper/engine/KeightJSModuleIntegrationTest.kt`

## 8) Useful commands (Windows PowerShell)

```powershell
$env:JAVA_HOME="c:\Users\ink\.jdks\jbr-21.0.8\"
Set-Location "D:\Work\Mobile\Android\AISuper"
.\gradlew.bat :composeApp:jvmTest --tests "com.damn.aisuper.engine.KeightJSModuleIntegrationTest"
```

```powershell
Set-Location "D:\Work\Mobile\Android\AISuper\modules-ts\tests"
node --test *.test.mjs
```

```powershell
Set-Location "D:\Work\Mobile\Android\AISuper"
Get-Content ".\composeApp\build\test-results\jvmTest\TEST-com.damn.aisuper.engine.KeightJSModuleIntegrationTest.xml"
```

## 9) If runtime still shows empty arrays

- Confirm `applet.json` points to Keight-safe module asset
- Check module export/proxy names match exactly
- Verify `http` module is present in feature modules
- Add logs around:
  - module call entry
  - `httpGet` URL
  - raw XML length
  - parsed block count
- Reinstall app after resource changes (`clean` + install)

