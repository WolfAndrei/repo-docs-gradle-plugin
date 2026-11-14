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

package io.github.wolfandrei.docssite.tasks

import io.github.wolfandrei.docssite.site.ApiModuleEntry
import io.github.wolfandrei.docssite.site.DiagramRender
import io.github.wolfandrei.docssite.site.SiteBuilder
import io.github.wolfandrei.docssite.site.SiteSources
import io.github.wolfandrei.docssite.markdown.AnchorStyle
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import io.github.wolfandrei.docssite.site.NavigationOptions
import io.github.wolfandrei.docssite.site.TitleSource
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.IgnoreEmptyDirectories
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

/** Builds the static documentation site: Markdown pages as HTML, static files, diagrams, API reference, search. */
@CacheableTask
internal abstract class BuildDocumentationSiteTask : DefaultTask() {
    @get:Internal
    abstract val repositoryRoot: DirectoryProperty

    /** Markdown pages: README, the documentation and the additional pages. */
    @get:InputFiles
    @get:IgnoreEmptyDirectories
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val pages: ConfigurableFileCollection

    /** Files of the documentation copied as they are. */
    @get:InputFiles
    @get:IgnoreEmptyDirectories
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val staticFiles: ConfigurableFileCollection

    /** Output of [RenderPlantUmlTask]; absent without diagrams. */
    @get:InputFiles
    @get:IgnoreEmptyDirectories
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val renderedDiagrams: DirectoryProperty

    /** Preprocessed sources of the diagrams from [RenderPlantUmlTask], for the browser rendering. */
    @get:InputFiles
    @get:IgnoreEmptyDirectories
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val preprocessedDiagrams: DirectoryProperty

    /** `auto`, `browser` or `build`. */
    @get:Input
    abstract val diagramRender: Property<String>

    /** Archive of the npm package `@plantuml/core`; empty without it. */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val plantUmlBundle: ConfigurableFileCollection

    /** Joined Dokka HTML from [AggregateApiReferenceTask]; absent without the API reference. */
    @get:Optional
    @get:InputFiles
    @get:IgnoreEmptyDirectories
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val apiReference: DirectoryProperty

    @get:Input
    abstract val siteTitle: Property<String>

    /** README path relative to the repository root. */
    @get:Input
    abstract val readmePath: Property<String>

    /** Path of the table of contents relative to the repository root. */
    @get:Input
    abstract val indexPath: Property<String>

    @get:Optional
    @get:Input
    abstract val sourceBrowseUrl: Property<String>

    /** Language of the interface, `en` or `ru`. */
    @get:Input
    abstract val language: Property<String>

    /** Titles of the sidebar groups by the lowercased heading, over the defaults of [language]. */
    @get:Input
    abstract val groupTitles: MapProperty<String, String>

    /** `heading` or `link`, see [TitleSource]. */
    @get:Input
    abstract val titleSource: Property<String>

    /** Globs of the tables of contents. */
    @get:Input
    abstract val hubPatterns: ListProperty<String>

    @get:Input
    abstract val detectHubs: Property<Boolean>

    @get:Input
    abstract val contentsHeadings: ListProperty<String>

    /** Library directories by Dokka module name. */
    @get:Input
    abstract val libraries: MapProperty<String, String>

    @get:Input
    abstract val referenceHeadings: ListProperty<String>

    /** `gitlab` or `github`, see [AnchorStyle]. */
    @get:Input
    abstract val anchorStyle: Property<String>

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    /** Paths of the pages and the static files relative to the repository root: they set the layout of the site. */
    @get:Input
    val sourcePaths: List<String>
        get() {
            val root = repositoryRoot.get().asFile
            return (pages.relativeTo(root).keys + staticFiles.relativeTo(root).keys).sorted()
        }

    @TaskAction
    fun build() {
        val root = repositoryRoot.get().asFile
        val api = apiReference.orNull?.asFile?.takeIf { it.isDirectory }
        val modules = api?.let {
            AggregateApiReferenceTask.readModules(it.resolve(AggregateApiReferenceTask.MODULES_FILE))
        }
        val render = DiagramRender.valueOf(diagramRender.get().uppercase())
        val sources = SiteSources(
            title = siteTitle.get(),
            pages = pages.relativeTo(root),
            staticFiles = staticFiles.relativeTo(root),
            renderedDiagrams = filesUnder(renderedDiagrams.orNull?.asFile),
            preprocessedDiagrams = filesUnder(preprocessedDiagrams.orNull?.asFile)
                .mapKeys { (path, _) -> path.removeSuffix(".preproc") + ".puml" },
            diagramRender = render,
            plantUmlBundle = plantUmlBundle.files.firstOrNull { it.name.endsWith(".tgz") },
            apiReference = api,
            apiModules = modules.orEmpty().map { ApiModuleEntry(it.name, it.directory) },
            readme = readmePath.get(),
            index = indexPath.get(),
            sourceBrowseUrl = sourceBrowseUrl.orNull,
            language = language.get(),
            navigation = NavigationOptions(
                groupTitles = groupTitles.get(),
                titleSource = TitleSource.valueOf(titleSource.get().uppercase()),
                hubPatterns = hubPatterns.get(),
                detectHubs = detectHubs.get(),
                referenceHeadings = referenceHeadings.get(),
                libraries = libraries.get(),
            ),
            contentsHeadings = contentsHeadings.get().toSet(),
            anchorStyle = AnchorStyle.valueOf(anchorStyle.get().uppercase()),
        )
        val output = outputDirectory.get().asFile
        SiteBuilder(sources, output) { logger.warn(it) }.build()

        logger.lifecycle("Documentation site: ${output.resolve("index.html").toURI()}")
    }
}
