package com.inspiredandroid.orcaeye.screenshots

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import com.inspiredandroid.orcaeye.model.AppSection
import com.inspiredandroid.orcaeye.ui.BrowserScreen
import com.inspiredandroid.orcaeye.ui.InventoryUiState
import com.inspiredandroid.orcaeye.ui.LoopsUiState
import com.inspiredandroid.orcaeye.ui.theme.OrcaeyeTheme
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import java.io.File
import kotlin.test.Test

/**
 * Renders the app screens off-screen and writes them as PNGs.
 *
 * Run `./gradlew :screenshotTests:updateScreenshots` to refresh the images in media/.
 */
class ScreenshotTest {
    @Test
    fun contextScreen() {
        capture("screen_01_context", SampleData.state.copy(section = AppSection.Context), dark = false)
    }

    @Test
    fun contextScreenDark() {
        capture("screen_01_context_dark", SampleData.state.copy(section = AppSection.Context), dark = true)
    }

    @Test
    fun loopsScreen() {
        capture(
            name = "screen_02_loops",
            state = SampleData.state.copy(section = AppSection.Loops),
            loopsState = SampleData.loopsState,
            dark = false,
        )
    }

    @Test
    fun loopsScreenDark() {
        capture(
            name = "screen_02_loops_dark",
            state = SampleData.state.copy(section = AppSection.Loops),
            loopsState = SampleData.loopsState,
            dark = true,
        )
    }

    @Test
    fun loopsEditor() {
        capture(
            name = "screen_03_loop_editor",
            state = SampleData.state.copy(section = AppSection.Loops),
            loopsState = SampleData.loopsEditorState,
            dark = false,
        )
    }

    @OptIn(ExperimentalTestApi::class)
    private fun capture(
        name: String,
        state: InventoryUiState,
        dark: Boolean,
        loopsState: LoopsUiState = LoopsUiState(loading = false),
    ) = runDesktopComposeUiTest(WIDTH, HEIGHT) {
        setContent {
            // Pin the density so the output is identical on every machine, and render
            // at 2x so the PNGs stay sharp on retina displays.
            CompositionLocalProvider(LocalDensity provides Density(SCALE)) {
                OrcaeyeTheme(darkTheme = dark) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background,
                    ) {
                        BrowserScreen(
                            state = state,
                            loopsState = loopsState,
                            onSelectSection = {},
                            onRefresh = {},
                            onSelectSystem = {},
                            onSelectProject = {},
                            onOpenSkill = {},
                            onOpenMemory = {},
                            onOpenFile = { _, _ -> },
                            onDraftChange = {},
                            onSave = {},
                            onClosePreview = {},
                            onShowCreate = { _, _, _ -> },
                            onDismissCreate = {},
                            onCreate = { _, _, _ -> },
                            onRequestDeleteFromEditor = {},
                            onCancelDelete = {},
                            onConfirmDelete = {},
                            onClearStatus = {},
                            onOpenTool = { _, _ -> },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }
        waitForIdle()
        // A dialog composes into its own root on top of the window's, so capture the last one.
        write(name, onAllNodes(isRoot()).onLast().captureToImage())
    }

    private fun write(
        name: String,
        image: ImageBitmap,
    ) {
        val outputDir = File(System.getProperty("screenshot.output.dir") ?: "build/screenshots")
        outputDir.mkdirs()
        val encoded =
            Image
                .makeFromBitmap(image.asSkiaBitmap())
                .encodeToData(EncodedImageFormat.PNG)
                ?: error("Failed to encode $name as PNG")
        val file = File(outputDir, "$name.png")
        file.writeBytes(encoded.bytes)
        println("Rendered ${file.absolutePath}")
    }

    private companion object {
        /**
         * Below 2x so the PNGs stay small, while the logical window below stays roomy —
         * that combination is what makes the screenshots read as compact rather than zoomed.
         */
        const val SCALE = 1f

        /** 1440x900 logical window at [SCALE]. */
        const val WIDTH = 800
        const val HEIGHT = 600
    }
}
