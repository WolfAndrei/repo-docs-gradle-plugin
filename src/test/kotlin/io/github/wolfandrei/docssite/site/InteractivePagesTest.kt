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

package io.github.wolfandrei.docssite.site

import java.io.File
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

internal class InteractivePagesTest {

    @Test
    fun `links leave the frame, anchors and links with a target stay`() {
        val html = "<html><head><title>T</title></head><body>" +
            "<a href=\"../a.html\">a</a><a href=\"#x\">x</a><a href=\"b.html\" target=\"_blank\">b</a>" +
            "<a class=\"c\" href=\"https://example.com\">c</a></body></html>"

        val frame = InteractivePages.frameHtml(html)

        assertTrue("<a href=\"../a.html\" target=\"_top\">" in frame) { frame }
        assertTrue("<a href=\"#x\">" in frame) { frame }
        assertTrue("<a href=\"b.html\" target=\"_blank\">" in frame) { frame }
        assertTrue("<a class=\"c\" href=\"https://example.com\" target=\"_top\">" in frame) { frame }
    }

    @Test
    fun `the copy reports its height before the end of its body and lies on the site background`() {
        val frame = InteractivePages.frameHtml("<html><head></head><body><p>x</p></body></html>")

        assertTrue(frame.indexOf("docsSiteFrameHeight") < frame.indexOf("</body>")) { frame }
        assertTrue(frame.indexOf("background: transparent") < frame.indexOf("</head>")) { frame }
        assertTrue("color-scheme: light dark" in frame) { frame }
    }

    @Test
    fun `title and visible text come from the page, scripts and styles do not`(@TempDir dir: File) {
        val file = dir.resolve("page.html")
        file.writeText(
            "<html><head><title> PKCE </title><style>p { color: red }</style></head>" +
                "<body><h1>Калькулятор</h1><script>var secret = 1;</script><p>Введите   строку</p></body></html>"
        )

        val page = InteractivePages.read("doc/lib/interactive/page.html", file)

        assertEquals("PKCE", page.title)
        assertEquals("Калькулятор Введите строку", page.text)
        assertEquals("doc/lib/interactive/page.frame.html", InteractivePages.framePath(page.path))
    }

    @Test
    fun `the site page shows the copy in a frame with a way back and a link to open it alone`() {
        val page = InteractivePage("doc/lib/interactive/page.html", "PKCE", "")

        val content = InteractivePages.content(page, "../index.html" to "lib", "Open in a separate tab")

        assertTrue("<a href=\"../index.html\">lib</a>" in content) { content }
        assertTrue("<iframe class=\"interactive-frame\" src=\"page.frame.html\" title=\"PKCE\"></iframe>" in content)
        assertTrue("href=\"page.frame.html\" target=\"_blank\"" in content) { content }
    }
}
