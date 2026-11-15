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

package io.github.wolfandrei.docssite

import io.github.wolfandrei.docssite.check.EditorConfig
import io.github.wolfandrei.docssite.check.EditorConfigLineLengthValueSource
import io.github.wolfandrei.docssite.check.RepositoryPathsValueSource
import io.github.wolfandrei.docssite.lint.RumdlDistribution
import io.github.wolfandrei.docssite.plantuml.GraphvizValueSource
import io.github.wolfandrei.docssite.tasks.AggregateApiReferenceTask
import io.github.wolfandrei.docssite.tasks.BuildDocumentationSiteTask
import io.github.wolfandrei.docssite.tasks.CheckDocumentationTask
import io.github.wolfandrei.docssite.tasks.FormatDocumentationTask
import io.github.wolfandrei.docssite.tasks.MarkdownLintSettings
import io.github.wolfandrei.docssite.tasks.RenderPlantUmlTask
import io.github.wolfandrei.docssite.tasks.ServeDocumentationSiteTask
import io.github.wolfandrei.docssite.tasks.WriteDiagramsTask
import io.github.wolfandrei.docssite.markdown.MarkdownPage
import io.github.wolfandrei.docssite.site.NavigationOptions
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.attributes.Attribute
import org.gradle.api.attributes.Usage
import org.gradle.api.file.Directory
import org.gradle.api.file.FileCollection
import org.gradle.api.file.FileTree
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.register

/**
 * `io.github.wolfandrei.docs-site`: a local site of the repository documentation. Applied to the root project.
 *
 * - `docsBuild` builds the site in `build/docs-site`: README and the Markdown pages of `documentation` as
 *   HTML, the diagrams from their PlantUML sources, the interactive pages, the API reference and the search index;
 * - `docs` serves it on `localhost` until Ctrl+C;
 * - `docsCheck` checks links, anchors, diagrams and the style of the Markdown pages, also with rumdl;
 * - `docsFormat` fixes the Markdown pages in place with rumdl.
 *
 * The plugin configures only the root project: the API reference comes from the Dokka artifacts of the modules that
 * apply Dokka themselves, resolved by project dependencies.
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
        val api = registerApiReference(extension)
        val plantUmlJs = lenientFiles(
            resolvable(PLANTUML_JS_CONFIGURATION, "Browser build of PlantUML, the npm package @plantuml/core.") {
                isTransitive = false
                defaultDependencies {
                    // Build rendering draws no diagram in the browser: the package is not downloaded.
                    if (extension.plantUml.render.get() != RENDER_BUILD) {
                        add(project.dependencies.create("$PLANTUML_JS_MODULE:${extension.plantUml.version.get()}@tgz"))
                    }
                }
            }
        )

        val site = tasks.register<BuildDocumentationSiteTask>(SITE_TASK) {
            group = GROUP
            description = "Builds the documentation site, by default in build/docs-site."
            repositoryRoot.set(layout.projectDirectory)
            this.pages.from(pages)
            this.staticFiles.from(staticFiles)
            renderedDiagrams.set(render.flatMap { it.outputDirectory.dir(RenderPlantUmlTask.SVG_DIR) })
            preprocessedDiagrams.set(render.flatMap { it.outputDirectory.dir(RenderPlantUmlTask.SOURCE_DIR) })
            diagramRender.set(extension.plantUml.render)
            plantUmlBundle.from(plantUmlJs)
            apiReference.set(
                extension.apiReference.enabled.flatMap { enabled ->
                    if (enabled) api.flatMap { it.outputDirectory } else provider<Directory> { null }
                }
            )
            siteTitle.set(extension.title)
            readmePath.set(extension.readme.map { it.asFile.relativeTo(rootDir).invariantSeparatorsPath })
            indexPath.set(extension.index.map { it.asFile.relativeTo(rootDir).invariantSeparatorsPath })
            sourceBrowseUrl.set(extension.sourceBrowseUrl)
            language.set(extension.language)
            groupTitles.set(extension.navigation.groupTitles)
            titleSource.set(extension.navigation.titleSource)
            hubPatterns.set(extension.navigation.hubs)
            detectHubs.set(extension.navigation.detectHubs)
            contentsHeadings.set(extension.navigation.contentsHeadings)
            libraries.set(extension.apiReference.libraries)
            referenceHeadings.set(extension.apiReference.referenceHeadings)
            anchorStyle.set(extension.anchorStyle)
            outputDirectory.set(extension.outputDirectory)
        }

        val checkTask = tasks.register<CheckDocumentationTask>(CHECK_TASK) {
            group = GROUP
            description = "Checks links, anchors, diagrams and the style of the documentation."
            repositoryRoot.set(layout.projectDirectory)
            this.pages.from(pages)
            this.staticFiles.from(staticFiles)
            repositoryPaths.set(
                providers.of(RepositoryPathsValueSource::class.java) {
                    parameters.root.set(layout.projectDirectory)
                    parameters.excludedDirectories.set(extension.checks.excludedDirectories)
                }
            )
            renderedDiagrams.set(render.flatMap { it.outputDirectory.dir(RenderPlantUmlTask.SVG_DIR) })
            plantUmlReport.set(render.flatMap { it.reportFile })
            maxLineLength.set(extension.checks.maxLineLength)
            editorConfigFiles.from(extension.index.map { EditorConfig.candidates(it.asFile.parentFile) })
            lineLengthInTables.set(extension.checks.lineLengthInTables)
            forbiddenCharacters.set(extension.checks.forbiddenCharacters)
            anchorStyle.set(extension.anchorStyle)
            checkCommittedDiagrams.set(extension.checks.committedDiagrams)
            configureMarkdownLint(markdownLint, extension, pages)
            reportFile.set(layout.buildDirectory.file("docs-site-check/report.txt"))
        }

        tasks.register<WriteDiagramsTask>(DIAGRAMS_TASK) {
            group = GROUP
            description = "Writes the SVG of every PlantUML diagram next to its source, as the site draws it."
            renderedDiagrams.set(render.flatMap { it.outputDirectory.dir(RenderPlantUmlTask.SVG_DIR) })
            plantUmlReport.set(render.flatMap { it.reportFile })
            repositoryRoot.set(layout.projectDirectory)
        }

        tasks.register<FormatDocumentationTask>(FORMAT_TASK) {
            group = GROUP
            description = "Fixes the Markdown pages of the documentation in place with rumdl."
            repositoryRoot.set(layout.projectDirectory)
            this.pages.from(pages)
            configureMarkdownLint(markdownLint, extension, pages)
        }

        tasks.named { it == "check" }.configureEach {
            dependsOn(extension.checks.runWithCheck.map { if (it) listOf(checkTask) else emptyList() })
        }

        tasks.register<ServeDocumentationSiteTask>(SERVE_TASK) {
            group = GROUP
            description = "Serves the documentation site on localhost until Ctrl+C."
            dependsOn(site)
            siteDirectory.set(extension.outputDirectory)
            repositoryRoot.set(layout.projectDirectory)
            port.set(extension.serve.port)
        }
    }

    private fun Project.configureConventions(extension: DocumentationSiteExtension) {
        extension.title.convention(name)
        extension.readme.convention(layout.projectDirectory.file("README.md"))
        extension.documentationDirectory.convention(
            layout.projectDirectory.dir(
                providers.of(DocumentationDirectoryValueSource::class.java) {
                    parameters.root.set(layout.projectDirectory)
                }
            )
        )
        extension.index.convention(extension.documentationDirectory.file("index.md"))
        extension.outputDirectory.convention(layout.buildDirectory.dir("docs-site"))
        extension.plantUml.version.convention(PLANTUML_VERSION)
        extension.plantUml.layout.convention(RenderPlantUmlTask.AUTO)
        extension.plantUml.render.convention(RENDER_BUILD)
        extension.apiReference.enabled.convention(true)
        extension.checks.runWithCheck.convention(false)
        extension.checks.maxLineLength.convention(
            providers.of(EditorConfigLineLengthValueSource::class.java) { parameters.file.set(extension.index) }
        )
        extension.checks.lineLengthInTables.convention(false)
        extension.checks.forbiddenCharacters.convention(emptyList())
        extension.language.convention("en")
        extension.navigation.groupTitles.convention(emptyMap())
        extension.navigation.titleSource.convention("heading")
        extension.navigation.hubs.convention(NavigationOptions.DEFAULT_HUB_PATTERNS)
        extension.navigation.detectHubs.convention(true)
        extension.navigation.contentsHeadings.convention(MarkdownPage.DEFAULT_CONTENTS_HEADINGS.toList())
        extension.apiReference.libraries.convention(emptyMap())
        extension.apiReference.referenceHeadings.convention(NavigationOptions.DEFAULT_REFERENCE_HEADINGS)
        extension.anchorStyle.convention("github")
        extension.checks.committedDiagrams.convention(false)
        extension.checks.excludedDirectories.convention(RepositoryPathsValueSource.DEFAULT_EXCLUDED_DIRECTORIES)
        extension.checks.markdownLint.apply {
            enabled.convention(true)
            version.convention(RUMDL_VERSION)
            distributionUrl.convention(RumdlDistribution.DEFAULT_URL)
            checksums.convention(
                version.map { if (it == RUMDL_VERSION) RumdlDistribution.CHECKSUMS else emptyMap() }
            )
            disabledRules.convention(emptyList())
        }
        extension.serve.port.convention(
            providers.gradleProperty(PORT_PROPERTY).map(String::toInt).orElse(DEFAULT_PORT)
        )
    }

    private fun Project.configureMarkdownLint(
        settings: MarkdownLintSettings,
        extension: DocumentationSiteExtension,
        pages: FileCollection,
    ) {
        val lint = extension.checks.markdownLint
        settings.enabled.set(lint.enabled)
        settings.executable.set(lint.executable)
        settings.version.set(lint.version)
        settings.distributionUrl.set(lint.distributionUrl)
        settings.checksums.set(lint.checksums)
        settings.configFile.set(lint.configFile)
        // rumdl takes the configuration of the nearest directory of a page that has one, up to the repository root.
        val root = layout.projectDirectory.asFile
        settings.discoveredConfigFiles.from(
            pages.elements.map { locations ->
                locations.asSequence()
                    .flatMap { page ->
                        generateSequence(page.asFile.parentFile) { it.parentFile }.takeWhile { it.startsWith(root) }
                    }
                    .distinct()
                    .flatMap { directory -> RUMDL_CONFIG_FILES.map(directory::resolve) }
                    .toList()
            }
        )
        // The plugin checks the links and the anchors itself, with the anchor rules of the site, and the line length
        // with its own exceptions: the same rules of rumdl would report the same problems twice.
        settings.disabledRules.set(
            lint.disabledRules.zip(extension.checks.maxLineLength) { rules, maxLineLength ->
                rules + RUMDL_RULES_OF_PLUGIN + if (maxLineLength > 0) listOf(RUMDL_LINE_LENGTH_RULE) else emptyList()
            }
        )
        settings.cacheDirectory.set(gradle.gradleUserHomeDir.resolve(RUMDL_CACHE))
        settings.offline.set(gradle.startParameter.isOffline)
    }

    private fun Project.registerRender(extension: DocumentationSiteExtension, documentation: FileTree) =
        tasks.register<RenderPlantUmlTask>(RENDER_TASK) {
            group = GROUP
            description = "Renders the PlantUML diagrams of the documentation to SVG and preprocessed sources."

            val classpath = resolvable(PLANTUML_CONFIGURATION, "PlantUML that renders the diagrams of the site.") {
                isTransitive = false
                defaultDependencies {
                    add(project.dependencies.create("$PLANTUML_MODULE:${extension.plantUml.version.get()}"))
                    // The ELK layout of plantuml-epl needs the Xtend runtime, which the EPL build leaves out.
                    add(project.dependencies.create(XBASE_LIB_MODULE))
                }
            }

            diagrams.from(documentation.matching { include("**/*.puml") })
            includes.from(documentation.matching { include(PLANTUML_INCLUDES) })
            repositoryRoot.set(project.layout.projectDirectory)
            plantUmlClasspath.from(classpath)
            layout.set(extension.plantUml.layout)
            graphviz.set(
                providers.of(GraphvizValueSource::class.java) {
                    parameters.executable.set(extension.plantUml.dotExecutable)
                }
            )
            outputDirectory.set(project.layout.buildDirectory.dir("docs-site-diagrams"))
            reportFile.set(project.layout.buildDirectory.file("docs-site-diagrams-report.json"))
        }

    /**
     * The API reference: the root resolves the Dokka module outputs of every project leniently, so a project without
     * Dokka has no such variant and is skipped; then the Dokka command line joins the outputs.
     */
    private fun Project.registerApiReference(extension: DocumentationSiteExtension) =
        tasks.register<AggregateApiReferenceTask>(API_TASK) {
            group = GROUP
            description = "Joins the Dokka HTML of the modules that apply Dokka into one API reference."

            val api = extension.apiReference
            // A detached configuration has a root component of its own: a module named as the root project has the
            // coordinates of the root, and in a configuration of the root project it would resolve to the root itself.
            val modules = project.configurations.detachedConfiguration().apply {
                isCanBeConsumed = false
                attributes {
                    attribute(Usage.USAGE_ATTRIBUTE, project.objects.named(DOKKA_USAGE))
                    attribute(DOKKA_FORMAT, "html")
                    attribute(DOKKA_MODULE_COMPONENT, "ModuleOutputDirectories")
                }
                rootProject.subprojects.map { it.path }.forEach { path ->
                    dependencies.add(project.dependencies.project(mapOf("path" to path)))
                }
                // The root only with its own Dokka.
                project.pluginManager.withPlugin(DOKKA_PLUGIN) {
                    dependencies.add(project.dependencies.project(mapOf("path" to project.path)))
                }
            }
            val included = api.modules
            val excluded = api.excludedModules
            // A project without Dokka may still match by missing attributes, such as the source directories of
            // `java`; only the directories with a Dokka module descriptor are module outputs.
            val outputs = modules.incoming.artifactView {
                lenient(true)
                componentFilter { id ->
                    val path = (id as? ProjectComponentIdentifier)?.projectPath
                    path != null && isSelected(path, included.get(), excluded.get())
                }
            }.files
            moduleOutputs.from(outputs.filter { it.resolve(AggregateApiReferenceTask.DESCRIPTOR).isFile })
            dokkaCli.from(
                resolvable(DOKKA_CLI_CONFIGURATION, "Dokka command line that joins the API reference.") {
                    isTransitive = false
                    defaultDependencies { add(project.dependencies.create("$DOKKA_GROUP:dokka-cli:$DOKKA_VERSION")) }
                }
            )
            dokkaPlugins.from(
                resolvable(DOKKA_PLUGINS_CONFIGURATION, "Dokka plugins that join the API reference.") {
                    defaultDependencies {
                        DOKKA_PLUGINS.forEach { add(project.dependencies.create("$DOKKA_GROUP:$it:$DOKKA_VERSION")) }
                    }
                }
            )
            referenceName.set(extension.title)
            outputDirectory.set(project.layout.buildDirectory.dir("docs-site-api"))
        }

    private fun Project.resolvable(name: String, text: String, configure: Configuration.() -> Unit): Configuration =
        configurations.findByName(name) ?: configurations.create(name) {
            isCanBeConsumed = false
            isCanBeResolved = true
            description = text
            configure()
        }

    /** Files of [configuration], or none if it does not resolve, for example without a repository of the package. */
    private fun lenientFiles(configuration: Configuration): FileCollection =
        configuration.incoming.artifactView { lenient(true) }.files

    internal companion object {
        const val PLUGIN_ID = "io.github.wolfandrei.docs-site"
        const val GROUP = "documentation"
        const val SITE_TASK = "docsBuild"
        const val SERVE_TASK = "docs"
        const val CHECK_TASK = "docsCheck"
        const val FORMAT_TASK = "docsFormat"
        const val RENDER_TASK = "docsRenderDiagrams"
        const val API_TASK = "docsApiReference"
        const val PLANTUML_CONFIGURATION = "documentationPlantUml"
        const val PLANTUML_JS_CONFIGURATION = "documentationPlantUmlJs"
        const val DOKKA_CLI_CONFIGURATION = "documentationDokkaCli"
        const val DOKKA_PLUGINS_CONFIGURATION = "documentationDokkaPlugins"
        const val DIAGRAMS_TASK = "docsDiagrams"

        /** The EPL build of PlantUML: unlike the MIT one, it has the ELK layout (`!pragma layout elk`). */
        const val PLANTUML_MODULE = "net.sourceforge.plantuml:plantuml-epl"

        /** Xtend runtime of ELK, EPL-2.0; 2.36.0 is the last release for Java 11, later ones need Java 21. */
        const val XBASE_LIB_MODULE = "org.eclipse.xtext:org.eclipse.xtext.xbase.lib:2.36.0"

        /** The npm package `@plantuml/core`, resolved through an Ivy repository over an npm registry. */
        const val PLANTUML_JS_MODULE = "plantuml-npm:core"
        const val PLANTUML_VERSION = "1.2026.9"
        const val RENDER_BUILD = "build"
        const val DOKKA_GROUP = "org.jetbrains.dokka"
        const val DOKKA_VERSION = "2.1.0"
        const val DOKKA_USAGE = "org.jetbrains.dokka"
        const val DOKKA_PLUGIN = "org.jetbrains.dokka"
        val DOKKA_FORMAT: Attribute<String> = Attribute.of("org.jetbrains.dokka.format", String::class.java)
        val DOKKA_MODULE_COMPONENT: Attribute<String> =
            Attribute.of("org.jetbrains.dokka.module-component", String::class.java)
        val DOKKA_PLUGINS =
            listOf("dokka-base", "templating-plugin", "all-modules-page-plugin", "analysis-kotlin-symbols")
        const val RUMDL_VERSION = RumdlDistribution.VERSION
        const val RUMDL_CACHE = "caches/docs-site/rumdl"

        /** Configuration files rumdl finds in the directory of a page and above, inputs of the tasks. */
        val RUMDL_CONFIG_FILES = listOf(
            ".rumdl.toml",
            "rumdl.toml",
            ".config/rumdl.toml",
            "pyproject.toml",
            ".markdownlint.json",
            ".markdownlint.jsonc",
            ".markdownlint.yaml",
            ".markdownlint.yml",
            ".markdownlintignore",
        )

        /** Rules of rumdl the plugin checks itself: link fragments (MD051) and relative links (MD057). */
        val RUMDL_RULES_OF_PLUGIN = listOf("MD051", "MD057")
        const val RUMDL_LINE_LENGTH_RULE = "MD013"
        const val PORT_PROPERTY = "docs.port"
        const val DEFAULT_PORT = 8000
        val PLANTUML_INCLUDES = listOf("**/*.puml", "**/*.iuml", "**/*.plantuml", "**/*.pu")

        /** Whether the module [path] is documented: listed in [included] or none listed, and not under [excluded]. */
        fun isSelected(path: String, included: Set<String>, excluded: Set<String>): Boolean =
            (included.isEmpty() || path in included) &&
                excluded.none { path == it || path.startsWith(if (it == ":") ":" else "$it:") }
    }
}
