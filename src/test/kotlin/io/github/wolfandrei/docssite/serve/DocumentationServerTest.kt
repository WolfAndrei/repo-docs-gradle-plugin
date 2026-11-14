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

package io.github.wolfandrei.docssite.serve

import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class DocumentationServerTest {
    private val root = File("build/documentation-server-test").absoluteFile
    private lateinit var server: DocumentationServer

    @BeforeEach
    fun start() {
        root.deleteRecursively()
        root.resolve("build/docs-site/documentation/диаграммы").mkdirs()
        root.resolve("build/docs-site/index.html").writeText("<h1>Главная</h1>")
        root.resolve("build/docs-site/documentation/index.html").writeText("<h1>Оглавление</h1>")
        root.resolve("build/docs-site/documentation/диаграммы/context.svg").writeText("<svg/>")
        root.resolve("lib/src").mkdirs()
        root.resolve("lib/src/Greeter.kt").writeText("interface Greeter")
        root.resolve(".git").mkdirs()
        root.resolve(".git/config").writeText("secret")

        server = DocumentationServer(root.resolve("build/docs-site"), root, port = 0)
        server.start()
    }

    @AfterEach
    fun stop() {
        server.stop()
    }

    private fun get(path: String): Pair<Int, String> {
        val connection = URI(server.url + path).toURL().openConnection() as HttpURLConnection
        connection.instanceFollowRedirects = false
        val status = connection.responseCode
        val stream = if (status < 400) connection.inputStream else connection.errorStream
        val body = stream?.use { it.readBytes().toString(Charsets.UTF_8) }

        return status to body.orEmpty()
    }

    @Test
    fun `pages, encoded paths and repository sources are served`() {
        assertEquals(200 to "<h1>Главная</h1>", get(""))
        assertEquals(200 to "<h1>Оглавление</h1>", get("documentation/"))
        assertEquals(
            200 to "<svg/>",
            get("documentation/%D0%B4%D0%B8%D0%B0%D0%B3%D1%80%D0%B0%D0%BC%D0%BC%D1%8B/context.svg")
        )
        assertEquals(200 to "interface Greeter", get("lib/src/Greeter.kt"))
        assertEquals(301, get("documentation").first)
    }

    @Test
    fun `hidden and missing paths are not served`() {
        assertEquals(404, get(".git/config").first)
        assertEquals(404, get("nothing.html").first)
    }
}
