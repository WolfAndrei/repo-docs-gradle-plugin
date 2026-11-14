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

import java.io.File
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/** Kind of a Kotlin declaration with the letter and the style of its badge; [UiText.kind] gives its label. */
internal enum class SymbolKind(val badge: String, val css: String) {
    CLASS("C", "class"),
    DATA_CLASS("C", "class"),
    OBJECT("O", "object"),
    INTERFACE("I", "interface"),
    ENUM("E", "enum"),
    ENUM_ENTRY("E", "enum"),
    ANNOTATION("A", "class"),
    TYPEALIAS("T", "typealias"),
    FUNCTION("F", "function"),
    CONSTRUCTOR("F", "function"),
    PROPERTY("P", "property"),
    PACKAGE("{}", "package"),
    MODULE("M", "module"),
    ;

    val isType: Boolean get() = this in TYPES

    companion object {
        private val TYPES = setOf(CLASS, DATA_CLASS, OBJECT, INTERFACE, ENUM, ENUM_ENTRY, ANNOTATION, TYPEALIAS)

        /** Kind by the keywords of a declaration, such as `abstract suspend fun` or `data class`. */
        fun of(keywords: List<String>, classlike: Boolean): SymbolKind = when {
            "interface" in keywords -> INTERFACE
            "fun" in keywords -> FUNCTION
            "constructor" in keywords -> CONSTRUCTOR
            "val" in keywords || "var" in keywords -> PROPERTY
            "typealias" in keywords -> TYPEALIAS
            "enum" in keywords -> ENUM
            "annotation" in keywords -> ANNOTATION
            "object" in keywords -> OBJECT
            "data" in keywords && "class" in keywords -> DATA_CLASS
            "class" in keywords -> CLASS
            classlike -> ENUM_ENTRY
            else -> PROPERTY
        }
    }
}

/** Role of a Dokka page. */
internal enum class ApiPageType { ROOT, MODULE, PACKAGE, CLASSLIKE, MEMBER }

/**
 * A Dokka page rewritten for the site.
 *
 * @property path path of the page relative to the site root, `api/<module>/...`.
 * @property module name of the Dokka module, `null` for the page of all modules.
 * @property title short name: a type, a member or a package.
 * @property qualifiedTitle name for the search: `Type.member` for a member.
 * @property content HTML of the page body in the site layout.
 * @property summary the first paragraph of the documentation as text.
 * @property text the documentation of the page as text, for the search.
 */
internal class ApiPage(
    val path: String,
    val module: String?,
    val type: ApiPageType,
    val kind: SymbolKind?,
    val title: String,
    val qualifiedTitle: String,
    val content: String,
    val summary: String,
    val text: String,
)

/**
 * The aggregated Dokka HTML in the site layout. Each page keeps its path under `api/`, so the links between the Dokka
 * pages and their anchors stay valid; from a page only its main content is taken (`.main-content`), the navigation,
 * header, footer and scripts of Dokka are dropped. The content is rebuilt as a page of a symbol: the kind, the name,
 * the abstract and the platforms, the declaration for each source set, the overview, the KDoc sections and the members
 * grouped by kind.
 */
internal class DokkaReference(
    private val directory: File,
    private val modules: List<ApiModuleEntry>,
    private val text: UiText,
) {

    val pages: List<ApiPage> by lazy { read() }

    private fun read(): List<ApiPage> {
        val moduleDirs = modules.associateBy { it.directory }
        return directory.walkTopDown()
            .filter { it.isFile && it.extension == "html" && it.name != NAVIGATION }
            .sortedBy { it.invariantSeparatorsPath }
            .mapNotNull { file ->
                val relative = file.relativeTo(directory).invariantSeparatorsPath
                val module = moduleDirs.entries.firstOrNull { relative.startsWith(it.key + "/") }?.value?.name
                if (module == null && relative != "index.html") return@mapNotNull null
                convert(relative, module, Jsoup.parse(file, Charsets.UTF_8.name()))
            }
            .toList()
    }

    private fun convert(relative: String, module: String?, document: Document): ApiPage? {
        val main = document.selectFirst(".main-content") ?: return null
        clean(main)

        val pageType = when {
            module == null -> ApiPageType.ROOT
            main.attr("data-page-type") == "package" -> ApiPageType.PACKAGE
            main.attr("data-page-type") == "classlike" -> ApiPageType.CLASSLIKE
            main.attr("data-page-type") == "member" -> ApiPageType.MEMBER
            else -> ApiPageType.MODULE
        }
        val crumbs = main.select(".breadcrumbs > a, .breadcrumbs > span.current")
        val title = when (pageType) {
            ApiPageType.PACKAGE -> crumbs.lastOrNull()?.text() ?: main.selectFirst("h1")?.text().orEmpty()
            else -> main.selectFirst("h1")?.text().orEmpty()
        }
        val declarations = declarations(main)
        val kind = when (pageType) {
            ApiPageType.ROOT -> null
            ApiPageType.MODULE -> SymbolKind.MODULE
            ApiPageType.PACKAGE -> SymbolKind.PACKAGE
            else -> SymbolKind.of(declarations.firstOrNull()?.let(::keywords).orEmpty(), pageType == ApiPageType.CLASSLIKE)
        }
        val description = description(main)
        val summary = description.firstOrNull { it.tagName() == "p" }
        val platforms = (declarations.map { platformOf(it) } + rowPlatforms(main)).distinct()
            .takeIf { pageType == ApiPageType.CLASSLIKE || pageType == ApiPageType.MEMBER }
            ?: declarations.map { platformOf(it) }.distinct()

        val owner = crumbs.dropLast(1).lastOrNull()?.text()
        val qualified = if (pageType == ApiPageType.MEMBER && owner != null && crumbs.size > 3) "$owner.$title" else title

        val html = StringBuilder()
        html.append("<article class=\"symbol-page\">")
        breadcrumbs(main)?.let(html::append)
        html.append("<header class=\"symbol-header\">")
        kind?.let { html.append("<p class=\"symbol-kind\">").append(escape(text.kind(it))).append("</p>") }
        html.append("<h1>").append(escape(title.ifEmpty { "API" })).append("</h1>")
        val (abstract, rest) = summary?.let { splitFirstSentence(it.html()) } ?: (null to null)
        abstract?.let { html.append("<div class=\"symbol-abstract\"><p>").append(it).append("</p></div>") }
        if (platforms.isNotEmpty()) html.append(platformList(platforms, "symbol-platforms"))
        html.append("</header>")

        if (declarations.isNotEmpty()) {
            html.append("<section class=\"symbol-section\"><h2 id=\"declaration\">${escape(text["declaration"])}</h2>")
            declarations.forEach { content ->
                html.append("<div class=\"declaration\">")
                if (declarations.size > 1 || platforms.size > 1) {
                    html.append(platformList(listOf(platformOf(content)), "declaration-platform"))
                }
                content.select(".symbol").forEach { symbol ->
                    html.append("<pre class=\"signature\"><code>").append(symbol.html().trim()).append("</code></pre>")
                }
                html.append("</div>")
            }
            html.append("</section>")
        }

        val overview = description.filter { it !== summary && !it.hasClass("kdoc-tag") }
        if (overview.isNotEmpty() || rest != null) {
            html.append("<section class=\"symbol-section\"><h2 id=\"overview\">${escape(text["overview"])}</h2>")
            rest?.let { html.append("<p class=\"paragraph\">").append(it).append("</p>") }
            overview.forEach { html.append(element(it)) }
            html.append("</section>")
        }
        description.filter { it.hasClass("kdoc-tag") }.forEach { tag ->
            val heading = tag.selectFirst("h4")?.text().orEmpty()
            tag.selectFirst("h4")?.remove()
            val label = text.tag(heading)
            html.append("<section class=\"symbol-section\"><h2>").append(escape(label)).append("</h2>")
            tag.children().forEach { html.append(element(it)) }
            html.append("</section>")
        }

        val topics = topics(main)
        if (topics.isNotEmpty()) {
            val heading = if (pageType == ApiPageType.CLASSLIKE || pageType == ApiPageType.MEMBER) text["topics"] else null
            html.append("<section class=\"symbol-section topics\">")
            heading?.let { html.append("<h2 id=\"topics\">").append(escape(it)).append("</h2>") }
            topics.forEach { html.append(it) }
            html.append("</section>")
        }
        html.append("</article>")

        val text = (listOfNotNull(summary) + overview).joinToString(" ") { it.text() }
        return ApiPage(
            path = "${SiteNavigation.API_DIR}/$relative",
            module = module,
            type = pageType,
            kind = kind,
            title = title,
            qualifiedTitle = qualified,
            content = cleanErrors(html.toString()),
            summary = cleanErrors(summary?.text().orEmpty()),
            text = cleanErrors(text),
        )
    }

    private fun platformList(platforms: List<String>, css: String): String = platforms.joinToString(
        prefix = "<ul class=\"platforms $css\" aria-label=\"${escape(text["platforms"])}\">",
        separator = "",
        postfix = "</ul>",
    ) { "<li class=\"platform platform-${platformClass(it)}\">${escape(it)}</li>" }

    /** Drops the parts of the Dokka interface: copy buttons, platform tabs, scripts. */
    private fun clean(main: Element) {
        main.select(
            ".anchor-wrapper, .copy-popup-wrapper, .platform-bookmarks-row, script, .tabs-section, " +
                ".source-link, .clearfix > .floating-right"
        ).remove()
        main.select("wbr").remove()
    }

    /** The declarations of the page: one per source set, from the cover or, on a member page, after it. */
    private fun declarations(main: Element): List<Element> =
        main.select(
            ".main-content > .cover > .platform-hinted > .sourceset-dependent-content, " +
                ".main-content > .platform-hinted > .sourceset-dependent-content, " +
                ".main-content > .divergent-group .sourceset-dependent-content"
        ).filter { it.selectFirst(".symbol") != null }

    /** The documentation of the page: the elements after the declaration in the first source set that has some. */
    private fun description(main: Element): List<Element> {
        val seen = mutableSetOf<String>()
        return declarations(main).flatMap { content ->
            content.children().filter { !it.hasClass("symbol") }
        }.filter { seen.add(it.text()) }
    }

    private fun keywords(content: Element): List<String> {
        val symbol = content.selectFirst(".symbol") ?: return emptyList()
        val keywords = mutableListOf<String>()
        for (child in symbol.children()) {
            if (child.hasClass("token") && child.hasClass("keyword")) {
                keywords += child.text().trim()
            } else if (!child.hasClass("token") || child.hasClass("function")) {
                break
            }
        }
        return keywords
    }

    private fun platformOf(content: Element): String = platformName(content.attr("data-togglable"))

    private fun rowPlatforms(main: Element): List<String> =
        main.select(".main-content > .cover [data-filterable-set]").flatMap { platforms(it) }

    private fun platforms(element: Element): List<String> =
        element.attr("data-filterable-set").split(',').filter { it.isNotBlank() }.map(::platformName)

    private fun breadcrumbs(main: Element): String? {
        val crumbs = main.selectFirst(".breadcrumbs") ?: return null
        val items = crumbs.children().filter { it.tagName() == "a" || it.hasClass("current") }
        if (items.size < 2) return null
        return items.dropLast(1).joinToString(
            prefix = "<nav class=\"breadcrumbs\" aria-label=\"${escape(text["breadcrumbs"])}\">",
            separator = "<span class=\"breadcrumb-separator\">/</span>",
            postfix = "</nav>",
        ) { "<a href=\"${escape(it.attr("href"))}\">${escape(it.text())}</a>" }
    }

    /** The member tables of the page, as lists grouped by the kind of their entries. */
    private fun topics(main: Element): List<String> {
        return main.select("h2").filter { it.parents().none { parent -> parent.hasClass("cover") } }.mapNotNull { h2 ->
            val table = h2.nextElementSibling()?.takeIf { it.hasClass("table") } ?: return@mapNotNull null
            val label = text.section(h2.text())
            val rows = table.select("> .table-row").joinToString("") { row(it, h2.text()) }
            "<h3>${escape(label)}</h3><div class=\"topic-list\">$rows</div>"
        }
    }

    private fun row(row: Element, section: String): String {
        val anchor = row.previousElementSibling()?.takeIf { it.tagName() == "a" && it.hasAttr("id") }?.id()
        val link = row.selectFirst(".main-subrow a")
        val contents = row.select(".title .sourceset-dependent-content")
        val typeSection = section == "Types" || section == "Inheritors" || section == "Entries"
        val classlike = section == "Types" || section == "Inheritors"
        val kind = when (section) {
            "Packages" -> SymbolKind.PACKAGE
            "Entries" -> SymbolKind.ENUM_ENTRY
            else -> SymbolKind.of(contents.firstOrNull()?.let(::keywords).orEmpty(), classlike)
        }
        val platforms = platforms(row).distinct()
        val html = StringBuilder("<div class=\"topic\"")
        anchor?.let { html.append(" id=\"").append(escape(it)).append("\"") }
        html.append(">")
        html.append(badge(kind, text.kind(kind)))
        html.append("<div class=\"topic-body\">")

        val seen = mutableSetOf<String>()
        val signatures = contents.mapNotNull { content ->
            val symbol = content.selectFirst(".symbol") ?: return@mapNotNull null
            val code = if (typeSection) shortSignature(content, link) else symbol.html().trim()
            code.takeIf { seen.add(Jsoup.parse(it).text()) }
        }
        if (signatures.isEmpty()) {
            val name = link?.let { "<a href=\"${escape(it.attr("href"))}\">${escape(it.text())}</a>" }
                ?: escape(row.selectFirst(".main-subrow")?.text().orEmpty())
            html.append("<div class=\"topic-signature\"><code>").append(name).append("</code></div>")
        } else {
            signatures.forEach { html.append("<div class=\"topic-signature\"><code>").append(it).append("</code></div>") }
        }

        val briefs = contents.mapNotNull { content -> content.selectFirst(".brief")?.html()?.trim() }
            .ifEmpty { row.select(".main-subrow + div p, .keyValue > div:nth-child(2) > p").map { it.outerHtml() } }
            .distinct()
        briefs.firstOrNull()?.let { html.append("<div class=\"topic-abstract\">").append(it).append("</div>") }
        if (platforms.isNotEmpty()) html.append(platformList(platforms, "topic-platforms"))
        html.append("</div></div>")
        return html.toString()
    }

    /** `keywords Name` of a type: the full constructor of a data class is too long for a list. */
    private fun shortSignature(content: Element, link: Element?): String {
        val words = keywords(content).joinToString("") { "<span class=\"token keyword\">${escape(it)} </span>" }
        val name = link?.let { "<a href=\"${escape(it.attr("href"))}\">${escape(it.text())}</a>" }
            ?: content.selectFirst(".symbol a")?.outerHtml().orEmpty()
        return words + name
    }

    /** An element of the documentation; the parameter tables of KDoc become lists of definitions. */
    private fun element(element: Element): String {
        if (element.hasClass("table")) {
            val items = element.select("> .table-row").joinToString("") { row ->
                val cells = row.select(".main-subrow > div")
                val name = cells.firstOrNull()?.text().orEmpty()
                val body = cells.drop(1).joinToString("") { it.html() }
                "<dt><code>${escape(name)}</code></dt><dd>$body</dd>"
            }
            return "<dl class=\"param-list\">$items</dl>"
        }
        if (element.hasClass("sample-container")) return sample(element).outerHtml()
        element.select("div.sample-container").forEach { it.replaceWith(sample(it)) }
        return element.outerHtml()
    }

    private fun sample(container: Element): Element {
        val code = container.selectFirst("code")?.wholeText().orEmpty()
        return Element("pre").appendChild(Element("code").addClass("language-kotlin").appendText(code))
    }

    companion object {
        private const val NAVIGATION = "navigation.html"



        /** Analysis errors of Dokka in the declarations, such as a supertype of another source set it did not see. */
        private val ANALYSIS_ERROR = Regex("(?:&lt;ERROR CLASS&gt;|<ERROR CLASS>)?\\s*ERROR CLASS: Symbol not found for ")

        fun cleanErrors(text: String): String = text.replace(ANALYSIS_ERROR, "")

        /** End of a sentence outside of tags and code: `. ` before a capital letter. */
        private val SENTENCE_END = Regex("(?<=[.!?])\\s+(?=[A-ZА-ЯЁ<])")

        /**
         * The first sentence of the paragraph [html] as the abstract and the rest, or `null` for a paragraph of one
         * sentence. A split inside a tag or an inline code is not taken.
         */
        fun splitFirstSentence(html: String): Pair<String, String?> {
            for (match in SENTENCE_END.findAll(html)) {
                val before = html.substring(0, match.range.first)
                val openTags = before.count { it == '<' } - before.count { it == '>' }
                val openCode = Regex("<code\\b").findAll(before).count() - before.split("</code>").size + 1
                if (openTags == 0 && openCode == 0) {
                    return before to html.substring(match.range.last + 1).takeIf { it.isNotBlank() }
                }
            }
            return html to null
        }

        /** Display name of a Dokka source set: `:lib/androidMain` -> `android`. */
        fun platformName(sourceSet: String): String =
            sourceSet.substringAfterLast('/').removeSuffix("Main").ifEmpty { sourceSet }

        fun badge(kind: SymbolKind, label: String): String =
            "<span class=\"kind-badge kind-${kind.css}\" title=\"${escape(label)}\">${escape(kind.badge)}</span>"

        private fun platformClass(name: String): String = when (name) {
            "common" -> "common"
            "android", "jvm", "desktopJvm", "main" -> "jvm"
            "js", "wasm", "wasmJs" -> "js"
            else -> "native"
        }

        private fun escape(text: String): String = SiteTemplate.escape(text)
    }
}
