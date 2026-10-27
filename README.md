# Compose Docking

IDE-style window docking for [Compose Multiplatform](https://www.jetbrains.com/compose-multiplatform/):
splits, tab groups, drag-to-dock with docking handles, floating windows, maximize,
and layout persistence.

A port of the concepts and algorithms of [ModernDocking](https://github.com/andrewauclair/ModernDocking)
(Java Swing, MIT) to an idiomatic Compose API.

**Status: early development.** Published, but expect breaking API changes between 0.x
releases.

## Modules

| Artifact | Description | Targets |
|---|---|---|
| `com.seanproctor:compose-docking-core` | Layout engine, state model, docking machinery, unstyled renderer | JVM (desktop), Android, iOS, wasmJs |
| `com.seanproctor:compose-docking-material3` | Material 3 renderer | same as core |
| `com.seanproctor:compose-docking-jewel` | Jewel (IntelliJ look-and-feel) renderer | JVM only |

Floating OS windows and cross-window drag are desktop (JVM) features. Everything else —
splits, tabs, in-window drag-to-dock, tab reordering, maximize, persistence —
is common code and works on every target.

## Installation

Depend on a renderer; it exposes the core as an `api` dependency, so you do not need to
declare `compose-docking-core` yourself.

```kotlin
repositories {
    // Compose itself resolves from Google's repository, so mavenCentral() alone
    // is not enough - the transitive androidx artifacts will not be found.
    google()
    mavenCentral()
}

dependencies {
    implementation("com.seanproctor:compose-docking-material3:0.3.1")
    // ...or, for the IntelliJ look on desktop:
    // implementation("com.seanproctor:compose-docking-jewel:0.3.1")
}
```

In a multiplatform build these go in `commonMain`, except `compose-docking-jewel`, which
is JVM only and belongs in `jvmMain` (or `desktopMain`).

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

### Floating windows draw their own chrome

A floating window is **undecorated**: the dockable's own header is the whole of it, so a
torn-off panel looks exactly as it did docked — same renderer, same colors — with no OS
title bar above repeating its name. Compose Desktop's resizer handles the edges.

Dragging that header moves the window, and **dropping it over another dock area docks it
there** — the window is its own drag preview, so the panel rides along inside it instead
of becoming a ghost, and the emptied window closes behind it. Released anywhere else the
window simply stays where you dropped it; Esc puts it back where the drag began.

Nobody draws minimize/maximize/close for you: the library contributes no header buttons
at all (a dockable's are its `trailingActions`). Read `LocalDockWindow` to tell a
torn-off panel from a docked one, and `DockState.awtWindow(windowId)` to act on the
window:

```kotlin
dockable("output", title = { "Output" }, trailingActions = {
    val window = LocalDockWindow.current
    if (window.isFloating) {
        IconButton(onClick = { state.awtWindow(window.windowId)?.extendedState = Frame.ICONIFIED }) {
            Icon(Icons.Default.Minimize, "Minimize")
        }
    }
    CloseButton()
}) { OutputPane() }
```

### Your own windows and decorations

`FloatingDockWindows(state)` makes the windows for you. To build them yourself — an OS
title bar, one you draw, a design system's window, another window toolkit entirely — pass a
lambda instead, and the library hands you one `FloatingWindowModel` per floating window:

```kotlin
FloatingDockWindows(state) { model ->
    Window(onCloseRequest = model.onCloseRequest, state = model.state, title = model.title, undecorated = true) {
        registerDockingWindow(state, model.windowId)
        Column {
            MyTitleBar(model.title, Modifier.dockWindowDragArea(state, model.windowId))
            DockArea(state, model.windowId, Modifier.weight(1f))
        }
    }
}
DockDragPreviewWindow(state)  // the window that follows the pointer during a drag
```

- **`model.state`** is bound to the layout both ways: moves and resizes persist, and a
  restored layout moves the window. Show the window with it.
- **`Modifier.dockWindowDragArea(state, windowId)`** makes any element a title bar: it moves
  the window, and released over another dock area docks what the window holds there (a lone
  dockable or a tab group; anything else is only moved). An OS title bar can move a window
  but never dock it, since the window manager keeps the pointer to itself. Or pass
  `DockArea(..., headerDragsWindow = true)` to let the lone dockable's header be the title
  bar, the way the built-in windows do.
- **`registerDockingWindow(state, windowId, window)`** connects any OS window: implement
  `DockHostWindow` — where its content is on screen, whether it has focus, and how to move
  it — and drags pass between windows. `FrameWindowScope.registerDockingWindow(state)` does
  this for a Compose Desktop window; `state.hostWindow(windowId)` gives it back to window
  buttons.
- **Platform drag-and-drop**: where windows cannot see the pointer outside themselves
  (native Wayland), provide a `DockingDragHandover` around the `DockArea` to start each drag
  in the platform's own session, and report it from each window's drop target to
  `state.windowHost` (`hoverInWindow`, `exitWindow`, `dropInWindow`, `endPlatformDrag`).

`demo-tao` uses all of this to dock in [Nucleus](https://github.com/NucleusFramework/Nucleus)
Tao windows, with no AWT in the process and no Nucleus in the library: see
[`NucleusDocking.kt`](demo/demo-tao/src/main/kotlin/com/seanproctor/docking/demo/NucleusDocking.kt).

## Concepts

- **`DockState`** — the single source of truth. Holds an immutable layout value
  (windows → tree of splits / tab groups / dockables), the dockable registry, and every
  operation: `dock`, `undock`, `close` (with veto), `maximize`, `moveToNewWindow`,
  `show`, … Observe with `snapshotFlow { state.layout }` or the
  `state.events` flow.
- **Dockables** are declared once with reactive `title`/`icon` lambdas, per-dockable
  `DockableOptions` (closable, floatable, docking style, anchor, minimum size, …), optional
  `saveState`/`restoreState` for persisted per-panel state, and the content composable.
  Content keeps all internal state when re-docked within a window; hoist what must
  survive a move *between windows* into `rememberSaveable`.
- **Drag-to-dock** replicates ModernDocking: docking handles (window edges + hovered
  panel), a translucent drop preview, 35% edge-region sensitivity,
  bidirectional docking-style filtering, tab-strip insertion, tearing off into floating
  windows, and Esc-cancel with full layout restore.
- **Anchors** give layouts named regions that survive close-all: a placeholder stays in
  the tree when the last anchored dockable leaves, and reopening docks back into it. Set
  `DockingSettings(collapsedAnchorThickness = 24.dp)` to draw an empty one as a strip
  instead of a full-size pane, so the area gives its space back while it is empty and
  still takes a drop, and add `emptyAnchorVisibility = EmptyAnchorVisibility.WhileDragging`
  to hide the strip entirely until a drag starts - an empty area then costs nothing at all
  until there is something to drop into it.
- **Minimum sizes** stop a divider before a pane gets too small to use: each dockable can
  set `DockableOptions(minSize = DpSize(240.dp, Dp.Unspecified))`, and any axis it leaves
  unspecified falls back to `DockingSettings.minPaneSize` (48dp by default). A tab group
  honours the largest minimum among its tabs. When the window is too small for every
  minimum, panes are squeezed in proportion to them, and the saved split proportions are
  left untouched so the layout comes back as the window grows.
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
./gradlew :demo:demo-tao:run                             # Jewel in Nucleus Tao windows, app-side integration (JDK 25)
```

## Credits

The docking model — drop-target precedence, region sensitivity, anchors, and
layout persistence semantics — is ported from
[ModernDocking](https://github.com/andrewauclair/ModernDocking) by Andrew Auclair (MIT).

## License

[MIT](LICENSE)
