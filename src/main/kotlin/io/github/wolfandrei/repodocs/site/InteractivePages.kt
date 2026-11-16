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

import java.io.File
import org.jsoup.Jsoup

/**
 * An interactive page of the documentation: a self-contained HTML file with its own styles and scripts.
 *
 * @property path path of the page relative to the site root, the same as in the repository.
 * @property title the `<title>` of the page, or its file name.
 * @property text visible text of the page, for the search.
 */
internal class InteractivePage(val path: String, val title: String, val text: String)

/**
 * Interactive pages inside the site: the page itself is copied next to its source as `<name>.frame.html` and shown in
 * an `<iframe>` of a site page at the original path, so the header, the sidebar and the search stay around it and its
 * own styles and scripts stay isolated. The copy reports its height to the site page, which resizes the frame, and its
 * links open in the whole window. The source files in the repository stay unchanged and keep working on their own.
 */
internal object InteractivePages {
    private const val FRAME_SUFFIX = ".frame.html"
    private val WHITESPACE = Regex("\\s+")
    private val LINK = Regex("<a\\b([^>]*)>", RegexOption.IGNORE_CASE)
    private val HREF = Regex("""\bhref\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s"'=<>`]+))""", RegexOption.IGNORE_CASE)
    private val TARGET = Regex("\\btarget\\s*=", RegexOption.IGNORE_CASE)
    private val BODY_END = Regex("</body>", RegexOption.IGNORE_CASE)
    private val HEAD_END = Regex("</head>", RegexOption.IGNORE_CASE)

    /**
     * The colour scheme of the frame follows the site: a page without a background of its own lies on the background
     * of the site, with the text colours of its scheme, rather than on a white box; a page with its own background keeps
     * it.
     */
    private const val FRAME_STYLE = "<style>html { color-scheme: light dark; }</style>\n"

    /**
     * Script of the copy: sends the height of the page to the site page, as `{repoDocsFrameHeight: <px>}`, on load and
     * on every change of the size.
     */
    private const val RESIZE_SCRIPT = "<script>(function () {\n" +
        "    if (window.parent === window) return;\n" +
        "    var last = 0;\n" +
        "    function post() {\n" +
        "        var height = Math.ceil(document.documentElement.getBoundingClientRect().height);\n" +
        "        if (Math.abs(height - last) < 2) return;\n" +
        "        last = height;\n" +
        "        window.parent.postMessage({repoDocsFrameHeight: height}, \"*\");\n" +
        "    }\n" +
        "    window.addEventListener(\"load\", post);\n" +
        "    if (window.ResizeObserver) new ResizeObserver(post).observe(document.documentElement);\n" +
        "    post();\n" +
        "})();</script>\n"

    /** Path of the copy that the frame shows. */
    fun framePath(path: String): String = path.removeSuffix(".html").removeSuffix(".HTML") + FRAME_SUFFIX

    /** Whether [path] is such a copy, which is not a page of its own. */
    fun isFrame(path: String): Boolean = path.endsWith(FRAME_SUFFIX)

    fun read(path: String, file: File): InteractivePage {
        val document = Jsoup.parse(file, Charsets.UTF_8.name())
        document.select("script, style, template, noscript").remove()
        val title = document.title().trim().ifEmpty { path.substringAfterLast('/') }
        val text = document.body()?.text().orEmpty().replace(WHITESPACE, " ").trim()
        return InteractivePage(path, title, text)
    }

    /**
     * [html] of the page for the frame: links leave the frame, whatever the quotes of their `href`, the height goes to
     * the site page, the colour scheme follows the site.
     */
    fun frameHtml(html: String): String {
        val linked = LINK.replace(html) { match ->
            val attributes = match.groupValues[1]
            val href = HREF.find(attributes)?.let { it.groups[1]?.value ?: it.groups[2]?.value ?: it.groupValues[3] }
            if (href == null || href.startsWith("#") || href.startsWith("javascript:") || TARGET.containsMatchIn(attributes)) {
                match.value
            } else {
                "<a$attributes target=\"_top\">"
            }
        }
        val head = HEAD_END.find(linked)
        val styled = if (head == null) FRAME_STYLE + linked else linked.replaceRange(head.range, FRAME_STYLE + head.value)
        val end = BODY_END.findAll(styled).lastOrNull()
        return if (end == null) styled + RESIZE_SCRIPT else styled.replaceRange(end.range, RESIZE_SCRIPT + end.value)
    }

    /** Body of the site page that shows [page] in a frame; [back] is a link to the library page, if there is one. */
    fun content(page: InteractivePage, back: Pair<String, String>?, openLabel: String): String {
        val frame = framePath(page.path).substringAfterLast('/')
        val html = StringBuilder()
        back?.let { (href, title) ->
            html.append("<nav class=\"breadcrumbs\"><a href=\"").append(SiteTemplate.escape(href)).append("\">")
                .append(SiteTemplate.escape(title)).append("</a></nav>")
        }
        html.append("<div class=\"interactive\"><iframe class=\"interactive-frame\" src=\"")
            .append(SiteTemplate.escape(frame)).append("\" title=\"").append(SiteTemplate.escape(page.title))
            .append("\"></iframe></div>")
        html.append("<p class=\"interactive-open\"><a href=\"").append(SiteTemplate.escape(frame))
            .append("\" target=\"_blank\" rel=\"noopener\">").append(SiteTemplate.escape(openLabel)).append("</a></p>")
        return html.toString()
    }
}
