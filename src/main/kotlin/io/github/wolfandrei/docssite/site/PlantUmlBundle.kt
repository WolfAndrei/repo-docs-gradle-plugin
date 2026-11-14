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
import java.io.InputStream
import java.util.zip.GZIPInputStream

/**
 * The browser build of PlantUML, the npm package `@plantuml/core` (TeaVM): unpacks the files the site needs from the
 * package archive into [PLANTUML_DIR] of the assets.
 *
 * `plantuml.js` is an ES module, and a browser does not load a module from `file://`. So the export statement is
 * replaced with `window.PlantUmlJs = {render, renderToString}`, and the file is loaded as a classic script.
 */
internal object PlantUmlBundle {
    const val PLANTUML_DIR = "plantuml"
    private val FILES = setOf("plantuml.js", "viz-global.js", "emoji.js", "openiconic.js", "themes.js", "LICENSE")
    private val EXPORT = Regex("""export\s*\{\s*([\w$]+)\s+as\s+render\s*,\s*([\w$]+)\s+as\s+renderToString\s*};?""")

    /** Unpacks [archive] into [directory]; `false` if the archive has no `plantuml.js` with the expected export. */
    fun unpack(archive: File, directory: File): Boolean {
        directory.mkdirs()
        val files = GZIPInputStream(archive.inputStream()).use(::readTar).filterKeys { it in FILES }
        val engine = files["plantuml.js"]?.toString(Charsets.UTF_8) ?: return false
        val match = EXPORT.find(engine) ?: return false

        files.forEach { (name, bytes) -> directory.resolve(name).writeBytes(bytes) }
        val (render, renderToString) = match.destructured
        directory.resolve("plantuml.js").writeText(
            engine.replaceRange(match.range, "window.PlantUmlJs={render:$render,renderToString:$renderToString};")
        )
        return true
    }

    /** Regular files of a tar stream by name without the leading `package/`. */
    private fun readTar(input: InputStream): Map<String, ByteArray> {
        val files = mutableMapOf<String, ByteArray>()
        val header = ByteArray(512)
        while (input.readNBytes(header, 0, 512) == 512) {
            val name = String(header, 0, 100, Charsets.UTF_8).substringBefore('\u0000')
            if (name.isEmpty()) break
            val prefix = String(header, 345, 155, Charsets.UTF_8).substringBefore('\u0000')
            val size = String(header, 124, 12, Charsets.US_ASCII).trim('\u0000', ' ').ifEmpty { "0" }.toLong(8)
            val type = header[156].toInt().toChar()
            val content = input.readNBytes(size.toInt())
            val padding = (512 - size % 512) % 512
            input.skipNBytes(padding)
            if (type == '0' || type == '\u0000') {
                val path = if (prefix.isEmpty()) name else "$prefix/$name"
                files[path.removePrefix("package/")] = content
            }
        }
        return files
    }
}
