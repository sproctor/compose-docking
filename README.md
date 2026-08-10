# Compose Docking

IDE-style window docking for [Compose Multiplatform](https://www.jetbrains.com/compose-multiplatform/):
splits, tab groups, drag-to-dock with docking handles, floating windows, maximize,
and layout persistence.

A port of the concepts and algorithms of [ModernDocking](https://github.com/andrewauclair/ModernDocking)
(Java Swing, MIT) to an idiomatic Compose API.

**Status: early development — not yet published to Maven Central.**

## Modules

| Artifact | Description | Targets |
|---|---|---|
| `com.seanproctor:compose-docking-core` | Layout engine, state model, docking machinery, unstyled renderer | JVM (desktop), Android, iOS, wasmJs |
| `com.seanproctor:compose-docking-material3` | Material 3 renderer | same as core |
| `com.seanproctor:compose-docking-jewel` | Jewel (IntelliJ look-and-feel) renderer | JVM only |

Floating OS windows and cross-window drag are desktop (JVM) features. Everything else —
splits, tabs, in-window drag-to-dock, tab reordering, maximize, persistence —
is common code and works on every target.

## Quick start (desktop, Material 3)

```kotlin
fun main() = application {
    val state = rememberDockState(
        initialLayout = {
            dockLayout {
                mainWindow {
                    dock("project")
                    dock("editor", target = "project", region = DockRegion.East, proportion = 0.75f)
                    dock("terminal", target = "editor", region = DockRegion.South, proportion = 0.3f)
                    dock("problems", target = "terminal", region = DockRegion.Center)
                    display("terminal")
                }
            }
        },
    ) {
        dockable("project", title = { "Project" }, options = DockableOptions(closable = false)) {
            ProjectTree()
        }
        dockable("editor", title = { "Editor" }) { EditorPane() }
        dockable("terminal", title = { "Terminal" }) { TerminalPane() }
        dockable("problems", title = { "Problems" }) { ProblemsPane() }
        dockable("todo", title = { "TODO" }) { TodoPane() }
    }

    // Restore on launch + debounced auto-save on every layout change.
    rememberAutoPersist(state, FileLayoutStorage(File(configDir, "layout.json")))

    Window(onCloseRequest = ::exitApplication, title = "My App") {
        MaterialTheme {
            Material3Docking {
                registerDockingWindow(state)   // desktop: cross-window drag + focus handling
                DockArea(state, modifier = Modifier.fillMaxSize())
            }
        }
    }
    // One OS window per floating entry, plus the drag preview window.
    MaterialTheme {
        Material3Docking { FloatingDockWindows(state) }
    }
}
```

On web/mobile, drop the two desktop calls and just use `DockArea` inside `Material3Docking`.

### Jewel (IntelliJ look)

```kotlin
IntUiTheme(isDark = true) {
    JewelDocking {
        registerDockingWindow(state)
        DockArea(state, Modifier.fillMaxSize())
    }
}
```

The Jewel adapter only *reads* from `JewelTheme`, so it works under both the standalone
`IntUiTheme` and the IDE `SwingBridgeTheme`. Jewel 0.39+ ships Java 25 bytecode — run on
a JDK 25+ (JetBrains Runtime recommended).

## Concepts

- **`DockState`** — the single source of truth. Holds an immutable layout value
  (windows → tree of splits / tab groups / dockables), the dockable registry, and every
  operation: `dock`, `undock`, `close` (with veto), `maximize`, `moveToNewWindow`,
  `show`, … Observe with `snapshotFlow { state.layout }` or the
  `state.events` flow.
- **Dockables** are declared once with reactive `title`/`icon` lambdas, per-dockable
  `DockableOptions` (closable, floatable, docking style, anchor, …), optional
  `saveState`/`restoreState` for persisted per-panel state, and the content composable.
  Content keeps all internal state when re-docked within a window; hoist what must
  survive a move *between windows* into `rememberSaveable`.
- **Drag-to-dock** replicates ModernDocking: docking handles (window edges + hovered
  panel), a translucent drop preview, 35% edge-region sensitivity,
  bidirectional docking-style filtering, tab-strip insertion, tearing off into floating
  windows, and Esc-cancel with full layout restore.
- **Anchors** give layouts named regions that survive close-all: a placeholder stays in
  the tree when the last anchored dockable leaves, and reopening docks back into it.
- **Persistence** is JSON (`kotlinx.serialization`) with a version field and migration
  hooks; unknown dockables in a saved layout are kept as placeholders and fill in when
  registered (or are created on demand via a `DockableResolver`). Named layout snapshots
  live on `state.layouts`.
- **Renderer SPI** — `DockingRenderer` is a slot interface; core builds all models and
  gesture modifiers, adapters only draw. Implement it to match any design system, and
  provide it via `LocalDockingRenderer` + `LocalDockingTheme`.

## Demos

```bash
./gradlew :demo:demo-material3:run                       # Material 3, desktop
./gradlew :demo:demo-material3:wasmJsBrowserDevelopmentRun   # Material 3, browser
./gradlew :demo:demo-jewel:run                           # IntelliJ look (needs JDK 25 toolchain)
```

## Credits

The docking model — drop-target precedence, region sensitivity, anchors, and
layout persistence semantics — is ported from
[ModernDocking](https://github.com/andrewauclair/ModernDocking) by Andrew Auclair (MIT).

## License

[MIT](LICENSE)
