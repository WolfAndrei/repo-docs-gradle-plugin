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

import java.io.File
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class EditorConfigTest {
    private val root = File("build/editorconfig-test").absoluteFile.apply {
        deleteRecursively()
        mkdirs()
    }

    private fun write(path: String, text: String) = root.resolve(path).apply { parentFile.mkdirs() }.writeText(text)

    private fun limit(path: String) = EditorConfig.maxLineLength(root.resolve(path))

    @Test
    fun `globs of the sections`() {
        assertTrue(EditorConfig.matches("*", "docs/index.md"))
        assertTrue(EditorConfig.matches("*.md", "docs/index.md"))
        assertTrue(EditorConfig.matches("*.{md,kt}", "Main.kt"))
        assertTrue(EditorConfig.matches("{*.md,*.txt}", "a/b/notes.txt"))
        assertTrue(EditorConfig.matches("docs/**.md", "docs/a/b.md"))
        assertTrue(EditorConfig.matches("/docs/*.md", "docs/b.md"))
        assertTrue(EditorConfig.matches("[Rr]EADME.md", "README.md"))
        assertTrue(EditorConfig.matches("page?.md", "page1.md"))
        assertFalse(EditorConfig.matches("*.kt", "index.md"))
        assertFalse(EditorConfig.matches("docs/*.md", "docs/a/b.md"))
        assertFalse(EditorConfig.matches("docs/*.md", "other/docs/b.md"))
        assertFalse(EditorConfig.matches("[!R]EADME.md", "README.md"))
    }

    @Test
    fun `later sections and closer files win, root stops the search`() {
        write(".editorconfig", "root = true\n\n[*]\nmax_line_length = 100\n\n[*.md]\nmax_line_length = 120\n")
        assertEquals(120, limit("docs/index.md"))
        assertEquals(100, limit("src/Main.kt"))

        write("docs/.editorconfig", "[*.md]\nmax_line_length = 80\n")
        assertEquals(80, limit("docs/index.md"))
        assertEquals(120, limit("README.md"))

        write("docs/.editorconfig", "root = true\n[*.kt]\nmax_line_length = 80\n")
        assertEquals(0, limit("docs/index.md"))
    }

    @Test
    fun `off, comments, case and missing values`() {
        write(".editorconfig", "root = true\n# comment\n[*.md]\n; comment\nMax_Line_Length = OFF\n")
        assertEquals(0, limit("index.md"))

        write(".editorconfig", "root = true\n[*.md]\nmax_line_length = wide\n")
        assertEquals(0, limit("index.md"))

        write(".editorconfig", "root = true\n[*.md]\nindent_size = 2\n")
        assertEquals(0, limit("index.md"))

        root.resolve(".editorconfig").delete()
        write("sub/.editorconfig", "root = true\n")
        assertEquals(0, limit("sub/index.md"))
    }

    @Test
    fun `candidates go from the directory up`() {
        val candidates = EditorConfig.candidates(root.resolve("a/b"))
        assertEquals(root.resolve("a/b/.editorconfig"), candidates.first())
        assertEquals(root.resolve("a/.editorconfig"), candidates[1])
        assertTrue(root.resolve(".editorconfig") in candidates)
    }
}
