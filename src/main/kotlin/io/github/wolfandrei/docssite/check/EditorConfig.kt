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

package io.github.wolfandrei.docssite.check

import java.io.File
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters

/**
 * The `max_line_length` of [EditorConfig](https://editorconfig.org) for a file: the `.editorconfig` files of its
 * directory and of the directories above it, up to the one with `root = true`. A closer file overrides a farther
 * one, a later section of a file an earlier one.
 */
internal object EditorConfig {
    const val FILE_NAME = ".editorconfig"

    /** A section `[glob]` of a file with its properties, keys and values in lower case. */
    class Section(val glob: String, val properties: Map<String, String>)

    class Parsed(val root: Boolean, val sections: List<Section>)

    /** The `.editorconfig` files that may apply to the files of [directory], from the closest one up. */
    fun candidates(directory: File): List<File> =
        generateSequence(directory.absoluteFile) { it.parentFile }.map { it.resolve(FILE_NAME) }.toList()

    /**
     * Longest line of [file] by the `max_line_length` of the `.editorconfig` files, `0` if none sets it or it is
     * `off`; a value that is not a number counts as not set.
     */
    fun maxLineLength(file: File): Int {
        val absolute = file.absoluteFile
        val applicable = mutableListOf<Pair<File, Parsed>>()
        for (config in candidates(absolute.parentFile)) {
            if (!config.isFile) continue
            val parsed = parse(config.readText())
            applicable += config to parsed
            if (parsed.root) break
        }

        var value: String? = null
        applicable.asReversed().forEach { (config, parsed) ->
            val path = absolute.relativeTo(config.parentFile).invariantSeparatorsPath
            parsed.sections.filter { matches(it.glob, path) }.forEach { section ->
                section.properties["max_line_length"]?.let { value = it }
            }
        }

        return when (val result = value) {
            null, "off" -> 0
            else -> result.toIntOrNull()?.coerceAtLeast(0) ?: 0
        }
    }

    fun parse(text: String): Parsed {
        var root = false
        val sections = mutableListOf<Section>()
        var glob: String? = null
        var properties = mutableMapOf<String, String>()

        fun close() {
            glob?.let { sections += Section(it, properties) }
        }

        text.lineSequence().map { it.trim() }.forEach { line ->
            when {
                line.isEmpty() || line.startsWith('#') || line.startsWith(';') -> Unit
                line.startsWith('[') && line.endsWith(']') -> {
                    close()
                    glob = line.substring(1, line.length - 1)
                    properties = mutableMapOf()
                }
                '=' in line -> {
                    val key = line.substringBefore('=').trim().lowercase()
                    val value = line.substringAfter('=').trim().lowercase()
                    if (glob == null) {
                        if (key == "root") root = value == "true"
                    } else {
                        properties[key] = value
                    }
                }
            }
        }
        close()

        return Parsed(root, sections)
    }

    /**
     * Whether the section [glob] applies to [path], relative to the directory of the `.editorconfig` with `/`. A glob
     * without `/` matches the file name in any directory; `*`, `**`, `?`, `[...]`, `[!...]` and `{a,b}` work.
     */
    fun matches(glob: String, path: String): Boolean {
        val anchored = '/' in glob
        val pattern = toRegex(glob.removePrefix("/"))
        return if (anchored) Regex(pattern).matches(path) else Regex("(?:.*/)?$pattern").matches(path)
    }

    private fun toRegex(glob: String): String {
        val regex = StringBuilder()
        var braces = 0
        var i = 0
        while (i < glob.length) {
            when (val c = glob[i]) {
                '*' -> if (glob.getOrNull(i + 1) == '*') {
                    regex.append(".*")
                    i++
                } else {
                    regex.append("[^/]*")
                }
                '?' -> regex.append("[^/]")
                '[' -> {
                    val end = glob.indexOf(']', i + 1)
                    if (end < 0) {
                        regex.append("\\[")
                    } else {
                        val content = glob.substring(i + 1, end)
                        val negated = content.startsWith('!')
                        val chars = content.removePrefix("!").replace("\\", "\\\\")
                        regex.append(if (negated) "[^$chars]" else "[$chars]")
                        i = end
                    }
                }
                '{' -> {
                    braces++
                    regex.append("(?:")
                }
                '}' -> if (braces > 0) {
                    braces--
                    regex.append(")")
                } else {
                    regex.append("\\}")
                }
                ',' -> regex.append(if (braces > 0) "|" else ",")
                '\\' -> {
                    glob.getOrNull(i + 1)?.let { regex.append(Regex.escape(it.toString())) }
                    i++
                }
                else -> regex.append(Regex.escape(c.toString()))
            }
            i++
        }
        repeat(braces) { regex.append(")") }
        return regex.toString()
    }
}

/**
 * `max_line_length` of `.editorconfig` for [Parameters.file], `0` without it; see [EditorConfig]. The check reads it
 * at execution, so a change of `.editorconfig` does not invalidate the configuration cache.
 */
internal abstract class EditorConfigLineLengthValueSource :
    ValueSource<Int, EditorConfigLineLengthValueSource.Parameters> {
    interface Parameters : ValueSourceParameters {
        /** A Markdown file the value is for: the table of contents of the documentation. */
        val file: RegularFileProperty
    }

    override fun obtain(): Int = EditorConfig.maxLineLength(parameters.file.get().asFile)
}
