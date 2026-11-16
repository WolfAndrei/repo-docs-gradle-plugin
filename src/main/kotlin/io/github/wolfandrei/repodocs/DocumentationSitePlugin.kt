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

package io.github.wolfandrei.repodocs

import io.github.wolfandrei.repodocs.check.RepositoryPathsValueSource
import io.github.wolfandrei.repodocs.lint.Rumdl
import io.github.wolfandrei.repodocs.markdown.MarkdownPage
import io.github.wolfandrei.repodocs.plantuml.GraphvizValueSource
import io.github.wolfandrei.repodocs.site.NavigationOptions
import io.github.wolfandrei.repodocs.tasks.BuildDocumentationSiteTask
import io.github.wolfandrei.repodocs.tasks.CheckDocumentationTask
import io.github.wolfandrei.repodocs.tasks.FormatDocumentationTask
import io.github.wolfandrei.repodocs.tasks.MarkdownLintSettings
import io.github.wolfandrei.repodocs.tasks.OpenDocumentationSiteTask
import io.github.wolfandrei.repodocs.tasks.RenderPlantUmlTask
import io.github.wolfandrei.repodocs.tasks.WriteDiagramsTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.FileCollection
import org.gradle.api.file.FileTree
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.register

/**
 * `io.github.wolfandrei.repo-docs`: a local site of the repository documentation. Applied to the root project.
 *
 * - `docsBuild` builds the site in `build/repo-docs`: README and the Markdown pages of `documentation` as
 *   HTML, the diagrams from their PlantUML sources, the HTML pages, the API reference and the search index;
 * - `docs` builds it and opens it in the browser;
 * - `docsCheck` checks links, anchors, diagrams and the style of the Markdown pages, also with rumdl;
 * - `docsFormat` fixes the Markdown pages in place with rumdl.
 *
 * The plugin configures only the root project. The API reference is the Dokka publication of the root, when the
 * root applies Dokka, see [DokkaApiReference].
 */
internal class DocumentationSitePlugin : Plugin<Project> {
    override fun apply(project: Project): Unit = with(project) {
        check(this == rootProject) { "$PLUGIN_ID is applied to the root project, not to $path" }

        val extension = extensions.create<DocumentationSiteExtension>(DocumentationSiteExtension.NAME)
        configureConventions(extension)

        val documentation = objects.fileTree().from(extension.documentationDirectory)
        val pages = files(
            extension.readme.map { file -> listOf(file.asFile).filter { it.isFile } },
            documentation.matching { include("**/*.md") },
            extension.additionalPages,
        )
        val staticFiles = documentation.matching { exclude("**/*.md") }

        val render = registerRender(extension, documentation)
        val api = objects.directoryProperty()
        DokkaApiReference.connect(this, api)

        val site = tasks.register<BuildDocumentationSiteTask>(SITE_TASK) {
            group = GROUP
            description = "Builds the documentation site, by default in build/repo-docs."
            repositoryRoot.set(extension.repositoryRoot)
            this.pages.from(pages)
            this.staticFiles.from(staticFiles)
            renderedDiagrams.set(render.flatMap { it.outputDirectory.dir(RenderPlantUmlTask.SVG_DIR) })
            apiReference.set(api)
            siteTitle.set(extension.title)
            readmePath.set(extension.readme.zip(extension.repositoryRoot) { file, root ->
                file.asFile.relativeTo(root.asFile).invariantSeparatorsPath
            })
            indexPath.set(extension.index.zip(extension.repositoryRoot) { file, root ->
                file.asFile.relativeTo(root.asFile).invariantSeparatorsPath
            })
            language.set(extension.language)
            hubPatterns.set(extension.navigation.hubs)
            detectHubs.set(extension.navigation.detectHubs)
            contentsHeadings.set(extension.navigation.contentsHeadings)
            anchorStyle.set(extension.anchorStyle)
            outputDirectory.set(extension.outputDirectory)
        }

        val checkTask = tasks.register<CheckDocumentationTask>(CHECK_TASK) {
            group = GROUP
            description = "Checks links, anchors, diagrams and the style of the documentation."
            repositoryRoot.set(extension.repositoryRoot)
            this.pages.from(pages)
            this.staticFiles.from(staticFiles)
            repositoryPaths.set(
                providers.of(RepositoryPathsValueSource::class.java) {
                    parameters.root.set(extension.repositoryRoot)
                    parameters.excludedDirectories.set(extension.checks.excludedDirectories)
                }
            )
            renderedDiagrams.set(render.flatMap { it.outputDirectory.dir(RenderPlantUmlTask.SVG_DIR) })
            plantUmlReport.set(render.flatMap { it.reportFile })
            anchorStyle.set(extension.anchorStyle)
            configureMarkdownLint(markdownLint, extension, pages)
            reportFile.set(layout.buildDirectory.file("repo-docs-check/report.txt"))
        }

        tasks.register<WriteDiagramsTask>(DIAGRAMS_TASK) {
            group = GROUP
            description = "Writes the SVG of every PlantUML diagram next to its source, as the site draws it."
            renderedDiagrams.set(render.flatMap { it.outputDirectory.dir(RenderPlantUmlTask.SVG_DIR) })
            plantUmlReport.set(render.flatMap { it.reportFile })
            repositoryRoot.set(extension.repositoryRoot)
        }

        tasks.register<FormatDocumentationTask>(FORMAT_TASK) {
            group = GROUP
            description = "Fixes the Markdown pages of the documentation in place with rumdl."
            repositoryRoot.set(extension.repositoryRoot)
            this.pages.from(pages)
            configureMarkdownLint(markdownLint, extension, pages)
        }

        tasks.named { it == "check" }.configureEach {
            dependsOn(extension.checks.runWithCheck.map { if (it) listOf(checkTask) else emptyList() })
        }

        tasks.register<OpenDocumentationSiteTask>(OPEN_TASK) {
            group = GROUP
            description = "Builds the documentation site and opens it in the browser."
            dependsOn(site)
            siteDirectory.set(extension.outputDirectory)
            continuous.set(gradle.startParameter.isContinuous)
        }
    }

    private fun Project.configureConventions(extension: DocumentationSiteExtension) {
        extension.title.convention(name)
        extension.repositoryRoot.convention(layout.projectDirectory)
        extension.readme.convention(extension.repositoryRoot.file("README.md"))
        extension.documentationDirectory.convention(
            extension.repositoryRoot.dir(
                providers.of(DocumentationDirectoryValueSource::class.java) {
                    parameters.root.set(extension.repositoryRoot)
                }
            )
        )
        extension.index.convention(extension.documentationDirectory.file("index.md"))
        extension.outputDirectory.convention(layout.buildDirectory.dir("repo-docs"))
        extension.plantUml.layout.convention(DiagramLayout.SMETANA)
        extension.checks.runWithCheck.convention(false)
        extension.language.convention("en")
        extension.navigation.hubs.convention(NavigationOptions.DEFAULT_HUB_PATTERNS)
        extension.navigation.detectHubs.convention(true)
        extension.navigation.contentsHeadings.convention(MarkdownPage.DEFAULT_CONTENTS_HEADINGS.toList())
        extension.anchorStyle.convention(AnchorStyle.GITHUB)
        extension.checks.excludedDirectories.convention(RepositoryPathsValueSource.DEFAULT_EXCLUDED_DIRECTORIES)
    }

    private fun Project.configureMarkdownLint(
        settings: MarkdownLintSettings,
        extension: DocumentationSiteExtension,
        pages: FileCollection,
    ) {
        // rumdl takes the configuration of the nearest directory of a page that has one, up to the repository root.
        settings.discoveredConfigFiles.from(
            pages.elements.zip(extension.repositoryRoot) { locations, repository ->
                val root = repository.asFile
                locations.asSequence()
                    .flatMap { page ->
                        generateSequence(page.asFile.parentFile) { it.parentFile }.takeWhile { it.startsWith(root) }
                    }
                    .distinct()
                    .map { directory -> directory.resolve(Rumdl.CONFIG_FILE) }
                    .toList()
            }
        )
        // The plugin checks the links and the anchors itself, with the anchor rules of the site: the same rules of
        // rumdl would report the same problems twice.
        settings.disabledRules.set(RUMDL_RULES_OF_PLUGIN)
    }

    private fun Project.registerRender(extension: DocumentationSiteExtension, documentation: FileTree) =
        tasks.register<RenderPlantUmlTask>(RENDER_TASK) {
            group = GROUP
            description = "Renders the PlantUML diagrams of the documentation to SVG and preprocessed sources."

            diagrams.from(documentation.matching { include("**/*.puml") })
            includes.from(documentation.matching { include(PLANTUML_INCLUDES) })
            repositoryRoot.set(extension.repositoryRoot)
            layout.set(extension.plantUml.layout)
            // Graphviz is an input only for the engines that use it, so Smetana builds share the cache everywhere.
            val dot = providers.of(GraphvizValueSource::class.java) {}
            graphviz.set(extension.plantUml.layout.flatMap { if (it == DiagramLayout.SMETANA) provider { "" } else dot })
            outputDirectory.set(project.layout.buildDirectory.dir("repo-docs-diagrams"))
            reportFile.set(project.layout.buildDirectory.file("repo-docs-diagrams-report.json"))
        }

    internal companion object {
        const val PLUGIN_ID = "io.github.wolfandrei.repo-docs"
        const val GROUP = "documentation"
        const val SITE_TASK = "docsBuild"
        const val OPEN_TASK = "docs"
        const val CHECK_TASK = "docsCheck"
        const val FORMAT_TASK = "docsFormat"
        const val RENDER_TASK = "docsRenderDiagrams"
        const val DIAGRAMS_TASK = "docsDiagrams"

        /** Rules of rumdl the plugin checks itself: link fragments (MD051) and relative links (MD057). */
        val RUMDL_RULES_OF_PLUGIN = listOf("MD051", "MD057")
        val PLANTUML_INCLUDES = listOf("**/*.puml", "**/*.iuml", "**/*.plantuml", "**/*.pu")
    }
}
