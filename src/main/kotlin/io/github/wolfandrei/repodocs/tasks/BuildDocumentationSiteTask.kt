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

package io.github.wolfandrei.repodocs.tasks

import io.github.wolfandrei.repodocs.AnchorStyle
import io.github.wolfandrei.repodocs.site.DokkaReference
import io.github.wolfandrei.repodocs.site.NavigationOptions
import io.github.wolfandrei.repodocs.site.SiteBuilder
import io.github.wolfandrei.repodocs.site.SiteSources
import java.io.File
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
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

    /** The joined Dokka HTML of the modules; absent without the API reference. */
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

    /** Language of the interface, `en` or `ru`. */
    @get:Input
    abstract val language: Property<String>

    /** Globs of the tables of contents. */
    @get:Input
    abstract val hubPatterns: ListProperty<String>

    @get:Input
    abstract val detectHubs: Property<Boolean>

    @get:Input
    abstract val contentsHeadings: ListProperty<String>

    @get:Input
    abstract val anchorStyle: Property<AnchorStyle>

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
        checkOutputDirectory(root)
        // Dokka writes a publication even without modules: such a reference is no reference.
        val modules = apiReference.orNull?.asFile?.takeIf { it.isDirectory }?.let(DokkaReference::modules).orEmpty()
        val api = apiReference.orNull?.asFile?.takeIf { modules.isNotEmpty() }
        val sources = SiteSources(
            title = siteTitle.get(),
            pages = pages.relativeTo(root),
            staticFiles = staticFiles.relativeTo(root),
            renderedDiagrams = filesUnder(renderedDiagrams.orNull?.asFile),
            apiReference = api,
            apiModules = modules,
            readme = readmePath.get(),
            index = indexPath.get(),
            repositoryRoot = root,
            language = language.get(),
            navigation = NavigationOptions(
                hubPatterns = hubPatterns.get(),
                detectHubs = detectHubs.get(),
            ),
            contentsHeadings = contentsHeadings.get().toSet(),
            anchorStyle = anchorStyle.get(),
        )
        val output = outputDirectory.get().asFile
        SiteBuilder(sources, output) { logger.warn(it) }.build()

        logger.lifecycle("Documentation site: ${output.resolve("index.html").toURI()}")
    }

    /**
     * The site directory is cleared before a build, so it must hold nothing else: not the repository, not a source of
     * the site. A mistaken `outputDirectory`, such as the repository root, would otherwise delete the sources.
     */
    private fun checkOutputDirectory(root: File) {
        val output = outputDirectory.get().asFile.canonicalFile
        val sources = pages.files + staticFiles.files
        val clash = when {
            root.canonicalFile.startsWith(output) -> "contains the repository"
            sources.any { it.canonicalFile.startsWith(output) } -> "contains sources of the site"
            else -> null
        }
        if (clash != null) {
            throw GradleException("docs.outputDirectory $output $clash, and docsBuild would delete them; choose a directory under build/")
        }
    }
}
