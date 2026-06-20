---
categories:
- skills
created: '2026-06-20T04:51:14.261146+00:00'
id: native-modules-skill
modified: '2026-06-20T04:51:14.261168+00:00'
tags:
- skills
- native-modules
- kotlin
- factory-pattern
- bridge
title: Native Module Development Skill
type: leaf
---

# Native Module Development Skill

This guide outlines the process for adding new native capabilities (modules) to the AISuper runtime.

## 1. Architectural Overview

Native modules bridge the gap between the JavaScript execution environment and the underlying platform (JVM/Android). They are implemented as `FeatureModule`s and registered via `FeatureModuleFactory`.

### Key Components
- **`FeatureModule`**: The implementation of the module logic.
- **`FeatureModuleFactory`**: Responsible for creating module instances and declaring which JS functions it exposes.
- **`FeatureModuleContext`**: Provides access to storage, UI state updates, and JS function registration.
- **`NativeCommandFeatureModule`**: (Optional) Allows the module to handle direct commands from the layout XML (e.g., via `ModuleCommand`).

## 2. Implementation Steps

### Step 1: Create the Module Class
Implement the `FeatureModule` interface. Use `attach(context)` to register your functions.

```kotlin
class MyCustomModule : FeatureModule {
    override suspend fun attach(context: FeatureModuleContext) {
        context.registerFunction("myNativeFunction") { args ->
            // Parse arguments from args: List<JsonElement>
            // Perform native work
            JsonPrimitive("Result from native")
        }

        context.registerSuspendFunction("myAsyncNativeFunction") { args ->
            // Perform async work (e.g., network, delay)
            JsonPrimitive("Async result")
        }
    }

    override fun detach() {
        // Cleanup resources, cancel jobs
    }
}
```

### Step 2: Create and Register the Factory
Create an `object` that implements `FeatureModuleFactory`. This is where you declare the `type` string (used in `applet.json`) and the `exposedFunctions`.

```kotlin
object MyCustomModuleFactory : FeatureModuleFactory {
    override val type: String = "myCustomType"

    // IMPORTANT: Only functions listed here will be available to JS
    override val exposedFunctions: Set<String> = setOf(
        "myNativeFunction",
        "myAsyncNativeFunction"
    )

    override suspend fun create(definition: ModuleDefinition): FeatureModule {
        return MyCustomModule()
    }
}
```

Register the factory in `FeatureModuleFactories.kt` (or wherever your app initializes its module registry).

### Step 3: Update TypeScript Definitions
Add the new functions to `modules-ts/types/runtime-globals.d.ts` so the AI and the build system know about them.

```typescript
// My Custom Module
declare function myNativeFunction(arg1: string): string;
declare function myAsyncNativeFunction(arg1: number): Promise<string>;
```

### Step 4: Synchronize Templates
Run the synchronization script to propagate the new type definitions to the `template/` directory and other subprojects.

```powershell
cd modules-ts
npm run sync-template
```

## 3. Best Practices

- **Argument Parsing**: Use helper extensions like `jsonPrimitive.contentOrNull` to safely extract values from `JsonElement`.
- **State Updates**: Use `context.updateValue(id, value)` to push updates to the UI state.
- **Naming Conventions**: Prefix module-specific functions (e.g., `audioPlay`, `geoGetCurrent`) to avoid global namespace collisions.
- **Error Handling**: Catch exceptions within callbacks and return a meaningful `JsonElement` (or `JsonNull`) to the JS environment.
- **Documentation**: Update the [[ai-harness-skill|AI Harness Skill]] if the new module introduces significant new capabilities that an external AI model should be aware of.

## 4. Troubleshooting

- **"Function not found" in JS**: Ensure the function name is included in the factory's `exposedFunctions` set.
- **Type mismatch**: Remember that Keight converts JS types to `JsonElement`. Numbers from JS might come in as `JsonPrimitive` containing a string or a numeric type; use `doubleOrNull()` or `longOrNull()` carefully.
- **Blocking the UI**: Use `registerSuspendFunction` for any work that might take time, as these are executed in a coroutine-friendly way.