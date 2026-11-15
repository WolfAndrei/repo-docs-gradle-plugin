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

import io.github.wolfandrei.docssite.plantuml.PlantUmlReport
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.UntrackedTask

/**
 * Writes the SVG of every diagram next to its `.puml`, so that the repository shows the same picture as the site. The
 * SVG come from `docsRenderDiagrams`; with a diagram PlantUML cannot render the task fails and writes nothing.
 */
@UntrackedTask(because = "It writes into the documentation, which other tasks read as their input")
internal abstract class WriteDiagramsTask : DefaultTask() {
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val renderedDiagrams: DirectoryProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val plantUmlReport: RegularFileProperty

    @get:Internal
    abstract val repositoryRoot: DirectoryProperty

    @TaskAction
    fun write() {
        val errors = PlantUmlReport.read(plantUmlReport.get().asFile).errors
        if (errors.isNotEmpty()) {
            val list = errors.joinToString("\n") { "  ${it.source}:${it.line ?: "?"}: ${it.message}" }
            throw GradleException("PlantUML cannot render ${errors.size} diagram(s), no SVG is written:\n$list")
        }

        val rendered = renderedDiagrams.get().asFile
        val root = repositoryRoot.get().asFile
        val written = rendered.walkTopDown().filter { it.isFile && it.extension == "svg" }.map { svg ->
            val target = root.resolve(svg.relativeTo(rendered).path)
            if (!target.isFile || !target.readBytes().contentEquals(svg.readBytes())) svg.copyTo(target, overwrite = true)
            target
        }.count()
        logger.lifecycle("Diagrams: $written SVG next to their sources")
    }
}
