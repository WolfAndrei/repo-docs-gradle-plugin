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
import java.nio.file.Files
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.SetProperty
import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters

/**
 * Paths of the files and directories of the repository relative to its root, the targets that a documentation link
 * may point to. The directories in [Parameters.excludedDirectories] are skipped by name at any depth: build outputs
 * and tool caches are not part of the repository.
 *
 * The plugin passes the value to the check without reading it at configuration time, so with the configuration cache
 * the paths are listed when the check runs, and a new file does not invalidate the cached configuration.
 */
internal abstract class RepositoryPathsValueSource : ValueSource<Set<String>, RepositoryPathsValueSource.Parameters> {
    interface Parameters : ValueSourceParameters {
        val root: DirectoryProperty
        val excludedDirectories: SetProperty<String>
    }

    override fun obtain(): Set<String> {
        val root = parameters.root.get().asFile
        val excluded = parameters.excludedDirectories.get()
        val paths = sortedSetOf<String>()

        fun walk(directory: File, prefix: String) {
            directory.listFiles()?.forEach { file ->
                if (file.isDirectory && file.name in excluded) return@forEach

                val path = prefix + file.name
                paths += path
                if (file.isDirectory && !Files.isSymbolicLink(file.toPath())) walk(file, "$path/")
            }
        }
        walk(root, "")

        return paths
    }

    companion object {
        val DEFAULT_EXCLUDED_DIRECTORIES = setOf(
            ".git",
            ".gradle",
            ".idea",
            ".kotlin",
            ".cxx",
            "build",
            "node_modules",
            "Pods",
            "DerivedData",
        )
    }
}
