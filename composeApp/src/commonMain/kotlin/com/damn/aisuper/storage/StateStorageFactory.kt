package com.damn.aisuper.storage

/**
 * Factory for creating StateStorage instances.
 *
 * Provides different storage configurations:
 * - Separate persistent + transient storages (recommended)
 * - Scoped wrappers for access control
 */
object StateStorageFactory {

    /**
     * Create a composite storage providing access to BOTH backends.
     * Applications choose which backend to use per operation:
     * - Use memoryStorage for temporary, fast data
     * - Use persistentStorage for data that survives app restarts
     *
     * This is the recommended configuration.
     * All scopes have access to both backends.
     */
    fun createComposite(): CompositeStateStorage {
        return CompositeStateStorage(
            memoryStorage = InMemoryStateStorage(),
            persistentStorage = PersistentStateStorage()
        )
    }


    /**
     * Create a scoped storage that enforces access control and namespace isolation.
     * @param baseStorage The underlying storage to wrap
     * @param context Identifies the applet/feature/module for key namespacing
     * @param maxAccessibleScope The maximum scope accessible from this context
     */
    fun createScoped(
        baseStorage: StateStorage,
        context: StorageContext,
        maxAccessibleScope: StorageScope
    ): StateStorage {
        return ScopedStateStorage(baseStorage, context, maxAccessibleScope)
    }
}


