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

import io.github.wolfandrei.repodocs.AnchorStyle

/**
 * Heading ids by the rules of GitLab or GitHub, so that the `#anchor` links written for the hosting keep working on
 * the site. Both styles:
 *
 * 1. lowercase the text;
 * 2. remove everything except letters, marks, decimal digits, `_`, `-` and the space;
 * 3. turn each space into `-`;
 * 4. number a repeated id with `-1`, `-2` and so on in the order of the headings.
 *
 * GitLab also turns runs of `-` into one `-` and gives an id of digits only the `anchor-` prefix; GitHub does neither:
 * `A - B` is `a-b` on GitLab and `a---b` on GitHub.
 *
 * One instance numbers the headings of one page.
 */
internal class HeadingAnchors(private val style: AnchorStyle = AnchorStyle.GITHUB) {
    private val counters = mutableMapOf<String, Int>()

    /** Id of the next heading of the page with the text [text]. */
    fun next(text: String): String {
        val id = slug(text, style)
        val count = counters.getOrDefault(id, 0)
        counters[id] = count + 1

        return if (count == 0) id else "$id-$count"
    }

    companion object {
        private val REJECTED = Regex("[^\\p{L}\\p{M}\\p{Nd}\\p{Pc}\\- ]")
        private val HYPHENS = Regex("-{2,}")
        private val DIGITS = Regex("\\d+")

        /** Id of a heading with the text [text] before the repeated ids are numbered. */
        fun slug(text: String, style: AnchorStyle = AnchorStyle.GITHUB): String {
            val id = text.lowercase()
                .replace(REJECTED, "")
                .replace(' ', '-')
            if (style == AnchorStyle.GITHUB) return id

            val collapsed = id.replace(HYPHENS, "-")
            return if (DIGITS.matches(collapsed)) "anchor-$collapsed" else collapsed
        }
    }
}
