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

import java.io.File
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInfo

/** The rumdl linter of the plugin in a project: the configuration of the repository, the cache, the format. */
internal class MarkdownLintPluginTest {
    private lateinit var fixture: DocumentationFixture

    @BeforeEach
    fun createProject(testInfo: TestInfo) {
        val testName = testInfo.testMethod.get().name.replace(Regex("[^A-Za-z0-9]+"), "_")
        val projectDir = File("build/markdown-lint-tests/$testName").absoluteFile.apply {
            deleteRecursively()
            mkdirs()
        }
        fixture = DocumentationFixture(projectDir)
        fixture.write()
        // The pages of the fixture start with a link back to the table of contents and repeat a heading on purpose.
        fixture.file(".rumdl.toml").writeText("[global]\ndisable = [\"MD041\", \"MD024\"]\n")
        fixture.commitRenderedDiagrams()
    }

    @Test
    fun `check passes with the configuration of the repository`() {
        val result = fixture.run("docsCheck")

        assertTrue("no problems" in result.output) { result.output }
    }

    @Test
    fun `check reports the findings of rumdl once, next to its own problems`() {
        fixture.file("documentation/lib/how-to/usage.md").appendText(
            "\n## Пример\n\nПробелы в конце   \n\n```\nкод\n```\n\nНет якоря: [якорь](#нет-такого).\n"
        )

        val result = fixture.run("docsCheck", expectFailure = true)
        val output = result.output

        listOf(
            "documentation/lib/how-to/usage.md:32: [MD009] 3 trailing spaces found",
            "documentation/lib/how-to/usage.md:34: [MD040] Code block (```) missing language",
            "documentation/lib/how-to/usage.md:38: anchor `#нет-такого` of `#нет-такого` is not a heading",
        ).forEach { expected -> assertTrue(expected in output) { "No `$expected` in:\n$output" } }
        assertFalse("MD051" in output || "MD057" in output) { output }
        val report = fixture.projectDir.resolve("build/repo-docs-check/report.txt").readText()
        assertTrue("[MD040]" in report) { report }
    }

    @Test
    fun `check is reused from the configuration cache and reruns when the rumdl configuration changes`() {
        val tasks = arrayOf("docsCheck", "--configuration-cache")
        fixture.run(*tasks)

        val second = fixture.run(*tasks)
        assertTrue("Reusing configuration cache" in second.output) { second.output }
        assertEquals(TaskOutcome.UP_TO_DATE, second.task(":docsCheck")?.outcome) { second.output }

        fixture.file(".rumdl.toml").writeText("[global]\ndisable = [\"MD041\", \"MD024\", \"MD022\"]\n")
        val changed = fixture.run(*tasks)
        assertTrue("Reusing configuration cache" in changed.output) { changed.output }
        assertEquals(TaskOutcome.SUCCESS, changed.task(":docsCheck")?.outcome) { changed.output }

        // rumdl takes the configuration of the nearest directory of a page.
        fixture.file("documentation/lib/how-to/usage.md").appendText("\nПробелы в конце   \n")
        fixture.file("documentation/lib/how-to/.rumdl.toml")
            .writeText("extends = \"../../../.rumdl.toml\"\n\n[global]\nextend-disable = [\"MD009\"]\n")
        val nested = fixture.run(*tasks)
        assertTrue("Reusing configuration cache" in nested.output) { nested.output }
        assertEquals(TaskOutcome.SUCCESS, nested.task(":docsCheck")?.outcome) { nested.output }
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

        fixture.run("docsFormat", "--configuration-cache")

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
        assertTrue("no problems" in fixture.run("docsCheck").output)
    }
}
