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

package io.github.wolfandrei.repodocs.plantuml

import java.io.File

/**
 * What the diagrams may read: the standard library of PlantUML (`<C4/C4_Context>`) and the files of the repository.
 * PlantUML itself lets a diagram include any file of the machine, such as `~/.gradle/gradle.properties`, and the
 * text would end in the SVG of the site or of `docsDiagrams`; its security profiles that forbid it forbid the local
 * includes too. So the plugin checks the includes before PlantUML runs.
 *
 * A diagram and every local file it includes may use `!include`, `!include_once`, `!include_many`, `!includesub`,
 * `!import`, `%load_json` and `!theme ... from` only with a path that stays inside [repositoryRoot]. An address, an
 * absolute path outside the repository and a path computed by the preprocessor (with `$` or `%`) are problems.
 */
internal class IncludeCheck(private val repositoryRoot: File) {
    private val root = repositoryRoot.canonicalFile

    /** Problems of [diagrams] and of the files they include, as `<path>:<line>: <message>`. */
    fun problems(diagrams: List<File>): List<String> {
        val problems = linkedSetOf<String>()
        val visited = mutableSetOf<File>()

        fun visit(file: File) {
            if (!visited.add(file.canonicalFile) || !file.isFile) return
            file.readLines().forEachIndexed { index, line ->
                val target = target(line.trim()) ?: return@forEachIndexed
                val where = "${file.canonicalFile.relativeTo(root).invariantSeparatorsPath}:${index + 1}"
                when {
                    target.startsWith("<") -> Unit
                    "://" in target -> problems += "$where: `$target` is an address; a diagram reads only the repository"
                    '$' in target || '%' in target ->
                        problems += "$where: `$target` is computed, the plugin cannot check that it stays in the repository"
                    else -> {
                        val included = resolve(file.parentFile, target)
                        if (!included.startsWith(root)) {
                            problems += "$where: `$target` is outside the repository"
                        } else {
                            visit(included)
                        }
                    }
                }
            }
        }
        diagrams.forEach(::visit)
        return problems.toList()
    }

    private fun resolve(directory: File, target: String): File {
        val path = target.removeSurrounding("\"").substringBefore('!').trim()
        val expanded = if (path.startsWith("~/")) File(System.getProperty("user.home"), path.removePrefix("~/")) else File(path)
        return (if (expanded.isAbsolute) expanded else directory.resolve(path)).canonicalFile
    }

    private companion object {
        val INCLUDE = Regex("""^!(?:include|include_once|include_many|includesub|includeurl|import)\s+(.+)$""")
        val LOAD_JSON = Regex("""%load_json\(\s*("[^"]*"|[^,)]+)""")
        val THEME = Regex("""^!theme\s+\S+\s+from\s+(.+)$""")

        /** The file or address a line of a diagram reads, `null` for another line. */
        fun target(line: String): String? =
            INCLUDE.matchEntire(line)?.groupValues?.get(1)?.trim()
                ?: THEME.matchEntire(line)?.groupValues?.get(1)?.trim()
                ?: LOAD_JSON.find(line)?.groupValues?.get(1)?.trim()?.removeSurrounding("\"")
    }
}
