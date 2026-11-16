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

import java.io.File
import java.io.InputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Properties
import java.util.UUID

/**
 * The files the plugin carries, so that a build downloads nothing: the PlantUML jars that draw the diagrams. rumdl
 * is compiled into the classes of the plugin.
 *
 * The PlantUML jars are files of the plugin rather than its dependencies: `plantuml-epl` contains Guava, EMF and
 * other libraries under their own packages, which would join the build classpath of every plugin of the project.
 * They run in a separate JVM from a directory of the Gradle user home.
 */
internal object Bundled {
    private const val PACKAGE = "/io/github/wolfandrei/repodocs/bundled/"

    private val properties: Properties by lazy {
        Properties().apply { resource("bundled.properties").use(::load) }
    }

    /** Version of PlantUML, the same for the build and for the browser. */
    val plantUmlVersion: String get() = properties.getProperty("plantuml.version")

    /** Version of rumdl. */
    val rumdlVersion: String get() = properties.getProperty("rumdl.version")

    /** Hash of the PlantUML jars; it names their directory in the cache. */
    val plantUmlHash: String get() = properties.getProperty("plantuml.sha256")

    /** Directory of the unpacked PlantUML jars under the Gradle user home. */
    private const val CACHE = "caches/repo-docs/plantuml"

    /**
     * The PlantUML jars in `<gradleUserHome>/caches/repo-docs/plantuml/<hash>/`, unpacked there on the first use.
     * Another build that unpacks them at the same time does no harm: the directory is moved into place whole.
     */
    fun plantUmlClasspath(gradleUserHome: File): List<File> {
        val cacheDirectory = gradleUserHome.resolve(CACHE)
        val names = properties.getProperty("plantuml.classpath").split(',')
        val directory = cacheDirectory.resolve(plantUmlHash)
        if (names.all { directory.resolve(it).isFile }) return names.map(directory::resolve)

        cacheDirectory.mkdirs()
        val temporary = cacheDirectory.resolve(".$plantUmlHash-${UUID.randomUUID()}").apply { mkdirs() }
        try {
            names.forEach { name -> resource(name).use { Files.copy(it, temporary.resolve(name).toPath()) } }
            try {
                Files.move(temporary.toPath(), directory.toPath(), StandardCopyOption.ATOMIC_MOVE)
            } catch (_: FileAlreadyExistsException) {
                // Another build was first.
            } catch (_: AtomicMoveNotSupportedException) {
                if (!directory.exists()) Files.move(temporary.toPath(), directory.toPath())
            } catch (e: java.nio.file.DirectoryNotEmptyException) {
                if (!names.all { directory.resolve(it).isFile }) throw e
            }
        } finally {
            temporary.deleteRecursively()
        }
        return names.map(directory::resolve)
    }

    private fun resource(name: String): InputStream =
        Bundled::class.java.getResourceAsStream(PACKAGE + name)
            ?: throw IllegalStateException("The plugin has no bundled $name")
}
