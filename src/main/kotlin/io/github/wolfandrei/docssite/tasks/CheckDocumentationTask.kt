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

import io.github.wolfandrei.docssite.check.DocumentationChecker
import io.github.wolfandrei.docssite.check.DocumentationProblem
import io.github.wolfandrei.docssite.check.DocumentationSources
import io.github.wolfandrei.docssite.plantuml.PlantUmlReport
import io.github.wolfandrei.docssite.markdown.AnchorStyle
import java.io.File
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.IgnoreEmptyDirectories
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

/**
 * Checks the documentation: relative links and anchors, pages referenced from the indexes, PlantUML errors, committed
 * SVG that do not match their source, long lines and forbidden characters, and the rules of the rumdl Markdown linter.
 * Fails with the list of problems; pages that no page links to are only reported.
 */
@CacheableTask
internal abstract class CheckDocumentationTask : DefaultTask() {
    @get:Internal
    abstract val repositoryRoot: DirectoryProperty

    @get:InputFiles
    @get:IgnoreEmptyDirectories
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val pages: ConfigurableFileCollection

    /** Files of the documentation other than Markdown: interactive pages and committed SVG are checked. */
    @get:InputFiles
    @get:IgnoreEmptyDirectories
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val staticFiles: ConfigurableFileCollection

    /** Files and directories of the repository relative to its root, see `RepositoryPathsValueSource`. */
    @get:Input
    abstract val repositoryPaths: SetProperty<String>

    @get:InputFiles
    @get:IgnoreEmptyDirectories
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val renderedDiagrams: DirectoryProperty

    /** Report of [RenderPlantUmlTask]; missing without diagrams. */
    @get:Optional
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val plantUmlReport: RegularFileProperty

    @get:Input
    abstract val maxLineLength: Property<Int>

    @get:Input
    abstract val lineLengthInTables: Property<Boolean>

    @get:Input
    abstract val forbiddenCharacters: ListProperty<String>

    @get:Input
    abstract val checkCommittedDiagrams: Property<Boolean>

    /** `gitlab` or `github`, see [io.github.wolfandrei.docssite.markdown.AnchorStyle]. */
    @get:Input
    abstract val anchorStyle: Property<String>

    @get:Nested
    abstract val markdownLint: MarkdownLintSettings

    @get:OutputFile
    abstract val reportFile: RegularFileProperty

    @get:Input
    val sourcePaths: List<String>
        get() {
            val root = repositoryRoot.get().asFile
            return (pages.relativeTo(root).keys + staticFiles.relativeTo(root).keys).sorted()
        }

    @TaskAction
    fun check() {
        val root = repositoryRoot.get().asFile
        val staticFiles = staticFiles.relativeTo(root)
        val sources = DocumentationSources(
            pages = pages.relativeTo(root),
            htmlPages = staticFiles.filterKeys { it.endsWith(".html", ignoreCase = true) },
            repositoryPaths = repositoryPaths.get(),
            plantUmlReport = PlantUmlReport.read(plantUmlReport.orNull?.asFile),
            renderedDiagrams = filesUnder(renderedDiagrams.orNull?.asFile),
            committedDiagrams = staticFiles.filterKeys { it.endsWith(".svg", ignoreCase = true) },
            maxLineLength = maxLineLength.get(),
            lineLengthInTables = lineLengthInTables.get(),
            forbiddenCharacters = forbiddenCharacters.get(),
            checkCommittedDiagrams = checkCommittedDiagrams.get(),
            anchorStyle = AnchorStyle.valueOf(anchorStyle.get().uppercase()),
        )
        val problems = DocumentationChecker(sources).check() + lint(root, sources.pages.keys.toList())
        val (warnings, errors) = problems.partition { it.warning }

        reportFile.get().asFile.apply { parentFile.mkdirs() }.writeText(problems.joinToString("\n", postfix = "\n"))
        warnings.forEach { logger.warn(it.toString()) }
        if (errors.isNotEmpty()) {
            throw GradleException(
                "Documentation has ${errors.size} problem(s):\n" + errors.joinToString("\n") { "  $it" } +
                    "\nReport: ${reportFile.get().asFile}"
            )
        }
        logger.lifecycle("Documentation check: ${sources.pages.size} pages, no problems")
    }

    /** Findings of rumdl on [pages] as problems; rumdl's own messages, such as configuration warnings, are logged. */
    private fun lint(root: File, pages: List<String>): List<DocumentationProblem> {
        if (!markdownLint.enabled.get() || pages.isEmpty()) return emptyList()

        val output = markdownLint.rumdl(root).check(pages)
        output.messages.forEach { logger.warn("rumdl: $it") }
        return output.findings
            .sortedWith(compareBy({ it.path }, { it.line ?: 0 }))
            .map { DocumentationProblem(it.path, it.line, "[${it.rule}] ${it.message}") }
    }
}
