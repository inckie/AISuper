package com.damn.aisuper.storage

/**
 * Provides access to both in-memory and persistent storage instances.
 *
 * Contexts can choose which storage backend to use per operation:
 * - Use memoryStorage for temporary, fast access data
 * - Use persistentStorage for data that survives app restarts
 *
 * All scopes (APPLET, FEATURE, MODULE) have access to both backends.
 * The choice of which to use is made by the application code, not automatically.
 */
class CompositeStateStorage(
    val memoryStorage: InMemoryStateStorage = InMemoryStateStorage(),
    val persistentStorage: StateStorage = PersistentStateStorage()
)
