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

import io.github.wolfandrei.docssite.markdown.AnchorStyle
import io.github.wolfandrei.docssite.markdown.MarkdownPage
import io.github.wolfandrei.docssite.markdown.RelativeLink
import java.io.File
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.security.MessageDigest

/**
 * Files the site is built from. The keys of the maps are paths relative to the repository root with `/`: the site
 * keeps the layout of the repository, so the relative links between the files stay valid.
 *
 * @property pages Markdown pages.
 * @property staticFiles files copied as they are: pictures, interactive pages, sources of the diagrams.
 * @property renderedDiagrams SVG rendered from PlantUML; the site never uses the committed SVG of a diagram.
 * @property preprocessedDiagrams preprocessed PlantUML sources by the path of their `.puml`.
 * @property diagramRender where the diagrams are drawn, see [DiagramRender].
 * @property plantUmlBundle archive of the npm package `@plantuml/core`, or `null` without it.
 * @property apiReference aggregated Dokka output, copied to `api/`, or `null` without the API reference.
 * @property apiModules modules of [apiReference].
 * @property readme path of the README of the repository: the home page of the site.
 * @property index path of the table of contents: the root of the sidebar.
 * @property sourceBrowseUrl prefix of the address of a repository file that is not on the site, such as
 * `https://gitlab.example.com/group/repo/-/blob/main/`; without it such links stay relative.
 */
/** Module of the API reference: [name] and [directory] relative to `api/`. */
internal data class ApiModuleEntry(val name: String, val directory: String)

internal class SiteSources(
    val title: String,
    val pages: Map<String, File>,
    val staticFiles: Map<String, File>,
    val renderedDiagrams: Map<String, File>,
    val preprocessedDiagrams: Map<String, File>,
    val diagramRender: DiagramRender,
    val plantUmlBundle: File?,
    val apiReference: File?,
    val apiModules: List<ApiModuleEntry>,
    val readme: String,
    val index: String,
    val sourceBrowseUrl: String?,
    val language: String = "en",
    val anchorStyle: AnchorStyle = AnchorStyle.GITLAB,
    val groupTitles: Map<String, String> = emptyMap(),
)

/**
 * Builds the static site from [SiteSources] into [outputDir], which it clears first. The last file written is
 * [BUILD_STAMP]: a hash of the site, which the pages poll to reload after a rebuild.
 */
internal class SiteBuilder(
    private val sources: SiteSources,
    private val outputDir: File,
    private val warn: (String) -> Unit = {},
) {

    private val text = UiText.of(sources.language)

    fun build() {
        outputDir.deleteRecursively()
        outputDir.mkdirs()

        val bundleDir = outputDir.resolve("${SiteTemplate.ASSETS_DIR}/${PlantUmlBundle.PLANTUML_DIR}")
        val bundle = sources.plantUmlBundle?.takeIf { it.isFile }?.let { PlantUmlBundle.unpack(it, bundleDir) } == true
        val render = if (bundle) sources.diagramRender else DiagramRender.BUILD
        if (!bundle && sources.diagramRender != DiagramRender.BUILD) {
            warn("The browser build of PlantUML is not available, the diagrams are drawn at build time")
        }
        val diagrams = SiteDiagrams(sources.staticFiles, sources.preprocessedDiagrams, render, text)

        val pages = sources.pages.mapValues { (path, file) -> MarkdownPage(path, file.readText(), sources.anchorStyle) }
        val siteFiles = sources.staticFiles.keys + sources.renderedDiagrams.keys
        val apiReference = sources.apiReference?.takeIf { it.isDirectory }
        val apiModules = if (apiReference != null) sources.apiModules else emptyList()
        val apiPages = apiReference?.let { DokkaReference(it, apiModules, text).pages }.orEmpty()
        val interactive = sources.staticFiles
            .filterKeys { it.endsWith(".html", ignoreCase = true) && !InteractivePages.isFrame(it) }
            .map { (path, file) -> InteractivePages.read(path, file) }
        val navigation = SiteNavigation(pages, siteFiles, text, sources.groupTitles).build(
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
            val library = libraryIndex(page.path)?.takeIf { it in pages }
            val back = library?.let {
                val href = SiteTemplate.rootPrefix(page.path) + SiteNavigation.htmlPath(it)
                href to pages.getValue(it).title
            }
            val content = InteractivePages.content(page, back, text["interactiveOpen"])
            outputDir.resolve(page.path).writeText(template.page(page.path, page.title, content, PageStyle.INTERACTIVE))
        }

        pages.values.forEach { page ->
            val htmlPath = SiteNavigation.htmlPath(page.path)
            val html = page.renderHtml { destination -> rewrite(page.path, destination, pages.keys, siteFiles) }
            val decorated = SiteTemplate.decorateArticle(diagrams.decorate(page.path, html))
            val content = CodeHighlighter.highlightBlocks(decorated)
            val style = if (isLibraryIndex(page.path)) PageStyle.LANDING else PageStyle.ARTICLE
            outputDir.resolve(htmlPath).apply {
                parentFile.mkdirs()
            }.writeText(template.page(htmlPath, page.title, content, style))
        }

        if (!diagrams.usesBrowser) bundleDir.deleteRecursively()
        writeHome(pages)
        writeAssets(SearchIndex.entries(pages.values) + SearchIndex.apiEntries(apiPages, text) +
                SearchIndex.interactiveEntries(interactive))
        writeStamp()
    }

    /** The index page of the library that [path] belongs to, `<documentation>/<library>/index.md`, if any. */
    private fun libraryIndex(path: String): String? {
        val documentation = sources.index.substringBeforeLast('/', "")
        val prefix = if (documentation.isEmpty()) "" else "$documentation/"
        if (!path.startsWith(prefix)) return null
        val library = path.removePrefix(prefix).substringBefore('/', "").ifEmpty { return null }
        return "$prefix$library/${SiteNavigation.INDEX}"
    }

    /** Whether [path] is the index page of a library, `<documentation>/<library>/index.md`: the landing style. */
    private fun isLibraryIndex(path: String): Boolean {
        val documentation = sources.index.substringBeforeLast('/', "")
        val prefix = if (documentation.isEmpty()) "" else "$documentation/"
        if (!path.startsWith(prefix) || path == sources.index) return false
        val rest = path.removePrefix(prefix)
        return rest.count { it == '/' } == 1 && rest.endsWith("/${SiteNavigation.INDEX}")
    }

    /** Hash of every file of the site: it changes with the content and not with the time of the build. */
    private fun writeStamp() {
        val digest = MessageDigest.getInstance("SHA-256")
        outputDir.walkTopDown().filter { it.isFile }.sortedBy { it.invariantSeparatorsPath }.forEach { file ->
            digest.update(file.relativeTo(outputDir).invariantSeparatorsPath.toByteArray())
            digest.update(file.readBytes())
        }
        val stamp = digest.digest().joinToString("") { "%02x".format(it) }
        outputDir.resolve(BUILD_STAMP).writeText(stamp)
    }

    /**
     * The destination of a link of the page [pagePath] on the site: a page gets the `.html` extension, a repository
     * file that is not on the site gets the [SiteSources.sourceBrowseUrl] prefix, the rest stays as written.
     */
    private fun rewrite(pagePath: String, destination: String, pages: Set<String>, siteFiles: Set<String>): String {
        val link = RelativeLink.resolve(pagePath, destination) ?: return destination
        val pathPart = destination.substringBefore('#').substringBefore('?')
        if (pathPart.isEmpty()) return destination

        return when {
            link.path in pages -> pathPart.removeSuffix(".md") + ".html" + destination.substring(pathPart.length)
            link.path in siteFiles || isSiteDirectory(link.path, siteFiles, pages) -> destination
            else -> sources.sourceBrowseUrl?.let { it.trimEnd('/') + "/" + link.path + fragmentOf(link) } ?: destination
        }
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
        const val BUILD_STAMP = "${SiteTemplate.ASSETS_DIR}/build-stamp"
        private const val RESOURCES = "/io/github/wolfandrei/docssite/"
    }
}
