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

import java.io.ByteArrayOutputStream
import java.io.File
import javax.inject.Inject
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.IgnoreEmptyDirectories
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.SkipWhenEmpty
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations

/** Module of the aggregated API reference: [name] from Dokka, [directory] relative to `api/`. */
@Serializable
internal data class ApiModule(val name: String, val directory: String)

/**
 * Joins the Dokka HTML of the modules into one API reference, as the root `dokkaGeneratePublicationHtml` of Dokka
 * does, but without the Dokka plugin in the root project: the Dokka command line runs with the multi-module
 * configuration in a separate JVM.
 *
 * The module outputs are the artifacts that Dokka publishes for aggregation in every module where it is applied
 * (`dokkaGenerateModuleHtml`); the root resolves them by project dependencies, so no project configures another.
 * Each output has `module-descriptor.json` with the name and the path of the module.
 */
@CacheableTask
internal abstract class AggregateApiReferenceTask @Inject constructor(
    private val execOperations: ExecOperations,
) : DefaultTask() {
    /** Module outputs of Dokka: directories with `module-descriptor.json`. */
    @get:InputFiles
    @get:SkipWhenEmpty
    @get:IgnoreEmptyDirectories
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val moduleOutputs: ConfigurableFileCollection

    /** `org.jetbrains.dokka:dokka-cli`. */
    @get:Classpath
    abstract val dokkaCli: ConfigurableFileCollection

    /** Dokka plugins of the aggregation with their dependencies: base, templating, all-modules page, analysis. */
    @get:Classpath
    abstract val dokkaPlugins: ConfigurableFileCollection

    /** Title of the aggregated reference. */
    @get:Input
    abstract val referenceName: Property<String>

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun aggregate() {
        val output = outputDirectory.get().asFile.apply {
            deleteRecursively()
            mkdirs()
        }
        val modules = moduleOutputs.files.filter {
            it.resolve(DESCRIPTOR).isFile
        }.map(::readModule).sortedBy { it.second.directory }
        val configuration = temporaryDir.resolve("dokka-configuration.json")
        configuration.writeText(json.encodeToString(JsonObject.serializer(), configuration(output, modules)))

        val log = ByteArrayOutputStream()
        val result = execOperations.javaexec {
            classpath = dokkaCli
            mainClass.set(MAIN_CLASS)
            args(configuration.path)
            standardOutput = log
            errorOutput = log
            isIgnoreExitValue = true
        }
        temporaryDir.resolve("dokka.log").writeBytes(log.toByteArray())
        if (result.exitValue != 0) {
            throw GradleException("Dokka could not join the API reference, see ${temporaryDir.resolve("dokka.log")}")
        }

        output.resolve(MODULES_FILE).writeText(json.encodeToString(modules.map { it.second }))
    }

    private fun readModule(directory: File): Pair<File, ApiModule> {
        val descriptor = Json.parseToJsonElement(directory.resolve(DESCRIPTOR).readText()).jsonObject
        val name = descriptor.getValue("name").jsonPrimitive.content
        val path = descriptor.getValue("modulePath").jsonPrimitive.content
        val outputName = descriptor["moduleOutputDirName"]?.jsonPrimitive?.content ?: "module"

        return directory.resolve(outputName) to ApiModule(name, path)
    }

    private fun configuration(output: File, modules: List<Pair<File, ApiModule>>) = buildJsonObject {
        put("moduleName", referenceName.get())
        put("outputDir", output.path)
        put("offlineMode", true)
        put("sourceSets", buildJsonArray { })
        put("pluginsClasspath", buildJsonArray { dokkaPlugins.files.forEach { add(JsonPrimitive(it.path)) } })
        put(
            "modules",
            buildJsonArray {
                modules.forEach { (directory, module) ->
                    add(
                        buildJsonObject {
                            put("name", module.name)
                            put("relativePathToOutputDirectory", module.directory)
                            put("includes", buildJsonArray { })
                            put("sourceOutputDirectory", directory.path)
                        }
                    )
                }
            }
        )
        put("failOnWarning", false)
        put("delayTemplateSubstitution", false)
        put("suppressObviousFunctions", true)
        put("includes", buildJsonArray { })
        put("suppressInheritedMembers", false)
        put("finalizeCoroutines", false)
    }

    internal companion object {
        const val MAIN_CLASS = "org.jetbrains.dokka.MainKt"
        const val DESCRIPTOR = "module-descriptor.json"
        const val MODULES_FILE = "documentation-site-modules.json"
        private val json = Json { prettyPrint = true }

        fun readModules(file: File): List<ApiModule> =
            if (file.isFile) json.decodeFromString(file.readText()) else emptyList()
    }
}
