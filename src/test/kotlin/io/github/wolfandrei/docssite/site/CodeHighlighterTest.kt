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

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class CodeHighlighterTest {

    private fun tokens(code: String, language: String): List<Pair<String, String>> {
        val html = CodeHighlighter.highlight(code, language) ?: error("no highlighting for $language")
        return Regex("<span class=\"tok-(\\w+)\">([^<]*)</span>").findAll(html)
            .map { it.groupValues[1] to it.groupValues[2] }.toList()
    }

    @Test
    fun `Kotlin keywords, types, strings, numbers, comments and annotations`() {
        val found = tokens(
            "@Suppress(\"x\")\nsuspend fun load(id: Int = 42): Result<String> // loads\n/* block */",
            "kotlin",
        )

        assertTrue("meta" to "@Suppress" in found) { found.toString() }
        assertTrue("string" to "&quot;x&quot;" in found) { found.toString() }
        assertTrue("keyword" to "suspend" in found && "keyword" to "fun" in found) { found.toString() }
        assertTrue("type" to "Result" in found && "type" to "String" in found) { found.toString() }
        assertTrue("number" to "42" in found) { found.toString() }
        assertTrue("comment" to "// loads" in found && "comment" to "/* block */" in found) { found.toString() }
        assertTrue(found.none { it.second == "load" }) { "names of functions stay plain: $found" }
    }

    @Test
    fun `text inside strings and comments is not split into tokens`() {
        val found = tokens("val s = \"fun class 1\" // val Type", "kts")

        assertEquals(
            listOf("keyword" to "val", "string" to "&quot;fun class 1&quot;", "comment" to "// val Type"),
            found,
        )
    }

    @Test
    fun `the HTML of a block keeps every character and escapes it`() {
        val code = "if (a < b && c > \"d\") { x = 'y' }"
        val html = CodeHighlighter.highlight(code, "swift")!!
        val text = html.replace(Regex("<[^>]+>"), "")
            .replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&amp;", "&")

        assertEquals(code, text)
    }

    @Test
    fun `shell commands, options, variables and comments`() {
        val found = tokens("./gradlew build --info -Pkey=\$HOME # build it\nexport A=1", "bash")

        assertTrue("type" to "gradlew" in found) { found.toString() }
        assertTrue("property" to "--info" in found) { found.toString() }
        assertTrue("variable" to "\$HOME" in found) { found.toString() }
        assertTrue("comment" to "# build it" in found) { found.toString() }
        assertTrue("keyword" to "export" in found) { found.toString() }
    }

    @Test
    fun `JSON keys differ from string values`() {
        val found = tokens("{\"name\": \"site\", \"port\": 8000, \"debug\": false}", "json")

        assertEquals(
            listOf(
                "property" to "&quot;name&quot;",
                "string" to "&quot;site&quot;",
                "property" to "&quot;port&quot;",
                "number" to "8000",
                "property" to "&quot;debug&quot;",
                "keyword" to "false",
            ),
            found,
        )
    }

    @Test
    fun `XML tags, attributes and comments`() {
        val found = tokens("<!-- c --><manifest package=\"a.b\"><uses-sdk/></manifest>", "xml")

        assertEquals(
            listOf(
                "comment" to "&lt;!-- c --&gt;",
                "tag" to "manifest",
                "property" to "package",
                "string" to "&quot;a.b&quot;",
                "tag" to "uses-sdk",
                "tag" to "manifest",
            ),
            found,
        )
    }

    @Test
    fun `YAML, properties, CMake, PlantUML and diff`() {
        assertTrue("property" to "name" in tokens("name: site # c", "yaml"))
        assertTrue("comment" to "# c" in tokens("name: site # c", "yml"))
        assertTrue("property" to "org.gradle.caching" in tokens("org.gradle.caching=true", "properties"))
        assertTrue("meta" to "[versions]" in tokens("[versions]\nkotlin = \"2.0\"", "toml"))
        assertTrue("keyword" to "add_library" in tokens("add_library(core STATIC \${SOURCES})", "cmake"))
        assertTrue("variable" to "\${SOURCES}" in tokens("add_library(core STATIC \${SOURCES})", "cmake"))
        assertTrue("meta" to "@startuml" in tokens("@startuml\nparticipant App\n' comment\n@enduml", "plantuml"))
        assertTrue("comment" to "' comment" in tokens("@startuml\nparticipant App\n' comment\n@enduml", "puml"))
        val diff = tokens("@@ -1 +1 @@\n-old\n+new", "diff")
        assertEquals(listOf("property" to "@@ -1 +1 @@", "deleted" to "-old", "inserted" to "+new"), diff)
    }

    @Test
    fun `C preprocessor lines and Objective-C keywords`() {
        val found = tokens("#include <stdio.h>\n@interface Foo : NSObject\n@end", "objc")

        assertTrue("meta" to "#include &lt;stdio.h&gt;" in found) { found.toString() }
        assertTrue("keyword" to "@interface" in found && "keyword" to "@end" in found) { found.toString() }
    }

    @Test
    fun `text and unknown languages are not highlighted`() {
        assertNull(CodeHighlighter.highlight("fun a()", "text"))
        assertNull(CodeHighlighter.highlight("fun a()", "brainfuck"))
    }

    @Test
    fun `blocks of a page are highlighted, inline code and other blocks are not`() {
        val html = "<p><code>val x</code></p>\n<pre><code class=\"language-kotlin\">val x = &quot;a&quot;\n</code></pre>\n" +
            "<pre><code class=\"language-text\">val x</code></pre><pre><code>val y</code></pre>"

        val result = CodeHighlighter.highlightBlocks(html)

        assertTrue(result.startsWith("<p><code>val x</code></p>")) { result }
        assertTrue(
            "<pre><code class=\"language-kotlin highlighted\"><span class=\"tok-keyword\">val</span> x = " +
                "<span class=\"tok-string\">&quot;a&quot;</span>\n</code></pre>" in result
        ) { result }
        assertTrue(result.endsWith("<pre><code class=\"language-text\">val x</code></pre><pre><code>val y</code></pre>")) {
            result
        }
    }

    @Test
    fun `KDoc samples of Dokka are highlighted`() {
        val html = "<pre><code class=\"block lang-kotlin\" theme=\"idea\">val a = 1</code></pre>"

        assertTrue("<span class=\"tok-number\">1</span>" in CodeHighlighter.highlightBlocks(html))
    }
}
