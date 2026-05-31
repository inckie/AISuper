# Using Dual Storage in Modules

Since all scopes have access to both transient (in-memory) and persistent storage, modules should understand when to use each.

## Quick Decision Guide

Use **transient (storage)** for:
- Temporary state
- Caches (use TTL logic)
- Current page in pagination
- Loading flags
- Animation states
- Temporary form input

Use **persistent (persistentStorage)** for:
- User preferences
- Last updated timestamps
- Saved data across sessions
- Important state
- User authentication tokens
- Feature-specific settings

## Example: Feature Module with Dual Storage

```kotlin
class MyFeatureModule : FeatureModule {
    override suspend fun attach(context: FeatureModuleContext) {
        val storage = context.storage
        val persistent = context.persistentStorage

        // Register a function that uses both storages
        context.registerSuspendFunction("loadCachedData") { args ->
            val key = args.firstOrNull()?.jsonPrimitive?.contentOrNull ?: return@registerSuspendFunction JsonNull

            // Check transient cache first (fast)
            val cached = storage.getObject(StorageScope.FEATURE, "cache_$key")
            if (cached != null) {
                println("Cache hit!")
                return@registerSuspendFunction cached
            }

            // Check persistent storage (survives restarts)
            val lastSaved = persistent.getObject(StorageScope.FEATURE, "saved_$key")
            if (lastSaved != null) {
                // Restore to transient cache for fast future access
                storage.putObject(StorageScope.FEATURE, "cache_$key", lastSaved)
                return@registerSuspendFunction lastSaved
            }

            // Load fresh data
            val freshData = fetchData(key)

            // Cache in both storages
            storage.putObject(StorageScope.FEATURE, "cache_$key", freshData)
            persistent.putObject(StorageScope.FEATURE, "saved_$key", freshData)

            freshData
        }
    }

    override fun detach() {}
}
```

## Example: Preference System

```kotlin
class PreferencesModule : FeatureModule {
    override suspend fun attach(context: FeatureModuleContext) {
        val persistent = context.persistentStorage

        // Load user preferences from persistent storage
        context.registerSuspendFunction("getPreference") { args ->
            val key = args.firstOrNull()?.jsonPrimitive?.contentOrNull ?: return@registerSuspendFunction JsonNull

            // Preferences should persist
            persistent.getString(StorageScope.APPLET, "pref_$key")?.let { JsonPrimitive(it) }
                ?: JsonNull
        }

        context.registerSuspendFunction("setPreference") { args ->
            if (args.size < 2) return@registerSuspendFunction JsonNull

            val key = args[0].jsonPrimitive.contentOrNull ?: return@registerSuspendFunction JsonNull
            val value = args[1].jsonPrimitive.contentOrNull ?: return@registerSuspendFunction JsonNull

            // Save to persistent storage
            persistent.putString(StorageScope.APPLET, "pref_$key", value)
            JsonNull
        }
    }

    override fun detach() {}
}
```

## Scope Selection Examples

### Working at APPLET Scope
```kotlin
// Transient: Global runtime cache
storage.putObject(StorageScope.APPLET, "globalCache", data)

// Persistent: User's preferred theme (persists across sessions)
persistentStorage.putString(StorageScope.APPLET, "theme", "dark")
```

### Working at FEATURE Scope
```kotlin
// Transient: Current page in pagination (working state)
storage.putInt(StorageScope.FEATURE, "currentPage", 2)

// Persistent: Last time we fetched data (for staleness check)
persistentStorage.putLong(StorageScope.FEATURE, "lastFetch", System.currentTimeMillis())
```

### Working at MODULE Scope
```kotlin
// Transient: Module's temporary working data
storage.putObject(StorageScope.MODULE, "tempParsing", intermediateData)

// Persistent: Important state that module needs to maintain
persistentStorage.putObject(StorageScope.MODULE, "moduleState", state)
```

## Best Practices

1. **Default to transient for temporary data** - It's faster and cleaner
2. **Use persistent for user data** - Anything the user expects to survive app restart
3. **Use persistent for sync points** - Timestamps, last known good state, etc.
4. **Scope determines visibility, not storage** - Choose storage backend separately
5. **Check both storages in sequence** - Try transient first (faster), then persistent
6. **Clear transient on logout** - Keep temporary data cleaned up
7. **Version your persistent data** - Add schema versions to persistent storage keys

## Common Patterns

### Pattern: Transient Cache with Persistent Backup
```kotlin
// Try transient first
val data = storage.getObject(StorageScope.FEATURE, "data")
    ?: persistentStorage.getObject(StorageScope.FEATURE, "data")
    ?: fetchFreshData()

// Keep both in sync
storage.putObject(StorageScope.FEATURE, "data", data)
persistentStorage.putObject(StorageScope.FEATURE, "data", data)
```

### Pattern: Feature State with Transient + Persistent
```kotlin
// Transient: UI state (doesn't need to persist)
storage.putInt(StorageScope.FEATURE, "selectedIndex", 5)

// Persistent: User's choice (should survive restart)
persistentStorage.putString(StorageScope.FEATURE, "sortOrder", "ascending")
```

### Pattern: Fast Timeout-Based Cache
```kotlin
val cacheTimeKey = "cache_time"
val cacheDataKey = "cache_data"
val ttlMs = 10 * 60 * 1000 // 10 minutes

// Check if cache is still valid
val cacheTime = storage.getLong(StorageScope.FEATURE, cacheTimeKey) ?: 0L
if (System.currentTimeMillis() - cacheTime < ttlMs) {
    val cached = storage.getObject(StorageScope.FEATURE, cacheDataKey)
    if (cached != null) return cached
}

// Cache expired or doesn't exist - fetch fresh
val fresh = fetchData()
storage.putLong(StorageScope.FEATURE, cacheTimeKey, System.currentTimeMillis())
storage.putObject(StorageScope.FEATURE, cacheDataKey, fresh)
return fresh
```

