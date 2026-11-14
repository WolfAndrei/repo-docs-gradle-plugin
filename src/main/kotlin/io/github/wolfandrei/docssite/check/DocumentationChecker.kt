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

import io.github.wolfandrei.docssite.markdown.MarkdownPage
import io.github.wolfandrei.docssite.markdown.RelativeLink
import io.github.wolfandrei.docssite.plantuml.PlantUmlReport
import io.github.wolfandrei.docssite.plantuml.SvgFingerprint
import java.io.File

/** A problem of the documentation at [line] of [path] (relative to the repository root); [line] counts from 1. */
internal data class DocumentationProblem(
    val path: String,
    val line: Int?,
    val message: String,
    val warning: Boolean = false
) {
    override fun toString(): String = buildString {
        append(path)
        line?.let { append(':').append(it) }
        append(": ")
        if (warning) append("warning: ")
        append(message)
    }
}

/**
 * What the documentation is checked against. Paths are relative to the repository root with `/`.
 *
 * @property pages Markdown pages.
 * @property htmlPages interactive pages whose `href` and `src` are checked.
 * @property repositoryPaths files and directories of the repository: the targets of the links.
 * @property renderedDiagrams SVG rendered from PlantUML.
 * @property committedDiagrams committed SVG files of the documentation.
 * @property maxLineLength longest Markdown line outside code blocks; `0` turns the check off.
 * @property lineLengthInTables whether the rows of tables are checked for [maxLineLength].
 * @property forbiddenCharacters characters a Markdown page must not contain outside code blocks.
 */
internal class DocumentationSources(
    val pages: Map<String, File>,
    val htmlPages: Map<String, File>,
    val repositoryPaths: Set<String>,
    val plantUmlReport: PlantUmlReport,
    val renderedDiagrams: Map<String, File>,
    val committedDiagrams: Map<String, File>,
    val maxLineLength: Int,
    val lineLengthInTables: Boolean,
    val forbiddenCharacters: List<String>,
    val checkCommittedDiagrams: Boolean,
)

/** Checks the links, the style of the Markdown pages and the diagrams. */
internal class DocumentationChecker(private val sources: DocumentationSources) {
    private val pages = sources.pages.mapValues { (path, file) -> MarkdownPage(path, file.readText()) }

    fun check(): List<DocumentationProblem> {
        val problems = mutableListOf<DocumentationProblem>()
        pages.values.sortedBy { it.path }.forEach { page ->
            problems += links(page)
            problems += style(page)
        }
        sources.htmlPages.toSortedMap().forEach { (path, file) -> problems += htmlLinks(path, file.readText()) }
        problems += orphans()
        problems += sources.plantUmlReport.errors.map { DocumentationProblem(it.source, it.line, it.message) }
        if (sources.checkCommittedDiagrams) problems += staleDiagrams()

        return problems
    }

    private fun links(page: MarkdownPage): List<DocumentationProblem> {
        val fromIndex = page.path.substringAfterLast('/') == INDEX
        return page.links.mapNotNull { link ->
            link(page.path, link.destination, fromIndex)?.let { DocumentationProblem(page.path, link.line, it) }
        }
    }

    private fun htmlLinks(path: String, html: String): List<DocumentationProblem> {
        return MarkdownPage.HTML_LINK.findAll(html).mapNotNull { match ->
            val line = html.substring(0, match.range.first).count { it == '\n' } + 1
            link(path, match.groupValues[2], fromIndex = false)?.let { DocumentationProblem(path, line, it) }
        }.toList()
    }

    /** Why the link [destination] of [path] is broken, or `null` if it is not. */
    private fun link(path: String, destination: String, fromIndex: Boolean): String? {
        val link = RelativeLink.resolve(path, destination) ?: return null
        val target = link.path
        if (target.isNotEmpty() && target !in sources.repositoryPaths) {
            val hint = if (target in sources.renderedDiagrams) " (rendered from PlantUML, but not committed)" else ""
            val what = if (fromIndex) "page referenced from the index" else "link target"
            return "$what `$destination` does not exist$hint"
        }

        val fragment = link.fragment?.takeIf { it.isNotEmpty() } ?: return null
        val targetPage = pages[target] ?: return null
        val ids = targetPage.headings.map { it.id }
        if (fragment in ids || fragment.removePrefix(USER_CONTENT) in ids) return null

        val similar = ids.firstOrNull { it.equals(fragment, ignoreCase = true) }
            ?: ids.firstOrNull { it.replace("-", "") == fragment.replace("-", "") }
        val suggestion = similar?.let { ", did you mean `#$it`?" }.orEmpty()
        return "anchor `#$fragment` of `$destination` is not a heading of ${target.ifEmpty { path }}$suggestion"
    }

    /** Pages that no other page links to, except README and the tables of contents. */
    private fun orphans(): List<DocumentationProblem> {
        val referenced = mutableSetOf<String>()
        pages.values.forEach { page ->
            page.links.mapNotNullTo(referenced) { link ->
                RelativeLink.resolve(page.path, link.destination)?.path?.takeIf { it != page.path }
            }
        }
        sources.htmlPages.forEach { (path, file) ->
            MarkdownPage.HTML_LINK.findAll(file.readText()).mapNotNullTo(referenced) {
                RelativeLink.resolve(path, it.groupValues[2])?.path
            }
        }

        return pages.keys.sorted()
            .filter { it !in referenced && it.substringAfterLast('/') != INDEX && it.substringAfterLast('/') != README }
            .map { DocumentationProblem(it, null, "no page links to this page", warning = true) }
    }

    private fun style(page: MarkdownPage): List<DocumentationProblem> {
        val problems = mutableListOf<DocumentationProblem>()
        page.source.lines().forEachIndexed { index, line ->
            val number = index + 1
            if (number in page.codeLines) return@forEachIndexed

            val length = line.codePointCount(0, line.length)
            val table = number in page.tableLines && !sources.lineLengthInTables
            if (sources.maxLineLength > 0 && length > sources.maxLineLength && !table && !exemptFromLength(line)) {
                problems +=
                    DocumentationProblem(
                        page.path,
                        number,
                        "line is $length characters, longer than ${sources.maxLineLength}"
                    )
            }

            val prose = line.replace(INLINE_CODE, "")
            sources.forbiddenCharacters.filter { it in prose }.forEach { character ->
                problems += DocumentationProblem(page.path, number, "forbidden character `$character`")
            }
        }

        return problems
    }

    /** A line with an address or one that fits once the link destinations are removed. */
    private fun exemptFromLength(line: String): Boolean {
        if ("://" in line) return true

        val withoutDestinations = line.replace(LINK_DESTINATION, "]()")
        return withoutDestinations.codePointCount(0, withoutDestinations.length) <= sources.maxLineLength
    }

    private fun staleDiagrams(): List<DocumentationProblem> {
        return sources.renderedDiagrams.toSortedMap().mapNotNull { (path, rendered) ->
            val committed = sources.committedDiagrams[path] ?: return@mapNotNull null
            val expected = SvgFingerprint.of(rendered.readText())
            val actual = SvgFingerprint.of(committed.readText())
            if (expected == actual) return@mapNotNull null

            DocumentationProblem(path, null, "out of date with its PlantUML source: ${actual.difference(expected)}")
        }
    }

    private companion object {
        const val INDEX = "index.md"
        const val README = "README.md"
        const val USER_CONTENT = "user-content-"
        val INLINE_CODE = Regex("`[^`]*`")
        val LINK_DESTINATION = Regex("""]\([^)\s]*(\s+"[^"]*")?\)""")
    }
}
