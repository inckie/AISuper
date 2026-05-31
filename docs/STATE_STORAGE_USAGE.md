# State Storage Usage Guide

A practical guide to using the state storage system in AISuper modules and features.

## Quick Start

### Basic Usage in a Module

```kotlin
class MyModule : FeatureModule {
    override suspend fun attach(context: FeatureModuleContext) {
        val storage = context.storage

        // Register a function that uses storage
        context.registerSuspendFunction("saveData") { args ->
            val data = args.getOrNull(0) ?: return@registerSuspendFunction JsonNull

            // Store at feature level (shared with other modules)
            storage.putObject(StorageScope.FEATURE, "myData", data)

            JsonPrimitive("saved")
        }

        context.registerSuspendFunction("loadData") { args ->
            // Load from feature level
            val data = storage.getObject(StorageScope.FEATURE, "myData")
            data ?: JsonNull
        }
    }

    override fun detach() {}
}
```

## Scope Selection Guide

### When to use APPLET scope?

Use when data should be:
- Shared across **all features** in your app
- Accessible **globally**
- **Persisted** across app restarts

**Examples**:
- User authentication token
- Global theme preference
- Language setting
- User ID
- App-level configuration

```kotlin
// Save user preference globally
storage.putString(StorageScope.APPLET, "theme", "dark")

// Other features can access it
val theme = storage.getString(StorageScope.APPLET, "theme")
```

### When to use FEATURE scope?

Use when data should be:
- Shared **only within this feature**
- Shared with all **modules in this feature**
- **Persisted** across app restarts
- NOT visible to other features

**Examples**:
- Last refresh time
- Cached list data
- Feature-specific settings
- User input in progress
- Feature state

```kotlin
// Save refresh timestamp
storage.putLong(StorageScope.FEATURE, "lastRefresh", System.currentTimeMillis())

// Retrieve for checking if cache is stale
val lastRefresh = storage.getLong(StorageScope.FEATURE, "lastRefresh") ?: 0L
val ageMs = System.currentTimeMillis() - lastRefresh

if (ageMs > 5 * 60 * 1000) { // 5 minutes
    // Cache is stale, fetch fresh data
}
```

### When to use MODULE scope?

Use when data should be:
- **Module-private only**
- Used for **temporary working state**
- NOT persisted (cleared on app exit)
- Fast access (in-memory)

**Examples**:
- Temporary parsing state
- Current page number in pagination
- Form field buffer
- Animation state
- Loading flags

```kotlin
// Store current page during pagination
storage.putInt(StorageScope.MODULE, "currentPage", 1)

// Check all items loaded
val totalPages = storage.getInt(StorageScope.MODULE, "totalPages") ?: 1
val currentPage = storage.getInt(StorageScope.MODULE, "currentPage") ?: 1

if (currentPage >= totalPages) {
    // No more pages to load
}
```

## Common Patterns

### Pattern 1: Caching with TTL (Time To Live)

```kotlin
context.registerSuspendFunction("fetchData") { args ->
    val key = "cachedData"
    val ttlKey = "cachedDataTime"
    val ttlMs = 10 * 60 * 1000 // 10 minutes

    // Check if cached and not expired
    val cached = storage.getObject(StorageScope.FEATURE, key)
    val cacheTime = storage.getLong(StorageScope.FEATURE, ttlKey) ?: 0L

    if (cached != null && System.currentTimeMillis() - cacheTime < ttlMs) {
        return@registerSuspendFunction cached
    }

    // Fetch fresh data
    val freshData = fetchFromApi()

    // Cache it
    storage.putObject(StorageScope.FEATURE, key, freshData)
    storage.putLong(StorageScope.FEATURE, ttlKey, System.currentTimeMillis())

    freshData
}
```

### Pattern 2: Cascading Preferences

```kotlin
suspend fun getPreference(key: String): String? {
    // Check module private setting first
    storage.getString(StorageScope.MODULE, key)?.let { return it }

    // Fall back to feature setting
    storage.getString(StorageScope.FEATURE, key)?.let { return it }

    // Fall back to app-wide setting
    return storage.getString(StorageScope.APPLET, key)
}
```

### Pattern 3: Safe Delete with Fallback

```kotlin
suspend fun clearUserData(scope: StorageScope) {
    try {
        storage.delete(scope, "userData")
    } catch (e: Exception) {
        println("Failed to delete: ${e.message}")
        // Try to clear entire scope
        storage.clearScope(scope)
    }
}
```

### Pattern 4: Enumeration and Batch Operations

```kotlin
suspend fun backupAllFeatureData(): JsonObject {
    val backup = mutableMapOf<String, JsonElement>()

    // Get all keys in feature scope
    val keys = storage.keys(StorageScope.FEATURE)
    keys.forEach { key ->
        val value = storage.getObject(StorageScope.FEATURE, key)
        if (value != null) {
            backup[key] = value
        }
    }

    return JsonObject(backup)
}

suspend fun restoreFromBackup(backup: JsonObject) {
    backup.forEach { (key, value) ->
        storage.putObject(StorageScope.FEATURE, key, value)
    }
}
```

### Pattern 5: Type-Safe Configuration

```kotlin
data class FeatureConfig(
    val apiEndpoint: String,
    val isDebugMode: Boolean,
    val cacheSize: Int,
    val timeout: Long
)

suspend fun loadConfig(): FeatureConfig {
    return FeatureConfig(
        apiEndpoint = storage.getString(StorageScope.FEATURE, "apiEndpoint") ?: "https://api.example.com",
        isDebugMode = storage.getBoolean(StorageScope.FEATURE, "debugMode") ?: false,
        cacheSize = storage.getInt(StorageScope.FEATURE, "cacheSize") ?: 100,
        timeout = storage.getLong(StorageScope.FEATURE, "timeout") ?: 30000L
    )
}

suspend fun saveConfig(config: FeatureConfig) {
    storage.putString(StorageScope.FEATURE, "apiEndpoint", config.apiEndpoint)
    storage.putBoolean(StorageScope.FEATURE, "debugMode", config.isDebugMode)
    storage.putInt(StorageScope.FEATURE, "cacheSize", config.cacheSize)
    storage.putLong(StorageScope.FEATURE, "timeout", config.timeout)
}
```

### Pattern 6: JSON Array Handling

```kotlin
suspend fun addToList(itemKey: String, item: JsonElement) {
    val listKey = "items"

    // Get existing list
    val existing = storage.getObject(StorageScope.FEATURE, listKey)
    val items = if (existing is JsonArray) {
        existing.toMutableList()
    } else {
        mutableListOf()
    }

    // Add new item
    items.add(item)

    // Save back
    storage.putObject(StorageScope.FEATURE, listKey, JsonArray(items))
}

suspend fun getList(): List<JsonElement> {
    val listKey = "items"
    val stored = storage.getObject(StorageScope.FEATURE, listKey)

    return if (stored is JsonArray) {
        stored.toList()
    } else {
        emptyList()
    }
}
```

### Pattern 7: State Machine with Storage

```kotlin
enum class LoadState {
    IDLE, LOADING, SUCCESS, ERROR
}

suspend fun startLoad(): JsonElement {
    try {
        // Set loading state
        storage.putString(StorageScope.MODULE, "state", "LOADING")

        // Fetch data
        val data = fetchData()

        // Save results
        storage.putObject(StorageScope.FEATURE, "data", data)
        storage.putString(StorageScope.MODULE, "state", "SUCCESS")

        return data
    } catch (e: Exception) {
        storage.putString(StorageScope.MODULE, "state", "ERROR")
        storage.putString(StorageScope.MODULE, "error", e.message ?: "Unknown error")
        return JsonNull
    }
}

suspend fun getState(): String {
    return storage.getString(StorageScope.MODULE, "state") ?: "IDLE"
}
```

## Error Handling

### All operations return null, never throw

```kotlin
// Safe - returns null if key doesn't exist
val value = storage.getString(StorageScope.FEATURE, "key")

// Always check for null
if (value != null) {
    // Use value
} else {
    // Key didn't exist
}
```

### Check before use

```kotlin
// Safe option 1: Elvis operator
val theme = storage.getString(StorageScope.APPLET, "theme") ?: "light"

// Safe option 2: Explicit null check
val fontSize = storage.getInt(StorageScope.FEATURE, "fontSize")
if (fontSize != null && fontSize > 0) {
    // Use fontSize
}

// Safe option 3: Extension function for defaults
suspend inline fun <T> StorageScope.getOrDefault(
    storage: StateStorage,
    key: String,
    getter: suspend (StorageScope, String) -> T?,
    default: T
): T {
    return getter(this, key) ?: default
}
```

## Performance Tips

### 1. Batch Operations
```kotlin
// ❌ Bad: Multiple writes
for (i in 0..99) {
    storage.putInt(StorageScope.MODULE, "item_$i", i)
}

// ✅ Good: Single batch operation
val data = JsonArray((0..99).map { JsonPrimitive(it) })
storage.putObject(StorageScope.MODULE, "items", data)
```

### 2. Use MODULE scope for hot data
```kotlin
// ❌ Slower: Reading from persistent storage multiple times
val count1 = storage.getInt(StorageScope.FEATURE, "count")
val count2 = storage.getInt(StorageScope.FEATURE, "count")

// ✅ Faster: Cache in memory first time
val count = storage.getInt(StorageScope.MODULE, "count")
    ?: storage.getInt(StorageScope.FEATURE, "count")
        ?.also { storage.putInt(StorageScope.MODULE, "count", it) }
```

### 3. Check existence before operations
```kotlin
// ✅ Good: Check before expensive operations
if (storage.contains(StorageScope.FEATURE, "largeData")) {
    val data = storage.getObject(StorageScope.FEATURE, "largeData")
    // Process data
}
```

## Debugging

### List all keys in a scope

```kotlin
suspend fun debugAllKeys() {
    println("APPLET keys: ${storage.keys(StorageScope.APPLET)}")
    println("FEATURE keys: ${storage.keys(StorageScope.FEATURE)}")
    println("MODULE keys: ${storage.keys(StorageScope.MODULE)}")
}
```

### Export storage state

```kotlin
suspend fun exportStorage(): JsonObject {
    val backup = mutableMapOf<String, JsonElement>()

    listOf(StorageScope.APPLET, StorageScope.FEATURE, StorageScope.MODULE)
        .forEach { scope ->
            val scopeData = mutableMapOf<String, JsonElement>()
            storage.keys(scope).forEach { key ->
                val value = storage.getObject(scope, key)
                if (value != null) {
                    scopeData[key] = value
                }
            }
            backup[scope.value] = JsonObject(scopeData)
        }

    return JsonObject(backup)
}
```

### Clear everything

```kotlin
suspend fun resetAllStorage() {
    listOf(StorageScope.APPLET, StorageScope.FEATURE, StorageScope.MODULE)
        .forEach { storage.clearScope(it) }
}
```

## Migration Guide

### From old getValue/setValue

**Old way** (Feature-level, values flow):
```kotlin
engine.registerFunction("getValue") { args ->
    val key = args.firstOrNull()?.jsonPrimitive?.contentOrNull ?: return@registerFunction JsonNull
    _values.value[key] ?: JsonPrimitive("")
}

engine.registerFunction("setValue") { args ->
    if (args.size >= 2) {
        val key = args[0].jsonPrimitive.contentOrNull ?: return@registerFunction JsonNull
        val value = args[1]
        updateValue(key, value)
    }
    JsonNull
}
```

**New way** (fully typed, scope-aware, persistent):
```kotlin
context.registerSuspendFunction("getValue") { args ->
    val key = args.firstOrNull()?.jsonPrimitive?.contentOrNull ?: return@registerSuspendFunction JsonNull
    // Reads from feature-level persistent storage
    storage.getObject(StorageScope.FEATURE, key) ?: JsonNull
}

context.registerSuspendFunction("setValue") { args ->
    if (args.size >= 2) {
        val key = args[0].jsonPrimitive.contentOrNull ?: return@registerSuspendFunction JsonNull
        val value = args[1]
        // Writes to feature-level persistent storage
        storage.putObject(StorageScope.FEATURE, key, value)
    }
    JsonNull
}
```

## Best Practices

1. ✅ Use type-specific methods (getString, getInt, etc.)
2. ✅ Check for null returns
3. ✅ Use appropriate scope for your use case
4. ✅ Cache hot data in MODULE scope
5. ✅ Persist user preferences in APPLET scope
6. ✅ Use FEATURE scope for feature-specific shared state
7. ❌ Don't use APPLET for temporary data
8. ❌ Don't treat MODULE as persistent
9. ❌ Don't ignore null returns
10. ❌ Don't write massive objects frequently

