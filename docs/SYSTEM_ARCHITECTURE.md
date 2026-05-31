# AISuper Architecture Overview

This document provides a comprehensive overview of the AISuper system architecture, including all major components and their interactions.

## System Architecture

```
┌────────────────────────────────────────────────────────────────┐
│                         AISuper App                             │
│  (Kotlin Multiplatform - Android, iOS, Desktop, Web)            │
└────────────────────────────┬─────────────────────────────────────┘
                             │
         ┌───────────────────┼───────────────────┐
         ▼                   ▼                   ▼
    ┌─────────┐          ┌────────┐        ┌──────────┐
    │ Compose │          │ Layout │        │  State   │
    │   UI    │          │ System │        │ Storage  │
    │Framework│          │(JSON)  │        │          │
    └─────────┘          └────────┘        └──────────┘
         │                   │                   │
         └───────────────────┼───────────────────┘
                             │
              ┌──────────────┴──────────────┐
              ▼                             ▼
        ┌───────────────┐          ┌──────────────────┐
        │  AppJSEngine  │          │  FeatureModule   │
        │ (Keight/JS)   │          │     Host         │
        └───────────────┘          └──────────────────┘
              │                             │
              └──────────────┬──────────────┘
                             │
         ┌───────────────────┼───────────────────┐
         ▼                   ▼                   ▼
    ┌─────────┐        ┌──────────┐      ┌──────────┐
    │  Applet │        │ Feature  │      │ Modules  │
    │         │        │          │      │          │
    │- Config │        |- State   │      │- Kotlin  │
    │- Layout │        |- Layout  │      │- JS/TS   │
    │- Script │        |- Script  │      │          │
    └─────────┘        └──────────┘      └──────────┘
```

## Core Components

### 1. Applet (Root Container)
**Files**: `composeApp/src/commonMain/kotlin/.../runtime/Applet.kt`

The top-level container that manages:
- Applet manifest loading
- Feature lifecycle
- Global JavaScript functions
- Applet-level state storage

**Responsibilities**:
- Load applet configuration from `applet.json`
- Create and manage features
- Provide global JS functions (xmlParse, jsonParse, encodeURIComponent, etc.)
- Manage applet-level storage (StorageScope.APPLET)

### 2. Feature
**Files**: `composeApp/src/commonMain/kotlin/.../runtime/Feature.kt`

Represents a feature within an applet:
- Loads layout and script
- Manages module host
- Maintains feature-level values
- Handles actions and module commands

**Responsibilities**:
- Parse and render layout
- Load and execute feature scripts
- Attach modules and provide contexts
- Manage feature-level storage (StorageScope.FEATURE)
- Handle user interactions

### 3. Layout System
**Files**: `composeApp/src/commonMain/kotlin/.../layout/`

Type-safe layout representation and rendering:
- JSON-based layout definitions
- Multi-frontend support (Material3, Rikka, Glance)
- Style sheets and theming
- Interactive widgets

**Supported Frontends**:
- **Material3**: Primary Compose-based UI framework
- **Rikka UI**: Alternative Material Design library
- **Glance**: Android App Widgets

### 4. AppJSEngine
**Files**: `composeApp/src/commonMain/kotlin/.../engine/`

JavaScript/TypeScript execution environment:
- Keight engine wrapper (QuickJS on Android/iOS/JVM, native JS on web)
- Function registration
- Type conversion between Kotlin and JS

**Key Classes**:
- `AppJSEngine`: Main interface
- `KeightJSEngine`: QuickJS-based implementation
- `JsonBridgeConversion`: Type conversion utilities

### 5. Feature Modules
**Files**: `composeApp/src/commonMain/kotlin/.../modules/`

Native Kotlin modules that enhance features:
- Implement `FeatureModule` interface
- Register functions in JS context
- Access feature state and storage
- Examples: HTTP client, file I/O, platform-specific APIs

**Key Interfaces**:
- `FeatureModule`: Base module interface
- `FeatureModuleContext`: Provides functions, storage, and state management
- `FeatureModuleHost`: Manages module lifecycle

### 6. State Storage System
**Files**: `composeApp/src/commonMain/kotlin/.../storage/`

Hierarchical state management with persistence:
- **StorageScope**: APPLET, FEATURE, MODULE, MODULE_GLOBAL
- **Type-Based API**: getString, getInt, getObject, etc.
- **Dual Storage**: Transient (In-Memory) + Persistent (KStorage-backed)
- **Access Control**: Scope-based visibility enforcement

**Key Classes**:
- `StateStorage`: Main interface
- `InMemoryStateStorage`: Fast, temporary storage
- `PersistentStateStorage`: Platform-backed (KStorage)
- `CompositeStateStorage`: Hybrid approach
- `ScopedStateStorage`: Access control wrapper

## Data Flow

### Feature Initialization

```
Applet.launchFeature(featureId)
  │
  ├─ Create Feature instance
  ├─ Create FeatureModuleHost
  ├─ Register global JS functions
  │   ├─ xmlParse, jsonParse
  │   ├─ encodeURIComponent
  │   └─ launchFeature (navigation)
  │
  ├─ Load layout JSON
  ├─ Load feature script
  │
  ├─ Create FeatureModuleContext
  │   ├─ Feature script context: transient + persistent (APPLET/FEATURE only)
  │   └─ Per-module context: transient + persistent (APPLET/FEATURE/MODULE/MODULE_GLOBAL)
  │
  ├─ Attach all modules via FeatureModuleHost
  │   └─ Modules register their own functions
  │
  └─ Call feature script initialize()
```

### User Action Handling

```
UI Widget (onAction)
  │
  └─ applet.handleAction(actionName, args)
       │
       └─ feature.handleAction(actionName, args)
            │
            └─ engine.callFunction(actionName, args)
                 │
                 └─ Executes JS function with parameters
                      │
                      └─ May call registered Kotlin functions
                           └─ Modules/Features may update storage
```

### XML Parsing Flow

```
JS Module calls: xmlParse(xmlString)
  │
  └─ KeightJSEngine routes to registered function
       │
       └─ XmlJsonParser.parse(xmlString)
            │
            └─ Returns JsonElement
                 │
                 ├─ Decodes entities
                 ├─ Extracts attributes (@attributes)
                 ├─ Handles CDATA sections
                 ├─ Processes arrays (repeated elements)
                 │
                 └─ Returns to JS as JSON object
```

## Storage Architecture

### Scope Hierarchy and Access Rules

```
APPLET Scope (Global)
  └─ Visible to: All features and modules in the applet
  └─ Persistence: Available in both transient and persistent backends

  FEATURE Scope (Feature-Level)
    └─ Visible to: The active feature script + modules attached to that feature
    └─ Persistence: Available in both transient and persistent backends

    MODULE Scope (Module-Private, feature-isolated)
      └─ Visible to: Only that module instance within that feature
      └─ Persistence: Available in both transient and persistent backends

    MODULE_GLOBAL Scope (Module-Private, applet-wide)
      └─ Visible to: Same module type across features in the same applet
      └─ Persistence: Available in both transient and persistent backends

Access enforcement summary (via `ScopedStateStorage`):
- Feature script context (`maxScope = FEATURE`) can access `APPLET`, `FEATURE`
- Module context (`maxScope = MODULE_GLOBAL`, `moduleName != null`) can access all scopes
- `MODULE` and `MODULE_GLOBAL` require `moduleName` in `StorageContext`
- JS storage bridge currently exposes only `"applet"` and `"feature"` scope strings
```

### Storage Implementation Strategy

| Backend | Scope Coverage | Persistence | Typical Use Case |
|---------|----------------|-------------|------------------|
| InMemoryStateStorage (transient) | APPLET, FEATURE, MODULE, MODULE_GLOBAL | ❌ No | Session-only state, hot caches |
| PersistentStateStorage (KStorage-backed) | APPLET, FEATURE, MODULE, MODULE_GLOBAL | ✅ Yes | Preferences, durable caches, module-level persisted state |

Scope namespacing format in backend keys:
- `APPLET` -> `applet:{appletId}/{key}`
- `FEATURE` -> `feature:{appletId}/{featureId}/{key}`
- `MODULE` -> `module:{appletId}/{featureId}/{moduleName}/{key}`
- `MODULE_GLOBAL` -> `module.global:{appletId}/{moduleName}/{key}`

## Module System

### Native Kotlin Modules

Modules can:
- Register synchronous and asynchronous functions in JS
- Access transient and persistent storage via context
- Read/write to all scopes permitted by module context
- Handle native commands from JS

Example structure:
```kotlin
class MyModule : FeatureModule {
    override suspend fun attach(context: FeatureModuleContext) {
        context.registerSuspendFunction("getData") { args ->
            val key = args[0].jsonPrimitive.content
            val data = context.storage.getObject(StorageScope.FEATURE, key)
            data ?: JsonNull
        }
    }

    override fun detach() {
        // Cleanup
    }
}
```

### JavaScript Modules

TypeScript modules in `modules-ts/modules/`:
- Compiled to JavaScript
- Loaded via resources
- Have access to:
  - `xmlParse()`: Kotlin XML parser
  - `httpGet()`: HTTP requests
  - `registerExports()`: Module registration
  - Module-specific functions

## Configuration Files

### applet.json (Manifest)
```json
{
  "entryFeature": "dashboard",
  "features": {
    "dashboard": {
      "name": "Dashboard",
      "layout": "files/features/dashboard.json",
      "script": "files/features/dashboard.js",
      "modules": [...]
    }
  }
}
```

### Feature Layout
- JSON-based format
- Declarative widget trees
- Style references
- Event binding

## Platform-Specific Considerations

### Android
- Uses QuickJS for JS engine
- Persistent storage backend: SharedPreferences (wrapped by KStorage krates)
- Compose for UI rendering
- App Widgets support via Glance

### iOS
- Uses QuickJS for JS engine
- Persistent storage backend: UserDefaults (wrapped by KStorage krates)
- SwiftUI for UI rendering

### Desktop/JVM
- Uses QuickJS for JS engine
- Persistent storage backend: file-based properties store (wrapped by KStorage krates)
- Compose for UI rendering

### Web/Browser
- Native JavaScript engine
- Persistent storage backend: LocalStorage (wrapped by KStorage krates)
- Browser's native canvas/DOM for rendering

## Key Design Patterns

### 1. Scope-Based Isolation
Modules only see what they should - enforced at the storage wrapper level.

### 2. Type-Safe Storage
No guessing: getString returns String?, getInt returns Int?, etc.

### 3. Null-Safe API
All storage operations return null for missing keys - no exceptions.

### 4. Lazy Initialization
Features and modules load only when needed.

### 5. Resource Management
Proper cleanup via detach() and close() calls.

## Security Model

1. **Scope Enforcement**: ScopedStateStorage prevents unauthorized access
2. **Script Isolation**: JS runs in controlled engine with registered functions only
3. **Function Registration**: Only explicitly registered functions are callable
4. **Type Validation**: Automatic conversion prevents type confusion
5. **No Direct File Access**: All I/O through modules

## Performance Optimization

### Caching Strategies
- In-memory module cache for hot data
- Feature-level persistent cache for frequently accessed data
- Lazy loading of modules and features

### Storage Access
- Minimal reads through caching
- Batched writes where possible
- Type conversion optimized via type-specific methods

## Testing

### Unit Testing
- Test features in isolation
- Mock JS engine
- Mock storage implementations

### Integration Testing
- Full applet loading
- Feature + module interaction
- Storage persistence verification

### Runtime Testing
- WebView/app widget verification
- Performance profiling
- Memory leak detection

## Future Enhancements

- [ ] Storage encryption for sensitive data
- [ ] Cross-feature state sharing protocols
- [ ] Module hot-reload
- [ ] State snapshots and undo/redo
- [ ] Storage inspection tools
- [ ] Performance metrics collection
- [ ] A/B testing framework
- [ ] Remote feature loading

