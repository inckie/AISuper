<!-- State Management and Persistence Architecture -->

# State Management and Persistence

## Overview

AISuper provides a comprehensive state management system for sharing data between modules, features, and the applet. The system supports both in-memory and persistent storage with **scope-based access control** and a **type-safe API**.

- **In-memory**: Fast, cleared on app restart
- **Persistent**: Survives app restarts, backed by platform-specific storage
- **Composite**: Smart mixing - applet/feature data persists, module data stays in memory
- **Type-safe**: `getString()`, `getInt()`, `getObject()`, etc. - no guessing about data types
- **Scope-aware**: Modules see only their scope, features see their scope + module scope, applet sees all

## Architecture

```
┌─────────────────────────────────────────────────────────┐
│                   Applet (Global State)                  │
│                                                          │
│  ┌──────────────────────────────────────────────────┐  │
│  │  Applet Storage (StorageScope.APPLET)            │  │
│  │  - Persistent (KStorage)                          │  │
│  │  - Visible to all features and modules            │  │
│  │  - Examples: user preferences, app settings       │  │
│  └──────────────────────────────────────────────────┘  │
│                                          ▲              │
└──────────────────────────────────────────┼──────────────┘
                                           │
                    ┌──────────────────────┴──────────────────┐
                    ▼                                         ▼
      ┌──────────────────────────┐            ┌──────────────────────────┐
      │  Feature 1 (Dashboard)   │            │  Feature 2 (Settings)    │
      │                          │            │                          │
      │  ┌────────────────────┐  │            │  ┌────────────────────┐  │
      │  │Feature Storage     │  │            │  │Feature Storage     │  │
      │  │(StorageScope.     │  │            │  │(StorageScope.     │  │
      │  │ FEATURE)          │  │            │  │ FEATURE)          │  │
      │  │- Persistent       │  │            │  │- Persistent       │  │
      │  │- Visible to       │  │            │  │- Visible to       │  │
      │  │  feature + all    │  │            │  │  feature + all    │  │
      │  │  modules in it    │  │            │  │  modules in it    │  │
      │  └────────────────────┘  │            │  └────────────────────┘  │
      │          ▲                 │            │          ▲               │
      └──────────┼─────────────────┘            └──────────┼───────────────┘
                 │                                         │
         ┌───────┴───────┬───────────────┐        ┌───────┴──────┐
         ▼               ▼               ▼        ▼              ▼
    ┌────────┐    ┌────────┐     ┌────────┐ ┌────────┐    ┌────────┐
    │Module 1│    │Module 2│     │Module 3│ │Module 4│    │Module 5│
    │        │    │        │     │        │ │        │    │        │
    │Module  │    │Module  │     │Module  │ │Module  │    │Module  │
    │Storage │    │Storage │     │Storage │ │Storage │    │Storage │
    │(In-mem)│    │(In-mem)│     │(In-mem)│ │(In-mem)│    │(In-mem)│
    └────────┘    └────────┘     └────────┘ └────────┘    └────────┘
```

## Storage Scopes

### StorageScope.APPLET
- **Visibility**: Visible to all features and modules
- **Both Storage Types Available**: In-memory AND persistent
- **Use Cases**:
  - Persistent: User authentication, global preferences, app theme
  - Transient: Global runtime state, temporary cache data
- **Example**:
  ```kotlin
  // Choose which storage to use
  storage.putString(StorageScope.APPLET, "theme", "dark")  // Default: transient
  persistentStorage.putString(StorageScope.APPLET, "lastUser", username)  // Persistent
  ```

### StorageScope.FEATURE
- **Visibility**: Visible to all modules within the feature
- **Both Storage Types Available**: In-memory AND persistent
- **Use Cases**:
  - Persistent: Feature cache, user input in progress, feature-specific settings
  - Transient: Temporary working data, loading states, pagination state
- **Example**:
  ```kotlin
  // Transient: fast, temporary
  storage.putInt(StorageScope.FEATURE, "currentPage", 1)

  // Persistent: survives app restart
  persistentStorage.putLong(StorageScope.FEATURE, "lastRefresh", System.currentTimeMillis())
  ```

### StorageScope.MODULE
- **Visibility**: Only the module itself can access
- **Both Storage Types Available**: In-memory AND persistent
- **Use Cases**:
  - Persistent: Important module state
  - Transient: Module working state, temporary buffers
- **Example**:
  ```kotlin
  // Transient: working data (cleared on app exit)
  storage.putObject(StorageScope.MODULE, "workingCache", data)

  // Persistent: if module needs to save important state
  persistentStorage.putObject(StorageScope.MODULE, "savedState", data)
  ```

## Type-Based API

The storage API provides methods for different data types with **dual storage backends**:

### Transient (In-Memory) Storage
Available via `context.storage`:
```
String:  getString() -> String?  |  putString(value: String)
Int:     getInt() -> Int?        |  putInt(value: Int)
Long:    getLong() -> Long?      |  putLong(value: Long)
Double:  getDouble() -> Double?  |  putDouble(value: Double)
Boolean: getBoolean() -> Boolean?|  putBoolean(value: Boolean)
Object:  getObject() -> Json?    |  putObject(value: Json)
```

### Persistent Storage
Available via `context.persistentStorage`:
```
String:  getString() -> String?  |  putString(value: String)
Int:     getInt() -> Int?        |  putInt(value: Int)
Long:    getLong() -> Long?      |  putLong(value: Long)
Double:  getDouble() -> Double?  |  putDouble(value: Double)
Boolean: getBoolean() -> Boolean?|  putBoolean(value: Boolean)
Object:  getObject() -> Json?    |  putObject(value: Json)
```

All scopes (APPLET, FEATURE, MODULE) have access to both storage types. The choice is made per operation.

## Storage Architecture

The storage system provides **type-safe, scope-based access** to both **persistent and in-memory storage**. Each scope can use either backend - the choice is made per operation or per context, not automatically.

```
┌─────────────────────────────────────────────────────────┐
│                   Storage Backends                       │
│                                                          │
│  ┌──────────────────────┐    ┌──────────────────────┐  │
│  │ PersistentStorage    │    │ InMemoryStorage      │  │
│  │ (Platform-specific)  │    │ (RAM, fast)          │  │
│  │                      │    │                      │  │
│  │ ├─ SharedPrefs (A)   │    │ ├─ Temporary data    │  │
│  │ ├─ UserDefaults (i)  │    │ ├─ Caches            │  │
│  │ └─ Files (Desktop)   │    │ └─ Working state     │  │
│  └──────────────────────┘    └──────────────────────┘  │
│           │                            │                 │
│           └────────────┬───────────────┘                 │
│                        │                                 │
│        ┌───────────────▼────────────────┐               │
│        │   ScopedStateStorage           │               │
│        │ (APPLET/FEATURE/MODULE aware) │               │
│        └───────────────┬────────────────┘               │
│                        │                                 │
└────────────────────────┼─────────────────────────────────┘
                         │
         ┌───────────────┼───────────────┐
         ▼               ▼               ▼
    ┌────────┐    ┌────────┐     ┌────────┐
    │Applet  │    │Feature │     │Module  │
    │        │    │        │     │        │
    │Can use │    │Can use │     │Can use │
    │both    │    │both    │     │both    │
    │storages│    │storages│     │storages│
    └────────┘    └────────┘     └────────┘
```

### Storage Backend Choices

Instead of automatically routing based on scope, **each context chooses which backend(s) to use**:

- **Modules**: Typically use in-memory for quick access, but can use persistent for important data
- **Features**: Can use both - persistent for shared state, in-memory for temporary working data
- **Applet**: Can use both - persistent for global settings, in-memory for runtime state

## Usage Examples

### In a Feature Module (Kotlin)

```kotlin
class MyFeatureModule : FeatureModule {
    override suspend fun attach(context: FeatureModuleContext) {
        val storage = context.storage

        // Read applet-level setting
        val userPreference = storage.getString(StorageScope.APPLET, "language") ?: "en"

        // Store feature-specific data
        storage.putString(StorageScope.FEATURE, "lastFeatureState", "initialized")

        // Use module-local cache
        val cache = storage.getObject(StorageScope.MODULE, "cache") ?: JsonObject(emptyMap())

        // Register a function that uses storage
        context.registerSuspendFunction("getUserData") { args ->
            val userId = args.firstOrNull()?.jsonPrimitive?.contentOrNull ?: return@registerSuspendFunction JsonNull

            // Check feature-level cache first
            val cached = storage.getString(StorageScope.FEATURE, "user_$userId")
            if (cached != null) {
                return@registerSuspendFunction Json.parseToJsonElement(cached)
            }

            // Fetch and cache
            val userData = fetchUser(userId) // your async function
            storage.putString(StorageScope.FEATURE, "user_$userId", Json.encodeToString(userData))

            Json.encodeToString(userData)
        }
    }

    override fun detach() {}
}
```

### In JavaScript/TypeScript Features

Currently, JavaScript access to storage is through module registration. In the future, we can expose storage functions directly to JS:

```typescript
// Future JS API (not yet exposed)
// await storage.getString("feature", "lastRefresh")
// await storage.putInt("applet", "theme_mode", 1)
// await storage.getObject("feature", "cachedData")
```

### Accessing Applet-Level State from a Feature

```kotlin
// Feature receives the storage scoped to FEATURE level
// It can access both FEATURE and MODULE scopes directly
// To access APPLET scope, we need to use the full base storage

// In the applet
val appletStorage = ScopedStateStorage(baseStorage, StorageScope.APPLET)

// Share specific applet-level data with features via FEATURE scope
val sharedSettings = storage.getString(StorageScope.APPLET, "globalSettings")
storage.putString(StorageScope.FEATURE, "globalSettingsSnapshot", sharedSettings)
```

## Storage Persistence Behavior

### What Gets Persisted?

| Scope | Stored In | Persists? | When |
|-------|-----------|-----------|------|
| APPLET | KStorage | ✅ Yes | Immediately |
| FEATURE | KStorage | ✅ Yes | Immediately |
| MODULE | In-Memory | ❌ No | Lost on app exit |

### Example Persistence Scenario

1. User launches app
2. Feature reads `StorageScope.APPLET` values (from persistent storage)
3. Feature caches data in `StorageScope.FEATURE` (persisted)
4. Module creates temporary working data in `StorageScope.MODULE` (not persisted)
5. User closes app
6. Next launch:
   - APPLET values: ✅ Available (persisted)
   - FEATURE values: ✅ Available (persisted)
   - MODULE values: ❌ Gone (was in-memory)

## Thread Safety

All storage operations are **suspend functions** and designed to be called from coroutine contexts:

```kotlin
// Safe to call from coroutines
launch {
    val value = storage.getString(StorageScope.FEATURE, "key")
}

// Use within a module's suspend functions
context.registerSuspendFunction("getData") { args ->
    val data = storage.getString(StorageScope.FEATURE, "data")
    // ...
}
```

## Performance Considerations

1. **Type Conversions**: String-based storage with automatic type conversion is slightly slower than native storage, but negligible for typical loads
2. **Persistence**: Writes to KStorage are blocking - avoid high-frequency writes to APPLET/FEATURE scopes
3. **Scope Overhead**: ScopedStateStorage adds minimal overhead (just scope validation)
4. **Composite Strategy**: MODULE scope is in-memory for fast access; consider it for hot data

## Migration from Old getValue/setValue

The legacy `getValue`/`setValue` functions in Feature are retained for backward compatibility but now operate on feature-level values:

```kotlin
// Old API (still works)
engine.registerFunction("getValue") { args ->
    val key = args.firstOrNull()?.jsonPrimitive?.contentOrNull ?: return@registerFunction JsonNull
    _values.value[key] ?: JsonPrimitive("")
}

// New API (recommended for modules)
storage.getString(StorageScope.FEATURE, key)
```

## Security Guarantees

1. **Scope Isolation**: Module code cannot accidentally access applet-level data
2. **Access Control**: Enforced at wrapper level before reaching storage
3. **Key Namespacing**: Each scope is internally namespaced to prevent collisions
4. **Null Safety**: No exceptions on missing keys - predictable error handling

## Future Enhancements

- [ ] Encryption for sensitive applet-level data
- [ ] Storage quota enforcement per scope
- [ ] Data migration tools between scopes
- [ ] JavaScript/TypeScript API exposure
- [ ] Storage inspection/debugging tools
- [ ] Reactive storage (Flow/StateFlow based)
- [ ] Backup and restore functionality

