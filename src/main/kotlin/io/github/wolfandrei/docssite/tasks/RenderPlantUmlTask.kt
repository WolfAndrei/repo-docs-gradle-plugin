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

import io.github.wolfandrei.docssite.plantuml.GraphvizValueSource
import io.github.wolfandrei.docssite.plantuml.PlantUmlError
import io.github.wolfandrei.docssite.plantuml.PlantUmlReport
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.IgnoreEmptyDirectories
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.SkipWhenEmpty
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations

/**
 * Renders the PlantUML diagrams to SVG with the PlantUML jar in a separate JVM, one run per directory of diagrams,
 * and writes their preprocessed sources: one text with every `!include`, local and of the standard library, resolved,
 * which the browser build of PlantUML renders on the page. For `<dir>/<name>.puml` (`<dir>` is relative to the
 * repository root) the task writes `<outputDirectory>/svg/<dir>/<name>.svg` and
 * `<outputDirectory>/source/<dir>/<name>.preproc`, as the PlantUML command line names them.
 *
 * The layout engine is Graphviz `dot` if [layout] is `dot`, or `auto` and Graphviz is found ([graphviz]), otherwise
 * the built-in Smetana.
 *
 * A diagram with an error does not fail the task: PlantUML draws the error into the SVG, and the task writes it to
 * [reportFile], which `documentationCheck` reads. So the site is built even with a broken diagram.
 */
@CacheableTask
internal abstract class RenderPlantUmlTask @Inject constructor(
    private val execOperations: ExecOperations
) : DefaultTask() {
    /** Diagrams to render, `.puml`. */
    @get:InputFiles
    @get:SkipWhenEmpty
    @get:IgnoreEmptyDirectories
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val diagrams: ConfigurableFileCollection

    /** Files the diagrams include, such as `.iuml`. */
    @get:InputFiles
    @get:IgnoreEmptyDirectories
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val includes: ConfigurableFileCollection

    @get:Internal
    abstract val repositoryRoot: DirectoryProperty

    @get:Classpath
    abstract val plantUmlClasspath: ConfigurableFileCollection

    /** `auto`, `dot` or `smetana`. */
    @get:Input
    abstract val layout: Property<String>

    /** Graphviz of the machine as `GraphvizValueSource` gives it: `<path>|<version>` or empty. */
    @get:Input
    abstract val graphviz: Property<String>

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @get:OutputFile
    abstract val reportFile: RegularFileProperty

    /** Paths of the diagrams relative to the repository root: they set the paths of the SVG files. */
    @get:Input
    val diagramPaths: List<String>
        get() = diagrams.relativeTo(repositoryRoot.get().asFile).keys.toList()

    @TaskAction
    fun render() {
        val root = repositoryRoot.get().asFile
        val output = outputDirectory.get().asFile.apply {
            deleteRecursively()
            mkdirs()
        }
        val dot = GraphvizValueSource.path(graphviz.get())
        val engine = when (layout.get()) {
            SMETANA -> SMETANA
            DOT -> dot?.let { DOT } ?: throw GradleException("plantUml.layout is dot, but Graphviz dot is not found")
            else -> if (dot != null) DOT else SMETANA
        }
        logger.info("PlantUML layout engine: $engine${dot?.let { " ($it)" }.orEmpty()}")

        val errors = diagrams.relativeTo(root).keys
            .groupBy { it.substringBeforeLast('/', "") }
            .flatMap { (directory, paths) ->
                preprocess(paths, output.resolve("$SOURCE_DIR/$directory").path)
                renderDirectory(directory, paths, output.resolve("$SVG_DIR/$directory").path, engine, dot)
            }

        errors.forEach { logger.warn("PlantUML: ${it.source}:${it.line ?: "?"}: ${it.message}") }
        PlantUmlReport(errors, engine).write(reportFile.get().asFile)
    }

    private fun preprocess(paths: List<String>, output: String) {
        execOperations.javaexec {
            classpath = plantUmlClasspath
            mainClass.set(MAIN_CLASS)
            workingDir = repositoryRoot.get().asFile
            jvmArgs("-Djava.awt.headless=true", "-Dfile.encoding=UTF-8")
            args(listOf("-preproc", "-charset", "UTF-8", "-o", output) + paths)
            standardOutput = ByteArrayOutputStream()
            errorOutput = ByteArrayOutputStream()
            isIgnoreExitValue = true
        }
    }

    private fun renderDirectory(
        directory: String,
        paths: List<String>,
        output: String,
        engine: String,
        dot: String?,
    ): List<PlantUmlError> {
        val log = ByteArrayOutputStream()
        val result = execOperations.javaexec {
            classpath = plantUmlClasspath
            mainClass.set(MAIN_CLASS)
            workingDir = repositoryRoot.get().asFile
            jvmArgs("-Djava.awt.headless=true", "-Dfile.encoding=UTF-8")
            if (engine == DOT && dot != null) environment("GRAPHVIZ_DOT", dot)
            args(
                buildList {
                    add("-tsvg")
                    add("-charset")
                    add("UTF-8")
                    if (engine == SMETANA) add("-Playout=smetana")
                    add("-o")
                    add(output)
                    addAll(paths)
                }
            )
            standardOutput = log
            errorOutput = log
            isIgnoreExitValue = true
        }

        val text = log.toString(Charsets.UTF_8)
        val errors = ERROR.findAll(text).map { match ->
            val (line, source) = match.destructured
            PlantUmlError(source.trim(), line.toInt(), "PlantUML cannot render the diagram, see the SVG for the error")
        }.toList()

        if (result.exitValue != 0 && errors.isEmpty()) {
            return listOf(
                PlantUmlError(
                    directory.ifEmpty {
                        "."
                    },
                    null,
                    "PlantUML exited with ${result.exitValue}: $text"
                )
            )
        }

        return errors
    }

    internal companion object {
        const val MAIN_CLASS = "net.sourceforge.plantuml.Run"
        const val SMETANA = "smetana"
        const val DOT = "dot"
        const val AUTO = "auto"
        const val SVG_DIR = "svg"
        const val SOURCE_DIR = "source"
        private val ERROR = Regex("Error line (\\d+) in file: (.+)")
    }
}
