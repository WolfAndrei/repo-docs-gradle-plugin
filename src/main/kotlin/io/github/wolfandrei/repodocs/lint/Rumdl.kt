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

package io.github.wolfandrei.repodocs.lint

import java.io.File
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.gradle.api.GradleException

/** A finding of rumdl: [path] is relative to the repository root, [line] counts from 1, [rule] is such as `MD040`. */
internal data class RumdlFinding(val path: String, val line: Int?, val rule: String, val message: String)

/** What rumdl reported: the findings and the warnings of the configurations, such as unknown rule options. */
internal class RumdlOutput(val findings: List<RumdlFinding>, val messages: List<String>)

/**
 * The rumdl Markdown linter on pages of [repositoryRoot], given relative to it. The rules of a page come from the
 * [CONFIG_FILE] of the nearest directory of the page, up to the repository root, with its `extends`, as rumdl reads
 * it; a page without one is checked by the defaults of rumdl.
 *
 * @property disabledRules rules that are always off, over the configuration: those the plugin checks itself.
 */
internal class Rumdl(
    private val repositoryRoot: File,
    disabledRules: List<String>,
) {
    private val engine by lazy { RumdlWasm() }
    private val linters = mutableMapOf<String?, Configured>()
    private val disabled = disabledRules.map { it.trim().uppercase() }.filter { it.isNotEmpty() }.distinct()

    /** A linter and the directory its patterns, such as `per-file-ignores`, are relative to. */
    private class Configured(val linter: RumdlWasm.Linter, val directory: String, val warnings: List<String>)

    /** Checks [pages] and returns the findings. */
    fun check(pages: List<String>): RumdlOutput {
        val findings = mutableListOf<RumdlFinding>()
        pages.forEach { page ->
            val configured = configured(page)
            val content = repositoryRoot.resolve(page).readText()
            val warnings = json.decodeFromString<List<Warning>>(
                configured.linter.check(content, relativeTo(page, configured.directory))
            )
            warnings.mapTo(findings) { RumdlFinding(page, it.line, it.rule_name, it.message) }
        }
        return RumdlOutput(findings, linters.values.flatMap { it.warnings }.distinct())
    }

    /** Fixes what rumdl can fix in [pages], as `rumdl fmt` does; returns the pages that changed. */
    fun format(pages: List<String>): List<String> = pages.filter { page ->
        val configured = configured(page)
        val file = repositoryRoot.resolve(page)
        val content = file.readText()
        val fixed = configured.linter.fix(content, relativeTo(page, configured.directory))
        (fixed != content).also { changed -> if (changed) file.writeText(fixed) }
    }

    private fun configured(page: String): Configured {
        val config = discover(page)
        return linters.getOrPut(config) {
            try {
                if (config == null) {
                    Configured(engine.linter(mapOf("disable" to disabled)), "", emptyList())
                } else {
                    load(config)
                }
            } catch (e: RumdlException) {
                throw GradleException("rumdl cannot use the configuration ${config ?: "of its defaults"}: ${e.message}")
            }
        }
    }

    /** The [CONFIG_FILE] of [page] relative to the repository root, `null` if there is none. */
    private fun discover(page: String): String? {
        var directory = page.substringBeforeLast('/', "")
        while (true) {
            join(directory, CONFIG_FILE).takeIf { repositoryRoot.resolve(it).isFile }?.let { return it }
            if (directory.isEmpty()) return null
            directory = directory.substringBeforeLast('/', "")
        }
    }

    /**
     * [config] through the config chain of rumdl. The disabled rules come from a virtual file next to it that extends
     * it, so that they win over the chain, as a disable of the command line does.
     */
    private fun load(config: String): Configured {
        val directory = config.substringBeforeLast('/', "")
        val root = join(directory, VIRTUAL_CONFIG)
        val disable = disabled.joinToString { tomlString(it) }
        val files = mutableMapOf<String, String?>(
            root to "extends = ${tomlString(CONFIG_FILE)}\n\n[global]\nextend-disable = [$disable]\n"
        )
        while (true) {
            val status = json.parseToJsonElement(engine.resolveConfigChain(root, files)).jsonObject
            when (status["status"]?.jsonPrimitive?.content) {
                "need-file" -> {
                    val path = status.getValue("path").jsonPrimitive.content
                    files[path] = repositoryRoot.resolve(path).normalize().takeIf { it.isFile }?.readText()
                }

                "complete" -> break
                else -> throw RumdlException(status["message"]?.jsonPrimitive?.content ?: status.toString())
            }
        }
        val linter = engine.linter(root, files)
        val warnings = json.decodeFromString<List<String>>(linter.configWarnings()).map { "$config: $it" }
        return Configured(linter, directory, warnings)
    }

    @Serializable
    @Suppress("ConstructorParameterNaming")
    private class Warning(val rule_name: String = "", val line: Int? = null, val message: String)

    internal companion object {
        /** The configuration file of rumdl, the only one the plugin reads. */
        const val CONFIG_FILE = ".rumdl.toml"

        private const val VIRTUAL_CONFIG = ".repo-docs-rumdl.toml"
        private val json = Json { ignoreUnknownKeys = true }

        private fun join(directory: String, name: String) = if (directory.isEmpty()) name else "$directory/$name"

        private fun relativeTo(path: String, directory: String) =
            if (directory.isEmpty()) path else path.removePrefix("$directory/")

        private fun tomlString(text: String) = "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
    }
}
