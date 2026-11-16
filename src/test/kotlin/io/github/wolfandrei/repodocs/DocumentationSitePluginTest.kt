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

package io.github.wolfandrei.repodocs

import io.github.wolfandrei.repodocs.tasks.OpenDocumentationSiteTask
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
        fixture.run("docsBuild")

        val usage = fixture.site("documentation/lib/how-to/usage.html").readText()
        assertTrue("id=\"установка-и-настройка\"" in usage) { usage }
        assertTrue("id=\"установка-и-настройка-1\"" in usage) { usage }
        assertTrue("id=\"формат-хранения-v2\"" in usage) { usage }
        assertTrue("href=\"../index.html\"" in usage) { usage }
        assertTrue("<table>" in usage && "type=\"checkbox\"" in usage) { usage }
        // The theme is set by the script of the head, before the styles, and switched by the button of the header.
        assertTrue(usage.indexOf("data-theme-choice") in 0 until usage.indexOf("site.css")) { usage.take(1500) }
        assertTrue("class=\"theme-toggle\"" in usage && "aria-label=\"Тема\"" in usage) { usage }
        assertTrue(":root[data-theme=\"dark\"]" in fixture.site("_assets/site.css").readText())

        val home = fixture.site("index.html").readText()
        assertTrue("href=\"documentation/index.html\"" in home) { home }
        assertTrue("documentation/lib/how-to/usage.html" in home) { "the sidebar lists the pages of the library index" }
        // A branch of the sidebar has the same key on every page, so that the script keeps it open or closed.
        val branches = { html: String -> Regex("data-nav=\"([^\"]*)\"").findAll(html).map { it.groupValues[1] }.toList() }
        assertTrue(branches(home).isNotEmpty() && branches(home) == branches(usage)) { branches(home).toString() }

        val diagram = fixture.site("documentation/lib/diagrams/context.svg").readText()
        assertTrue("<svg" in diagram && "&#1055;" in diagram || "Приложение" in diagram) { diagram.take(500) }
        assertFalse(fixture.site("documentation/lib/diagrams/context.puml").readText().isEmpty())

        assertTrue("<details class=\"diagram-source\"><summary>Исходник: <code>context.puml</code>" in usage) { usage }
        assertTrue("<li><code>&lt;C4/C4_Context&gt;</code></li>" in usage) { usage }
        assertTrue("<li><code>documentation/lib/diagrams/defines.iuml</code></li>" in usage) { usage }
        assertTrue("System_Ext(app, &quot;Приложение&quot;" in usage.replace(Regex("</?span[^>]*>"), "")) { usage }
        assertTrue("<span class=\"tok-meta\">!include</span>" in usage) { "the source is highlighted: $usage" }


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
        assertTrue("repoDocsFrameHeight" in frame) { frame }
        assertFalse("repoDocsFrameHeight" in fixture.file("documentation/lib/interactive/modes.html").readText())

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

        val result = fixture.run("docsCheck")

        assertEquals(TaskOutcome.SUCCESS, result.task(":docsCheck")?.outcome)
        assertTrue("no problems" in result.output) { result.output }
    }

    @Test
    fun `check reports broken links, anchors, diagram errors and stale diagrams`() {
        fixture.write(siteSettings = "")
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
            |
            """.trimMargin()
        )

        val result = fixture.run("docsCheck", expectFailure = true)
        val output = result.output

        listOf(
            "documentation/lib/index.md:12: page referenced from the index `how-to/missing.md` does not exist",
            "link target `../nothing.md` does not exist",
            "anchor `#установка-настройка` of `#установка-настройка` is not a heading",
            "did you mean `#формат-хранения-v2`?",
            "documentation/lib/diagrams/broken.puml:3",
            "documentation/lib/diagrams/context.svg: out of date with its PlantUML source",
        ).forEach { expected -> assertTrue(expected in output) { "No `$expected` in:\n$output" } }
        assertFalse("```" in output.substringAfter("problem(s)").substringBefore("Report:")) { output }
    }

    @Test
    fun `only a committed SVG next to its source is compared, anchors follow GitHub by default`() {
        fixture.write(siteSettings = "")
        fixture.file("documentation/lib/how-to/usage.md").appendText(
            "\n## Раздел - с дефисом\n\n[GitHub](#раздел---с-дефисом)\n"
        )
        fixture.commitRenderedDiagrams()
        fixture.file("documentation/lib/diagrams/picture.svg").writeText("<svg/>\n")
        val fresh = fixture.run("docsCheck").output
        assertTrue("no problems" in fresh) { "an SVG without a .puml is not compared:\n$fresh" }

        fixture.file("documentation/lib/diagrams/context.svg").writeText("<svg><text>старая схема</text></svg>\n")
        val stale = fixture.run("docsCheck", expectFailure = true).output
        assertTrue("documentation/lib/diagrams/context.svg: out of date with its PlantUML source" in stale) { stale }
        assertFalse("picture.svg" in stale || "is not a heading" in stale) { stale }
    }

    @Test
    fun `docs is the documentation directory when there is no documentation directory`() {
        fixture.write()
        fixture.commitRenderedDiagrams()
        fixture.projectDir.resolve("documentation").renameTo(fixture.projectDir.resolve("docs"))
        fixture.file("README.md").writeText("# Fixture\n\nДокументация - в [оглавлении](docs/index.md).\n")

        val tasks = arrayOf("docsBuild", "docsCheck", "--configuration-cache")
        val result = fixture.run(*tasks)

        assertTrue("Documentation check: 4 pages, no problems" in result.output) { result.output }
        assertTrue(fixture.site("docs/lib/how-to/usage.html").isFile)

        fixture.file("documentation/index.md").writeText("# Новая документация\n")
        val moved = fixture.run(*tasks)
        assertTrue("configuration cache cannot be reused" in moved.output) { moved.output }
        assertTrue("Documentation check: 2 pages" in moved.output) { moved.output }
        assertTrue(fixture.site("documentation/index.html").isFile)
    }

    @Test
    fun `check runs with check only when enabled`() {
        fixture.write(siteSettings = "checks { runWithCheck.set(true) }")

        val result = fixture.run("check", "--dry-run")

        assertTrue(":docsCheck SKIPPED" in result.output) { result.output }
    }

    @Test
    fun `tasks are reused from the configuration cache and from the build cache`() {
        fixture.write()
        fixture.commitRenderedDiagrams()
        // The render above ran without the build cache: drop its output so that the first run below stores it.
        fixture.projectDir.resolve("build").deleteRecursively()
        val tasks = arrayOf("docsBuild", "docsCheck", "--configuration-cache", "--build-cache")

        fixture.run(*tasks)
        val second = fixture.run(*tasks)
        assertTrue("Reusing configuration cache" in second.output) { second.output }
        listOf(":docsBuild", ":docsCheck", ":docsRenderDiagrams").forEach {
            assertEquals(TaskOutcome.UP_TO_DATE, second.task(it)?.outcome) { second.output }
        }

        fixture.projectDir.resolve("build").deleteRecursively()
        val cached = fixture.run(*tasks)
        listOf(":docsBuild", ":docsCheck", ":docsRenderDiagrams").forEach {
            assertEquals(TaskOutcome.FROM_CACHE, cached.task(it)?.outcome) { cached.output }
        }

        fixture.file("documentation/lib/how-to/usage.md").appendText("\nНовый абзац ${System.nanoTime()}.\n")
        val changed = fixture.run(*tasks)
        assertEquals(TaskOutcome.SUCCESS, changed.task(":docsBuild")?.outcome) { changed.output }
        assertEquals(TaskOutcome.UP_TO_DATE, changed.task(":docsRenderDiagrams")?.outcome) { changed.output }
    }

    @Test
    fun `docsDiagrams writes the SVG next to the sources, the ELK layout included, and fails on a broken diagram`() {
        fixture.write()
        fixture.file("documentation/lib/diagrams/modules.puml").writeText(ELK_DIAGRAM)

        fixture.run("docsDiagrams")

        val modules = fixture.file("documentation/lib/diagrams/modules.svg").readText()
        assertTrue("lib-impl" in modules && "Exception" !in modules) { modules.take(2000) }
        assertTrue("<svg" in fixture.file("documentation/lib/diagrams/context.svg").readText())
        assertTrue("<svg" in fixture.file("documentation/lib/diagrams/put.svg").readText())

        fixture.file("documentation/lib/diagrams/broken.puml").writeText("@startuml\nAlice -> Bob : hi\nfoo bar baz ((\n@enduml\n")
        val result = fixture.run("docsDiagrams", expectFailure = true)

        assertTrue("documentation/lib/diagrams/broken.puml:3" in result.output) { result.output }
        assertFalse(fixture.file("documentation/lib/diagrams/broken.svg").exists()) { "nothing is written on an error" }
    }

    @Test
    fun `committed SVG is never used, the site draws the diagram from its source`() {
        fixture.write()
        fixture.file("documentation/lib/diagrams/context.svg").writeText("<svg><text>устарело</text></svg>")

        fixture.run("docsBuild")

        val diagram = fixture.site("documentation/lib/diagrams/context.svg").readText()
        assertFalse("устарело" in diagram) { diagram.take(300) }
    }

    @Test
    fun `smetana by default on every machine, layout auto uses Graphviz dot when it is on PATH`() {
        fixture.write()
        fixture.run("docsRenderDiagrams")
        assertTrue("\"engine\": \"smetana\"" in fixture.file("build/repo-docs-diagrams-report.json").readText())

        assumeTrue(graphvizAvailable(), "Graphviz dot is not on PATH")
        fixture.write(siteSettings = "plantUml { layout.set(DiagramLayout.AUTO) }")
        fixture.run("docsRenderDiagrams")
        assertTrue("\"engine\": \"dot\"" in fixture.file("build/repo-docs-diagrams-report.json").readText())
    }

    @Test
    fun `a link to a repository file leads to the file from the site`() {
        fixture.write()
        fixture.file("documentation/lib/how-to/usage.md").appendText("\n[Сборка](../../../build.gradle.kts#L3)\n")
        fixture.run("docsBuild")
        val local = fixture.site("documentation/lib/how-to/usage.html").readText()
        assertTrue("href=\"../../../../../build.gradle.kts#L3\"" in local) { local }
    }

    @Test
    fun `docsBuild refuses an output directory that holds the repository or its documentation`() {
        fixture.write(siteSettings = "outputDirectory.set(layout.projectDirectory.dir(\"documentation\"))")

        val output = fixture.run("docsBuild", expectFailure = true).output

        assertTrue("contains sources of the site, and docsBuild would delete them" in output) { output }
        assertTrue(fixture.file("documentation/index.md").isFile)
    }

    @Test
    fun `a build in a subdirectory documents the repository above it`() {
        // The repository: README and Documentation at its root, the Gradle build in Modules/.
        val repository = fixture.projectDir
        val build = DocumentationFixture(repository.resolve("Modules"))
        build.file("settings.gradle.kts").writeText("rootProject.name = \"modules\"\n")
        build.file("build.gradle.kts").writeText(
            """
            |plugins {
            |    base
            |    id("io.github.wolfandrei.repo-docs")
            |}
            |
            |docs {
            |    repositoryRoot.set(layout.projectDirectory.dir(".."))
            |}
            """.trimMargin()
        )
        fixture.file(".rumdl.toml").writeText("[global]\ndisable = [\"all\"]\n")
        fixture.file("README.md").writeText("# Приложение\n\nДокументация - в [оглавлении](Documentation/index.md).\n")
        fixture.file("Documentation/index.md").writeText("# Документация\n\n- [Сборка](Build.md) - как собрать.\n")
        fixture.file("Documentation/Build.md").writeText("# Сборка\n\nМодули - в [Modules](../Modules/build.gradle.kts).\n")

        val result = build.run("docsBuild", "docsCheck", "--configuration-cache")

        assertTrue("Documentation check: 3 pages, no problems" in result.output) { result.output }
        val site = build.projectDir.resolve("build/repo-docs")
        assertTrue(site.resolve("Documentation/index.html").isFile) { site.walkTopDown().joinToString("\n") }
        assertTrue("href=\"Documentation/index.html\"" in site.resolve("README.html").readText())
        // From Modules/build/repo-docs/Documentation/ to Modules/build.gradle.kts.
        assertTrue("href=\"../../../build.gradle.kts\"" in site.resolve("Documentation/Build.html").readText())
    }

    @Test
    fun `docs opens the site with the browser of the system`() {
        val url = "file:///repo/build/repo-docs/index.html"
        assertEquals(listOf("open", url), OpenDocumentationSiteTask.command("Mac OS X", url))
        assertEquals(listOf("xdg-open", url), OpenDocumentationSiteTask.command("Linux", url))
        assertEquals(
            listOf("rundll32", "url.dll,FileProtocolHandler", url),
            OpenDocumentationSiteTask.command("Windows 11", url),
        )
    }

    @Test
    fun `build stamp changes with the content and only with it`() {
        fixture.write()
        fixture.run("docsBuild")
        val first = fixture.site("_assets/build-stamp.js").readText()

        fixture.run("docsBuild", "--rerun-tasks")
        assertEquals(first, fixture.site("_assets/build-stamp.js").readText())

        fixture.file("documentation/lib/how-to/usage.md").appendText("\nНовый абзац ${System.nanoTime()}.\n")
        fixture.run("docsBuild")
        val changed = fixture.site("_assets/build-stamp.js").readText()
        assertTrue(changed.matches(Regex("window.repoDocsBuildStamp = \"[0-9a-f]{64}\";\n")) && changed != first) { changed }
        assertTrue("_assets/build-stamp.js" in fixture.site("_assets/site.js").readText())
    }

    @Test
    fun `API reference has the modules that apply Dokka and only them`() {
        fixture.write(module = true, siteSettings = "")

        val result = fixture.run("docsBuild", "--configuration-cache")

        assertEquals(TaskOutcome.SUCCESS, result.task(":dokkaGeneratePublicationHtml")?.outcome) { result.output }
        assertEquals(null, result.task(":plain:dokkaGenerateModuleHtml")) { "a module without Dokka is skipped" }
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

        val reused = fixture.run("docsBuild", "--configuration-cache")
        assertTrue("Reusing configuration cache" in reused.output) { reused.output }
    }

    @Test
    fun `the tasks work with isolated projects`() {
        fixture.write(module = true, siteSettings = "")

        val isolated = "-Dorg.gradle.unsafe.isolated-projects=true"
        fixture.run("docsDiagrams", isolated)
        // docs opens a browser, so it is only configured: a dry run.
        fixture.run("docs", "--dry-run", isolated)
        val result = fixture.run("docsBuild", "docsCheck", isolated)

        assertTrue("Isolated Projects is an incubating feature" in result.output) { result.output }
        assertFalse("problems were found" in result.output || "problem was found" in result.output) { result.output }
        assertTrue(fixture.site("api/lib/index.html").isFile)
    }

    private companion object {
        fun graphvizAvailable(): Boolean = System.getenv("PATH").orEmpty().split(File.pathSeparator)
            .any { File(it, "dot").canExecute() }

        /** A diagram of the ELK layout: the PlantUML build of the plugin has to carry ELK and its xbase library. */
        val ELK_DIAGRAM = """
            |@startuml
            |!pragma layout elk
            |rectangle "lib-api" as api
            |rectangle "lib-impl" as impl
            |impl --> api : реализует
            |@enduml
            |
        """.trimMargin()
    }
}
