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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Monitor
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.unit.dp
import com.seanproctor.docking.model.DockableOptions
import com.seanproctor.docking.model.DockingStyle
import com.seanproctor.docking.model.TabPreference
import com.seanproctor.docking.demo.generated.resources.Res
import com.seanproctor.docking.demo.generated.resources.fullscreen
import com.seanproctor.docking.model.DockableId
import com.seanproctor.docking.state.DockState
import com.seanproctor.docking.state.DockableSpec
import com.seanproctor.docking.ui.LocalDockCapabilities
import kotlinx.serialization.json.JsonElement
import org.jetbrains.compose.resources.painterResource
import kotlinx.coroutines.launch

// The Material 3 face of the ModernDocking basic demo. Panel identities, the default
// layout, and the panels' behavior live in demo-shared's DemoModel.kt.

// ---------- Themes (the ModernDocking demo's FlatLaf switcher) ----------

val demoThemeNames: List<String> = listOf("Light", "Dark", "GitHub Dark", "Solarized Dark")

var demoTheme: String by mutableStateOf("Light")

private val GitHubDarkColors = darkColorScheme(
    primary = Color(0xFF58A6FF),
    background = Color(0xFF0D1117),
    surface = Color(0xFF161B22),
    surfaceVariant = Color(0xFF21262D),
    onBackground = Color(0xFFC9D1D9),
    onSurface = Color(0xFFC9D1D9),
)

private val SolarizedDarkColors = darkColorScheme(
    primary = Color(0xFF268BD2),
    secondary = Color(0xFF2AA198),
    background = Color(0xFF002B36),
    surface = Color(0xFF073642),
    surfaceVariant = Color(0xFF0A4552),
    onBackground = Color(0xFF93A1A1),
    onSurface = Color(0xFF93A1A1),
)

@Composable
fun DemoTheme(content: @Composable () -> Unit) {
    val colors = when (demoTheme) {
        "Dark" -> darkColorScheme()
        "GitHub Dark" -> GitHubDarkColors
        "Solarized Dark" -> SolarizedDarkColors
        else -> lightColorScheme()
    }
    MaterialTheme(colorScheme = colors, content = content)
}

/** Compose once per application (inside the theme): renders pending demo dialogs. */
@Composable
fun DemoDialogs() {
    closeConfirmation.message?.let { message ->
        AlertDialog(
            onDismissRequest = { closeConfirmation.answer(false) },
            title = { Text("Close Panel") },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = { closeConfirmation.answer(true) }) { Text("Yes") }
            },
            dismissButton = {
                TextButton(onClick = { closeConfirmation.answer(false) }) { Text("No") }
            },
        )
    }
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
                                OutlinedTextField(
                                    value = text,
                                    onValueChange = { text = it },
                                    modifier = Modifier.width(120.dp),
                                    singleLine = true,
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
        HorizontalDivider()
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
            var text by propsDemo.texts.getValue(field)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("$field:", Modifier.width(72.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { if (propsDemo.acceptsInput(field, it)) text = it },
                    modifier = Modifier.width(160.dp),
                    singleLine = true,
                )
            }
        }
        Button(onClick = { propsDemo.save() }) { Text("Save") }
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
                            Modifier.background(MaterialTheme.colorScheme.primaryContainer)
                        } else {
                            Modifier
                        },
                    )
                    .clickable { demoTheme = name }
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                color = if (selected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    LocalContentColor.current
                },
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
            Button(onClick = {}) { Text("Add") }
            Button(onClick = {}) { Text("Remove") }
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
    icon: (@Composable () -> Painter)? = null,
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
            icon = icon,
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
private fun DockState.DemoOverflowMenu(id: DockableId, color: Color) {
    val options = registry[id]?.options ?: DockableOptions()
    val canFloat = LocalDockCapabilities.current.floatingWindows && options.floatable
    if (!canFloat && !options.maximizable) return
    var menuOpen by remember { mutableStateOf(false) }
    Box {
        DemoIconButton(Icons.Filled.MoreVert, "More options", color) { menuOpen = true }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            if (canFloat) {
                DropdownMenuItem(
                    text = { Text("Window") },
                    onClick = { menuOpen = false; moveToNewWindow(id) },
                )
            }
            if (options.maximizable) {
                DropdownMenuItem(
                    text = { Text(if (isMaximized(id)) "Restore" else "Maximize") },
                    onClick = { menuOpen = false; toggleMaximize(id) },
                )
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
    DemoOverflowMenu(id, MaterialTheme.colorScheme.onSurfaceVariant)
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
    DemoIconButton(Icons.Filled.Close, "Close", MaterialTheme.colorScheme.onSurfaceVariant) {
        scope.launch { close(id) }
    }
}

/** The title-bar buttons: maximized indicator, overflow menu, close. */
@Composable
private fun DockState.DemoHeaderActions(id: DockableId, tint: Color?) {
    val scope = rememberCoroutineScope()
    val color = tint ?: MaterialTheme.colorScheme.onSurfaceVariant
    val options = registry[id]?.options ?: DockableOptions()
    val maximized = isMaximized(id)
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (maximized) {
            Icon(
                painterResource(Res.drawable.fullscreen),
                contentDescription = "Maximized",
                tint = color,
                modifier = Modifier.padding(end = 2.dp).size(16.dp),
            )
        }
        DemoOverflowMenu(id, color)
        if (options.closable) {
            DemoIconButton(Icons.Filled.Close, "Close", color) { scope.launch { close(id) } }
        }
    }
}

@Composable
private fun DemoIconButton(
    imageVector: ImageVector,
    contentDescription: String,
    color: Color,
    onClick: () -> Unit,
) {
    Box(
        Modifier.padding(horizontal = 2.dp).size(22.dp).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(imageVector, contentDescription, tint = color, modifier = Modifier.size(16.dp))
    }
}

/**
 * The demo's panel chrome. The library draws no border of its own, so an app decides how
 * a dockable's content is bounded; here it is a hairline in the theme's outline color.
 */
@Composable
fun DemoPanel(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().border(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
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
        icon = { rememberVectorPainter(Icons.Filled.Monitor) },
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
        icon = { rememberVectorPainter(Icons.Filled.Monitor) },
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
