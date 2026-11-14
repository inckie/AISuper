---
categories:
- architecture
- system-overview
created: '2026-11-14T03:00:05.561874+00:00'
id: state-management-and-persistence
modified: '2026-11-14T03:00:05.561890+00:00'
tags:
- architecture
- storage
- persistence
- state-management
- kotlin
- runtime
title: State Management and Persistence
type: leaf
---

# State Management and Persistence

AISuper provides a **type-safe, scope-isolated dual-storage system** for sharing and persisting data across applets, features, and modules.

## 1. Dual-Storage Model

Every execution context has access to two distinct storage backends (`StateStorage`):
* **Transient Storage (`context.storage` / `InMemoryStateStorage`)**: Fast, in-memory storage cleared when the applet/app exits. Ideal for temporary UI state, pagination, loading flags, and hot TTL caches.
* **Persistent Storage (`context.persistentStorage` / `PersistentStateStorage`)**: Durable storage backed by platform-specific `KStorage` implementations:
  * **Android**: `SharedPreferences`
  * **iOS**: `UserDefaults`
  * **Desktop / JVM**: File-based properties store
  * **Web (Compose)**: `LocalStorage`
  * **TinyJS**: File-backed JSON store (`aisuper_storage.json`)

Choosing between transient and persistent storage is done per operation or per context—not automatically by scope.

---

## 2. Scope Hierarchy and Access Control

Access is enforced by `ScopedStateStorage`, which validates permissions and namespaces keys to prevent collisions:

| Scope | Visibility | Key Namespace | Typical Use Cases |
|---|---|---|---|
| `StorageScope.APPLET` | All features and modules in the applet | `applet:{appletId}/{key}` | Auth tokens, global theme, user preferences |
| `StorageScope.FEATURE` | Active feature script + modules attached to that feature | `feature:{appletId}/{featureId}/{key}` | Feature settings, cached lists, last refresh timestamps |
| `StorageScope.MODULE` | Module-private, isolated to a single feature instance | `module:{appletId}/{featureId}/{moduleName}/{key}` | Parsing buffers, pagination state, loading state |
| `StorageScope.MODULE_GLOBAL` | Module-private, shared across all features for the same module type | `module.global:{appletId}/{moduleName}/{key}` | Cross-feature module caches or configuration |

### Access Enforcement Rules
* **Feature scripts** (`maxScope = FEATURE`): Can access `APPLET` and `FEATURE` scopes (exposed to JS via `"applet"` and `"feature"` scope strings).
* **Native modules** (`maxScope = MODULE_GLOBAL`, with `moduleName` set): Can access all four scopes. `MODULE` and `MODULE_GLOBAL` require `moduleName` in `StorageContext`.

---

## 3. Type-Safe, Null-Safe API

All storage operations are `suspend` functions safe to call from coroutines. Missing keys always return `null` (never throw exceptions):

* **Primitives**: `getString`/`putString`, `getInt`/`putInt`, `getLong`/`putLong`, `getDouble`/`putDouble`, `getBoolean`/`putBoolean`
* **Structured JSON**: `getObject`/`putObject` (`JsonElement`)
* **Management**: `contains(scope, key)`, `keys(scope)`, `delete(scope, key)`, `clearScope(scope)`

---

## 4. Common Usage Patterns & Best Practices

### Dual-Storage Caching (Transient + Persistent Fallback)
1. Check `context.storage` (transient) first for fast memory hits.
2. On miss, check `context.persistentStorage` (durable). If found, hydrate transient storage and return.
3. On full miss, fetch fresh data, then write to both transient (with TTL timestamp) and persistent storage.

### Cascading Preferences
Look up settings from most-specific to least-specific scope: `MODULE` → `FEATURE` → `APPLET`.

### Performance & Reliability Guidelines
* **Batch writes**: Store arrays/objects as a single `JsonArray`/`JsonObject` rather than writing keys in a loop.
* **Cache hot persistent data in memory**: Avoid repeated reads/writes to `PersistentStateStorage` in tight loops (writes to `KStorage` may block).
* **Version persistent keys**: Include schema version suffixes when storing serialized objects that may evolve.
* **Clear transient state on logout**: Explicitly clear session-scoped transient keys when authentication state resets.