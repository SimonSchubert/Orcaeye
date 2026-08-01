plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.spotless)
}

kotlin {
    jvmToolchain(17)
}

val screenshotsDir = layout.buildDirectory.dir("screenshots")
val mediaDir = layout.projectDirectory.dir("../media")

tasks.withType<Test>().configureEach {
    useJUnit()
    reports.html.required.set(false)
    // Skia renders offscreen; no display needed on CI.
    systemProperty("java.awt.headless", "true")
    systemProperty("screenshot.output.dir", screenshotsDir.get().asFile.absolutePath)
    maxHeapSize = "2g"
}

/** Renders the app screens and refreshes the PNGs committed under media/. */
tasks.register("updateScreenshots") {
    dependsOn(tasks.named("test"))

    val sourceDir = screenshotsDir.get().asFile
    val targetDir = mediaDir.asFile

    doLast {
        val rendered =
            sourceDir
                .listFiles()
                ?.filter { it.extension == "png" }
                ?.sortedBy { it.name }
                .orEmpty()
        if (rendered.isEmpty()) {
            error("No screenshots were rendered in $sourceDir")
        }
        targetDir.mkdirs()
        rendered.forEach { file ->
            file.copyTo(targetDir.resolve(file.name), overwrite = true)
            println("Copied ${file.name} -> media/${file.name}")
        }
    }
}

spotless {
    kotlin {
        target("src/**/*.kt")
        ktlint("1.5.0").editorConfigOverride(
            mapOf(
                "ktlint_standard_no-wildcard-imports" to "disabled",
                "ktlint_standard_function-naming" to "disabled",
                "ktlint_standard_property-naming" to "disabled",
                "ktlint_standard_backing-property-naming" to "disabled",
            ),
        )
    }
    kotlinGradle {
        target("*.gradle.kts")
        ktlint("1.5.0")
    }
}

dependencies {
    testImplementation(projects.composeApp)
    testImplementation(compose.desktop.currentOs)
    testImplementation(compose.desktop.uiTestJUnit4)
    testImplementation(libs.compose.runtime)
    testImplementation(libs.compose.foundation)
    testImplementation(libs.compose.material3)
    testImplementation(libs.compose.ui)
    testImplementation(libs.kotlinx.collections.immutable)
    // LoopsUiState pins its "next run" times, so the fixture needs LocalDateTime.
    testImplementation(libs.kotlinx.datetime)
    testImplementation(kotlin("test"))
}
