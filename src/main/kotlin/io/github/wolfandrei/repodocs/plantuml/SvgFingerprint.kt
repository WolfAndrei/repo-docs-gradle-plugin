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

package io.github.wolfandrei.repodocs.plantuml

/**
 * What a PlantUML diagram shows, without its geometry: the texts, the elements, the number of links and the fill
 * colors of the shapes and texts, except the background.
 *
 * Coordinates and sizes depend on the layout engine (Graphviz `dot` or the built-in Smetana) and on the font metrics
 * of the JVM, so two renders of one source differ byte by byte on different machines. The fingerprint is equal for
 * such renders and changes when the source or an included file changes a text, an element or a color.
 */
internal data class SvgFingerprint(
    val texts: List<String>,
    val elements: List<String>,
    val links: Int,
    val colors: Set<String>,
) {
    /** What differs from [other], for the message of the check. */
    fun difference(other: SvgFingerprint): String {
        val differences = mutableListOf<String>()
        if (texts != other.texts) {
            val missing = (other.texts - texts.toSet()).take(3)
            val extra = (texts - other.texts.toSet()).take(3)
            differences += "texts differ (only committed: $extra, only rendered: $missing)"
        }
        if (elements != other.elements) differences += "elements differ"
        if (links != other.links) differences += "links: $links committed, ${other.links} rendered"
        if (colors != other.colors) differences += "colors differ: ${(colors - other.colors) + (other.colors - colors)}"

        return differences.joinToString("; ")
    }

    companion object {
        private val TEXT = Regex("<text[^>]*>([^<]*)</text>")
        private val ELEMENT = Regex("data-qualified-name=\"([^\"]*)\"")
        private val LINK = Regex("class=\"link\"")
        private val SHAPE = Regex("<(?:rect|text|ellipse)\\b[^>]*>")
        private val FILL = Regex(" fill=\"(#[0-9A-Fa-f]{3,8})\"")

        /** A rectangle over the whole canvas is the background, which some PlantUML runs draw and others do not. */
        private const val CANVAS = "width=\"100%\""

        fun of(svg: String): SvgFingerprint = SvgFingerprint(
            texts = TEXT.findAll(svg).map { unescape(it.groupValues[1]) }.sorted().toList(),
            elements = ELEMENT.findAll(svg).map { it.groupValues[1] }.sorted().toList(),
            links = LINK.findAll(svg).count(),
            colors = SHAPE.findAll(svg)
                .map { it.value }
                .filterNot { CANVAS in it }
                .mapNotNull { FILL.find(it)?.groupValues?.get(1)?.let(::normalizeColor) }
                .toSet(),
        )

        /** `#abc` -> `#AABBCC`: PlantUML versions write the same color in the short and in the long form. */
        private fun normalizeColor(color: String): String {
            val hex = color.removePrefix("#").uppercase()
            val long = if (hex.length == 3 || hex.length == 4) hex.map { "$it$it" }.joinToString("") else hex
            return "#$long"
        }

        private val ENTITY = Regex("&(#x[0-9a-fA-F]+|#\\d+|amp|lt|gt|quot|apos);")

        private fun unescape(text: String): String = ENTITY.replace(text) { match ->
            when (val entity = match.groupValues[1]) {
                "amp" -> "&"
                "lt" -> "<"
                "gt" -> ">"
                "quot" -> "\""
                "apos" -> "'"
                else -> {
                    val code = if (entity.startsWith("#x")) entity.drop(2).toInt(16) else entity.drop(1).toInt()
                    String(Character.toChars(code))
                }
            }
        }
    }
}
