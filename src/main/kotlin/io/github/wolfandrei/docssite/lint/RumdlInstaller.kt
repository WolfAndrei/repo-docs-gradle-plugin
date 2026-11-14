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

package io.github.wolfandrei.docssite.lint

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLDecoder
import java.nio.channels.FileChannel
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.security.MessageDigest
import java.util.Base64
import java.util.zip.GZIPInputStream
import java.util.zip.ZipInputStream
import org.gradle.api.GradleException

/** Why rumdl could not be installed: a download error, a checksum mismatch, a missing checksum. */
internal class RumdlInstallException(message: String, cause: Throwable? = null) : GradleException(message, cause)

/**
 * Installs the rumdl binary of [asset] in [cacheDirectory], such as `~/.gradle/caches/docs-site/rumdl`, under
 * `<version>/<target>/`. The archive is downloaded from [urlTemplate] (see [RumdlAsset.url]), its SHA-256 must be
 * [checksum], and only the binary is extracted from it. An installed binary is reused without the network while the
 * checksum it was installed with matches, so a build with `--offline` works once the binary is in the cache.
 */
internal class RumdlInstaller(
    private val cacheDirectory: File,
    private val urlTemplate: String,
    private val offline: Boolean,
) {
    fun install(asset: RumdlAsset, checksum: String?): File {
        val expected = checksum?.trim()?.lowercase()?.takeIf { it.isNotEmpty() } ?: throw RumdlInstallException(
            "No SHA-256 of ${asset.archiveName} (rumdl ${asset.version}, ${asset.target}): set " +
                "documentationSite.checks.markdownLint.checksums.put(\"${asset.target}\", \"<sha256>\"), the value " +
                "is in ${asset.url(urlTemplate)}.sha256"
        )
        val directory = cacheDirectory.resolve(asset.version).resolve(asset.target)
        val binary = directory.resolve(asset.binaryName)
        val marker = directory.resolve(MARKER)

        fun installed() = binary.isFile && marker.isFile && marker.readText().trim() == expected
        if (installed()) return binary

        directory.mkdirs()
        return locked(directory.resolve(LOCK)) {
            if (installed()) return@locked binary

            val url = asset.url(urlTemplate)
            if (offline) {
                throw RumdlInstallException(
                    "rumdl ${asset.version} is not in $directory and Gradle is offline: run the build once " +
                        "without --offline to download $url, or set documentationSite.checks.markdownLint.executable"
                )
            }
            val archive = File.createTempFile("download-", ".part", directory)
            try {
                download(url, archive)
                verify(archive, expected, url)
                val extracted = File.createTempFile("binary-", ".part", directory)
                extract(archive, asset, extracted)
                extracted.setExecutable(true, false)
                move(extracted, binary)
                marker.writeText(expected + "\n")
            } finally {
                archive.delete()
            }
            binary
        }
    }

    private fun download(url: String, target: File) {
        try {
            val uri = URI(url)
            val connection = uri.toURL().openConnection()
            connection.connectTimeout = TIMEOUT_MILLIS
            connection.readTimeout = TIMEOUT_MILLIS
            uri.rawUserInfo?.let { userInfo ->
                val credentials = URLDecoder.decode(userInfo, Charsets.UTF_8)
                val encoded = Base64.getEncoder().encodeToString(credentials.toByteArray(Charsets.UTF_8))
                connection.setRequestProperty("Authorization", "Basic $encoded")
            }
            if (connection is HttpURLConnection) {
                connection.instanceFollowRedirects = true
                val status = connection.responseCode
                if (status !in 200..299) throw IOException("HTTP $status")
            }
            connection.getInputStream().use { input -> target.outputStream().use { input.copyTo(it) } }
        } catch (e: IOException) {
            throw RumdlInstallException("Could not download rumdl from ${withoutUserInfo(url)}: ${e.message}", e)
        }
    }

    private fun verify(archive: File, expected: String, url: String) {
        val actual = sha256(archive)
        if (actual != expected) {
            throw RumdlInstallException(
                "SHA-256 of rumdl from ${withoutUserInfo(url)} is $actual, expected $expected. The file is not " +
                    "the release archive: check the mirror, or set markdownLint.checksums if the version was changed"
            )
        }
    }

    private fun extract(archive: File, asset: RumdlAsset, target: File) {
        val found = archive.inputStream().buffered().use { input ->
            if (asset.windows) extractZip(input, asset.binaryName, target) else extractTarGz(input, asset.binaryName, target)
        }
        if (!found) throw RumdlInstallException("${asset.archiveName} has no ${asset.binaryName}")
    }

    private fun move(source: File, target: File) {
        try {
            Files.move(source.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (e: AtomicMoveNotSupportedException) {
            Files.move(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    /** Runs [action] holding the file lock [lockFile]: two builds do not install into one directory at once. */
    private fun <T> locked(lockFile: File, action: () -> T): T = synchronized(RumdlInstaller) {
        FileChannel.open(lockFile.toPath(), StandardOpenOption.CREATE, StandardOpenOption.WRITE).use { channel ->
            channel.lock().use { action() }
        }
    }

    internal companion object {
        private const val MARKER = "archive.sha256"
        private const val LOCK = "install.lock"
        private const val TIMEOUT_MILLIS = 60_000
        private const val TAR_BLOCK = 512

        fun sha256(file: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    digest.update(buffer, 0, read)
                }
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }

        private fun withoutUserInfo(url: String): String = url.replace(Regex("(?<=://)[^/@]*@"), "")

        /** Copies the entry of [archive] whose file name is [name] to [target]; `false` if there is none. */
        fun extractZip(archive: InputStream, name: String, target: File): Boolean {
            val zip = ZipInputStream(archive)
            while (true) {
                val entry = zip.nextEntry ?: return false
                if (!entry.isDirectory && entry.name.substringAfterLast('/') == name) {
                    target.outputStream().use { zip.copyTo(it) }
                    return true
                }
            }
        }

        /**
         * Copies the regular file of the gzip-compressed tar [archive] whose file name is [name] to [target]; `false`
         * if there is none. Reads the ustar and GNU headers; long names are not needed for the release archives.
         */
        fun extractTarGz(archive: InputStream, name: String, target: File): Boolean {
            val tar = GZIPInputStream(archive)
            val header = ByteArray(TAR_BLOCK)
            while (true) {
                if (!readFully(tar, header)) return false
                if (header.all { it.toInt() == 0 }) return false

                val entryName = field(header, 0, 100)
                val prefix = field(header, 345, 155)
                val path = if (prefix.isEmpty()) entryName else "$prefix/$entryName"
                val size = field(header, 124, 12).trim().ifEmpty { "0" }.toLong(8)
                val type = header[156].toInt().toChar()
                val regular = type == '0' || type == '\u0000'

                if (regular && path.substringAfterLast('/') == name) {
                    target.outputStream().use { output -> copy(tar, output, size) }
                    return true
                }
                skip(tar, (size + TAR_BLOCK - 1) / TAR_BLOCK * TAR_BLOCK)
            }
        }

        private fun field(header: ByteArray, offset: Int, length: Int): String {
            var end = offset
            while (end < offset + length && header[end].toInt() != 0) end++
            return String(header, offset, end - offset, Charsets.UTF_8)
        }

        private fun readFully(input: InputStream, buffer: ByteArray): Boolean {
            var read = 0
            while (read < buffer.size) {
                val count = input.read(buffer, read, buffer.size - read)
                if (count < 0) return false
                read += count
            }
            return true
        }

        private fun copy(input: InputStream, output: java.io.OutputStream, size: Long) {
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var left = size
            while (left > 0) {
                val count = input.read(buffer, 0, minOf(buffer.size.toLong(), left).toInt())
                if (count < 0) throw IOException("truncated tar archive")
                output.write(buffer, 0, count)
                left -= count
            }
        }

        private fun skip(input: InputStream, size: Long) {
            var left = size
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (left > 0) {
                val count = input.read(buffer, 0, minOf(buffer.size.toLong(), left).toInt())
                if (count < 0) return
                left -= count
            }
        }
    }
}
