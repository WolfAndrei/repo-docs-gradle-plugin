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

package io.github.wolfandrei.docssite.lint

import java.io.File
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.gradle.api.GradleException

/** A finding of rumdl: [path] is relative to the repository root, [line] counts from 1, [rule] is such as `MD040`. */
internal data class RumdlFinding(val path: String, val line: Int?, val rule: String, val message: String)

/** What rumdl printed: the findings and the messages of its standard error, such as configuration warnings. */
internal class RumdlOutput(val findings: List<RumdlFinding>, val messages: List<String>)

/**
 * The rumdl command line [executable], run in [repositoryRoot] on pages given relative to it.
 *
 * @property configFile explicit configuration, `-c`; without it rumdl finds its configuration itself.
 * @property disabledRules rules added to the disabled ones of the configuration, `--extend-disable`.
 */
internal class Rumdl(
    private val executable: File,
    private val repositoryRoot: File,
    private val configFile: File?,
    private val disabledRules: List<String>,
) {
    /** Checks [pages] and returns the findings. */
    fun check(pages: List<String>): RumdlOutput {
        val findings = mutableListOf<RumdlFinding>()
        val messages = mutableListOf<String>()
        batches(pages).forEach { batch ->
            val result = run(listOf("check", "--output-format", "json", "--show-full-path") + options(), batch)
            if (result.exitCode !in 0..1) throw failure(result)
            findings += parse(result.stdout, repositoryRoot)
            messages += result.stderrLines()
        }
        return RumdlOutput(findings, messages)
    }

    /** Fixes what rumdl can fix in [pages], `rumdl fmt`; returns the lines rumdl printed. */
    fun format(pages: List<String>): List<String> {
        return batches(pages).flatMap { batch ->
            val result = run(listOf("fmt") + options(), batch)
            if (result.exitCode != 0) throw failure(result)
            result.stdout.lines().filter { it.isNotBlank() } + result.stderrLines()
        }
    }

    private fun options(): List<String> {
        val options = mutableListOf("--no-cache", "--color", "never")
        configFile?.let { options += listOf("--config", it.absolutePath) }
        val disabled = disabledRules.map { it.trim().uppercase() }.filter { it.isNotEmpty() }.distinct()
        if (disabled.isNotEmpty()) options += listOf("--extend-disable", disabled.joinToString(","))
        return options
    }

    private class Result(val exitCode: Int, val stdout: String, val stderr: String) {
        fun stderrLines(): List<String> = stderr.lines().filter { it.isNotBlank() }
    }

    private fun run(arguments: List<String>, pages: List<String>): Result {
        val stderr = File.createTempFile("rumdl-", ".stderr")
        try {
            val process = ProcessBuilder(listOf(executable.absolutePath) + arguments + "--" + pages)
                .directory(repositoryRoot)
                .redirectError(stderr)
                .start()
            process.outputStream.close()
            val stdout = process.inputStream.bufferedReader().readText()
            return Result(process.waitFor(), stdout, stderr.readText())
        } finally {
            stderr.delete()
        }
    }

    private fun failure(result: Result): GradleException {
        return GradleException(
            "rumdl ($executable) failed with exit code ${result.exitCode}:\n" + result.stderr.trim().ifEmpty {
                result.stdout.trim()
            }
        )
    }

    internal companion object {
        /** Longest total length of the page paths of one run, within the limit of a Windows command line. */
        private const val MAX_ARGUMENTS_LENGTH = 24_000

        private val json = Json { ignoreUnknownKeys = true }

        @Serializable
        private class Warning(val file: String, val line: Int? = null, val rule: String = "", val message: String)

        /**
         * Findings of the JSON output of `rumdl check --output-format json`; [repositoryRoot] makes the absolute paths
         * of `--show-full-path` relative.
         */
        fun parse(output: String, repositoryRoot: File): List<RumdlFinding> {
            val start = output.indexOf('[')
            if (start < 0) return emptyList()

            val root = repositoryRoot.canonicalFile
            return json.decodeFromString<List<Warning>>(output.substring(start)).map { warning ->
                RumdlFinding(relativePath(warning.file, root), warning.line, warning.rule, warning.message)
            }
        }

        private fun relativePath(path: String, root: File): String {
            val file = File(path)
            if (!file.isAbsolute) return path.replace('\\', '/')

            val relative = runCatching { file.canonicalFile.relativeToOrNull(root) }.getOrNull()
                ?: file.relativeToOrNull(root)
            return relative?.invariantSeparatorsPath?.takeUnless { it.startsWith("../") } ?: path
        }

        /** [pages] in runs that keep the command line short. */
        fun batches(pages: List<String>): List<List<String>> {
            val batches = mutableListOf<MutableList<String>>()
            var length = 0
            pages.forEach { page ->
                if (batches.isEmpty() || length + page.length + 1 > MAX_ARGUMENTS_LENGTH) {
                    batches += mutableListOf<String>()
                    length = 0
                }
                batches.last() += page
                length += page.length + 1
            }
            return batches
        }
    }
}
