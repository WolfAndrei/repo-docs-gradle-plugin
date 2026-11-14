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

/**
 * The rumdl linter through a local binary: `RUMDL_EXECUTABLE` or `rumdl` on `PATH`. Without one the tests are skipped;
 * the download is covered by the unit tests of the installer.
 */
internal class MarkdownLintPluginTest {
    private lateinit var fixture: DocumentationFixture
    private val rumdl: File? = (System.getenv("RUMDL_EXECUTABLE")?.takeIf { it.isNotBlank() }?.let(::File))
        ?: System.getenv("PATH").orEmpty().split(File.pathSeparator).map { File(it, "rumdl") }.firstOrNull {
            it.isFile && it.canExecute()
        }

    @BeforeEach
    fun createProject(testInfo: TestInfo) {
        assumeTrue(rumdl?.canExecute() == true) { "no rumdl: set RUMDL_EXECUTABLE or put rumdl on PATH" }
        val testName = testInfo.testMethod.get().name.replace(Regex("[^A-Za-z0-9]+"), "_")
        val projectDir = File("build/markdown-lint-tests/$testName").absoluteFile.apply {
            deleteRecursively()
            mkdirs()
        }
        fixture = DocumentationFixture(projectDir)
        fixture.write(markdownLint = "executable.set(file(\"${rumdl!!.invariantSeparatorsPath}\"))")
        // The pages of the fixture start with a link back to the table of contents and repeat a heading on purpose.
        fixture.file(".rumdl.toml").writeText("[global]\ndisable = [\"MD041\", \"MD024\"]\n")
        fixture.commitRenderedDiagrams()
    }

    @Test
    fun `check passes with the configuration of the repository`() {
        val result = fixture.run("documentationCheck")

        assertTrue("no problems" in result.output) { result.output }
    }

    @Test
    fun `check reports the findings of rumdl once, next to its own problems`() {
        fixture.file("documentation/lib/how-to/usage.md").appendText(
            "\n## Пример\n\nПробелы в конце   \n\n```\nкод\n```\n\nНет якоря: [якорь](#нет-такого).\n"
        )

        val result = fixture.run("documentationCheck", expectFailure = true)
        val output = result.output

        listOf(
            "documentation/lib/how-to/usage.md:32: [MD009] 3 trailing spaces found",
            "documentation/lib/how-to/usage.md:34: [MD040] Code block (```) missing language",
            "documentation/lib/how-to/usage.md:38: anchor `#нет-такого` of `#нет-такого` is not a heading",
        ).forEach { expected -> assertTrue(expected in output) { "No `$expected` in:\n$output" } }
        assertFalse("MD051" in output || "MD057" in output || "MD013" in output) { output }
        val report = fixture.projectDir.resolve("build/docs-site-check/report.txt").readText()
        assertTrue("[MD040]" in report) { report }
    }

    @Test
    fun `explicit configuration file and disabled rules replace the found configuration`() {
        fixture.file("documentation/lib/how-to/usage.md").appendText("\nПробелы в конце   \n")
        fixture.file("lint/markdownlint.json").writeText("{ \"MD041\": false, \"MD024\": false, \"MD009\": false }\n")
        fixture.file("build.gradle.kts").appendText(
            "\ndocumentationSite.checks.markdownLint.configFile.set(file(\"lint/markdownlint.json\"))\n"
        )
        assertTrue("no problems" in fixture.run("documentationCheck").output)

        // .rumdl.toml of the root is not read: MD024 it turns off is reported.
        fixture.file("lint/markdownlint.json").writeText("{ \"MD041\": false }\n")
        val output = fixture.run("documentationCheck", expectFailure = true).output
        assertTrue("[MD009]" in output && "[MD024]" in output) { output }

        fixture.file("build.gradle.kts")
            .appendText("\ndocumentationSite.checks.markdownLint.disable(\"MD009\", \"md024\")\n")
        assertTrue("no problems" in fixture.run("documentationCheck").output)
    }

    @Test
    fun `check is reused from the configuration cache and reruns when the rumdl configuration changes`() {
        val tasks = arrayOf("documentationCheck", "--configuration-cache")
        fixture.run(*tasks)

        val second = fixture.run(*tasks)
        assertTrue("Reusing configuration cache" in second.output) { second.output }
        assertEquals(TaskOutcome.UP_TO_DATE, second.task(":documentationCheck")?.outcome) { second.output }

        fixture.file(".rumdl.toml").writeText("[global]\ndisable = [\"MD041\", \"MD024\", \"MD022\"]\n")
        val changed = fixture.run(*tasks)
        assertTrue("Reusing configuration cache" in changed.output) { changed.output }
        assertEquals(TaskOutcome.SUCCESS, changed.task(":documentationCheck")?.outcome) { changed.output }

        // rumdl takes the configuration of the nearest directory of a page.
        fixture.file("documentation/lib/how-to/usage.md").appendText("\nПробелы в конце   \n")
        fixture.file("documentation/lib/how-to/.rumdl.toml")
            .writeText("extends = \"../../../.rumdl.toml\"\n\n[global]\nextend-disable = [\"MD009\"]\n")
        val nested = fixture.run(*tasks)
        assertTrue("Reusing configuration cache" in nested.output) { nested.output }
        assertEquals(TaskOutcome.SUCCESS, nested.task(":documentationCheck")?.outcome) { nested.output }
        fixture.file("documentation/lib/how-to/.rumdl.toml").writeText("extends = \"../../../.rumdl.toml\"\n")
        assertTrue("[MD009]" in fixture.run(*tasks, expectFailure = true).output)
    }

    @Test
    fun `format fixes the pages in place and aligns tables with Cyrillic text`() {
        fixture.file(".rumdl.toml").appendText("\n[MD060]\nenabled = true\nstyle = \"aligned\"\n")
        fixture.file("documentation/lib/how-to/table.md").writeText(
            """
            |[⇦&nbsp;К&nbsp;оглавлению](../index.md)
            |
            |# Таблица
            |
            |Пробелы в конце
            |
            || Параметр | Описание |
            ||---|---|
            || a | Короткий |
            || длинное имя параметра | x |
            |
            """.trimMargin()
        )
        fixture.file("documentation/lib/index.md").appendText("- [Таблица](how-to/table.md) - таблица.\n")

        fixture.run("documentationFormat", "--configuration-cache")

        assertEquals(
            """
            |[⇦&nbsp;К&nbsp;оглавлению](../index.md)
            |
            |# Таблица
            |
            |Пробелы в конце
            |
            || Параметр              | Описание |
            || --------------------- | -------- |
            || a                     | Короткий |
            || длинное имя параметра | x        |
            |
            """.trimMargin(),
            fixture.file("documentation/lib/how-to/table.md").readText(),
        )
        assertTrue("no problems" in fixture.run("documentationCheck").output)
    }
}
