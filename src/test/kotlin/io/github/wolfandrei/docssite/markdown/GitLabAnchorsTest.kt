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

internal class GitLabAnchorsTest {

    @Test
    fun `Cyrillic headings keep their letters and lose punctuation`() {
        assertEquals("форматы-хранения", GitLabAnchors.slug("Форматы хранения"))
        assertEquals("ключи-localproperties", GitLabAnchors.slug("Ключи local.properties"))
        assertEquals("что-нужно-обновить", GitLabAnchors.slug("Что нужно обновить?"))
        assertEquals("ёлка-и-ель", GitLabAnchors.slug("Ёлка и ель"))
    }

    @Test
    fun `runs of hyphens become one, as GitLab documents`() {
        assertEquals("this-is-an-en-dash", GitLabAnchors.slug("This – is an en-dash"))
        assertEquals(
            "oldinputs-deprecated-удалить-в-20",
            GitLabAnchors.slug("oldInputs (Deprecated - удалить в 2.0)")
        )
    }

    @Test
    fun `underscores, digits and code stay, other symbols go`() {
        assertEquals("snake_case-и-camelcase", GitLabAnchors.slug("snake_case и camelCase"))
        assertEquals("ios-17-и-macos-14", GitLabAnchors.slug("iOS 17 и macOS 14"))
        assertEquals("cinterop-для-sysroot", GitLabAnchors.slug("cinterop для sysroot!"))
        assertEquals("ab", GitLabAnchors.slug("a b"))
    }

    @Test
    fun `digits only get the anchor prefix`() {
        assertEquals("anchor-123", GitLabAnchors.slug("123"))
        assertEquals("1-шаг", GitLabAnchors.slug("1. Шаг"))
    }

    @Test
    fun `repeated headings are numbered from 1`() {
        val anchors = GitLabAnchors()

        assertEquals(
            listOf("пример", "пример-1", "пример-2", "другое"),
            listOf("Пример", "Пример", "Пример", "Другое").map(anchors::next)
        )
    }
}
