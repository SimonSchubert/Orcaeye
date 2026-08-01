package com.inspiredandroid.orcaeye

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.inspiredandroid.orcaeye.data.DesktopCrontabRepository
import com.inspiredandroid.orcaeye.data.DesktopInventoryRepository
import orcaeye.composeapp.generated.resources.Res
import orcaeye.composeapp.generated.resources.icon
import org.jetbrains.compose.resources.painterResource

fun main() {
    System.setProperty("apple.awt.application.appearance", "system")
    val repository = DesktopInventoryRepository()
    val crontabRepository = DesktopCrontabRepository()
    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "Orcaeye",
            icon = painterResource(Res.drawable.icon),
            state = rememberWindowState(width = 1280.dp, height = 800.dp),
        ) {
            App(repository = repository, crontabRepository = crontabRepository)
        }
    }
}
