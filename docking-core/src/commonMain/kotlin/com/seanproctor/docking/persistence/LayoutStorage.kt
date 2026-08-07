package com.seanproctor.docking.persistence

/**
 * Where auto-persisted layouts are stored. A file on desktop
 * ([com.seanproctor.docking.persistence.FileLayoutStorage]); apps provide localStorage /
 * DataStore / preferences implementations on other platforms.
 */
public interface LayoutStorage {
    /** Returns the stored layout text, or null when none exists. */
    public suspend fun load(): String?

    public suspend fun save(text: String)
}

/** In-memory storage, mainly for tests and previews. */
public class InMemoryLayoutStorage(initial: String? = null) : LayoutStorage {
    public var text: String? = initial
        private set

    override suspend fun load(): String? = text

    override suspend fun save(text: String) {
        this.text = text
    }
}
