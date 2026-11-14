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
 * The sidebar of the site: README, the tree of `documentation/index.md` with the index pages of the libraries expanded
 * in place, and the pages that no index mentions.
 *
 * The API of a Dokka module lives under its library: the module `name` belongs to the library of
 * `documentation/<name>/index.md`, and its tree `API -> package -> types` goes under the reference of the library
 * (a group or an entry named «Reference» or «Справочник»), or at the end of the library without one. The modules
 * without a library form the top-level group «API».
 */
internal class SiteNavigation(
    private val pages: Map<String, MarkdownPage>,
    private val siteFiles: Set<String>,
) {
    fun build(
        readme: String?,
        index: String?,
        apiModules: List<ApiModuleEntry> = emptyList(),
        apiPages: List<ApiPage> = emptyList(),
    ): List<NavigationItem> {
        val items = mutableListOf<NavigationItem>()

        readme?.let { pages[it] }?.let { items += NavigationItem(it.title, htmlPath(it.path), emptyList()) }
        index?.let { pages[it] }?.let { page ->
            items += NavigationItem(page.title, htmlPath(page.path), fromIndex(page, mutableSetOf(page.path)))
        }

        val documentationDir = index?.substringBeforeLast('/', "")?.let { if (it.isEmpty()) "" else "$it/" }.orEmpty()
        val orphans = mutableListOf<NavigationItem>()
        apiModules.forEach { module ->
            val api = apiTree(module, apiPages)
            val library = htmlPath("$documentationDir${module.name}/$INDEX")
            val placed = items.indices.any { position ->
                val updated = placeApi(items[position], library, api)
                if (updated != null) items[position] = updated
                updated != null
            }
            if (!placed) orphans += api.copy(title = module.name)
        }
        if (orphans.isNotEmpty()) {
            val root = apiPages.firstOrNull { it.type == ApiPageType.ROOT }?.path
            items += NavigationItem(API_TITLE, root, orphans)
        }

        val listed = items.flatMap { it.paths().toList() }.toSet()
        val others = pages.values
            .filter { htmlPath(it.path) !in listed }
            .sortedBy { it.path }
            .map { NavigationItem(it.path, htmlPath(it.path), emptyList()) }
        if (others.isNotEmpty()) {
            items += NavigationItem(OTHER_PAGES, null, others)
        }

        return items
    }

    /** [item] with [api] placed under the library page [library], or `null` if [item] does not contain it. */
    private fun placeApi(item: NavigationItem, library: String, api: NavigationItem): NavigationItem? {
        if (item.path == library) {
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

    private fun isReference(title: String): Boolean =
        REFERENCE_TITLES.any { title.trim().startsWith(it, ignoreCase = true) }

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
        return NavigationItem(API_TITLE, "$API_DIR/${module.directory}/index.html", packages)
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
                    groupTitle = entry.title
                }

                is OutlineEntry.Entry -> entry(page, entry, visited)?.let { (group ?: items) += it }
            }
        }
        closeGroup()

        return items
    }

    private fun entry(page: MarkdownPage, entry: OutlineEntry.Entry, visited: MutableSet<String>): NavigationItem? {
        val link = RelativeLink.resolve(page.path, entry.destination) ?: return null
        val nested = entry.children.mapNotNull { entry(page, it, visited) }
        val target = pages[link.path]

        return when {
            target != null && target.path.endsWith("/$INDEX") && visited.add(target.path) -> {
                NavigationItem(entry.title, htmlPath(target.path), fromIndex(target, visited) + nested)
            }

            target != null -> NavigationItem(entry.title, htmlPath(target.path), nested)
            link.path in siteFiles -> NavigationItem(entry.title, link.path, nested)
            else -> null
        }
    }

    companion object {
        const val API_DIR = "api"
        const val INDEX = "index.md"
        const val OTHER_PAGES = "Другие страницы"
        const val API_TITLE = "API"
        private val REFERENCE_TITLES = listOf("Reference", "Справочник")

        /** Path of the HTML page of the Markdown file [path]. */
        fun htmlPath(path: String): String = path.removeSuffix(".md") + ".html"
    }
}
