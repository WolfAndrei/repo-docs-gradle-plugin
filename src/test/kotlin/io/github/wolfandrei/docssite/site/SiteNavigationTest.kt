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

package io.github.wolfandrei.docssite.site

import io.github.wolfandrei.docssite.markdown.MarkdownPage
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

internal class SiteNavigationTest {
    private val pages = listOf(
        MarkdownPage("doc/index.md", "# Docs\n\n- [lib](lib/index.md) - lib.\n"),
        MarkdownPage(
            "doc/lib/index.md",
            """
            |# lib
            |
            |## How-to
            |
            |- [Usage](how-to/usage.md)
            |
            |## Reference
            |
            |- [API notes](reference/api.md)
            |- [Modes](interactive/modes.html)
            |
            |## Background
            |
            |- [Theory](explanation/theory.md)
            """.trimMargin(),
        ),
        MarkdownPage("doc/lib/how-to/usage.md", "# Usage\n"),
        MarkdownPage("doc/lib/reference/api.md", "# API notes\n"),
        MarkdownPage("doc/lib/explanation/theory.md", "# Theory\n"),
    ).associateBy { it.path }

    private fun api(module: String, vararg types: String): List<ApiPage> {
        fun page(path: String, type: ApiPageType, kind: SymbolKind?, title: String) =
            ApiPage("api/$module/$path", module, type, kind, title, title, "", "", "")
        return listOf(
            page("index.html", ApiPageType.MODULE, SymbolKind.MODULE, module),
            page("com.example/index.html", ApiPageType.PACKAGE, SymbolKind.PACKAGE, "com.example"),
        ) + types.map { name ->
            val directory = name.split('.').joinToString("/") { "-" + it.lowercase() }
            page("com.example/$directory/index.html", ApiPageType.CLASSLIKE, SymbolKind.CLASS, name.substringAfterLast('.'))
        }
    }

    private fun navigation(language: String = "en", titles: Map<String, String> = emptyMap()): List<NavigationItem> {
        val modules = listOf(ApiModuleEntry("lib", "lib"), ApiModuleEntry("tools", "tools"))
        return SiteNavigation(pages, setOf("doc/lib/interactive/modes.html"), UiText.of(language), titles).build(
            readme = null,
            index = "doc/index.md",
            apiModules = modules,
            apiPages = api("lib", "Greeter", "Result", "Result.Failure") + api("tools", "Tool"),
            interactivePages = listOf(
                InteractivePage("doc/lib/interactive/modes.html", "Cipher modes", ""),
                InteractivePage("doc/lib/interactive/extra.html", "Extra page", ""),
            ),
        )
    }

    private fun List<NavigationItem>.find(title: String): NavigationItem? =
        firstNotNullOfOrNull { if (it.title == title) it else it.children.find(title) }

    private fun NavigationItem.outline(depth: Int = 0): String =
        "  ".repeat(depth) + title + "\n" + children.joinToString("") { it.outline(depth + 1) }

    @Test
    fun `the API of a module goes under the reference of its library`() {
        val lib = navigation().find("lib")!!

        assertEquals(
            """
            |lib
            |  How-to guides
            |    Usage
            |  Reference
            |    API notes
            |    Modes
            |    API
            |      com.example
            |        Result
            |          Failure
            |        Greeter
            |  Background
            |    Theory
            |  Interactive pages
            |    Extra page
            |
            """.trimMargin(),
            lib.outline(),
        )
        assertEquals("api/lib/index.html", lib.children[1].children[2].path)
        assertEquals(SymbolKind.CLASS, lib.children[1].children[2].children[0].children[0].kind)
    }

    @Test
    fun `a module without a library goes to the top-level API group`() {
        val api = navigation().last()

        assertEquals("API", api.title)
        assertEquals(listOf("tools"), api.children.map { it.title })
        assertEquals("api/tools/index.html", api.children[0].path)
        assertNull(navigation().find("Other pages"))
    }

    @Test
    fun `group titles follow the language and the custom titles`() {
        val lib = navigation("ru", mapOf("Background" to "Теория")).find("lib")!!

        assertEquals(
            listOf("Инструкции", "Справочник", "Теория", "Интерактивные страницы"),
            lib.children.map { it.title },
        )
        assertEquals("API", lib.children[1].children.last().title) { "the reference is found by its new title" }
    }
}
