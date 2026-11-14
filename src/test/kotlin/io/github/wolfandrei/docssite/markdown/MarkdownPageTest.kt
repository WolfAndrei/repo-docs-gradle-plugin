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

package io.github.wolfandrei.docssite.markdown

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class MarkdownPageTest {
    private val page = MarkdownPage(
        "documentation/lib/index.md",
        """
        |# lib: `Greeter`
        |
        |## Обзор **схемы**
        |
        |![Схема](diagrams/context.svg)
        |
        |## How-to
        |
        |- [Как подключить](how-to/usage.md#установка) - описание;
        |  - [Вложенная](how-to/nested.md)
        |- без ссылки
        |
        |<a href="interactive/modes.html">Режимы</a>
        |
        |```shell
        |plantuml -tsvg documentation/lib/diagrams/*.puml
        |```
        """.trimMargin()
    )

    @Test
    fun `headings get GitLab ids from their text with code and emphasis`() {
        assertEquals(
            listOf("lib-greeter", "обзор-схемы", "how-to"),
            page.headings.map { it.id }
        )
        assertEquals("lib: Greeter", page.title)
    }

    @Test
    fun `links of Markdown and HTML are found with their lines`() {
        assertEquals(
            listOf(
                PageLink("diagrams/context.svg", 5, image = true),
                PageLink("how-to/usage.md#установка", 9, image = false),
                PageLink("how-to/nested.md", 10, image = false),
                PageLink("interactive/modes.html", 13, image = false),
            ),
            page.links
        )
    }

    @Test
    fun `lines of code blocks are known`() {
        assertEquals(setOf(15, 16, 17), page.codeLines)
    }

    @Test
    fun `outline has the groups and the linked list items`() {
        assertEquals(
            listOf(
                OutlineEntry.Group("Обзор схемы"),
                OutlineEntry.Group("How-to"),
                OutlineEntry.Entry(
                    "Как подключить",
                    "how-to/usage.md#установка",
                    listOf(OutlineEntry.Entry("Вложенная", "how-to/nested.md", emptyList()))
                ),
            ),
            page.outline()
        )
    }

    @Test
    fun `links are rewritten in Markdown and in HTML`() {
        val html = page.renderHtml { it.replace(".md", ".html").replace("interactive/", "pages/") }

        assertTrue("href=\"how-to/usage.html#установка\"" in html) { html }
        assertTrue("href=\"pages/modes.html\"" in html) { html }
        assertTrue("<h2 id=\"обзор-схемы\">" in html) { html }
    }

    @Test
    fun `relative links are resolved against the page`() {
        assertEquals(
            RelativeLink("documentation/reference/api.md", "форматы-хранения"),
            RelativeLink.resolve(
                "documentation/lib/index.md",
                "../reference/api.md#%D1%84%D0%BE%D1%80%D0%BC%D0%B0%D1%82%D1%8B-%D1%85%D1%80%D0%B0%D0%BD%D0%B5%D0%BD%D0%B8%D1%8F"
            )
        )
        assertEquals(
            RelativeLink("documentation/lib/index.md", "обзор"),
            RelativeLink.resolve("documentation/lib/index.md", "#обзор")
        )
        assertEquals(RelativeLink("README.md", null), RelativeLink.resolve("documentation/index.md", "../README.md"))
        assertNull(RelativeLink.resolve("documentation/index.md", "../../outside.md"))
        assertNull(RelativeLink.resolve("documentation/index.md", "https://gitlab.example.com/a.md"))
        assertNull(RelativeLink.resolve("documentation/index.md", "mailto:team@example.com"))
    }
}
