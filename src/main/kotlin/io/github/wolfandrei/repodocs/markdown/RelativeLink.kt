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

package io.github.wolfandrei.repodocs.markdown

import java.io.ByteArrayOutputStream

/**
 * A link destination of a page that points into the repository: [path] is the target relative to the repository root
 * with `.` and `..` resolved, empty for a link to the page itself; [fragment] is the decoded anchor without `#`.
 */
internal data class RelativeLink(val path: String, val fragment: String?) {
    companion object {
        private val SCHEME = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*:")

        /**
         * The target of [destination] written on the page [pagePath], or `null` for an external address, a link that
         * leaves the repository, and an empty destination.
         */
        fun resolve(pagePath: String, destination: String): RelativeLink? {
            val trimmed = destination.trim()
            if (trimmed.isEmpty() || SCHEME.containsMatchIn(trimmed) || trimmed.startsWith("//")) return null

            val withoutQuery = trimmed.substringBefore('#').substringBefore('?')
            val fragment = trimmed.substringAfter('#', "").takeIf { '#' in trimmed }?.let(::decode)
            if (withoutQuery.isEmpty()) return RelativeLink(pagePath, fragment)

            val base = if (withoutQuery.startsWith("/")) emptyList() else pagePath.split('/').dropLast(1)
            val path = normalize(base + decode(withoutQuery).split('/')) ?: return null

            return RelativeLink(path, fragment)
        }

        /** [segments] with `.` and `..` resolved, or `null` if they go above the root. */
        fun normalize(segments: List<String>): String? {
            val result = ArrayDeque<String>()
            for (segment in segments) {
                when (segment) {
                    "", "." -> Unit
                    ".." -> if (result.isEmpty()) return null else result.removeLast()
                    else -> result.addLast(segment)
                }
            }

            return result.joinToString("/")
        }

        /** [value] with `%XX` sequences decoded as UTF-8; `+` stays as is, unlike form encoding. */
        fun decode(value: String): String {
            if ('%' !in value) return value

            val bytes = ByteArrayOutputStream()
            var index = 0
            while (index < value.length) {
                val char = value[index]
                val hex = value.substring(index + 1, minOf(index + 3, value.length))
                if (char == '%' && hex.length == 2 && hex.all { it.isHexDigit() }) {
                    bytes.write(hex.toInt(16))
                    index += 3
                } else {
                    bytes.write(char.toString().toByteArray(Charsets.UTF_8))
                    index++
                }
            }

            return bytes.toString(Charsets.UTF_8)
        }

        private fun Char.isHexDigit(): Boolean = this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'
    }
}
