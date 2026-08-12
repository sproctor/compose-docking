package com.seanproctor.docking.demo

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.seanproctor.docking.model.DockableOptions
import com.seanproctor.docking.model.DockingStyle
import com.seanproctor.docking.model.TabPreference
import com.seanproctor.docking.model.DockableId
import com.seanproctor.docking.state.DockState
import com.seanproctor.docking.state.DockableSpec
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.window.Popup
import com.seanproctor.docking.ui.LocalDockCapabilities
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.IconActionButton
import org.jetbrains.jewel.ui.icon.IconKey
import org.jetbrains.jewel.ui.icons.AllIconsKeys
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import org.jetbrains.jewel.ui.Orientation
import org.jetbrains.jewel.ui.component.Checkbox
import org.jetbrains.jewel.ui.component.DefaultButton
import org.jetbrains.jewel.ui.component.Divider
import org.jetbrains.jewel.ui.component.RadioButton
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.TextField

// The Jewel face of the ModernDocking basic demo. Panel identities, the default layout,
// and the panels' behavior live in demo-shared's DemoModel.kt.

// ---------- Themes (the ModernDocking demo's LaF switcher, IntelliJ-flavored) ----------

val demoThemeNames: List<String> = listOf("IntUi Light", "IntUi Dark")

var demoTheme: String by mutableStateOf("IntUi Dark")

@Composable
fun DemoTheme(content: @Composable () -> Unit) {
    IntUiTheme(isDark = demoTheme == "IntUi Dark", content = content)
}

/**
 * Jewel has no text-field-with-String overload, so bridge the shared `String` state to
 * `TextFieldValue` and keep the caret while letting external writes (Save's reparse) win.
 */
@Composable
private fun StringTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var fieldValue by remember { mutableStateOf(TextFieldValue(value)) }
    if (fieldValue.text != value) fieldValue = TextFieldValue(value)
    TextField(
        value = fieldValue,
        onValueChange = {
            fieldValue = it
            onValueChange(it.text)
        },
        modifier = modifier,
    )
}

// ---------- Panel content ----------

@Composable
fun SimplePanelContent(id: String) {
    val rows = remember(id) { demoControls(id) }
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            for (row in rows) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    for (control in row) {
                        when (control) {
                            DemoControl.Label -> Text("Label Here")
                            DemoControl.TextField -> {
                                var text by rememberSaveable { mutableStateOf("") }
                                StringTextField(
                                    value = text,
                                    onValueChange = { text = it },
                                    modifier = Modifier.width(120.dp),
                                )
                            }
                            DemoControl.Checkbox -> {
                                var checked by rememberSaveable { mutableStateOf(false) }
                                Checkbox(checked, onCheckedChange = { checked = it })
                            }
                            DemoControl.RadioButton -> {
                                var selected by rememberSaveable { mutableStateOf(false) }
                                RadioButton(selected, onClick = { selected = !selected })
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OutputPanelContent() {
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth()) {
            Text("one", Modifier.weight(1f).padding(horizontal = 8.dp, vertical = 4.dp))
            Text("two", Modifier.weight(1f).padding(horizontal = 8.dp, vertical = 4.dp))
        }
        Divider(Orientation.Horizontal)
    }
}

@Composable
private fun PropertiesDemoContent() {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        for (field in propsDemo.fields) {
            val state = propsDemo.texts.getValue(field)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("$field:", Modifier.width(72.dp))
                StringTextField(
                    value = state.value,
                    onValueChange = { if (propsDemo.acceptsInput(field, it)) state.value = it },
                    modifier = Modifier.width(160.dp),
                )
            }
        }
        DefaultButton(onClick = { propsDemo.save() }) { Text("Save") }
    }
}

@Composable
private fun ThemesPanelContent() {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        for (name in demoThemeNames) {
            val selected = demoTheme == name
            Text(
                text = name,
                modifier = Modifier.fillMaxWidth()
                    .then(
                        if (selected) {
                            Modifier.background(JewelTheme.globalColors.outlines.focused)
                        } else {
                            Modifier
                        },
                    )
                    .clickable { demoTheme = name }
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            )
        }
    }
}

@Composable
private fun ScrollingWithToolbarContent() {
    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            DefaultButton(onClick = {}) { Text("Add") }
            DefaultButton(onClick = {}) { Text("Remove") }
        }
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
            repeat(30) { Text("label $it", Modifier.padding(horizontal = 8.dp)) }
        }
    }
}

// ---------- The dockables ----------

/**
 * Registers one demo dockable. The library draws no header affordances of its own, so
 * every panel gets the standard set - maximized indicator, overflow menu, close - from
 * [DemoHeaderActions].
 */
private fun DockState.dockable(
    id: String,
    title: @Composable () -> String,
    options: DockableOptions = DockableOptions(),
    headerBackground: (@Composable () -> Color)? = null,
    headerForeground: (@Composable () -> Color)? = null,
    canClose: suspend () -> Boolean = { true },
    saveState: (() -> JsonElement)? = null,
    restoreState: ((JsonElement) -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val dockableId = DockableId(id)
    registry.register(
        DockableSpec(
            id = dockableId,
            options = options,
            title = title,
            headerBackground = headerBackground,
            headerForeground = headerForeground,
            canClose = canClose,
            saveState = saveState,
            restoreState = restoreState,
            trailingActions = { DemoHeaderActions(dockableId, headerForeground?.invoke()) },
            tabActions = { DemoTabActions(dockableId) },
            tabStripActions = { DemoTabStripActions(dockableId) },
            content = content,
        ),
    )
}

/** The overflow menu: move to a window, maximize/restore. */
@Composable
private fun DockState.DemoOverflowMenu(id: DockableId, tint: Color?) {
    val options = registry[id]?.options ?: DockableOptions()
    val canFloat = LocalDockCapabilities.current.floatingWindows && options.floatable
    if (!canFloat && !options.maximizable) return
    var menuOpen by remember { mutableStateOf(false) }
    Box {
        DemoIconButton(AllIconsKeys.Actions.More, "More options", tint) { menuOpen = true }
        if (menuOpen) {
            DemoMenu(onDismiss = { menuOpen = false }) {
                if (canFloat) {
                    DemoMenuItem("Window") { menuOpen = false; moveToNewWindow(id) }
                }
                if (options.maximizable) {
                    DemoMenuItem(if (isMaximized(id)) "Restore" else "Maximize") {
                        menuOpen = false
                        toggleMaximize(id)
                    }
                }
            }
        }
    }
}

/**
 * The tab strip's trailing menu, for the selected tab. ModernDocking only adds this
 * trailing component in always-display-tabs mode, so the demo matches.
 */
@Composable
private fun DockState.DemoTabStripActions(id: DockableId) {
    if (!settings.alwaysDisplayTabs) return
    DemoOverflowMenu(id, tint = null)
}

/**
 * A tab's close button. ModernDocking only puts close buttons on tabs in
 * always-display-tabs mode - otherwise each dockable keeps its own header - so the demo
 * follows the same rule.
 */
@Composable
private fun DockState.DemoTabActions(id: DockableId) {
    if (!settings.alwaysDisplayTabs) return
    if (registry[id]?.options?.closable != true) return
    val scope = rememberCoroutineScope()
    DemoIconButton(AllIconsKeys.General.CloseSmall, "Close", tint = null) { scope.launch { close(id) } }
}

/** The title-bar buttons: maximized indicator, overflow menu, close. */
@Composable
private fun DockState.DemoHeaderActions(id: DockableId, tint: Color?) {
    val scope = rememberCoroutineScope()
    val options = registry[id]?.options ?: DockableOptions()
    val maximized = isMaximized(id)
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (maximized) {
            Icon(
                key = AllIconsKeys.General.ExpandComponent,
                contentDescription = "Maximized",
                modifier = Modifier.padding(end = 2.dp).size(16.dp),
                tint = tint ?: JewelTheme.globalColors.text.normal,
            )
        }
        DemoOverflowMenu(id, tint)
        if (options.closable) {
            DemoIconButton(AllIconsKeys.General.Close, "Close", tint) { scope.launch { close(id) } }
        }
    }
}

@Composable
private fun DemoIconButton(key: IconKey, contentDescription: String, tint: Color?, onClick: () -> Unit) {
    IconActionButton(
        key = key,
        contentDescription = contentDescription,
        onClick = onClick,
        modifier = Modifier.size(20.dp),
        colorFilter = tint?.let { ColorFilter.tint(it) },
    )
}

@Composable
private fun DemoMenu(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Popup(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .width(IntrinsicSize.Max)
                .background(JewelTheme.globalColors.panelBackground)
                .border(1.dp, JewelTheme.globalColors.borders.normal)
                .padding(vertical = 4.dp),
            content = content,
        )
    }
}

@Composable
private fun DemoMenuItem(label: String, onClick: () -> Unit) {
    Text(
        label,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 4.dp),
    )
}

/**
 * The demo's panel chrome. The library draws no border of its own, so an app decides how
 * a dockable's content is bounded; here it is a hairline in the IDE's normal border color.
 */
@Composable
fun DemoPanel(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().border(1.dp, JewelTheme.globalColors.borders.normal)) {
        content()
    }
}


fun DockState.demoDockables() {
    for (word in demoPanelWords) {
        val id = word.lowercase()
        val titleColor = demoPanelTitleColors[id]
        dockable(
            id = id,
            title = if (id == "one") ({ panelOneTitle }) else ({ "Panel $word" }),
            options = DockableOptions(
                // Panel Seven is CENTER_ONLY in the ModernDocking demo
                dockingStyle = if (id == "seven") DockingStyle.CenterOnly else DockingStyle.Both,
                // Panel Three anchors the west column, so closing it leaves the area behind
                // as a strip (see collapsedAnchorThickness in the demo's DockingSettings)
                // rather than collapsing it - drag Three back and the column reopens.
                anchor = if (id == "three") DemoToolsAnchor else null,
            ),
            headerBackground = titleColor?.let { { it } },
            headerForeground = titleColor?.let { { Color.Black } },
        ) {
            DemoPanel { SimplePanelContent(id) }
        }
    }

    dockable(
        id = "always-displayed",
        title = { "always displayed" },
        options = DockableOptions(
            closable = false,
            floatable = false,
            limitedToWindow = true,
            tabPreference = TabPreference.Top,
        ),
    ) {
        DemoPanel { SimplePanelContent("always-displayed") }
    }

    dockable(
        id = "explorer",
        title = { "Explorer" },
        options = DockableOptions(
            floatable = false,
            maximizable = false,
            dockingStyle = DockingStyle.Vertical,
        ),
        canClose = { closeConfirmation.ask("Are you sure you want to close this panel?") },
    ) {
        DemoPanel {}
    }

    dockable(
        id = "output",
        title = { "Output" },
        options = DockableOptions(
            floatable = false,
            maximizable = false,
            dockingStyle = DockingStyle.Horizontal,
        ),
        canClose = { closeConfirmation.ask("Are you sure you want to close this panel?") },
    ) {
        DemoPanel { OutputPanelContent() }
    }

    dockable(
        id = "props-demo",
        title = { "Properties Demo" },
        saveState = { propsDemo.toJson() },
        restoreState = { propsDemo.fromJson(it) },
    ) {
        DemoPanel { PropertiesDemoContent() }
    }

    dockable(id = "fixed-size", title = { "Fixed Size" }) {
        DemoPanel {
            Box(
                Modifier.sizeIn(minWidth = 300.dp, minHeight = 300.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("minimum size 300x300")
            }
        }
    }

    dockable(id = "scroll-with-toolbar", title = { "Scrolling With Toolbar" }) {
        DemoPanel { ScrollingWithToolbarContent() }
    }

    dockable(
        id = "themes",
        title = { "Themes" },
        options = DockableOptions(closable = false, floatable = false),
    ) {
        DemoPanel { ThemesPanelContent() }
    }
}
