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

import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters

/**
 * The default documentation directory relative to [Parameters.root]: the first of [CANDIDATES] that exists, its
 * name compared with the case, otherwise `documentation`. A file system that ignores the case would find
 * `documentation` as `Documentation`, and the paths of the site would not match the links of the pages. As a value
 * source it is an input of the configuration cache: creating one of the directories reconfigures the build.
 */
internal abstract class DocumentationDirectoryValueSource :
    ValueSource<String, DocumentationDirectoryValueSource.Parameters> {
    interface Parameters : ValueSourceParameters {
        val root: DirectoryProperty
    }

    override fun obtain(): String {
        val root = parameters.root.get().asFile
        val directories = root.listFiles().orEmpty().filter { it.isDirectory }.map { it.name }.toSet()
        return CANDIDATES.firstOrNull { it in directories } ?: CANDIDATES.first()
    }

    companion object {
        val CANDIDATES = listOf("documentation", "docs", "Documentation")
    }
}
