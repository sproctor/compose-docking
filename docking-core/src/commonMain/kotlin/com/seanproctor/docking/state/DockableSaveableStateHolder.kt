package com.seanproctor.docking.state

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.LocalSaveableStateRegistry
import androidx.compose.runtime.saveable.SaveableStateRegistry
import com.seanproctor.docking.model.DockableId

/**
 * Bridges `rememberSaveable` state across compositions. Each dockable's content runs
 * under its own [SaveableStateRegistry]; when the content leaves one composition (its
 * dockable moved to another window, or was closed), the registry's values are captured
 * here and restored wherever the dockable appears next - including a different OS
 * window's composition, which `movableContentOf` cannot cross.
 */
internal class DockableSaveableStateHolder {

    private val saved = mutableMapOf<DockableId, Map<String, List<Any?>>>()

    @Composable
    fun SaveableStateProvider(id: DockableId, content: @Composable () -> Unit) {
        val parent = LocalSaveableStateRegistry.current
        val registry = remember(id) {
            SaveableStateRegistry(
                restoredValues = saved[id],
                canBeSaved = { parent?.canBeSaved(it) ?: true },
            )
        }
        CompositionLocalProvider(LocalSaveableStateRegistry provides registry) {
            content()
        }
        DisposableEffect(id) {
            onDispose {
                saved[id] = registry.performSave()
            }
        }
    }

    fun clear(id: DockableId) {
        saved.remove(id)
    }
}
