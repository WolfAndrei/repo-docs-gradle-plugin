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

/** Layout of a page: an article, the landing of a library, a declaration of the API, an interactive page. */
internal enum class PageStyle(val css: String) {
    ARTICLE("article"),
    LANDING("landing"),
    SYMBOL("symbol"),
    INTERACTIVE("interactive"),
}

/**
 * HTML of the site pages. Every address is relative, so the site opens both from the server and from the file
 * system; the styles, the scripts and the search index lie in [ASSETS_DIR].
 */
internal class SiteTemplate(
    private val siteTitle: String,
    private val navigation: List<NavigationItem>,
    private val text: UiText = UiText.of("en"),
) {

    /** The page at [path] (relative to the site root) with the title [title] and the body [content]. */
    fun page(path: String, title: String, content: String, style: PageStyle = PageStyle.ARTICLE): String {
        val root = rootPrefix(path)

        return """
            |<!DOCTYPE html>
            |<html lang="${text.language}">
            |<head>
            |<meta charset="utf-8">
            |<meta name="viewport" content="width=device-width, initial-scale=1">
            |<title>${escape(title)} - ${escape(siteTitle)}</title>
            |<script>$THEME_SCRIPT</script>
            |<link rel="stylesheet" href="${root}$ASSETS_DIR/site.css">
            |</head>
            |<body data-root="$root" class="page-${style.css}">
            |<header class="site-header">
            |<button class="nav-toggle" type="button" aria-label="${escape(text["navigation"])}" aria-expanded="false"><span></span></button>
            |<a class="site-title" href="${root}index.html">${escape(siteTitle)}</a>
            |<div class="search">
            |<input id="search-input" type="search" placeholder="${escape(text["search"])}" autocomplete="off" aria-label="${escape(text["search"])}">
            |<div id="search-results" class="search-results" hidden></div>
            |</div>
            |<button class="theme-toggle" type="button" aria-label="${escape(text["theme"])}" title="${escape(text["theme"])}">$THEME_ICONS</button>
            |</header>
            |<div class="layout">
            |<nav class="sidebar" aria-label="${escape(text["navigation"])}">
            |${navigation(path, root)}
            |</nav>
            |<main class="content content-${style.css}">
            |$content
            |</main>
            |</div>
            |<script src="${root}$ASSETS_DIR/search-index.js"></script>
            |<script src="${root}$ASSETS_DIR/${SiteBuilder.UI_TEXT_SCRIPT}"></script>
            |<script src="${root}$ASSETS_DIR/site.js"></script>
            |</body>
            |</html>
            |
        """.trimMargin()
    }

    private fun navigation(path: String, root: String): String {
        val current = nearestListed(path)
        val html = StringBuilder("<ul>")
        navigation.forEach { append(html, it, current, root, depth = 0, parent = "") }
        return html.append("</ul>").toString()
    }

    /**
     * Appends [item] to the sidebar. A branch carries `data-nav`, the same on every page: the path of its page, or for
     * a group the key of its parent and its title. The script keeps the branches the reader opened or closed by it.
     */
    private fun append(
        html: StringBuilder,
        item: NavigationItem,
        current: String,
        root: String,
        depth: Int,
        parent: String,
    ) {
        val badge = item.kind?.let { DokkaReference.badge(it, text.kind(it)) }.orEmpty()
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
        val key = item.path ?: "$parent>${item.title}"
        html.append("<li><details data-nav=\"").append(escape(key)).append("\"").append(open).append("><summary>")
            .append(link).append("</summary><ul>")
        item.children.forEach { append(html, it, current, root, depth + 1, key) }
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
        /** Local storage key of the theme the reader chose: `system`, `light` or `dark`. */
        private const val THEME_KEY = "repo-docs-theme"

        /**
         * Sets the theme before the styles apply, so that a dark page does not flash white: the choice of the reader,
         * otherwise the scheme of the system. Local storage may be unavailable, such as in a private window.
         */
        private const val THEME_SCRIPT = "(function(){var c;try{c=localStorage.getItem(\"$THEME_KEY\")}catch(e){}" +
            "if(c!==\"light\"&&c!==\"dark\")c=\"system\";var d=c===\"dark\"||c===\"system\"&&" +
            "window.matchMedia(\"(prefers-color-scheme: dark)\").matches;var r=document.documentElement;" +
            "r.setAttribute(\"data-theme-choice\",c);r.setAttribute(\"data-theme\",d?\"dark\":\"light\")})();"

        private const val ICON = "viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"1.8\" " +
            "stroke-linecap=\"round\" aria-hidden=\"true\""

        /** Icons of the choices: half a circle for the system, the sun, the moon. */
        private const val THEME_ICONS =
            "<svg class=\"theme-system\" $ICON><circle cx=\"12\" cy=\"12\" r=\"8\"/>" +
                "<path d=\"M12 4a8 8 0 0 1 0 16z\" fill=\"currentColor\"/></svg>" +
                "<svg class=\"theme-light\" $ICON><circle cx=\"12\" cy=\"12\" r=\"4\"/>" +
                "<path d=\"M12 2v2M12 20v2M2 12h2M20 12h2M4.9 4.9l1.4 1.4M17.7 17.7l1.4 1.4M4.9 19.1l1.4-1.4" +
                "M17.7 6.3l1.4-1.4\"/></svg>" +
                "<svg class=\"theme-dark\" $ICON><path d=\"M20 14.5A8 8 0 1 1 9.5 4a6.5 6.5 0 0 0 10.5 10.5z\"/></svg>"

        const val ASSETS_DIR = "_assets"

        private val TABLE_OPEN = Regex("<table>")
        private val TABLE_CLOSE = Regex("</table>")
        private val WARNING = Regex("<blockquote>\\s*<p>(?:\u26A0\uFE0F?|&#9888;)\\s*")
        private val WARNING_WORD = Regex(
            "<blockquote>(\\s*<p><strong>(?:ВНИМАНИЕ|Внимание|Важно|WARNING|Warning|Important|IMPORTANT))"
        )
        private val BACK_LINK = Regex("\\A\\s*<p>(<a href=\"[^\"]*\">[^<]*</a>)</p>")
        private val NOTE = Regex("<blockquote>(\\s*<p>(?:<strong>)?(?:Примечание|Note))")

        /**
         * HTML of a Markdown page for the site layout: each table scrolls in its own container; a quote that starts
         * with `⚠️` or a bold «Warning», «Important», «Внимание», «Важно» is a warning and a quote that starts with
         * «Note» or «Примечание» is a note (the warning sign is dropped, the style marks the callout); a first
         * paragraph that is a single link, such as `[⇦ Contents](../index.md)`, is a back link.
         */
        fun decorateArticle(html: String): String = html
            .replace(TABLE_OPEN, "<div class=\"table-wrap\"><table>")
            .replace(TABLE_CLOSE, "</table></div>")
            .replace(WARNING, "<blockquote class=\"callout callout-warning\"><p>")
            .replace(WARNING_WORD, "<blockquote class=\"callout callout-warning\">$1")
            .replace(NOTE, "<blockquote class=\"callout callout-note\">$1")
            .replace(BACK_LINK, "<p class=\"back-link\">$1</p>")

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
