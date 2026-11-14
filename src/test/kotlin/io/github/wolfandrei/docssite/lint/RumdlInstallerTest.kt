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

import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.GZIPOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

internal class RumdlInstallerTest {
    private val root = File("build/rumdl-installer-test").absoluteFile.apply {
        deleteRecursively()
        mkdirs()
    }
    private val releases = root.resolve("releases")
    private val cache = root.resolve("cache")
    private val template = releases.toURI().toString().trimEnd('/') + "/v{version}/{asset}"
    private val binary = "#!/bin/sh\necho rumdl\n".toByteArray()

    private fun publish(asset: RumdlAsset, archive: ByteArray): String {
        releases.resolve("v${asset.version}/${asset.archiveName}").apply {
            parentFile.mkdirs()
            writeBytes(archive)
            return RumdlInstaller.sha256(this)
        }
    }

    @Test
    fun `tar archive is verified, extracted and then reused offline`() {
        val asset = RumdlAsset("0.3.4", "aarch64-apple-darwin")
        val checksum = publish(asset, tarGz(mapOf("LICENSE" to "MIT".toByteArray(), "rumdl" to binary)))

        val installed = RumdlInstaller(cache, template, offline = false).install(asset, checksum.uppercase())

        assertEquals(cache.resolve("0.3.4/aarch64-apple-darwin/rumdl"), installed)
        assertTrue(installed.readBytes().contentEquals(binary))
        assertTrue(installed.canExecute())
        assertEquals(listOf("archive.sha256", "install.lock", "rumdl"), installed.parentFile.list()!!.sorted())

        releases.deleteRecursively()
        assertEquals(installed, RumdlInstaller(cache, template, offline = true).install(asset, checksum))
    }

    @Test
    fun `zip archive of Windows is extracted`() {
        val asset = RumdlAsset("0.3.4", "x86_64-pc-windows-msvc")
        val checksum = publish(asset, zip(mapOf("rumdl.exe" to binary, "LICENSE" to "MIT".toByteArray())))

        val installed = RumdlInstaller(cache, template, offline = false).install(asset, checksum)

        assertEquals("rumdl.exe", installed.name)
        assertTrue(installed.readBytes().contentEquals(binary))
    }

    @Test
    fun `archive with another checksum fails and is not installed`() {
        val asset = RumdlAsset("0.3.4", "x86_64-unknown-linux-musl")
        val actual = publish(asset, tarGz(mapOf("rumdl" to binary)))
        val expected = "0".repeat(64)

        val error = assertThrows<RumdlInstallException> {
            RumdlInstaller(cache, template, offline = false).install(asset, expected)
        }

        assertTrue("SHA-256 of rumdl from" in error.message!! && "is $actual, expected $expected" in error.message!!) {
            error.message
        }
        assertFalse(cache.resolve("0.3.4/x86_64-unknown-linux-musl/rumdl").exists())
        assertEquals(listOf("install.lock"), cache.resolve("0.3.4/x86_64-unknown-linux-musl").list()!!.toList())
    }

    @Test
    fun `binary installed with another checksum is installed again`() {
        val asset = RumdlAsset("0.3.4", "aarch64-apple-darwin")
        val first = publish(asset, tarGz(mapOf("rumdl" to binary)))
        RumdlInstaller(cache, template, offline = false).install(asset, first)

        val changed = "#!/bin/sh\necho changed\n".toByteArray()
        val second = publish(asset, tarGz(mapOf("rumdl" to changed)))
        val installed = RumdlInstaller(cache, template, offline = false).install(asset, second)

        assertTrue(installed.readBytes().contentEquals(changed))
    }

    @Test
    fun `missing checksum, offline build without the binary and missing archive fail with the way out`() {
        val asset = RumdlAsset("9.9.9", "aarch64-apple-darwin")

        val noChecksum = assertThrows<RumdlInstallException> {
            RumdlInstaller(cache, template, offline = false).install(asset, null)
        }
        assertTrue("checksums.put(\"aarch64-apple-darwin\"" in noChecksum.message!!) { noChecksum.message }
        assertTrue("rumdl-v9.9.9-aarch64-apple-darwin.tar.gz.sha256" in noChecksum.message!!) { noChecksum.message }

        val offline = assertThrows<RumdlInstallException> {
            RumdlInstaller(cache, template, offline = true).install(asset, "0".repeat(64))
        }
        assertTrue("Gradle is offline" in offline.message!!) { offline.message }

        val missing = assertThrows<RumdlInstallException> {
            RumdlInstaller(cache, template, offline = false).install(asset, "0".repeat(64))
        }
        assertTrue("Could not download rumdl from" in missing.message!!) { missing.message }
    }

    @Test
    fun `credentials of the address are not in the messages`() {
        val asset = RumdlAsset("9.9.9", "aarch64-apple-darwin")
        val error = assertThrows<RumdlInstallException> {
            RumdlInstaller(cache, "http://user:secret@127.0.0.1:9/{asset}", offline = false)
                .install(asset, "0".repeat(64))
        }
        assertFalse("secret" in error.message!!) { error.message }
    }

    private fun tarGz(files: Map<String, ByteArray>): ByteArray {
        val tar = ByteArrayOutputStream()
        files.forEach { (name, content) ->
            val header = ByteArray(512)
            name.toByteArray().copyInto(header, 0)
            "0000755\u0000".toByteArray().copyInto(header, 100)
            "%011o\u0000".format(content.size).toByteArray().copyInto(header, 124)
            header[156] = '0'.code.toByte()
            "ustar\u000000".toByteArray().copyInto(header, 257)
            "        ".toByteArray().copyInto(header, 148)
            "%06o\u0000 ".format(header.sumOf { it.toInt() and 0xff }).toByteArray().copyInto(header, 148)
            tar.write(header)
            tar.write(content)
            tar.write(ByteArray((512 - content.size % 512) % 512))
        }
        tar.write(ByteArray(1024))

        return ByteArrayOutputStream().also { output -> GZIPOutputStream(output).use { it.write(tar.toByteArray()) } }
            .toByteArray()
    }

    private fun zip(files: Map<String, ByteArray>): ByteArray {
        return ByteArrayOutputStream().also { output ->
            ZipOutputStream(output).use { zip ->
                files.forEach { (name, content) ->
                    zip.putNextEntry(ZipEntry(name))
                    zip.write(content)
                    zip.closeEntry()
                }
            }
        }.toByteArray()
    }
}
