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

package io.github.wolfandrei.docssite.check

import io.github.wolfandrei.docssite.plantuml.PlantUmlReport
import java.io.File
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class DocumentationCheckerTest {
    private val root = File("build/documentation-checker-test").absoluteFile.apply {
        deleteRecursively()
        mkdirs()
    }

    private fun check(markdown: String, lineLengthInTables: Boolean = false): List<String> {
        val page = root.resolve("documentation/page.md").apply {
            parentFile.mkdirs()
            writeText(markdown)
        }
        val sources = DocumentationSources(
            pages = mapOf("documentation/page.md" to page),
            htmlPages = emptyMap(),
            repositoryPaths = setOf("documentation", "documentation/page.md", "README.md"),
            plantUmlReport = PlantUmlReport(emptyList()),
            renderedDiagrams = emptyMap(),
            committedDiagrams = emptyMap(),
            maxLineLength = 40,
            lineLengthInTables = lineLengthInTables,
            forbiddenCharacters = listOf("—"),
            checkCommittedDiagrams = true,
        )

        return DocumentationChecker(sources).check().filterNot { it.warning }.map { it.toString() }
    }

    private val page = """
        |# Страница
        |
        || Параметр | Значение по умолчанию и его подробное описание |
        ||----------|------------------------------------------------|
        |
        |Строка текста длиннее сорока символов без ссылок.
        |Строка со ссылкой [README](../README.md#раздел) короче.
        |Код `a — b` и [адрес](https://example.com/очень/длинный/адрес/страницы).
    """.trimMargin()

    @Test
    fun `rows of tables, links, addresses and code are not counted`() {
        assertEquals(listOf("documentation/page.md:6: line is 49 characters, longer than 40"), check(page))
    }

    @Test
    fun `rows of tables are checked when enabled`() {
        assertEquals(
            listOf(3, 4, 6),
            check(page, lineLengthInTables = true).map { it.substringAfter(':').substringBefore(':').toInt() }
        )
    }
}
