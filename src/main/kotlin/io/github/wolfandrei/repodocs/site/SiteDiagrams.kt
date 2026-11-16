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

import io.github.wolfandrei.repodocs.markdown.RelativeLink
import java.io.File

/**
 * The PlantUML diagrams of the site: an image `<dir>/<name>.svg` next to `<dir>/<name>.puml` is a diagram, drawn at
 * build time. Under such an image the page gets the source of the diagram with the list of its includes.
 *
 * @property staticFiles files of the documentation by their path; the diagram sources and includes are among them.
 */
internal class SiteDiagrams(
    private val staticFiles: Map<String, File>,
    private val text: UiText = UiText.of("en"),
) {
    /** Path of the `.puml` that the image [svgPath] is rendered from, or `null` for another image. */
    fun sourceOf(svgPath: String): String? {
        if (!svgPath.endsWith(".svg", ignoreCase = true)) return null
        return (svgPath.dropLast(4) + ".puml").takeIf { it in staticFiles }
    }

    /** Whether the committed file [path] is the image of a diagram: the site uses the rendered one instead. */
    fun isDiagramImage(path: String): Boolean = sourceOf(path) != null

    /** [html] of the page [pagePath] with the source block after each diagram. */
    fun decorate(pagePath: String, html: String): String {
        return PARAGRAPH.replace(html) { paragraph ->
            val extras = IMAGE.findAll(paragraph.value).mapNotNull { image ->
                val src = image.groupValues[1]
                val path = RelativeLink.resolve(pagePath, src)?.path ?: return@mapNotNull null
                sourceOf(path)?.let { extras(it) }
            }.joinToString("")
            paragraph.value + extras
        }
    }

    private fun extras(source: String): String {
        val html = StringBuilder()
        val name = source.substringAfterLast('/')
        html.append(
            "<details class=\"diagram-source\"><summary>${SiteTemplate.escape(text["diagramSource"])}: <code>${SiteTemplate.escape(name)}</code></summary>"
        )
        val includes = includes(source)
        if (includes.isNotEmpty()) {
            html.append("<p>${SiteTemplate.escape(text["diagramIncludes"])}</p><ul>")
            includes.forEach { html.append("<li><code>${SiteTemplate.escape(it)}</code></li>") }
            html.append("</ul>")
        }
        html.append("<pre><code class=\"language-plantuml\">")
            .append(SiteTemplate.escape(staticFiles.getValue(source).readText()))
            .append("</code></pre></details>")

        return html.toString()
    }

    /**
     * Files that [source] includes, directly and through other includes: repository paths for local files, `<lib/file>`
     * for the standard library.
     */
    fun includes(source: String): List<String> {
        val found = linkedSetOf<String>()

        fun visit(path: String) {
            val text = staticFiles[path]?.readText() ?: return
            text.lines().mapNotNull { INCLUDE.matchEntire(it.trim())?.groupValues?.get(2)?.trim() }.forEach { target ->
                if (target.startsWith("<")) {
                    found += target
                    return@forEach
                }
                val file = target.substringBefore('!').removeSurrounding("\"")
                val resolved = RelativeLink.resolve(path, file)?.path ?: return@forEach
                if (resolved != source && found.add(resolved)) visit(resolved)
            }
        }
        visit(source)

        return found.toList()
    }

    private companion object {
        val PARAGRAPH = Regex("<p>(?:(?!</p>).)*?<img\\b(?:(?!</p>).)*?</p>", RegexOption.DOT_MATCHES_ALL)
        val IMAGE = Regex("<img\\s+src=\"([^\"]+)\"")
        val INCLUDE = Regex("!(include|include_once|include_many|includesub)\\s+(.+)")
    }
}
