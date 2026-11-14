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

import java.util.IdentityHashMap
import org.commonmark.Extension
import org.commonmark.ext.autolink.AutolinkExtension
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension
import org.commonmark.ext.gfm.tables.TableBlock
import org.commonmark.ext.gfm.tables.TablesExtension
import org.commonmark.ext.task.list.items.TaskListItemsExtension
import org.commonmark.node.AbstractVisitor
import org.commonmark.node.Code
import org.commonmark.node.CustomBlock
import org.commonmark.node.FencedCodeBlock
import org.commonmark.node.HardLineBreak
import org.commonmark.node.Heading
import org.commonmark.node.HtmlBlock
import org.commonmark.node.HtmlInline
import org.commonmark.node.Image
import org.commonmark.node.IndentedCodeBlock
import org.commonmark.node.Link
import org.commonmark.node.ListBlock
import org.commonmark.node.Node
import org.commonmark.node.Paragraph
import org.commonmark.node.SoftLineBreak
import org.commonmark.node.Text
import org.commonmark.parser.IncludeSourceSpans
import org.commonmark.parser.Parser
import org.commonmark.renderer.html.AttributeProvider
import org.commonmark.renderer.html.HtmlRenderer

/** Heading of a page with the id GitLab gives it. [line] counts from 1. */
internal data class PageHeading(val level: Int, val text: String, val id: String, val line: Int)

/** Link or image of a page as written in the source; [line] counts from 1. */
internal data class PageLink(val destination: String, val line: Int, val image: Boolean)

/** Entry of the table of contents that a page such as `documentation/index.md` forms. */
internal sealed interface OutlineEntry {
    /** A heading that groups the entries after it. */
    data class Group(val title: String) : OutlineEntry

    /** A list item that starts with a link; [destination] is as written on the page. */
    data class Entry(val title: String, val destination: String, val children: List<Entry>) : OutlineEntry
}

/** Text of a page between two headings, for the search index; [heading] is absent before the first heading. */
internal data class PageSection(val heading: PageHeading?, val text: String)

/**
 * A Markdown page parsed with the GitLab extensions that the repositories use: tables, strikethrough, task lists and
 * autolinks. [path] is relative to the repository root and uses `/`.
 */
internal class MarkdownPage(
    val path: String,
    val source: String,
    anchorStyle: AnchorStyle = AnchorStyle.GITLAB,
) {
    private val document: Node = PARSER.parse(source)
    private val headingOf = IdentityHashMap<Heading, PageHeading>()

    val headings: List<PageHeading>
    val links: List<PageLink>

    /** The first heading of the first level, or the name of the file. */
    val title: String

    /** Lines of code blocks, counting from 1: the style checks skip them. */
    val codeLines: Set<Int>

    /** Lines of tables, counting from 1: the line length check skips them unless told otherwise. */
    val tableLines: Set<Int>

    init {
        val anchors = HeadingAnchors(anchorStyle)
        val headings = mutableListOf<PageHeading>()
        val links = mutableListOf<PageLink>()
        val codeLines = mutableSetOf<Int>()
        val tableLines = mutableSetOf<Int>()

        document.accept(object : AbstractVisitor() {
            override fun visit(heading: Heading) {
                val text = plainText(heading)
                val pageHeading = PageHeading(heading.level, text, anchors.next(text), heading.line)
                headingOf[heading] = pageHeading
                headings += pageHeading
                visitChildren(heading)
            }

            override fun visit(link: Link) {
                links += PageLink(link.destination, link.line, image = false)
                visitChildren(link)
            }

            override fun visit(image: Image) {
                links += PageLink(image.destination, image.line, image = true)
                visitChildren(image)
            }

            override fun visit(htmlInline: HtmlInline) {
                links += htmlLinks(htmlInline.literal, htmlInline.line)
            }

            override fun visit(htmlBlock: HtmlBlock) {
                links += htmlLinks(htmlBlock.literal, htmlBlock.line)
            }

            override fun visit(fencedCodeBlock: FencedCodeBlock) {
                codeLines += fencedCodeBlock.lines
            }

            override fun visit(indentedCodeBlock: IndentedCodeBlock) {
                codeLines += indentedCodeBlock.lines
            }

            override fun visit(customBlock: CustomBlock) {
                if (customBlock is TableBlock) tableLines += customBlock.lines
                visitChildren(customBlock)
            }
        })

        this.headings = headings
        this.links = links
        this.codeLines = codeLines
        this.tableLines = tableLines
        title = headings.firstOrNull { it.level == 1 }?.text ?: path.substringAfterLast('/').removeSuffix(".md")
    }

    /** Text of the page split by its headings. */
    fun sections(): List<PageSection> {
        val sections = mutableListOf<PageSection>()
        var heading: PageHeading? = null
        val text = StringBuilder()
        var node = document.firstChild

        while (node != null) {
            if (node is Heading) {
                if (heading != null || text.isNotBlank()) {
                    sections += PageSection(heading, text.toString().trim())
                }
                heading = headingOf[node]
                text.clear()
            } else {
                text.append(plainText(node)).append(' ')
            }
            node = node.next
        }
        if (heading != null || text.isNotBlank()) {
            sections += PageSection(heading, text.toString().trim())
        }

        return sections
    }

    /**
     * Structure of the page as a table of contents: a heading below the first level starts a group, and each list item
     * that starts with a link is an entry; the links of a nested list are its children.
     */
    fun outline(): List<OutlineEntry> {
        val entries = mutableListOf<OutlineEntry>()
        var node = document.firstChild

        while (node != null) {
            when (node) {
                is Heading -> if (node.level > 1) entries += OutlineEntry.Group(headingOf.getValue(node).text)
                is ListBlock -> entries += listLinks(node)
            }
            node = node.next
        }

        return entries
    }

    private fun listLinks(list: ListBlock): List<OutlineEntry.Entry> {
        val entries = mutableListOf<OutlineEntry.Entry>()
        var item = list.firstChild

        while (item != null) {
            val link = (item.firstChild as? Paragraph)?.firstChild as? Link
            val nested = generateSequence(item.firstChild) { it.next }.filterIsInstance<ListBlock>()
                .flatMap { listLinks(it) }
                .toList()
            if (link != null) {
                entries += OutlineEntry.Entry(plainText(link).trim(), link.destination, nested)
            } else {
                entries += nested
            }
            item = item.next
        }

        return entries
    }

    /**
     * HTML of the page with heading ids and with each link destination, including `href` and `src` of inline HTML,
     * replaced by [rewrite]. Changes the parsed document, so it is called once.
     */
    fun renderHtml(rewrite: (String) -> String): String {
        document.accept(object : AbstractVisitor() {
            override fun visit(link: Link) {
                link.destination = rewrite(link.destination)
                visitChildren(link)
            }

            override fun visit(image: Image) {
                image.destination = rewrite(image.destination)
                visitChildren(image)
            }

            override fun visit(htmlInline: HtmlInline) {
                htmlInline.literal = rewriteHtml(htmlInline.literal, rewrite)
            }

            override fun visit(htmlBlock: HtmlBlock) {
                htmlBlock.literal = rewriteHtml(htmlBlock.literal, rewrite)
            }
        })

        val renderer = HtmlRenderer.builder()
            .extensions(EXTENSIONS)
            .attributeProviderFactory {
                AttributeProvider { node, _, attributes ->
                    if (node is Heading) {
                        headingOf[node]?.let { attributes["id"] = it.id }
                    }
                }
            }
            .build()

        return renderer.render(document)
    }

    private val Node.line: Int
        get() = (sourceSpans.firstOrNull()?.lineIndex ?: 0) + 1

    private val Node.lines: IntRange
        get() {
            val spans = sourceSpans
            if (spans.isEmpty()) return IntRange.EMPTY

            return (spans.first().lineIndex + 1)..(spans.last().lineIndex + 1)
        }

    private fun htmlLinks(html: String, line: Int): List<PageLink> {
        return HTML_LINK.findAll(html).map { match ->
            val offset = html.substring(0, match.range.first).count { it == '\n' }
            PageLink(match.groupValues[2], line + offset, image = match.groupValues[1].equals("src", true))
        }.toList()
    }

    companion object {
        private val EXTENSIONS: List<Extension> = listOf(
            TablesExtension.create(),
            StrikethroughExtension.create(),
            TaskListItemsExtension.create(),
            AutolinkExtension.create(),
        )

        private val PARSER: Parser = Parser.builder()
            .extensions(EXTENSIONS)
            .includeSourceSpans(IncludeSourceSpans.BLOCKS_AND_INLINES)
            .build()

        /** `href` and `src` attributes of HTML in Markdown and of the interactive pages. */
        internal val HTML_LINK = Regex("""\b(href|src)\s*=\s*"([^"]*)"""", RegexOption.IGNORE_CASE)

        /** Replaces each `href` and `src` value of [html] by [rewrite]. */
        internal fun rewriteHtml(html: String, rewrite: (String) -> String): String {
            return HTML_LINK.replace(html) { match ->
                val (attribute, value) = match.destructured
                "$attribute=\"${rewrite(value)}\""
            }
        }

        /** Text of [node] as GitLab takes it for a heading id: text and code, without HTML tags. */
        internal fun plainText(node: Node): String {
            val text = StringBuilder()
            node.accept(object : AbstractVisitor() {
                override fun visit(textNode: Text) {
                    text.append(textNode.literal)
                }

                override fun visit(code: Code) {
                    text.append(code.literal)
                }

                override fun visit(fencedCodeBlock: FencedCodeBlock) {
                    text.append(fencedCodeBlock.literal)
                }

                override fun visit(indentedCodeBlock: IndentedCodeBlock) {
                    text.append(indentedCodeBlock.literal)
                }

                override fun visit(softLineBreak: SoftLineBreak) {
                    text.append(' ')
                }

                override fun visit(hardLineBreak: HardLineBreak) {
                    text.append(' ')
                }
            })

            return text.toString()
        }
    }
}
