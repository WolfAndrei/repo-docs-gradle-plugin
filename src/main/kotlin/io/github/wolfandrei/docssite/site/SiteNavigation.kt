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
import io.github.wolfandrei.docssite.markdown.OutlineEntry
import io.github.wolfandrei.docssite.markdown.RelativeLink
import java.nio.file.FileSystems
import java.nio.file.Paths

/** Where the title of a sidebar entry comes from: the heading of the page or the text of the link. */
internal enum class TitleSource { HEADING, LINK }

/**
 * Conventions of the documentation layout that the sidebar follows, each with a default that suits the common
 * layouts; see the `navigation` and `apiReference` blocks of the extension.
 *
 * @property groupTitles titles of the sidebar groups by heading, over the defaults of the language.
 * @property titleSource where the titles of the entries come from.
 * @property hubPatterns globs of the pages that are expanded as tables of contents; a pattern without `/` matches
 * the file name, other patterns the path relative to the repository root.
 * @property detectHubs whether a page that mostly lists other pages is expanded too.
 * @property referenceHeadings headings of the group or the entry of a library that gets its API, case-insensitive
 * prefixes; the title of the group `reference` in the language counts too.
 * @property libraries directories of the libraries under the documentation directory by Dokka module name; a module
 * without an entry has a directory of its own name.
 */
internal data class NavigationOptions(
    val groupTitles: Map<String, String> = emptyMap(),
    val titleSource: TitleSource = TitleSource.HEADING,
    val hubPatterns: List<String> = DEFAULT_HUB_PATTERNS,
    val detectHubs: Boolean = true,
    val referenceHeadings: List<String> = DEFAULT_REFERENCE_HEADINGS,
    val libraries: Map<String, String> = emptyMap(),
) {
    private val hubMatchers = hubPatterns.map { pattern ->
        val matcher = FileSystems.getDefault().getPathMatcher("glob:$pattern")
        val byName = '/' !in pattern
        { path: String -> matcher.matches(Paths.get(if (byName) path.substringAfterLast('/') else path)) }
    }

    /** Whether the page [path] is a table of contents by its name, see [hubPatterns]. */
    fun isHubName(path: String): Boolean = hubMatchers.any { it(path) }

    fun libraryOf(module: String): String = libraries[module] ?: module

    /** The Dokka module of the library directory [directory]. */
    fun moduleOf(directory: String): String = libraries.entries.firstOrNull { it.value == directory }?.key ?: directory

    companion object {
        val DEFAULT_HUB_PATTERNS = listOf("index.md", "README.md", "*-index.md", "*_index.md")
        val DEFAULT_REFERENCE_HEADINGS = listOf("Reference", "Справочник")
    }
}

/** Item of the sidebar; [path] is relative to the site root, absent for a group without its own page. */
internal data class NavigationItem(
    val title: String,
    val path: String?,
    val children: List<NavigationItem>,
    val kind: SymbolKind? = null,
) {
    fun contains(target: String): Boolean = path == target || children.any { it.contains(target) }

    fun paths(): Sequence<String> = sequenceOf(path).filterNotNull() + children.asSequence().flatMap { it.paths() }
}

/**
 * The sidebar of the site: README, the tree of the table of contents `documentation/index.md`, and the pages that no
 * table of contents mentions, in «Other pages».
 *
 * A heading of a table of contents opens a group, a list item with a link is an entry titled by the heading of its
 * page. A page that is itself a table of contents (a hub, see [isHub]) is expanded in place, each page is listed once.
 *
 * The API of a Dokka module lives under its library, the directory `documentation/<module>/` (see
 * [NavigationOptions.libraries]): its tree `API -> package -> types` goes under the reference of the library (a group
 * or an entry named by [NavigationOptions.referenceHeadings]), or at the end of the library without one. The modules
 * without a library form the top-level group «API». The interactive pages of a library that no table of contents
 * mentions form its group «Interactive pages».
 */
internal class SiteNavigation(
    private val pages: Map<String, MarkdownPage>,
    private val siteFiles: Set<String>,
    private val text: UiText = UiText.of("en"),
    private val options: NavigationOptions = NavigationOptions(),
) {
    /** Titles of the sidebar groups by the lowercased heading: the defaults of the language with the custom ones. */
    private val groupTitles: Map<String, String> =
        text.groupTitles() + options.groupTitles.mapKeys { it.key.trim().lowercase() }


    /** Pages that already have an entry: a hub lists only the pages that are not listed yet. */
    private val listed = mutableSetOf<String>()

    /** Titles of the interactive pages by path. */
    private var interactiveTitles: Map<String, String> = emptyMap()

    private val referenceTitle: String = groupTitles["reference"] ?: "Reference"

    /**
     * Title of the sidebar group of the heading [heading] of a table of contents: a heading such as `How-to` or
     * `Reference` (any case) gets its title from the group titles, any other heading stays as written.
     */
    fun groupTitle(heading: String): String = groupTitles[heading.trim().lowercase()] ?: heading

    fun build(
        readme: String?,
        index: String?,
        apiModules: List<ApiModuleEntry> = emptyList(),
        apiPages: List<ApiPage> = emptyList(),
        interactivePages: List<InteractivePage> = emptyList(),
    ): List<NavigationItem> {
        val items = mutableListOf<NavigationItem>()
        interactiveTitles = interactivePages.associate { it.path to it.title }

        readme?.let { pages[it] }?.let { items += NavigationItem(it.title, htmlPath(it.path), emptyList()) }
        readme?.let { listed += it }
        index?.let { pages[it] }?.let { page ->
            listed += page.path
            items += NavigationItem(page.title, htmlPath(page.path), fromIndex(page, mutableSetOf(page.path)))
        }

        val documentationDir = index?.substringBeforeLast('/', "")?.let { if (it.isEmpty()) "" else "$it/" }.orEmpty()
        val orphans = mutableListOf<NavigationItem>()
        apiModules.forEach { module ->
            val api = apiTree(module, apiPages)
            val library = "$documentationDir${options.libraryOf(module.name)}"
            val placed = items.indices.any { position ->
                val updated = placeApi(items[position], library, api)
                if (updated != null) items[position] = updated
                updated != null
            }
            if (!placed) orphans += api.copy(title = module.name)
        }
        if (orphans.isNotEmpty()) {
            val root = apiPages.firstOrNull { it.type == ApiPageType.ROOT }?.path
            items += NavigationItem(text["api"], root, orphans)
        }

        val interactiveTitle = groupTitle(INTERACTIVE)
        val listedBefore = items.flatMap { it.paths().toList() }.toSet()
        val unplaced = mutableListOf<NavigationItem>()
        interactivePages.filter { it.path !in listedBefore }.sortedBy { it.title }.groupBy { page ->
            val library = page.path.removePrefix(documentationDir).substringBefore('/', "")
            if (page.path.startsWith(documentationDir) && library.isNotEmpty()) "$documentationDir$library" else ""
        }.forEach { (library, group) ->
            val entries = group.map { NavigationItem(it.title, it.path, emptyList()) }
            val placed = library.isNotEmpty() && items.indices.any { position ->
                val updated = placeGroup(items[position], library, interactiveTitle, entries)
                if (updated != null) items[position] = updated
                updated != null
            }
            if (!placed) unplaced += entries
        }

        val listed = items.flatMap { it.paths().toList() }.toSet()
        val others = pages.values
            .filter { htmlPath(it.path) !in listed }
            .sortedBy { it.path }
            .map { NavigationItem(it.title, htmlPath(it.path), emptyList()) } + unplaced
        if (others.isNotEmpty()) {
            items += NavigationItem(text["otherPages"], null, others)
        }

        return items
    }

    /**
     * Whether [item] is the page of the library directory [library]: the first entry of the sidebar, in its order,
     * whose page lies directly in that directory, such as `<library>/index.md` or `<library>/<name>-index.md`.
     */
    private fun isLibraryPage(item: NavigationItem, library: String): Boolean =
        item.path != null && item.path.substringBeforeLast('/', "") == library

    /** [item] with [api] placed under the page of the library [library], or `null` if [item] does not contain it. */
    private fun placeApi(item: NavigationItem, library: String, api: NavigationItem): NavigationItem? {
        if (isLibraryPage(item, library)) {
            val children = item.children.toMutableList()
            val group = children.indexOfFirst { it.path == null && isReference(it.title) }
            val entry = children.indexOfFirst { it.path != null && isReference(it.title) }
            when {
                group >= 0 -> {
                    val reference = children[group]
                    val single = reference.children.singleOrNull()
                    children[group] = if (single != null && single.path != null) {
                        reference.copy(children = listOf(single.copy(children = single.children + api)))
                    } else {
                        reference.copy(children = reference.children + api)
                    }
                }

                entry >= 0 -> children[entry] = children[entry].let { it.copy(children = it.children + api) }
                else -> children += api
            }
            return item.copy(children = children)
        }
        item.children.forEachIndexed { position, child ->
            placeApi(child, library, api)?.let { updated ->
                return item.copy(children = item.children.toMutableList().apply { set(position, updated) })
            }
        }
        return null
    }

    /**
     * [item] with [entries] in its group [title] under the library page [library]: an existing group of that title
     * gets them at its end, otherwise a new group goes at the end of the library; `null` if [item] does not contain
     * the library.
     */
    private fun placeGroup(
        item: NavigationItem,
        library: String,
        title: String,
        entries: List<NavigationItem>,
    ): NavigationItem? {
        if (isLibraryPage(item, library)) {
            val children = item.children.toMutableList()
            val group = children.indexOfFirst { it.path == null && it.title == title }
            if (group >= 0) {
                children[group] = children[group].let { it.copy(children = it.children + entries) }
            } else {
                children += NavigationItem(title, null, entries)
            }
            return item.copy(children = children)
        }
        item.children.forEachIndexed { position, child ->
            placeGroup(child, library, title, entries)?.let { updated ->
                return item.copy(children = item.children.toMutableList().apply { set(position, updated) })
            }
        }
        return null
    }

    private fun isReference(title: String): Boolean =
        title.trim().let { trimmed ->
            trimmed.equals(referenceTitle, ignoreCase = true) ||
                options.referenceHeadings.any { trimmed.startsWith(it, ignoreCase = true) }
        }

    /** `API -> packages -> types` of [module]; nested types go under their outer type. */
    private fun apiTree(module: ApiModuleEntry, apiPages: List<ApiPage>): NavigationItem {
        val own = apiPages.filter { it.module == module.name }
        val types = own.filter { it.type == ApiPageType.CLASSLIKE }

        fun children(directory: String): List<NavigationItem> = types
            .filter { it.path.removeSuffix("/index.html").substringBeforeLast('/') == directory }
            .sortedBy { it.title.lowercase() }
            .map { type ->
                NavigationItem(type.title, type.path, children(type.path.removeSuffix("/index.html")), type.kind)
            }

        val packages = own.filter { it.type == ApiPageType.PACKAGE }.sortedBy { it.title }.map { pkg ->
            NavigationItem(pkg.title, pkg.path, children(pkg.path.removeSuffix("/index.html")), SymbolKind.PACKAGE)
        }
        return NavigationItem(text["api"], "$API_DIR/${module.directory}/index.html", packages)
    }

    private fun fromIndex(page: MarkdownPage, visited: MutableSet<String>): List<NavigationItem> {
        val items = mutableListOf<NavigationItem>()
        var group: MutableList<NavigationItem>? = null
        var groupTitle: String? = null

        fun closeGroup() {
            val children = group ?: return
            if (children.isNotEmpty()) items += NavigationItem(groupTitle.orEmpty(), null, children)
            group = null
        }

        page.outline().forEach { entry ->
            when (entry) {
                is OutlineEntry.Group -> {
                    closeGroup()
                    group = mutableListOf()
                    groupTitle = groupTitle(entry.title)
                }

                is OutlineEntry.Entry -> entry(page, entry, visited)?.let { (group ?: items) += it }
            }
        }
        closeGroup()

        return items
    }

    private fun entry(page: MarkdownPage, entry: OutlineEntry.Entry, visited: MutableSet<String>): NavigationItem? {
        val link = RelativeLink.resolve(page.path, entry.destination) ?: return null
        val target = pages[link.path]
        if (target != null && target.path in listed && target.path !in visited) {
            // Listed already: only its nested entries remain.
            return null
        }
        val hub = target != null && target.path !in visited && isHub(target)
        target?.let { listed += it.path }
        val nested = entry.children.mapNotNull { entry(page, it, visited) }

        return when {
            target != null && hub && visited.add(target.path) -> {
                NavigationItem(titleOf(target, entry.title), htmlPath(target.path), fromIndex(target, visited) + nested)
            }

            target != null -> NavigationItem(titleOf(target, entry.title), htmlPath(target.path), nested)
            link.path in siteFiles -> {
                // A link written as the file name, such as `pkce.html`, shows the title of the page.
                val pageTitle = interactiveTitles[link.path]?.takeIf { entry.title.endsWith(".html", ignoreCase = true) }
                NavigationItem(pageTitle ?: entry.title, link.path, nested)
            }
            else -> null
        }
    }

    /**
     * Whether the page [target] of a table of contents is expanded in place as a table of contents of its own: its
     * name matches [NavigationOptions.hubPatterns], or, with [NavigationOptions.detectHubs], it links to at least
     * [HUB_MIN_PAGES] pages that the sidebar does not list yet and has at most [HUB_MAX_WORDS] words of its own text.
     */
    private fun isHub(target: MarkdownPage): Boolean {
        if (options.isHubName(target.path)) return true
        if (!options.detectHubs) return false

        val fresh = target.outline().filterIsInstance<OutlineEntry.Entry>()
            .flatMap { listOf(it) + it.children }
            .mapNotNull { RelativeLink.resolve(target.path, it.destination)?.path }
            .filter { it in pages && it != target.path && it !in listed }
            .distinct()
        return fresh.size >= HUB_MIN_PAGES && target.proseWords() <= HUB_MAX_WORDS
    }

    /**
     * Title of the entry of [target] written as [linkText]: by default the heading of the page, the link text for a
     * page without headings, and the file name for a link text such as «here»; with [TitleSource.LINK] the link text
     * first.
     */
    private fun titleOf(target: MarkdownPage, linkText: String): String {
        val link = linkText.trim().takeUnless { it.isEmpty() || it.lowercase() in GENERIC_LINK_TEXTS }
        return when (options.titleSource) {
            TitleSource.HEADING -> target.headingTitle ?: link ?: target.title
            TitleSource.LINK -> link ?: target.title
        }
    }

    companion object {
        const val API_DIR = "api"
        const val INDEX = "index.md"
        private const val INTERACTIVE = "interactive"
        const val HUB_MIN_PAGES = 2
        const val HUB_MAX_WORDS = 60

        /** Link texts that say nothing about the page. */
        val GENERIC_LINK_TEXTS = setOf(
            "here", "click here", "this", "link", "more", "read more", "details",
            "здесь", "тут", "сюда", "ссылка", "по ссылке", "подробнее", "нажми здесь", "далее",
        )

        /** Path of the HTML page of the Markdown file [path]. */
        fun htmlPath(path: String): String = path.removeSuffix(".md") + ".html"
    }
}
