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

package io.github.wolfandrei.docssite.lint

import java.io.File
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class RumdlOutputTest {
    /** Output of `rumdl check --output-format json --show-full-path` 0.3.4 in `/repo`. */
    private val sample = javaClass.getResource("rumdl-check.json")!!.readText()

    @Test
    fun `findings of the JSON output are relative to the repository root`() {
        val findings = Rumdl.parse(sample, File("/repo"))

        assertEquals(
            listOf(
                RumdlFinding("README.md", 3, "MD009", "3 trailing spaces found"),
                RumdlFinding("README.md", 5, "MD040", "Code block (```) missing language"),
                RumdlFinding("documentation/page.md", 1, "MD041", "First line in file should be a level 1 heading"),
                RumdlFinding("documentation/page.md", 3, "MD022", "Expected 1 blank line below heading"),
                RumdlFinding("documentation/page.md", 4, "MD032", "List should be preceded by blank line"),
            ),
            findings,
        )
    }

    @Test
    fun `no findings and relative paths`() {
        assertEquals(emptyList<RumdlFinding>(), Rumdl.parse("[]\n", File("/repo")))
        assertEquals(emptyList<RumdlFinding>(), Rumdl.parse("", File("/repo")))
        assertEquals(
            listOf(RumdlFinding("docs/a.md", null, "MD047", "Files should end with a single newline character")),
            Rumdl.parse(
                """[{"file":"docs\\a.md","rule":"MD047","message":"Files should end with a single newline character"}]""",
                File("/repo"),
            ),
        )
    }

    @Test
    fun `long lists of pages are split into several runs`() {
        val pages = (1..1000).map { "documentation/section-$it/page-with-a-long-name.md" }

        val batches = Rumdl.batches(pages)

        assertEquals(pages, batches.flatten())
        assertEquals(3, batches.size)
        assertEquals(emptyList<List<String>>(), Rumdl.batches(emptyList()))
    }
}
