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

package io.github.wolfandrei.docssite.markdown

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class HeadingAnchorsTest {

    @Test
    fun `Cyrillic headings keep their letters and lose punctuation`() {
        assertEquals("форматы-хранения", HeadingAnchors.slug("Форматы хранения"))
        assertEquals("ключи-localproperties", HeadingAnchors.slug("Ключи local.properties"))
        assertEquals("что-нужно-обновить", HeadingAnchors.slug("Что нужно обновить?"))
        assertEquals("ёлка-и-ель", HeadingAnchors.slug("Ёлка и ель"))
    }

    @Test
    fun `runs of hyphens become one, as GitLab documents`() {
        assertEquals("this-is-an-en-dash", HeadingAnchors.slug("This – is an en-dash", AnchorStyle.GITLAB))
        assertEquals(
            "oldinputs-deprecated-удалить-в-20",
            HeadingAnchors.slug("oldInputs (Deprecated - удалить в 2.0)", AnchorStyle.GITLAB)
        )
    }

    @Test
    fun `underscores, digits and code stay, other symbols go`() {
        assertEquals("snake_case-и-camelcase", HeadingAnchors.slug("snake_case и camelCase"))
        assertEquals("ios-17-и-macos-14", HeadingAnchors.slug("iOS 17 и macOS 14"))
        assertEquals("cinterop-для-sysroot", HeadingAnchors.slug("cinterop для sysroot!"))
        assertEquals("ab", HeadingAnchors.slug("a b"))
    }

    @Test
    fun `digits only get the anchor prefix on GitLab`() {
        assertEquals("anchor-123", HeadingAnchors.slug("123", AnchorStyle.GITLAB))
        assertEquals("1-шаг", HeadingAnchors.slug("1. Шаг", AnchorStyle.GITLAB))
    }

    @Test
    fun `repeated headings are numbered from 1`() {
        val anchors = HeadingAnchors()

        assertEquals(
            listOf("пример", "пример-1", "пример-2", "другое"),
            listOf("Пример", "Пример", "Пример", "Другое").map(anchors::next)
        )
    }

    @Test
    fun `GitHub style is the default`() {
        assertEquals("a---b", HeadingAnchors.slug("A - B"))
        assertEquals(listOf("a---b", "a---b-1"), HeadingAnchors().let { listOf(it.next("A - B"), it.next("A - B")) })
    }

    @Test
    fun `GitHub style keeps runs of hyphens and digits as they are`() {
        assertEquals("a---b", HeadingAnchors.slug("A - B", AnchorStyle.GITHUB))
        assertEquals("123", HeadingAnchors.slug("123", AnchorStyle.GITHUB))
        assertEquals("форматы-хранения", HeadingAnchors.slug("Форматы хранения", AnchorStyle.GITHUB))
        val anchors = HeadingAnchors(AnchorStyle.GITHUB)
        assertEquals(listOf("a---b", "a---b-1"), listOf(anchors.next("A - B"), anchors.next("A - B")))
    }
}
