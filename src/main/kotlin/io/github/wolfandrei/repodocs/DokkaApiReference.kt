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

import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.jetbrains.dokka.gradle.tasks.DokkaGeneratePublicationTask

/**
 * The API reference of the site: the HTML publication of Dokka in the root project, as the project configures it
 * itself by the Dokka documentation, with the root applying `org.jetbrains.dokka` and naming the modules to join with
 * `dokka(project(...))`. The plugin only reads the result, through the public API of Dokka; the Dokka Gradle plugin is
 * not a dependency of this one, only its API at compile time, so the version is the one of the project.
 */
internal object DokkaApiReference {
    private const val PLUGIN_ID = "org.jetbrains.dokka"
    private const val PUBLICATION_TASK = "dokkaGeneratePublicationHtml"

    /** Sets [output] to the HTML publication of Dokka once the root [project] applies Dokka; without it, nothing. */
    fun connect(project: Project, output: DirectoryProperty) {
        project.pluginManager.withPlugin(PLUGIN_ID) {
            output.set(
                project.tasks.named(PUBLICATION_TASK, DokkaGeneratePublicationTask::class.java).flatMap { it.outputDirectory }
            )
        }
    }
}
