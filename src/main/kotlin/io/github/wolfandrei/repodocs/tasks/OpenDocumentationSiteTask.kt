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

import javax.inject.Inject
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.UntrackedTask
import org.gradle.process.ExecOperations

/**
 * Opens the built site in the default browser: `open` on macOS, `xdg-open` on Linux, the URL handler of Windows. The
 * site works from the file system, so no server is needed; an open page reloads itself after a rebuild.
 *
 * In a continuous build the task only logs the address, so that a rebuild does not open another tab.
 */
@UntrackedTask(because = "Opens the site in the browser")
internal abstract class OpenDocumentationSiteTask @Inject constructor(
    private val execOperations: ExecOperations,
) : DefaultTask() {
    @get:Internal
    abstract val siteDirectory: DirectoryProperty

    @get:Internal
    abstract val continuous: Property<Boolean>

    @TaskAction
    fun open() {
        val url = siteDirectory.get().asFile.resolve("index.html").toURI().toString()
        if (continuous.get()) {
            logger.lifecycle("Documentation site: $url")
            return
        }
        val opened = runCatching {
            execOperations.exec {
                commandLine(command(System.getProperty("os.name").orEmpty(), url))
                isIgnoreExitValue = true
            }.exitValue == 0
        }.getOrDefault(false)
        if (opened) {
            logger.lifecycle("Documentation site opened: $url")
        } else {
            logger.warn("Cannot open the browser, open the documentation site yourself: $url")
        }
    }

    internal companion object {
        /** The command that opens [url] in the default browser of the system [osName]. */
        fun command(osName: String, url: String): List<String> = when {
            osName.startsWith("Mac", ignoreCase = true) -> listOf("open", url)
            osName.startsWith("Windows", ignoreCase = true) -> listOf("rundll32", "url.dll,FileProtocolHandler", url)
            else -> listOf("xdg-open", url)
        }
    }
}
