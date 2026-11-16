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

package io.github.wolfandrei.repodocs.lint

import java.io.File
import org.gradle.api.GradleException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir

internal class RumdlTest {
    @TempDir
    lateinit var root: File

    private val page = "# Страница\n\nПробелы в конце   \n\n```\nкод\n```\n"

    private fun write(path: String, text: String) = root.resolve(path).apply { parentFile.mkdirs() }.writeText(text)

    private fun rules(pages: List<String>, disabled: List<String> = emptyList()) =
        Rumdl(root, disabled).check(pages).findings.map { "${it.path}:${it.line} ${it.rule}" }

    @Test
    fun `without a configuration the defaults of rumdl apply, the disabled rules are off`() {
        write("docs/page.md", page)

        assertEquals(listOf("docs/page.md:3 MD009", "docs/page.md:5 MD040"), rules(listOf("docs/page.md")))
        assertEquals(listOf("docs/page.md:5 MD040"), rules(listOf("docs/page.md"), disabled = listOf("md009")))
    }

    @Test
    fun `the nearest rumdl toml of a page wins, follows its extends, and the disabled rules win over it`() {
        write("a/page.md", page)
        write("b/c/page.md", page + "\nСтрока длиннее двадцати символов текста.\n")
        write(".rumdl.toml", "[global]\ndisable = [\"MD040\"]\n")
        write("b/.rumdl.toml", "extends = \"../.rumdl.toml\"\n\n[global]\nextend-disable = [\"MD009\"]\n")
        write("b/c/.rumdl.toml", "extends = \"../.rumdl.toml\"\n\n[MD013]\nline-length = 20\ncode-blocks = false\n")

        assertEquals(listOf("a/page.md:3 MD009", "b/c/page.md:9 MD013"), rules(listOf("a/page.md", "b/c/page.md")))
        assertEquals(listOf("b/c/page.md:9 MD013"), rules(listOf("a/page.md", "b/c/page.md"), disabled = listOf("MD009")))
    }

    @Test
    fun `other configuration files are not read`() {
        write("page.md", page)
        write(".markdownlint.json", "{ \"MD009\": false, \"MD040\": false }\n")
        write("pyproject.toml", "[tool.rumdl]\ndisable = [\"MD009\", \"MD040\"]\n")

        assertEquals(listOf("page.md:3 MD009", "page.md:5 MD040"), rules(listOf("page.md")))
    }

    @Test
    fun `format fixes the pages as rumdl fmt does`() {
        write("page.md", "# Таблица\n\nПробелы в конце   \n")

        assertEquals(listOf("page.md"), Rumdl(root, emptyList()).format(listOf("page.md")))
        assertEquals("# Таблица\n\nПробелы в конце\n", root.resolve("page.md").readText())
        assertEquals(emptyList<String>(), Rumdl(root, emptyList()).format(listOf("page.md")))
    }

    @Test
    fun `a broken configuration fails with the file named`() {
        write("page.md", page)
        write(".rumdl.toml", "[global\n")

        val error = assertThrows<GradleException> { rules(listOf("page.md")) }

        assertTrue(".rumdl.toml" in error.message!!) { error.message }
    }
}
