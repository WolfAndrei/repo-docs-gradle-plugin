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

import io.github.wolfandrei.docssite.serve.DocumentationServer
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.UntrackedTask
import org.gradle.api.tasks.options.Option

/**
 * Serves the site directory on `localhost` until the build is stopped with Ctrl+C. The task does not rebuild the
 * site: `./gradlew documentationSite --continuous` in another terminal does, and the pages reload themselves when the
 * build stamp of the site changes.
 */
@UntrackedTask(because = "Serves the site until the build is stopped")
internal abstract class ServeDocumentationSiteTask : DefaultTask() {
    @get:Internal
    abstract val siteDirectory: DirectoryProperty

    @get:Internal
    abstract val repositoryRoot: DirectoryProperty

    @get:Internal
    abstract val port: Property<Int>

    @Option(option = "port", description = "Port of the server on localhost")
    fun setPortOption(value: String) {
        port.set(value.toInt())
    }

    @TaskAction
    fun serve() {
        val server = DocumentationServer(siteDirectory.get().asFile, repositoryRoot.get().asFile, port.get())
        server.start()
        logger.lifecycle("Documentation site is served at ${server.url}")
        logger.lifecycle("Rebuild it with ./gradlew documentationSite --continuous; press Ctrl+C to stop the server.")
        try {
            while (!Thread.currentThread().isInterrupted) {
                Thread.sleep(POLL_MILLIS)
            }
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        } finally {
            server.stop()
        }
    }

    private companion object {
        const val POLL_MILLIS = 500L
    }
}
