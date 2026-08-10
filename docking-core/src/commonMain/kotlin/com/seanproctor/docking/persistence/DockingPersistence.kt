package com.seanproctor.docking.persistence

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Migrates a persisted layout's raw JSON from [fromVersion] to `fromVersion + 1`.
 * Registered migrations are applied in sequence until the current version is reached.
 */
public fun interface LayoutMigration {
    public fun migrate(fromVersion: Int, json: JsonObject): JsonObject
}

/** JSON (de)serialization of persisted layouts. */
public object DockingPersistence {

    public val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
    }

    public fun encode(layout: PersistedApplicationLayout): String =
        json.encodeToString(PersistedApplicationLayout.serializer(), layout)

    /**
     * Decodes a persisted layout, running [migrations] for any versions older than
     * [PersistedApplicationLayout.CURRENT_LAYOUT_VERSION]. Throws on malformed input -
     * callers decide whether to fall back to a default layout.
     */
    public fun decode(
        text: String,
        migrations: List<LayoutMigration> = emptyList(),
    ): PersistedApplicationLayout {
        var obj = json.parseToJsonElement(text).jsonObject
        var version = obj["version"]?.jsonPrimitive?.int ?: 1
        while (version < PersistedApplicationLayout.CURRENT_LAYOUT_VERSION) {
            val migrated = migrations.fold(obj) { acc, migration -> migration.migrate(version, acc) }
            check(migrated != obj || migrations.isNotEmpty()) {
                "Layout version $version is older than current " +
                    "${PersistedApplicationLayout.CURRENT_LAYOUT_VERSION} and no migration was provided"
            }
            obj = JsonObject(migrated + ("version" to JsonPrimitive(version + 1)))
            version++
        }
        return json.decodeFromJsonElement(PersistedApplicationLayout.serializer(), obj)
    }
}
