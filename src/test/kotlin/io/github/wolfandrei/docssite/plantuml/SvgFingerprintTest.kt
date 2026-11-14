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

package io.github.wolfandrei.docssite.plantuml

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

internal class SvgFingerprintTest {
    /** The same diagram as Graphviz `dot` draws it: other coordinates, link comments, source lines. */
    private val dot = """
        |<svg><?plantuml 1.2026.6?><g><text fill="#000000" x="414.1" y="23.5">lib: &#1082;&#1086;&#1085;&#1090;&#1077;&#1082;&#1089;&#1090;</text>
        |<g class="entity" data-qualified-name="app" data-source-line="7"><rect fill="#999999" height="104.79" width="207.96" x="306.39"/>
        |<text fill="#FFFFFF" x="356.9">App</text></g>
        |<!--link app to lib--><g class="link" data-source-line="9"><path d="M1,2 L3,4" fill="none"/><polygon fill="#666666" points="1,2"/></g>
        |</g></svg>
    """.trimMargin()

    /** The same diagram as Smetana draws it. */
    private val smetana = """
        |<svg><?plantuml 1.2026.10?><g><text fill="#000000" x="12.5" y="23.5">lib: контекст</text>
        |<g class="entity" data-qualified-name="app"><rect fill="#999999" height="104.79" width="207.96" x="6.39"/>
        |<text fill="#FFFFFF" x="56.9">App</text></g>
        |<g class="link"><polygon fill="none" points="5,6"/><path d="M5,6 L7,8 L9,9" fill="none"/></g>
        |</g></svg>
    """.trimMargin()

    @Test
    fun `renders by different layout engines have one fingerprint`() {
        assertEquals(SvgFingerprint.of(dot), SvgFingerprint.of(smetana))
    }

    @Test
    fun `short and long forms of a color are one color`() {
        assertEquals(
            SvgFingerprint.of(dot),
            SvgFingerprint.of(smetana.replace("#FFFFFF", "#FFF").replace("#000000", "#000"))
        )
    }

    @Test
    fun `a changed text or color changes the fingerprint`() {
        assertNotEquals(SvgFingerprint.of(dot), SvgFingerprint.of(smetana.replace(">App<", ">Приложение<")))
        assertNotEquals(SvgFingerprint.of(dot), SvgFingerprint.of(smetana.replace("#999999", "#438DD5")))
        assertNotEquals(SvgFingerprint.of(dot), SvgFingerprint.of(smetana.replace("<g class=\"link\">", "<g>")))
    }
}
