package com.seanproctor.docking.demo

import androidx.compose.ui.window.singleWindowApplication
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import org.jetbrains.jewel.ui.component.Text

fun main() = singleWindowApplication(title = "Compose Docking Jewel Demo") {
    IntUiTheme {
        Text("Compose Docking Jewel demo — docking UI coming soon")
    }
}
