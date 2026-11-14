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

package io.github.wolfandrei.docssite.plantuml

import java.io.File
import java.util.concurrent.TimeUnit
import org.gradle.api.provider.Property
import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters

/**
 * Graphviz `dot` of the machine as `<path>|<version>`, or an empty string without Graphviz. The path is the configured
 * one, otherwise `GRAPHVIZ_DOT`, otherwise the first `dot` on `PATH`; the version is the output of `dot -V`.
 *
 * The render task reads the value at execution, so the layout engine is an input of the task: a machine with Graphviz
 * and a machine without it do not share cached SVG.
 */
internal abstract class GraphvizValueSource : ValueSource<String, GraphvizValueSource.Parameters> {
    interface Parameters : ValueSourceParameters {
        /** Configured path of `dot`; absent for the search. */
        val executable: Property<String>
    }

    override fun obtain(): String {
        val dot = find() ?: return ""
        val version = runCatching {
            val process = ProcessBuilder(dot.path, "-V").redirectErrorStream(true).start()
            val output = process.inputStream.bufferedReader().readText().trim()
            if (process.waitFor(10, TimeUnit.SECONDS) && process.exitValue() == 0) output else null
        }.getOrNull() ?: return ""

        return "${dot.path}|$version"
    }

    private fun find(): File? {
        parameters.executable.orNull?.let { return File(it).takeIf(File::canExecute) }
        System.getenv("GRAPHVIZ_DOT")?.takeIf { it.isNotBlank() }?.let { return File(it).takeIf(File::canExecute) }

        return System.getenv("PATH").orEmpty()
            .split(File.pathSeparatorChar)
            .filter { it.isNotBlank() }
            .map { File(it, "dot") }
            .firstOrNull { it.isFile && it.canExecute() }
    }

    companion object {
        fun path(value: String): String? = value.substringBefore('|').takeIf { it.isNotEmpty() }
    }
}
