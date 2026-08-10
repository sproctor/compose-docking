# TODO

## Collapse a dockable to a window-edge strip ("auto-hide")

**Status:** removed 2026-08-08, to be redesigned. The first implementation was a loose
imitation of IntelliJ's tool-window behavior under ModernDocking's name for it, and
neither the name nor the behavior held up. Reintroduce it deliberately rather than
restoring the old code — `git log` has it if the previous shape is useful as reference.

### What the feature should do

A dockable can be taken out of the layout and parked as a labeled button on a window
edge, so it stops consuming space but stays one click away. Clicking the button slides
the panel back over the layout temporarily; dismissing it (clicking elsewhere, or the
window losing focus) tucks it away again. Restoring it puts it back in the tree.

### Why the first attempt was wrong

- **The name.** "Auto-hide" is ModernDocking's term, inherited from Visual Studio. Nothing
  about it is automatic from the user's side — you explicitly park a panel and explicitly
  bring it back. Pick a name that describes the state ("collapsed", "minimized",
  "pinned to edge") and use it consistently across the API, the menu item, and the docs.
- **It was two features wearing one name.** Parking a dockable on an edge is a *layout*
  change (it leaves the tree, it persists). Sliding it out to peek is *transient view*
  state (per window, never persisted, dismissed on focus loss). The old code mixed them:
  `DockWindow.autoHide` held the parked entries and the slide proportion, while
  `DockState` separately held `expandedAutoHide` and a session-only proportion map that
  quietly disagreed with the persisted one.
- **Drop targets were unreadable.** Dragging showed three extra handles drawn with the
  same glyph as the direction handles, so a pin was pixel-identical to the root handle for
  the same edge and dropping on it silently minimized the panel. If drag-to-park comes
  back, it needs its own glyph and its own position, clearly separated from the five
  direction handles.
- **The default was backwards.** `DockableOptions.autoHideAllowed` defaulted to `true`,
  so every dockable advertised the feature. ModernDocking defaults it off and opts in
  per panel. Default off.

### Design questions to settle first

- Where does parked state live — in the layout tree, or beside it? What happens to an
  anchor when its last dockable is parked?
- Which edges, and who decides: the dockable, the window, or the drop position?
- Does a parked dockable count as open for `isOpen`/`isDocked`/`show`, and what should
  the View-menu checkbox in the demo reflect?
- Does the slide-out overlay the layout or push it aside? Is its size persisted?
- What is the renderer SPI surface — an edge-strip button model plus a slide-out
  container, or does the adapter own the whole strip?

### Where it touched the codebase last time

`DockWindow.autoHide` + `AutoHideEntry`/`AutoHideState`/`AutoHideSide` (model),
`autoHideInLayout`/`autoShowInLayout`/`setSlideProportionInLayout` (tree),
`setAutoHide`/`expandAutoHide`/`setAutoHideSlide`/`isAutoHidden` (state),
`AutoHideUi.kt` + the toolbar strips in `DockArea` (ui), `AutoHideButtonModel` +
`DockingRenderer.AutoHideButton` + `HandleKind.Pin*` (spi, all three renderers),
`DropTarget.Pin` + pin handles (drag), the `autoHide*` fields (persistence), and
`RotationModifier.kt`, which existed only to draw the vertical strips.
