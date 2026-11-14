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

import io.github.wolfandrei.docssite.markdown.RelativeLink
import io.github.wolfandrei.docssite.site.SiteTemplate
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.io.File
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.Executors

/**
 * HTTP server of the site on `localhost`. A path missing from [siteDirectory] is looked up in [repositoryRoot], so the
 * relative links to the sources of the repository open as text. Responses are not cached, so a reloaded page shows
 * the rebuilt site.
 */
internal class DocumentationServer(
    private val siteDirectory: File,
    private val repositoryRoot: File,
    private val port: Int,
) {
    private var server: HttpServer? = null

    val url: String
        get() = "http://localhost:${server?.address?.port ?: port}/"

    fun start() {
        val server = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), port), 0)
        server.createContext("/") { exchange ->
            try {
                handle(exchange)
            } catch (exception: Exception) {
                runCatching { respond(exchange, 500, "text/plain; charset=utf-8", exception.toString().toByteArray()) }
            } finally {
                exchange.close()
            }
        }
        server.executor = Executors.newFixedThreadPool(4) { runnable ->
            Thread(runnable, "documentation-server").apply { isDaemon = true }
        }
        server.start()
        this.server = server
    }

    fun stop() {
        server?.stop(0)
        server = null
    }

    private fun handle(exchange: HttpExchange) {
        if (exchange.requestMethod != "GET" && exchange.requestMethod != "HEAD") {
            respond(exchange, 405, "text/plain; charset=utf-8", "Method not allowed".toByteArray())
            return
        }

        val path = RelativeLink.normalize(RelativeLink.decode(exchange.requestURI.rawPath).split('/'))
        val file = path?.let { resolve(it) }
        when {
            file == null -> respond(
                exchange,
                404,
                "text/plain; charset=utf-8",
                "Not found: ${exchange.requestURI}".toByteArray()
            )
            file.isDirectory && !exchange.requestURI.rawPath.endsWith("/") -> {
                exchange.responseHeaders.add("Location", exchange.requestURI.rawPath + "/")
                respond(exchange, 301, "text/plain; charset=utf-8", ByteArray(0))
            }

            file.isDirectory && file.resolve("index.html").isFile -> {
                respond(exchange, 200, "text/html; charset=utf-8", file.resolve("index.html").readBytes())
            }

            file.isDirectory -> respond(exchange, 200, "text/html; charset=utf-8", listing(path, file).toByteArray())
            else -> respond(exchange, 200, contentType(file.name), file.readBytes())
        }
    }

    /** The file or directory of [path] on the site, otherwise in the repository; hidden paths are not served. */
    private fun resolve(path: String): File? {
        val site = siteDirectory.resolve(path)
        if (site.exists()) return site

        if (path.split('/').any { it.startsWith(".") }) return null
        return repositoryRoot.resolve(path).takeIf { it.exists() }
    }

    private fun listing(path: String, directory: File): String {
        val entries = directory.listFiles().orEmpty()
            .filterNot { it.name.startsWith(".") }
            .sortedWith(compareBy({ !it.isDirectory }, { it.name }))
            .joinToString("\n") { file ->
                val name = file.name + if (file.isDirectory) "/" else ""
                "<li><a href=\"${SiteTemplate.escape(name)}\">${SiteTemplate.escape(name)}</a></li>"
            }

        return "<!DOCTYPE html>\n<html lang=\"ru\"><head><meta charset=\"utf-8\"><title>/$path</title></head>" +
            "<body><h1>/${SiteTemplate.escape(
                path
            )}</h1><ul><li><a href=\"../\">../</a></li>\n$entries</ul></body></html>\n"
    }

    private fun respond(exchange: HttpExchange, status: Int, contentType: String, body: ByteArray) {
        exchange.responseHeaders.add("Content-Type", contentType)
        exchange.responseHeaders.add("Cache-Control", "no-cache")
        if (exchange.requestMethod == "HEAD") {
            exchange.sendResponseHeaders(status, -1)
            return
        }
        exchange.sendResponseHeaders(status, if (body.isEmpty()) -1 else body.size.toLong())
        exchange.responseBody.use { it.write(body) }
    }

    private fun contentType(name: String): String {
        return when (name.substringAfterLast('.', "").lowercase()) {
            "html", "htm" -> "text/html; charset=utf-8"
            "css" -> "text/css; charset=utf-8"
            "js" -> "text/javascript; charset=utf-8"
            "json" -> "application/json; charset=utf-8"
            "svg" -> "image/svg+xml"
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            "ico" -> "image/x-icon"
            "pdf" -> "application/pdf"
            "woff" -> "font/woff"
            "woff2" -> "font/woff2"
            "zip", "jar", "aar", "klib", "a", "so", "dylib", "xcframework" -> "application/octet-stream"
            else -> "text/plain; charset=utf-8"
        }
    }
}
