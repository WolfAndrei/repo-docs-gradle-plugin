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

package io.github.wolfandrei.repodocs.site

import java.io.File
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

internal class ApiSymbolsTest {
    private fun type(module: String, name: String, path: String) =
        ApiSymbol(module, "com.example.$name", "api/$module/com.example/$path", type = true, packageName = "com.example")

    private fun member(module: String, name: String, path: String) =
        ApiSymbol(module, "com.example.$name", "api/$module/com.example/$path", type = false, packageName = "com.example")

    private val symbols = ApiSymbols(
        listOf(
            type("lib", "Greeter", "-greeter/index.html"),
            member("lib", "Greeter.greet", "-greeter/greet.html"),
            type("lib", "Result", "-result/index.html"),
            type("lib", "Result.Failure", "-result/-failure/index.html"),
            type("lib", "Error.Failure", "-error/-failure/index.html"),
            type("feature", "Result", "-result/index.html"),
            type("feature", "Client", "-client/index.html"),
            member("feature", "Client.close", "-client/close.html"),
            member("feature", "Other.close", "-other/close.html"),
        )
    )

    @Test
    fun `types by simple, nested and qualified name, members by Type dot member`() {
        assertEquals("api/lib/com.example/-greeter/index.html", symbols.resolve("Greeter", setOf("lib"))?.path)
        assertEquals(
            "api/lib/com.example/-greeter/greet.html",
            symbols.resolve("Greeter.greet", setOf("lib"))?.path,
        )
        assertEquals(
            "api/lib/com.example/-greeter/greet.html",
            symbols.resolve("Greeter.greet()")?.path,
        )
        assertEquals("api/lib/com.example/-result/-failure/index.html", symbols.resolve("Result.Failure", setOf("lib"))?.path)
        assertEquals(
            "api/lib/com.example/-greeter/index.html",
            symbols.resolve("com.example.Greeter", setOf("feature"))?.path,
        )
    }

    @Test
    fun `a bare member name is never linked`() {
        assertNull(symbols.resolve("greet", setOf("lib")))
        assertNull(symbols.resolve("close", setOf("feature")))
    }

    @Test
    fun `the module of the library wins, an ambiguous name in it is not linked`() {
        assertEquals("api/lib/com.example/-result/index.html", symbols.resolve("Result", setOf("lib"))?.path)
        assertEquals("api/feature/com.example/-result/index.html", symbols.resolve("Result", setOf("feature"))?.path)
        assertNull(symbols.resolve("Failure", setOf("lib"))) { "two types named Failure in lib" }
        assertNull(symbols.resolve("Result")) { "two modules have Result, the page has no library" }
        assertEquals("api/feature/com.example/-client/index.html", symbols.resolve("Client", setOf("lib"))?.path) {
            "a name that is unique in the whole reference links from another library too"
        }
    }

    @Test
    fun `code that is not a name is not linked`() {
        assertNull(symbols.resolve("Greeter(context)", setOf("lib")))
        assertNull(symbols.resolve("val x = Greeter", setOf("lib")))
        assertNull(symbols.resolve("List<Greeter>", setOf("lib")))
    }

    @Test
    fun `a kt file links to a type, not to a member`() {
        assertEquals("api/feature/com.example/-client/index.html", symbols.resolveType("Client", setOf("feature"))?.path)
        assertNull(symbols.resolveType("Unknown", setOf("feature")))
    }

    @Test
    fun `the declarations come from the Dokka search index`(@TempDir dir: File) {
        dir.resolve("scripts").mkdirs()
        dir.resolve("scripts/pages.json").writeText(
            """
            [
              {"name": "interface Greeter", "description": "com.example.Greeter",
               "location": "libs/lib/com.example/-greeter/index.html", "searchKeys": []},
              {"name": "abstract fun greet(key: String)", "description": "com.example.Greeter.greet",
               "location": "libs/lib/com.example/-greeter/greet.html", "searchKeys": []},
              {"name": "class Unknown", "description": "com.example.Unknown",
               "location": "elsewhere/com.example/-unknown/index.html", "searchKeys": []}
            ]
            """.trimIndent()
        )

        val read = ApiSymbols.read(dir, listOf(ApiModuleEntry("lib", "libs/lib")))

        assertEquals(
            ApiSymbol("lib", "com.example.Greeter", "api/libs/lib/com.example/-greeter/index.html", true, "com.example"),
            read.resolve("Greeter", setOf("lib")),
        )
        assertEquals("api/libs/lib/com.example/-greeter/greet.html", read.resolve("Greeter.greet")?.path)
        assertNull(read.resolve("Unknown")) { "a page outside the modules is skipped" }
    }
}
