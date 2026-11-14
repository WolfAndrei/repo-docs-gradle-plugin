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

/** Layout of a page: a narrative article, the landing of a library, a declaration of the API reference. */
internal enum class PageStyle(val css: String) { ARTICLE("article"), LANDING("landing"), SYMBOL("symbol") }

/**
 * HTML of the site pages. Every address is relative, so the site opens both from the server and from the file
 * system; the styles, the scripts and the search index lie in [ASSETS_DIR].
 */
internal class SiteTemplate(private val siteTitle: String, private val navigation: List<NavigationItem>) {

    /** The page at [path] (relative to the site root) with the title [title] and the body [content]. */
    fun page(path: String, title: String, content: String, style: PageStyle = PageStyle.ARTICLE): String {
        val root = rootPrefix(path)

        return """
            |<!DOCTYPE html>
            |<html lang="ru">
            |<head>
            |<meta charset="utf-8">
            |<meta name="viewport" content="width=device-width, initial-scale=1">
            |<title>${escape(title)} - ${escape(siteTitle)}</title>
            |<link rel="stylesheet" href="${root}$ASSETS_DIR/site.css">
            |</head>
            |<body data-root="$root" class="page-${style.css}">
            |<header class="site-header">
            |<button class="nav-toggle" type="button" aria-label="Навигация" aria-expanded="false"><span></span></button>
            |<a class="site-title" href="${root}index.html">${escape(siteTitle)}</a>
            |<div class="search">
            |<input id="search-input" type="search" placeholder="Поиск" autocomplete="off" aria-label="Поиск">
            |<div id="search-results" class="search-results" hidden></div>
            |</div>
            |</header>
            |<div class="layout">
            |<nav class="sidebar" aria-label="Навигация">
            |${navigation(path, root)}
            |</nav>
            |<main class="content content-${style.css}">
            |$content
            |</main>
            |</div>
            |<script src="${root}$ASSETS_DIR/search-index.js"></script>
            |<script src="${root}$ASSETS_DIR/site.js"></script>
            |</body>
            |</html>
            |
        """.trimMargin()
    }

    private fun navigation(path: String, root: String): String {
        val current = nearestListed(path)
        val html = StringBuilder("<ul>")
        navigation.forEach { append(html, it, current, root, depth = 0) }
        return html.append("</ul>").toString()
    }

    private fun append(html: StringBuilder, item: NavigationItem, current: String, root: String, depth: Int) {
        val badge = item.kind?.let { DokkaReference.badge(it) }.orEmpty()
        val label = "$badge<span class=\"nav-label\">${escape(item.title)}</span>"
        val link = item.path?.let { path ->
            val currentClass = if (path == current) " class=\"current\" aria-current=\"page\"" else ""
            "<a href=\"${escape(root + path)}\"$currentClass>$label</a>"
        } ?: "<span class=\"nav-group\">$label</span>"

        if (item.children.isEmpty()) {
            html.append("<li>").append(link).append("</li>")
            return
        }

        val open = if (depth == 0 || item.contains(current)) " open" else ""
        html.append("<li><details$open><summary>").append(link).append("</summary><ul>")
        item.children.forEach { append(html, it, current, root, depth + 1) }
        html.append("</ul></details></li>")
    }

    /**
     * [path], or for a page that the sidebar does not list, such as a member of a type in the API reference, the
     * closest `index.html` above it that the sidebar lists.
     */
    private fun nearestListed(path: String): String {
        if (navigation.any { it.contains(path) }) return path
        var directory = path.substringBeforeLast('/', "")
        if (path.endsWith("/index.html")) directory = directory.substringBeforeLast('/', "")
        while (directory.isNotEmpty()) {
            val candidate = "$directory/index.html"
            if (navigation.any { it.contains(candidate) }) return candidate
            directory = directory.substringBeforeLast('/', "")
        }
        return path
    }

    companion object {
        const val ASSETS_DIR = "_assets"

        private val TABLE_OPEN = Regex("<table>")
        private val TABLE_CLOSE = Regex("</table>")
        private val WARNING = Regex("<blockquote>\\s*<p>(?:\u26A0\uFE0F?|&#9888;)\\s*")
        private val NOTE = Regex("<blockquote>(\\s*<p>(?:<strong>)?(?:Примечание|Note))")

        /**
         * HTML of a Markdown page for the site layout: each table scrolls in its own container, a quote that starts
         * with `⚠️` is a warning and a quote that starts with «Примечание» is a note; the warning sign is dropped, the
         * style marks the callout.
         */
        fun decorateArticle(html: String): String = html
            .replace(TABLE_OPEN, "<div class=\"table-wrap\"><table>")
            .replace(TABLE_CLOSE, "</table></div>")
            .replace(WARNING, "<blockquote class=\"callout callout-warning\"><p>")
            .replace(NOTE, "<blockquote class=\"callout callout-note\">$1")

        /** Prefix that leads from the page [path] to the site root: `a/b.html` -> `../`. */
        fun rootPrefix(path: String): String = "../".repeat(path.count { it == '/' })

        fun escape(text: String): String = buildString(text.length) {
            text.forEach { char ->
                when (char) {
                    '&' -> append("&amp;")
                    '<' -> append("&lt;")
                    '>' -> append("&gt;")
                    '"' -> append("&quot;")
                    else -> append(char)
                }
            }
        }
    }
}
