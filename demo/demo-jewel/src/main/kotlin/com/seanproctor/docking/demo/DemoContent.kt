package com.seanproctor.docking.demo

import androidx.compose.foundation.background
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
import com.seanproctor.docking.state.DockStateBuilder
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

fun DockStateBuilder.demoDockables() {
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
            SimplePanelContent(id)
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
        SimplePanelContent("always-displayed")
    }

    dockable(
        id = "explorer",
        title = { "Explorer" },
        options = DockableOptions(
            floatable = false,
            maximizable = false,
            dockingStyle = DockingStyle.Vertical,
            autoHideStyle = DockingStyle.Vertical,
        ),
        canClose = { closeConfirmation.ask("Are you sure you want to close this panel?") },
    ) {
        Box(Modifier.fillMaxSize())
    }

    dockable(
        id = "output",
        title = { "Output" },
        options = DockableOptions(
            floatable = false,
            maximizable = false,
            dockingStyle = DockingStyle.Horizontal,
            autoHideStyle = DockingStyle.Horizontal,
        ),
        canClose = { closeConfirmation.ask("Are you sure you want to close this panel?") },
    ) {
        OutputPanelContent()
    }

    dockable(
        id = "props-demo",
        title = { "Properties Demo" },
        saveState = { propsDemo.toJson() },
        restoreState = { propsDemo.fromJson(it) },
    ) {
        PropertiesDemoContent()
    }

    dockable(id = "fixed-size", title = { "Fixed Size" }) {
        Box(Modifier.sizeIn(minWidth = 300.dp, minHeight = 300.dp), contentAlignment = Alignment.Center) {
            Text("minimum size 300×300")
        }
    }

    dockable(id = "scroll-with-toolbar", title = { "Scrolling With Toolbar" }) {
        ScrollingWithToolbarContent()
    }

    dockable(
        id = "themes",
        title = { "Themes" },
        options = DockableOptions(closable = false, floatable = false),
    ) {
        ThemesPanelContent()
    }
}
