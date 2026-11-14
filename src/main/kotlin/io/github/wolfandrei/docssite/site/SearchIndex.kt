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
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Entry of the search index: one section of a page. Short names keep the index small. */
@Serializable
internal data class SearchEntry(
    @SerialName("t") val title: String,
    @SerialName("u") val url: String,
    @SerialName("x") val text: String,
)

/** The search index of the site: an entry per section of each page, in the order of the pages. */
internal object SearchIndex {
    private const val MAX_TEXT = 4000
    private val WHITESPACE = Regex("\\s+")
    private val json = Json { encodeDefaults = true }

    fun entries(pages: Collection<MarkdownPage>): List<SearchEntry> {
        return pages.sortedBy { it.path }.flatMap { page ->
            val url = SiteNavigation.htmlPath(page.path)
            page.sections().map { section ->
                val heading = section.heading
                val title = if (heading == null || heading.level == 1) page.title else "${page.title} - ${heading.text}"
                val anchor = if (heading == null || heading.level == 1) "" else "#${heading.id}"
                SearchEntry(title, url + anchor, section.text.replace(WHITESPACE, " ").take(MAX_TEXT))
            }
        }
    }

    /** An entry per declaration of the API reference: its qualified name, its kind and its documentation. */
    fun apiEntries(pages: Collection<ApiPage>, text: UiText): List<SearchEntry> = pages
        .filter { it.type == ApiPageType.CLASSLIKE || it.type == ApiPageType.MEMBER }
        .sortedBy { it.path }
        .map { page ->
            val kind = page.kind?.let(text::kind)?.let { "$it, ${page.module}" } ?: page.module.orEmpty()
            SearchEntry("${page.qualifiedTitle} ($kind)", page.path, page.text.replace(WHITESPACE, " ").take(MAX_TEXT))
        }

    /** An entry per interactive page: its title and its visible text. */
    fun interactiveEntries(pages: Collection<InteractivePage>): List<SearchEntry> = pages
        .sortedBy { it.path }
        .map { SearchEntry(it.title, it.path, it.text.take(MAX_TEXT)) }

    fun toJson(entries: List<SearchEntry>): String = json.encodeToString(entries)

    /** The index as a script, so that the page loads it with `<script>` also from the file system. */
    fun toScript(entries: List<SearchEntry>): String = "window.DOCS_SEARCH_INDEX = ${toJson(entries)};\n"
}
