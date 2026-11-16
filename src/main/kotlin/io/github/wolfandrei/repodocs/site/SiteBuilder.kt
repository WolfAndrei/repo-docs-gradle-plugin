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

import io.github.wolfandrei.repodocs.AnchorStyle
import io.github.wolfandrei.repodocs.markdown.MarkdownPage
import io.github.wolfandrei.repodocs.markdown.RelativeLink
import java.io.File
import java.security.MessageDigest
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * Files the site is built from. The keys of the maps are paths relative to the repository root with `/`: the site
 * keeps the layout of the repository, so the relative links between the files stay valid.
 *
 * @property pages Markdown pages.
 * @property staticFiles files copied as they are: pictures, interactive pages, sources of the diagrams.
 * @property renderedDiagrams SVG rendered from PlantUML; the site never uses the committed SVG of a diagram.
 * @property apiReference aggregated Dokka output, copied to `api/`, or `null` without the API reference.
 * @property apiModules modules of [apiReference].
 * @property readme path of the README of the repository: the home page of the site.
 * @property index path of the table of contents: the root of the sidebar.
 * @property repositoryRoot the repository: a link to its file that is not on the site leads there.
 */
/** Module of the API reference: [name] and [directory] relative to `api/`. */
internal data class ApiModuleEntry(val name: String, val directory: String)

internal class SiteSources(
    val title: String,
    val pages: Map<String, File>,
    val staticFiles: Map<String, File>,
    val renderedDiagrams: Map<String, File>,
    val apiReference: File?,
    val apiModules: List<ApiModuleEntry>,
    val readme: String,
    val index: String,
    val repositoryRoot: File,
    val language: String = "en",
    val anchorStyle: AnchorStyle = AnchorStyle.GITHUB,
    val navigation: NavigationOptions = NavigationOptions(),
    val contentsHeadings: Set<String> = MarkdownPage.DEFAULT_CONTENTS_HEADINGS,
)

/**
 * Builds the static site from [SiteSources] into [outputDir], which it clears first. The last file written is
 * [BUILD_STAMP]: a script with a hash of the site, which the pages load again and again to reload after a rebuild.
 */
internal class SiteBuilder(
    private val sources: SiteSources,
    private val outputDir: File,
    private val warn: (String) -> Unit = {},
) {

    private val text = UiText.of(sources.language)

    /** Declarations of the API reference for the links of the pages; `null` without the reference. */
    private var symbols: ApiSymbols? = null

    fun build() {
        outputDir.deleteRecursively()
        outputDir.mkdirs()

        val diagrams = SiteDiagrams(sources.staticFiles, text)

        val pages = sources.pages.mapValues { (path, file) -> MarkdownPage(path, file.readText(), sources.anchorStyle, sources.contentsHeadings)
        }
        val siteFiles = sources.staticFiles.keys + sources.renderedDiagrams.keys
        val apiReference = sources.apiReference?.takeIf { it.isDirectory }
        val apiModules = if (apiReference != null) sources.apiModules else emptyList()
        val apiPages = apiReference?.let { DokkaReference(it, apiModules, text).pages }.orEmpty()
        val interactive = sources.staticFiles
            .filterKeys { it.endsWith(".html", ignoreCase = true) && !InteractivePages.isFrame(it) }
            .map { (path, file) -> InteractivePages.read(path, file) }
        val navigation = SiteNavigation(pages, siteFiles, text, sources.navigation).build(
            readme = sources.readme.takeIf { it in pages },
            index = sources.index.takeIf { it in pages },
            apiModules = apiModules,
            apiPages = apiPages,
            interactivePages = interactive,
        )
        val template = SiteTemplate(sources.title, navigation, text)

        sources.staticFiles
            .filterKeys { !diagrams.isDiagramImage(it) }
            .forEach { (path, file) -> copyStatic(path, file, pages.keys) }
        sources.renderedDiagrams.forEach { (path, file) -> file.copyTo(outputDir.resolve(path), overwrite = true) }
        apiPages.forEach { page ->
            outputDir.resolve(page.path).apply { parentFile.mkdirs() }
                .writeText(
                    template.page(
                        page.path,
                        page.qualifiedTitle.ifEmpty { text["api"] },
                        CodeHighlighter.highlightBlocks(page.content),
                        PageStyle.SYMBOL,
                    )
                )
        }

        interactive.forEach { page ->
            val library = libraryPage(page.path, pages.keys)
            val back = library?.let {
                val href = SiteTemplate.rootPrefix(page.path) + SiteNavigation.htmlPath(it)
                href to pages.getValue(it).title
            }
            val content = InteractivePages.content(page, back, text["interactiveOpen"])
            outputDir.resolve(page.path).writeText(template.page(page.path, page.title, content, PageStyle.INTERACTIVE))
        }

        symbols = apiReference?.let { ApiSymbols.read(it, apiModules) }
        pages.values.forEach { page ->
            val htmlPath = SiteNavigation.htmlPath(page.path)
            val modules = libraryModules(page.path, pages.keys)
            val html = page.renderHtml(
                rewrite = { destination -> rewrite(page.path, destination, pages.keys, siteFiles) },
                codeLink = { code ->
                    symbols?.resolve(code, modules)?.let { SiteTemplate.rootPrefix(htmlPath) + it.path }
                },
            )
            val decorated = SiteTemplate.decorateArticle(diagrams.decorate(page.path, html))
            val content = CodeHighlighter.highlightBlocks(decorated)
            val style = if (isLibraryIndex(page.path, pages.keys)) PageStyle.LANDING else PageStyle.ARTICLE
            outputDir.resolve(htmlPath).apply {
                parentFile.mkdirs()
            }.writeText(template.page(htmlPath, page.title, content, style))
        }

        writeHome(pages)
        writeAssets(SearchIndex.entries(pages.values) + SearchIndex.apiEntries(apiPages, text) +
                SearchIndex.interactiveEntries(interactive))
        writeStamp()
    }

    /** Directory of the library that [path] belongs to: the first directory under the documentation directory. */
    private fun libraryDirectory(path: String): String? {
        val documentation = sources.index.substringBeforeLast('/', "")
        val prefix = if (documentation.isEmpty()) "" else "$documentation/"
        if (!path.startsWith(prefix)) return null
        val library = path.removePrefix(prefix).substringBefore('/', "").ifEmpty { return null }
        return "$prefix$library"
    }

    /**
     * The page of the library that [path] belongs to: a page directly in the library directory whose name makes it a
     * table of contents, such as `index.md` or `<name>-index.md`.
     */
    private fun libraryPage(path: String, pages: Set<String>): String? {
        val directory = libraryDirectory(path) ?: return null
        return pages.filter { it.substringBeforeLast('/') == directory && sources.navigation.isHubName(it) }
            .minByOrNull { if (it.endsWith("/${SiteNavigation.INDEX}")) "" else it }
    }

    /** Names of the Dokka modules of the library that [path] belongs to, see [SiteNavigation.libraryOf]. */
    private fun libraryModules(path: String, pages: Set<String>): Set<String> {
        val library = libraryDirectory(path)?.substringAfterLast('/') ?: return emptySet()
        val documentation = sources.index.substringBeforeLast('/', "").let { if (it.isEmpty()) "" else "$it/" }
        val libraries = SiteNavigation.libraryDirectories(pages, documentation)
        return sources.apiModules.filter { SiteNavigation.libraryOf(it, libraries) == library }.map { it.name }.toSet()
    }

    /** Whether [path] is the page of a library, see [libraryPage]: the landing style. */
    private fun isLibraryIndex(path: String, pages: Set<String>): Boolean =
        path != sources.index && libraryPage(path, pages) == path

    /** Hash of every file of the site: it changes with the content and not with the time of the build. */
    private fun writeStamp() {
        val digest = MessageDigest.getInstance("SHA-256")
        outputDir.walkTopDown().filter { it.isFile }.sortedBy { it.invariantSeparatorsPath }.forEach { file ->
            digest.update(file.relativeTo(outputDir).invariantSeparatorsPath.toByteArray())
            digest.update(file.readBytes())
        }
        val stamp = digest.digest().joinToString("") { "%02x".format(it) }
        outputDir.resolve(BUILD_STAMP).writeText("window.repoDocsBuildStamp = \"$stamp\";\n")
    }

    /**
     * The destination of a link of the page [pagePath] on the site: a page gets the `.html` extension, a `.kt` file
     * named as a type of the API reference leads to the page of the type, a repository file that is not on the site
     * gets a relative path to the file in the repository, so that it opens from the file system; the rest stays as
     * written.
     */
    private fun rewrite(pagePath: String, destination: String, pages: Set<String>, siteFiles: Set<String>): String {
        val link = RelativeLink.resolve(pagePath, destination) ?: return destination
        val pathPart = destination.substringBefore('#').substringBefore('?')
        if (pathPart.isEmpty()) return destination

        val sourceType = link.path.takeIf { it.endsWith(".kt") }?.let { kotlinFile ->
            symbols?.resolveType(kotlinFile.substringAfterLast('/').removeSuffix(".kt"), libraryModules(pagePath, pages))
        }
        return when {
            link.path in pages -> pathPart.removeSuffix(".md") + ".html" + destination.substring(pathPart.length)
            sourceType != null -> SiteTemplate.rootPrefix(pagePath) + sourceType.path
            link.path in siteFiles || isSiteDirectory(link.path, siteFiles, pages) -> destination
            else -> repositoryLink(pagePath, link)
        }
    }

    /** The relative path from the page [pagePath] of the site to the file of [link] in the repository. */
    private fun repositoryLink(pagePath: String, link: RelativeLink): String {
        val root = sources.repositoryRoot
        val page = outputDir.resolve(SiteNavigation.htmlPath(pagePath)).absoluteFile.parentFile.toPath()
        val target = root.resolve(link.path).absoluteFile.toPath()
        return page.relativize(target).joinToString("/") + fragmentOf(link)
    }

    private fun fragmentOf(link: RelativeLink): String = link.fragment?.let { "#$it" }.orEmpty()

    private fun isSiteDirectory(path: String, siteFiles: Set<String>, pages: Set<String>): Boolean {
        val prefix = if (path.isEmpty()) "" else "$path/"
        return siteFiles.any { it.startsWith(prefix) } || pages.any { it.startsWith(prefix) }
    }

    /**
     * An interactive page is copied for its frame with its links to the Markdown pages rewritten, see
     * [InteractivePages]; other files are copied as they are.
     */
    private fun copyStatic(path: String, file: File, pages: Set<String>) {
        val target = outputDir.resolve(path).apply { parentFile.mkdirs() }
        if (!path.endsWith(".html", ignoreCase = true)) {
            file.copyTo(target, overwrite = true)
            return
        }

        val html = MarkdownPage.rewriteHtml(file.readText()) { destination ->
            val link = RelativeLink.resolve(path, destination)
            val pathPart = destination.substringBefore('#').substringBefore('?')
            if (link != null && pathPart.isNotEmpty() && link.path in pages) {
                pathPart.removeSuffix(".md") + ".html" + destination.substring(pathPart.length)
            } else {
                destination
            }
        }
        outputDir.resolve(InteractivePages.framePath(path)).writeText(InteractivePages.frameHtml(html))
    }

    /**
     * `index.html` of the site root: a copy of the README page if README lies in the root, otherwise a redirect to the
     * table of contents or to the first page.
     */
    private fun writeHome(pages: Map<String, MarkdownPage>) {
        val home = outputDir.resolve("index.html")
        if (home.exists()) return

        if (sources.readme in pages && '/' !in sources.readme) {
            outputDir.resolve(SiteNavigation.htmlPath(sources.readme)).copyTo(home)
            return
        }

        val location = (sources.index.takeIf { it in pages } ?: pages.keys.minOrNull())?.let(SiteNavigation::htmlPath)
        val body = location?.let { "<meta http-equiv=\"refresh\" content=\"0; url=${SiteTemplate.escape(it)}\">" }
            ?: "<p>${SiteTemplate.escape(text["noPages"])}</p>"
        home.writeText(
            "<!DOCTYPE html>\n<html lang=\"${text.language}\"><head><meta charset=\"utf-8\">$body</head><body></body></html>\n"
        )
    }

    private fun writeAssets(entries: List<SearchEntry>) {
        val assets = outputDir.resolve(SiteTemplate.ASSETS_DIR).apply { mkdirs() }
        listOf("site.css", "site.js").forEach { name ->
            val resource = javaClass.getResourceAsStream(RESOURCES + name) ?: error("No resource $RESOURCES$name")
            resource.use { input -> assets.resolve(name).outputStream().use { input.copyTo(it) } }
        }
        assets.resolve("search-index.json").writeText(SearchIndex.toJson(entries))
        assets.resolve("search-index.js").writeText(SearchIndex.toScript(entries))
        assets.resolve(UI_TEXT_SCRIPT).writeText(
            "window.DOCS_TEXT = ${json.encodeToString(MapSerializer(String.serializer(), String.serializer()), text.scriptStrings())};\n"
        )
    }

    companion object {
        const val UI_TEXT_SCRIPT = "ui-text.js"
        private val json = Json
        const val BUILD_STAMP = "${SiteTemplate.ASSETS_DIR}/build-stamp.js"
        private const val RESOURCES = "/io/github/wolfandrei/repodocs/"
    }
}
