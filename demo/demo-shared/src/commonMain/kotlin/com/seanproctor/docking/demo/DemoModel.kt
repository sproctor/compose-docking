package com.seanproctor.docking.demo

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import com.seanproctor.docking.layout.dockLayout
import com.seanproctor.docking.model.DockLayout
import com.seanproctor.docking.model.DockRegion
import kotlin.random.Random
import kotlinx.coroutines.CompletableDeferred
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

// The toolkit-agnostic half of the ModernDocking basic-demo port (its
// `demo-single-app/src/basic` package): panel identities, the default layout, and the
// behavior behind the panels. Each demo module renders these with its own widgets.

fun randomString(characters: String, length: Int): String =
    buildString { repeat(length) { append(characters.random()) } }

// ---------- Panel identities ----------

val demoPanelWords: List<String> =
    listOf("One", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight")

/** MainFrame's setTitleBackground colors; those four also force a black title foreground. */
val demoPanelTitleColors: Map<String, Color> = mapOf(
    "one" to Color(0xFFA1F2FF),
    "two" to Color(0xFFDDA1FF),
    "three" to Color(0xFFFFAEA1),
    "four" to Color(0xFFC3FFA1),
)

/** "Change tab text" rewrites this (SimplePanel.setTabText). */
var panelOneTitle: String by mutableStateOf("Panel One")

// ---------- SimplePanel's random pile of controls ----------

enum class DemoControl { Label, TextField, Checkbox, RadioButton }

/**
 * SimplePanel fills itself with up to 15 random controls in random rows. Seeded by the
 * panel id so a panel keeps its shape across recompositions and restarts, unlike the
 * Swing original which randomizes per construction.
 */
fun demoControls(id: String): List<List<DemoControl>> {
    val rand = Random(id.hashCode())
    val rows = mutableListOf(mutableListOf<DemoControl>())
    repeat(rand.nextInt(15)) {
        rows.last() += DemoControl.entries[rand.nextInt(DemoControl.entries.size)]
        if (rand.nextBoolean()) rows += mutableListOf<DemoControl>()
    }
    return rows.filter { it.isNotEmpty() }
}

// ---------- Close confirmation (ToolPanel.requestClose) ----------

/**
 * Bridges the suspending [com.seanproctor.docking.state.DockableSpec.canClose] veto to a
 * dialog: [ask] publishes [message] and suspends until the UI calls [answer].
 */
class CloseConfirmation {
    var message: String? by mutableStateOf(null)
        private set
    private var response: CompletableDeferred<Boolean>? = null

    suspend fun ask(message: String): Boolean {
        val deferred = CompletableDeferred<Boolean>()
        response = deferred
        this.message = message
        return try {
            deferred.await()
        } finally {
            this.message = null
            response = null
        }
    }

    fun answer(close: Boolean) {
        response?.complete(close)
    }
}

val closeConfirmation: CloseConfirmation = CloseConfirmation()

// ---------- Properties demo (@DockingProperty -> saveState/restoreState) ----------

class PropsDemoState {
    val fields: List<String> =
        listOf("Byte", "Short", "Integer", "Long", "Float", "Double", "Char", "Boolean", "String")

    val texts: Map<String, androidx.compose.runtime.MutableState<String>> =
        fields.associateWith { mutableStateOf(defaultFor(it)) }

    private val saved = fields.associateWith { defaultFor(it) }.toMutableMap()

    private fun defaultFor(field: String) = when (field) {
        "Float", "Double" -> "0.0"
        "Char" -> "a"
        "Boolean" -> "false"
        "String" -> ""
        else -> "0"
    }

    /** True when [field] only accepts integers (the demo's MyIntFilter DocumentFilter). */
    fun acceptsInput(field: String, text: String): Boolean =
        field != "Byte" || text.isEmpty() || text.toIntOrNull() != null

    /** Commits the field texts, parsed to their declared types (the Save button). */
    fun save() {
        for (field in fields) {
            val text = texts.getValue(field).value
            saved[field] = when (field) {
                "Byte" -> (text.toByteOrNull() ?: 0).toString()
                "Short" -> (text.toShortOrNull() ?: 0).toString()
                "Integer" -> (text.toIntOrNull() ?: 0).toString()
                "Long" -> (text.toLongOrNull() ?: 0).toString()
                "Float" -> (text.toFloatOrNull() ?: 0f).toString()
                "Double" -> (text.toDoubleOrNull() ?: 0.0).toString()
                "Char" -> text.firstOrNull()?.toString() ?: " "
                "Boolean" -> text.toBoolean().toString()
                else -> text
            }
            texts.getValue(field).value = saved.getValue(field)
        }
    }

    fun toJson(): JsonElement = buildJsonObject {
        for ((field, value) in saved) put("sample_${field.lowercase()}", value)
    }

    fun fromJson(element: JsonElement) {
        val obj = element as? JsonObject ?: return
        for (field in fields) {
            obj["sample_${field.lowercase()}"]?.jsonPrimitive?.content?.let { value ->
                saved[field] = value
                texts.getValue(field).value = value
            }
        }
    }
}

val propsDemo: PropsDemoState = PropsDemoState()

// ---------- The default layout (MainFrame's WindowLayoutBuilder) ----------

fun demoLayout(): DockLayout = dockLayout {
    mainWindow {
        dock("always-displayed")
        dock("one", target = "always-displayed", region = DockRegion.Center)
        dock("two", target = "one", region = DockRegion.South)
        dock("three", region = DockRegion.West)
        dock("four", target = "two", region = DockRegion.Center)
        dock("props-demo", target = "four", region = DockRegion.Center)
        dock("output", region = DockRegion.South)
        dock("themes", region = DockRegion.East)
        dock("explorer", target = "themes", region = DockRegion.Center)
        display("themes")
    }
}
