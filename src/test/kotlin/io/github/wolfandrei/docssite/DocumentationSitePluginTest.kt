/*
 * Copyright 2026 Andrei Volkau
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.github.wolfandrei.docssite

import java.io.File
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInfo

internal class DocumentationSitePluginTest {
    private lateinit var fixture: DocumentationFixture

    @BeforeEach
    fun createProject(testInfo: TestInfo) {
        val testName = testInfo.testMethod.get().name.replace(Regex("[^A-Za-z0-9]+"), "_")
        val projectDir = File("build/documentation-site-tests/$testName").absoluteFile.apply {
            deleteRecursively()
            mkdirs()
        }
        fixture = DocumentationFixture(projectDir)
    }

    @Test
    fun `site has the pages with GitLab anchors, the diagrams, the interactive pages and the search index`() {
        fixture.write()
        fixture.run("documentationSite")

        val usage = fixture.site("documentation/lib/how-to/usage.html").readText()
        assertTrue("id=\"установка-и-настройка\"" in usage) { usage }
        assertTrue("id=\"установка-и-настройка-1\"" in usage) { usage }
        assertTrue("id=\"формат-хранения-v2\"" in usage) { usage }
        assertTrue("href=\"../index.html\"" in usage) { usage }
        assertTrue("<table>" in usage && "type=\"checkbox\"" in usage) { usage }

        val home = fixture.site("index.html").readText()
        assertTrue("href=\"documentation/index.html\"" in home) { home }
        assertTrue("documentation/lib/how-to/usage.html" in home) { "the sidebar lists the pages of the library index" }

        val diagram = fixture.site("documentation/lib/diagrams/context.svg").readText()
        assertTrue("<svg" in diagram && "&#1055;" in diagram || "Приложение" in diagram) { diagram.take(500) }
        assertFalse(fixture.site("documentation/lib/diagrams/context.puml").readText().isEmpty())

        assertTrue("<details class=\"diagram-source\"><summary>Исходник: <code>context.puml</code>" in usage) { usage }
        assertTrue("<li><code>&lt;C4/C4_Context&gt;</code></li>" in usage) { usage }
        assertTrue("<li><code>documentation/lib/diagrams/defines.iuml</code></li>" in usage) { usage }
        assertTrue("System_Ext(app, &quot;Приложение&quot;" in usage.replace(Regex("</?span[^>]*>"), "")) { usage }
        assertTrue("<span class=\"tok-meta\">!include</span>" in usage) { "the source is highlighted: $usage" }

        // auto: the sequence diagram is drawn in the browser, the C4 one needs library sprites and stays from the build
        val browser = Regex("class=\"plantuml-browser\" id=\"[^\"]+\" data-image=\"([^\"]+)\"").findAll(usage)
            .map { it.groupValues[1] }.toList()
        assertEquals(listOf("../diagrams/put.svg"), browser) { usage }
        val engine = fixture.site("_assets/plantuml/plantuml.js").readText()
        assertTrue("window.PlantUmlJs={render:" in engine) { engine.takeLast(300) }
        assertTrue(fixture.site("_assets/plantuml/viz-global.js").isFile)

        val interactive = fixture.site("documentation/lib/interactive/modes.html").readText()
        assertTrue("<iframe class=\"interactive-frame\" src=\"modes.frame.html\" title=\"Режимы\">" in interactive) {
            interactive
        }
        assertTrue("class=\"site-header\"" in interactive && "<title>Режимы - fixture</title>" in interactive) {
            interactive
        }
        assertTrue("<span class=\"nav-label\">Режимы</span></a>" in interactive) { "the sidebar shows the page title: $interactive" }
        val frame = fixture.site("documentation/lib/interactive/modes.frame.html").readText()
        assertTrue("href=\"../how-to/usage.html#формат-хранения-v2\" target=\"_top\"" in frame) { frame }
        assertTrue("docsSiteFrameHeight" in frame) { frame }
        assertFalse("docsSiteFrameHeight" in fixture.file("documentation/lib/interactive/modes.html").readText())

        val search = fixture.site("_assets/search-index.js").readText()
        assertTrue(search.startsWith("window.DOCS_SEARCH_INDEX = "))
        assertTrue("usage.html#формат-хранения-v2" in search) { search.take(1000) }
        assertTrue("\"t\":\"Режимы\",\"u\":\"documentation/lib/interactive/modes.html\"" in search) { search }
        assertTrue(fixture.site("_assets/site.js").isFile && fixture.site("_assets/site.css").isFile)
    }

    @Test
    fun `check passes on consistent documentation`() {
        fixture.write()
        fixture.commitRenderedDiagrams()

        val result = fixture.run("documentationCheck")

        assertEquals(TaskOutcome.SUCCESS, result.task(":documentationCheck")?.outcome)
        assertTrue("no problems" in result.output) { result.output }
    }

    @Test
    fun `check reports broken links, anchors, style, diagram errors and stale diagrams`() {
        fixture.write(siteSettings = "apiReference { enabled.set(false) }\n    checks { forbiddenCharacters.add(\"—\") }")
        fixture.commitRenderedDiagrams()
        fixture.file(
            "documentation/lib/diagrams/defines.iuml"
        ).writeText("skinparam backgroundColor #FFFFFF\nskinparam defaultFontColor #FF0000\n")
        fixture.file(
            "documentation/lib/diagrams/broken.puml"
        ).writeText("@startuml\nAlice -> Bob : hi\nfoo bar baz ((\n@enduml\n")
        fixture.file("documentation/lib/index.md").appendText("- [Нет страницы](how-to/missing.md) - потеряна.\n")
        fixture.file("documentation/lib/how-to/usage.md").appendText(
            """
            |
            |## Ссылки
            |
            |Нет файла: [файл](../nothing.md), нет якоря: [якорь](#установка-настройка), регистр: [якорь](#Формат-хранения-v2).
            |Строка с длинным тире — запрещена.
            |${"Очень длинная строка ".repeat(8)}
            |
            """.trimMargin()
        )

        val result = fixture.run("documentationCheck", expectFailure = true)
        val output = result.output

        listOf(
            "documentation/lib/index.md:12: page referenced from the index `how-to/missing.md` does not exist",
            "link target `../nothing.md` does not exist",
            "anchor `#установка-настройка` of `#установка-настройка` is not a heading",
            "did you mean `#формат-хранения-v2`?",
            "forbidden character `—`",
            "longer than 120",
            "documentation/lib/diagrams/broken.puml:3",
            "documentation/lib/diagrams/context.svg: out of date with its PlantUML source",
        ).forEach { expected -> assertTrue(expected in output) { "No `$expected` in:\n$output" } }
        assertFalse("```" in output.substringAfter("problem(s)").substringBefore("Report:")) { output }
    }

    @Test
    fun `check runs with check only when enabled`() {
        fixture.write(siteSettings = "apiReference { enabled.set(false) }\n    checks { runWithCheck.set(true) }")

        val result = fixture.run("check", "--dry-run")

        assertTrue(":documentationCheck SKIPPED" in result.output) { result.output }
    }

    @Test
    fun `tasks are reused from the configuration cache and from the build cache`() {
        fixture.write()
        fixture.commitRenderedDiagrams()
        // The render above ran without the build cache: drop its output so that the first run below stores it.
        fixture.projectDir.resolve("build").deleteRecursively()
        val tasks = arrayOf("documentationSite", "documentationCheck", "--configuration-cache", "--build-cache")

        fixture.run(*tasks)
        val second = fixture.run(*tasks)
        assertTrue("Reusing configuration cache" in second.output) { second.output }
        listOf(":documentationSite", ":documentationCheck", ":documentationRenderDiagrams").forEach {
            assertEquals(TaskOutcome.UP_TO_DATE, second.task(it)?.outcome) { second.output }
        }

        fixture.projectDir.resolve("build").deleteRecursively()
        val cached = fixture.run(*tasks)
        listOf(":documentationSite", ":documentationCheck", ":documentationRenderDiagrams").forEach {
            assertEquals(TaskOutcome.FROM_CACHE, cached.task(it)?.outcome) { cached.output }
        }

        fixture.file("documentation/lib/how-to/usage.md").appendText("\nНовый абзац ${System.nanoTime()}.\n")
        val changed = fixture.run(*tasks)
        assertEquals(TaskOutcome.SUCCESS, changed.task(":documentationSite")?.outcome) { changed.output }
        assertEquals(TaskOutcome.UP_TO_DATE, changed.task(":documentationRenderDiagrams")?.outcome) { changed.output }
    }

    @Test
    fun `committed SVG is never used, the site draws the diagram from its source`() {
        fixture.write(siteSettings = "apiReference { enabled.set(false) }\n    plantUml { render.set(\"build\") }")
        fixture.file("documentation/lib/diagrams/context.svg").writeText("<svg><text>устарело</text></svg>")

        fixture.run("documentationSite")

        val diagram = fixture.site("documentation/lib/diagrams/context.svg").readText()
        assertFalse("устарело" in diagram) { diagram.take(300) }
        assertFalse(fixture.site("_assets/plantuml").exists()) { "build rendering needs no browser engine" }
    }

    @Test
    fun `layout auto uses Graphviz dot when it is on PATH, smetana is forced by the setting`() {
        assumeTrue(graphvizAvailable(), "Graphviz dot is not on PATH")
        fixture.write()
        fixture.run("documentationRenderDiagrams")
        assertTrue("\"engine\": \"dot\"" in fixture.file("build/docs-site-diagrams-report.json").readText())

        fixture.write(siteSettings = "apiReference { enabled.set(false) }\n    plantUml { layout.set(\"smetana\") }")
        fixture.run("documentationRenderDiagrams")
        assertTrue("\"engine\": \"smetana\"" in fixture.file("build/docs-site-diagrams-report.json").readText())
    }

    @Test
    fun `build stamp changes with the content and only with it`() {
        fixture.write()
        fixture.run("documentationSite")
        val first = fixture.site("_assets/build-stamp").readText()

        fixture.run("documentationSite", "--rerun-tasks")
        assertEquals(first, fixture.site("_assets/build-stamp").readText())

        fixture.file("documentation/lib/how-to/usage.md").appendText("\nНовый абзац ${System.nanoTime()}.\n")
        fixture.run("documentationSite")
        val changed = fixture.site("_assets/build-stamp").readText()
        assertTrue(changed.matches(Regex("[0-9a-f]{64}")) && changed != first) { changed }
        assertTrue("_assets/build-stamp" in fixture.site("_assets/site.js").readText())
    }

    @Test
    fun `API reference has the modules that apply Dokka and only them`() {
        fixture.write(module = true, siteSettings = "")

        val result = fixture.run("documentationSite", "--configuration-cache")

        assertEquals(TaskOutcome.SUCCESS, result.task(":documentationApiReference")?.outcome) { result.output }
        assertEquals(null, result.task(":dokkaGeneratePublicationHtml")) { "the plugin does not apply Dokka" }
        assertEquals(null, result.task(":plain:dokkaGenerateModuleHtml")) { result.output }
        val api = fixture.site("api")
        assertTrue(api.resolve("lib/index.html").isFile) { api.walkTopDown().take(50).joinToString("\n") }
        assertFalse(api.resolve("plain").exists())
        val home = fixture.site("index.html").readText()
        assertTrue("api/lib/index.html" in home) { home }
        val module = api.resolve("lib").walkTopDown().filter { it.name == "index.html" }.any {
            "Greeter" in
                it.readText()
        }
        assertTrue(module) { api.walkTopDown().take(50).joinToString("\n") }
        assertFalse("###" in api.resolve("lib/index.html").readText()) { "templates are substituted" }

        val reused = fixture.run("documentationSite", "--configuration-cache")
        assertTrue("Reusing configuration cache" in reused.output) { reused.output }
    }

    @Test
    fun `excluded modules are not in the API reference`() {
        fixture.write(module = true, siteSettings = "apiReference { excludedModules.add(\":lib\") }")

        val result = fixture.run("documentationSite")

        assertEquals(TaskOutcome.NO_SOURCE, result.task(":documentationApiReference")?.outcome) { result.output }
        assertFalse(fixture.site("api").exists())
    }

    @Test
    fun `API reference works with isolated projects`() {
        fixture.write(module = true, siteSettings = "")

        val result = fixture.run("documentationSite", "-Dorg.gradle.unsafe.isolated-projects=true")

        assertTrue("Isolated Projects is an incubating feature" in result.output) { result.output }
        assertFalse("problems were found" in result.output || "problem was found" in result.output) { result.output }
        assertTrue(fixture.site("api/lib/index.html").isFile)
    }

    private companion object {
        fun graphvizAvailable(): Boolean = System.getenv("PATH").orEmpty().split(File.pathSeparator)
            .any { File(it, "dot").canExecute() }
    }
}
