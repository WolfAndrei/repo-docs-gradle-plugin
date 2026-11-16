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

package io.github.wolfandrei.repodocs.tasks

import java.io.File
import org.gradle.api.file.FileCollection

/** Files of [this] by their path relative to [root] with `/`; files outside [root] are skipped. */
internal fun FileCollection.relativeTo(root: File): Map<String, File> {
    return files.filter { it.isFile }
        .mapNotNull { file ->
            val relative = file.relativeToOrNull(root)?.invariantSeparatorsPath ?: return@mapNotNull null
            if (relative.startsWith("../")) null else relative to file
        }
        .toMap()
        .toSortedMap()
}

/** Files under [directory] by their path relative to it with `/`; empty if the directory does not exist. */
internal fun filesUnder(directory: File?): Map<String, File> {
    if (directory == null || !directory.isDirectory) return emptyMap()

    return directory.walkTopDown()
        .filter { it.isFile }
        .associateBy { it.relativeTo(directory).invariantSeparatorsPath }
        .toSortedMap()
}
