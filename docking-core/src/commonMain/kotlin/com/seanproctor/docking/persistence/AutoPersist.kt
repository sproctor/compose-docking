package com.seanproctor.docking.persistence

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import com.seanproctor.docking.state.DockState
import com.seanproctor.docking.state.DockingEvent
import com.seanproctor.docking.state.LayoutChangeKind
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop

/**
 * Restores the layout from [storage] on first composition (keeping the current layout
 * when the storage is empty or corrupt), then saves it debounced on every change -
 * the port of ModernDocking's `AppState` auto-persist.
 *
 * @param paused suspends saving while true (restore still happens); mirror of
 *   `AppState.setPaused`.
 */
@OptIn(FlowPreview::class)
@Composable
public fun rememberAutoPersist(
    state: DockState,
    storage: LayoutStorage,
    debounce: Duration = 500.milliseconds,
    paused: Boolean = false,
    resolver: DockableResolver? = null,
    migrations: List<LayoutMigration> = emptyList(),
    mergeFloatingWindows: Boolean = false,
) {
    val currentPaused = rememberUpdatedState(paused)
    LaunchedEffect(state, storage) {
        val stored = runCatching { storage.load() }.getOrNull()
        if (stored != null) {
            runCatching { DockingPersistence.decode(stored, migrations) }
                .onSuccess { state.restoreLayout(it, resolver, mergeFloatingWindows) }
        }
        snapshotFlow { state.layout }
            .drop(1) // don't immediately re-save what we just restored
            .debounce(debounce)
            .collect {
                if (!currentPaused.value) {
                    runCatching {
                        storage.save(DockingPersistence.encode(state.captureLayout()))
                        state.emit(DockingEvent.LayoutChanged(LayoutChangeKind.Persisted))
                    }
                }
            }
    }
}
