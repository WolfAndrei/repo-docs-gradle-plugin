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

package io.github.wolfandrei.repodocs.site

import io.github.wolfandrei.repodocs.markdown.MarkdownPage
import org.junit.jupiter.api.Assertions.assertEquals
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

    private fun navigation(language: String = "en"): List<NavigationItem> {
        val modules = listOf(ApiModuleEntry("lib", "lib"), ApiModuleEntry("tools", "tools"))
        return SiteNavigation(pages, setOf("doc/lib/interactive/modes.html"), UiText.of(language)).build(
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
            |  How-to
            |    Usage
            |  Reference
            |    API notes
            |    Modes
            |    API
            |      com.example
            |        Greeter
            |        Result
            |          Failure
            |  Background
            |    Theory
            |
            """.trimMargin(),
            lib.outline(),
        )
        assertEquals("api/lib/index.html", lib.children[1].children[2].path)
        assertEquals(SymbolKind.CLASS, lib.children[1].children[2].children[0].children[0].kind)
    }

    @Test
    fun `a reference heading without pages holds the API, and disappears without one`() {
        val index = MarkdownPage(
            "doc/lib/index.md",
            "# lib\n\n## How-to\n\n- [Usage](how-to/usage.md)\n\n## Reference\n\n- API - KDoc of the public types.\n",
        )
        val pages = mapOf(
            "doc/index.md" to MarkdownPage("doc/index.md", "# Docs\n\n- [lib](lib/index.md) - lib.\n"),
            index.path to index,
            "doc/lib/how-to/usage.md" to MarkdownPage("doc/lib/how-to/usage.md", "# Usage\n"),
        )
        fun build(apiPages: List<ApiPage>) = SiteNavigation(pages, emptySet(), UiText.of("en"), NavigationOptions())
            .build(
                readme = null,
                index = "doc/index.md",
                apiModules = if (apiPages.isEmpty()) emptyList() else listOf(ApiModuleEntry("lib", "lib")),
                apiPages = apiPages,
            )
            .find("lib")!!

        assertEquals(
            "lib\n  How-to\n    Usage\n  Reference\n    API\n      com.example\n        Greeter\n",
            build(api("lib", "Greeter")).outline(),
        )
        assertEquals("lib\n  How-to\n    Usage\n", build(emptyList()).outline())
    }

    @Test
    fun `a module without a library goes to the top-level API group`() {
        val api = navigation().single { it.title == "API" }

        assertEquals(listOf("tools"), api.children.map { it.title })
        assertEquals("api/tools/index.html", api.children[0].path)
    }

    @Test
    fun `groups keep the headings of the table of contents, the generated ones follow the language`() {
        val navigation = navigation("ru")

        assertEquals(listOf("How-to", "Reference", "Background"), navigation.find("lib")!!.children.map { it.title })
        assertEquals("Другие страницы", navigation.last().title)
    }

    @Test
    fun `an interactive page that no table of contents lists is one of the other pages`() {
        val others = navigation().last()

        assertEquals("Other pages", others.title)
        assertEquals(listOf("Extra page"), others.children.map { it.title })
        assertEquals("doc/lib/interactive/extra.html", others.children.single().path)
    }

    private fun library(index: String, vararg modules: ApiModuleEntry, extra: Map<String, String> = emptyMap()) =
        SiteNavigation(
            (mapOf("doc/index.md" to "# Docs\n\n- [vault](vault/index.md) - vault.\n", "doc/vault/index.md" to index) + extra)
                .mapValues { (path, text) -> MarkdownPage(path, text) },
            emptySet(),
        ).build(
            readme = null,
            index = "doc/index.md",
            apiModules = modules.toList(),
            apiPages = modules.flatMap { api(it.name, "Greeter") },
        )

    @Test
    fun `a reference group is found by a Russian heading or by its pages in reference`() {
        val russian = library("# vault\n\n## Справочник\n\n- API\n", ApiModuleEntry("vault", "vault"))
        val byDirectory = library(
            "# vault\n\n## Facts\n\n- [Formats](reference/formats.md)\n- [Errors](reference/errors.md)\n",
            ApiModuleEntry("vault", "vault"),
            extra = mapOf(
                "doc/vault/reference/formats.md" to "# Formats\n",
                "doc/vault/reference/errors.md" to "# Errors\n",
            ),
        )

        assertEquals("vault\n  Справочник\n    API\n      com.example\n        Greeter\n", russian.find("vault")!!.outline())
        assertEquals(
            "vault\n  Facts\n    Formats\n    Errors\n    API\n      com.example\n        Greeter\n",
            byDirectory.find("vault")!!.outline(),
        )
    }

    @Test
    fun `the modules of a family belong to the library named as the family, a library of their own name wins`() {
        val navigation = library(
            "# vault\n\n## Reference\n\n- API\n",
            ApiModuleEntry("lib", "vault/lib"),
            ApiModuleEntry("android", "vault/android"),
            ApiModuleEntry("other", "other"),
        )

        assertEquals(
            "vault\n  Reference\n    API\n      android\n        com.example\n          Greeter\n" +
                "      lib\n        com.example\n          Greeter\n",
            navigation.find("vault")!!.outline(),
        )
        assertEquals(listOf("other"), navigation.last().children.map { it.title }) { "no library named other" }
        assertEquals("lib", SiteNavigation.libraryOf(ApiModuleEntry("lib", "family/lib"), setOf("family", "lib")))
    }

    /** A folder per plugin with its own `<name>-index.md` hub whose items read «see [here](...)». */
    private val pluginLayout = listOf(
        MarkdownPage(
            "documentation/index.md",
            """
            |[Back](../README.md)
            |
            |# Plugins
            |
            |## Infrastructure
            |
            |- [secrets-plugin](secrets/secrets-plugin-index.md) - secrets at build time
            |- [tools](tools/overview.md) - helper tasks
            |- [essay](essay/essay.md) - a long text
            """.trimMargin(),
        ),
        MarkdownPage(
            "documentation/secrets/secrets-plugin-index.md",
            """
            |[Back to contents](../index.md)
            |
            |## Contents
            |
            |<img src="./imgs/flow.png" width="40%"/>
            |
            |- First set up the environment - see [here](./secrets-requirements.md)
            |- Then apply the plugin - see [here](./secrets-usage.md)
            |- FAQ - see [here](./secrets-faq.md)
            """.trimMargin(),
        ),
        MarkdownPage(
            "documentation/secrets/secrets-requirements.md",
            "## Contents\n\n- [Environment setup](#environment-setup)\n\n## Environment setup\n\nText.\n",
        ),
        MarkdownPage("documentation/secrets/secrets-usage.md", "# Secrets plugin usage\n\nText.\n"),
        MarkdownPage("documentation/secrets/secrets-faq.md", "Questions and answers without headings.\n"),
        MarkdownPage("documentation/secrets/secrets-notes.md", "# Notes on secrets\n"),
        MarkdownPage(
            "documentation/tools/overview.md",
            "# Tools\n\nTasks of the plugin.\n\n- [Copy](copy.md)\n- [Clean](clean.md)\n",
        ),
        MarkdownPage("documentation/tools/copy.md", "# Copy task\n"),
        MarkdownPage("documentation/tools/clean.md", "# Clean task\n"),
        MarkdownPage(
            "documentation/essay/essay.md",
            "# Essay\n\n" + "A sentence of real prose that explains the design in some detail. ".repeat(10) +
                "\n\n- [Part one](part-one.md)\n- [Part two](part-two.md)\n",
        ),
        MarkdownPage("documentation/essay/part-one.md", "# Part one\n"),
        MarkdownPage("documentation/essay/part-two.md", "# Part two\n"),
    ).associateBy { it.path }

    private fun pluginNavigation(options: NavigationOptions = NavigationOptions()): List<NavigationItem> =
        SiteNavigation(pluginLayout, emptySet(), UiText.of("en"), options).build(readme = null, index = "documentation/index.md")

    @Test
    fun `a name-index hub expands in place, its entries take the headings of their pages`() {
        val navigation = pluginNavigation()

        assertEquals(
            """
            |Plugins
            |  Infrastructure
            |    secrets-plugin
            |      Environment setup
            |      Secrets plugin usage
            |      secrets-faq
            |    Tools
            |      Copy task
            |      Clean task
            |    Essay
            |
            """.trimMargin(),
            navigation.first().outline(),
        )
        assertEquals("documentation/secrets/secrets-plugin-index.html", navigation.find("secrets-plugin")?.path)
    }

    @Test
    fun `pages outside the tables of contents show their headings, not their paths`() {
        val others = pluginNavigation().last()

        assertEquals("Other pages", others.title)
        assertEquals(listOf("Part one", "Part two", "Notes on secrets"), others.children.map { it.title })
    }

    @Test
    fun `hubs can be named explicitly and detection turned off`() {
        val navigation = pluginNavigation(
            NavigationOptions(
                hubPatterns = listOf("*-index.md", "documentation/essay/essay.md"),
                detectHubs = false,
            )
        )

        assertEquals(
            """
            |Plugins
            |  Infrastructure
            |    secrets-plugin
            |      Environment setup
            |      Secrets plugin usage
            |      secrets-faq
            |    Tools
            |    Essay
            |      Part one
            |      Part two
            |
            """.trimMargin(),
            navigation.first().outline(),
        )
    }

    @Test
    fun `without an index README is the table of contents`() {
        val pages = listOf(
            MarkdownPage("README.md", "# App\n\n## Documentation\n\n- [Build](Documentation/Build.md) - how to build.\n"),
            MarkdownPage("Documentation/Build.md", "# Build\n"),
            MarkdownPage("Documentation/Unlisted.md", "# Unlisted\n"),
        ).associateBy { it.path }

        val navigation = SiteNavigation(pages, emptySet()).build(readme = "README.md", index = "Documentation/index.md")

        assertEquals("App\n  Documentation\n    Build\n", navigation.first().outline())
        assertEquals(listOf("Unlisted"), navigation.last().children.map { it.title })
    }
}
