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

package io.github.wolfandrei.docssite

import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters

/**
 * The default documentation directory relative to [Parameters.root]: `documentation` if it exists, otherwise `docs`
 * if it exists, otherwise `documentation`. As a value source it is an input of the configuration cache: creating
 * one of the directories reconfigures the build.
 */
internal abstract class DocumentationDirectoryValueSource :
    ValueSource<String, DocumentationDirectoryValueSource.Parameters> {
    interface Parameters : ValueSourceParameters {
        val root: DirectoryProperty
    }

    override fun obtain(): String {
        val root = parameters.root.get().asFile
        return CANDIDATES.firstOrNull { root.resolve(it).isDirectory } ?: CANDIDATES.first()
    }

    companion object {
        val CANDIDATES = listOf("documentation", "docs")
    }
}
