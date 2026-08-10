package com.seanproctor.docking.persistence

import com.seanproctor.docking.layout.dockLayout
import com.seanproctor.docking.model.DockNode
import com.seanproctor.docking.model.DockRegion
import com.seanproctor.docking.model.DockableId
import com.seanproctor.docking.model.WindowBounds
import com.seanproctor.docking.model.WindowId
import com.seanproctor.docking.state.DockState
import com.seanproctor.docking.state.DockTarget
import com.seanproctor.docking.state.DockableSpec
import com.seanproctor.docking.tree.findAnchorNode
import com.seanproctor.docking.tree.findLeaf
import com.seanproctor.docking.tree.findTabsContaining
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val A = DockableId("a")
private val B = DockableId("b")
private val C = DockableId("c")

private fun spec(id: DockableId) = DockableSpec(id = id, title = { id.value }, content = {})

class DockLayoutBuilderTest {

    @Test
    fun buildsCompleteLayout() {
        val layout = dockLayout {
            mainWindow {
                dock("a")
                dock("b", target = "a", region = DockRegion.East, proportion = 0.3f)
                dock("c", target = "b", region = DockRegion.Center)
                display("b")
                anchorAtRoot("tools", DockRegion.West, proportion = 0.2f)
            }
            floatingWindow(WindowBounds(10f, 20f, 300f, 400f)) { dock("palette") }
        }

        val main = layout.mainWindow
        val root = assertNotNull(main.root)
        assertNotNull(root.findLeaf(A))
        val tabs = assertNotNull(root.findTabsContaining(B))
        assertEquals(B, tabs.selectedTab.dockableId)
        assertNotNull(root.findAnchorNode(com.seanproctor.docking.model.AnchorId("tools")))

        val floating = layout.floatingWindows.single()
        assertEquals(WindowBounds(10f, 20f, 300f, 400f), floating.bounds)
        assertNotNull(floating.root?.findLeaf(DockableId("palette")))
    }

    @Test
    fun dockOntoMissingTargetThrows() {
        assertFailsWith<IllegalStateException> {
            dockLayout { mainWindow { dock("b", target = "missing") } }
        }
    }
}

class PersistenceRoundTripTest {

    private fun sampleState(): DockState {
        val state = DockState {
            dockable(spec(A))
            dockable(spec(B))
            dockable(spec(C))
        }
        state.dock(A)
        state.dock(B, DockTarget.OnDockable(A), DockRegion.East, 0.35f)
        state.dock(C, DockTarget.OnDockable(B), DockRegion.Center)
        state.moveToNewWindow(C, WindowBounds(5f, 6f, 200f, 100f))
        return state
    }

    @Test
    fun encodeDecodeRoundTripsLosslessly() {
        val state = sampleState()
        val captured = state.captureLayout()
        val decoded = DockingPersistence.decode(DockingPersistence.encode(captured))
        assertEquals(captured, decoded)
    }

    @Test
    fun restoreReproducesRuntimeLayout() {
        val state = sampleState()
        val original = state.layout
        val captured = state.captureLayout()

        val fresh = DockState {
            dockable(spec(A))
            dockable(spec(B))
            dockable(spec(C))
        }
        fresh.restoreLayout(captured)
        assertEquals(original, fresh.layout)
    }

    @Test
    fun maximizedStateSurvivesRoundTrip() {
        val state = sampleState()
        state.maximize(A)
        val captured = state.captureLayout()
        val fresh = DockState { dockable(spec(A)) }
        fresh.restoreLayout(captured)
        assertTrue(fresh.isMaximized(A))
        fresh.restore(WindowId.MAIN)
        assertNotNull(fresh.layout.mainWindow.root?.findLeaf(A))
    }

    @Test
    fun perDockableStateSavesAndRestores() {
        var counter = 7
        val statefulSpec = DockableSpec(
            id = A,
            title = { "a" },
            saveState = { JsonPrimitive(counter) },
            restoreState = { counter = it.jsonPrimitive.int },
            content = {},
        )
        val state = DockState { dockable(statefulSpec) }
        state.dock(A)
        val captured = state.captureLayout()

        counter = 0
        val fresh = DockState { dockable(statefulSpec) }
        fresh.restoreLayout(captured)
        assertEquals(7, counter)
    }

    @Test
    fun unresolvedDockablesAreRetainedAndRoundTrip() {
        val state = sampleState()
        val captured = state.captureLayout()
        // Restore into a state with NO registered dockables and no resolver.
        val fresh = DockState { }
        fresh.restoreLayout(captured)
        // The unresolved leaves stay in the tree...
        assertNotNull(fresh.layout.mainWindow.root?.findLeaf(A))
        // ...and re-capturing loses nothing structural.
        assertEquals(captured.windows.map { it.root }, fresh.captureLayout().windows.map { it.root })
    }

    @Test
    fun resolverCreatesAndRegistersMissingDockables() {
        val state = sampleState()
        val captured = state.captureLayout()
        val resolved = mutableListOf<DockableId>()
        val fresh = DockState { }
        fresh.restoreLayout(captured, resolver = { id, _ ->
            resolved += id
            spec(id)
        })
        assertTrue(A in resolved && B in resolved && C in resolved)
        assertTrue(fresh.registry.isRegistered(A))
    }

    @Test
    fun mergeFloatingWindowsGraftsIntoMain() {
        val state = sampleState()
        val captured = state.captureLayout()
        val fresh = DockState {
            dockable(spec(A))
            dockable(spec(B))
            dockable(spec(C))
        }
        fresh.restoreLayout(captured, mergeFloatingWindows = true)
        assertEquals(1, fresh.layout.windows.size)
        assertNotNull(fresh.layout.mainWindow.root?.findLeaf(C))
    }

    @Test
    fun namedLayoutsSaveRestoreRemove() {
        val state = sampleState()
        val beforeLayout = state.layout
        state.layouts.save("workspace")
        assertEquals(listOf("workspace"), state.layouts.names)

        state.undock(A)
        state.undock(B)
        assertTrue(state.layouts.restore("workspace"))
        assertEquals(beforeLayout, state.layout)

        state.layouts.remove("workspace")
        assertTrue(state.layouts.names.isEmpty())
        assertTrue(!state.layouts.restore("workspace"))
    }

    @Test
    fun decodeAppliesMigrations() {
        // A fake "version 0" file that stores windows under an old key name.
        val v0 = """{"version":0,"windows":[]}"""
        val migration = LayoutMigration { from, json ->
            assertEquals(0, from)
            JsonObject(json + ("windows" to json.getValue("windows")))
        }
        val decoded = DockingPersistence.decode(v0, listOf(migration))
        assertEquals(PersistedApplicationLayout.CURRENT_LAYOUT_VERSION, decoded.version)
    }

    @Test
    fun decodeMalformedInputThrows() {
        assertFailsWith<Exception> { DockingPersistence.decode("not json at all") }
    }

    @Test
    fun encodedJsonIsStableAndReadable() {
        val state = sampleState()
        val text = DockingPersistence.encode(state.captureLayout())
        val obj = DockingPersistence.json.parseToJsonElement(text).jsonObject
        assertNull(obj["version"]) // default omitted (encodeDefaults = false)
        assertNotNull(obj["windows"])
    }
}
