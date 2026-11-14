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

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

internal class RumdlDistributionTest {
    @Test
    fun `platform selects the target, Linux gets the static musl build`() {
        assertEquals("aarch64-apple-darwin", RumdlDistribution.target("Mac OS X", "aarch64"))
        assertEquals("x86_64-apple-darwin", RumdlDistribution.target("Mac OS X", "x86_64"))
        assertEquals("x86_64-unknown-linux-musl", RumdlDistribution.target("Linux", "amd64"))
        assertEquals("aarch64-unknown-linux-musl", RumdlDistribution.target("Linux", "aarch64"))
        assertEquals("x86_64-pc-windows-msvc", RumdlDistribution.target("Windows 11", "amd64"))
        assertEquals("x86_64-pc-windows-msvc", RumdlDistribution.target("Windows 11", "aarch64"))
    }

    @Test
    fun `platform without a release archive asks for a local binary`() {
        val error = assertThrows<UnsupportedOperationException> { RumdlDistribution.target("FreeBSD", "amd64") }
        assertTrue("markdownLint.executable" in error.message.orEmpty()) { error.message }
        assertThrows<UnsupportedOperationException> { RumdlDistribution.target("Linux", "riscv64") }
    }

    @Test
    fun `every target the platform selects has a pinned checksum`() {
        listOf("Mac OS X", "Linux", "Windows 10").forEach { os ->
            listOf("aarch64", "amd64").forEach { arch ->
                val target = RumdlDistribution.target(os, arch)
                assertTrue(RumdlDistribution.CHECKSUMS[target].orEmpty().matches(Regex("[0-9a-f]{64}"))) { target }
            }
        }
    }

    @Test
    fun `asset names and addresses follow the release`() {
        val mac = RumdlAsset("0.3.4", "aarch64-apple-darwin")
        assertEquals("rumdl-v0.3.4-aarch64-apple-darwin.tar.gz", mac.archiveName)
        assertEquals("rumdl", mac.binaryName)
        assertEquals(
            "https://github.com/rvben/rumdl/releases/download/v0.3.4/rumdl-v0.3.4-aarch64-apple-darwin.tar.gz",
            mac.url(RumdlDistribution.DEFAULT_URL),
        )

        val windows = RumdlAsset("0.3.4", "x86_64-pc-windows-msvc")
        assertEquals("rumdl-v0.3.4-x86_64-pc-windows-msvc.zip", windows.archiveName)
        assertEquals("rumdl.exe", windows.binaryName)
        assertEquals(
            "https://mirror.example.org/raw/rumdl/0.3.4/x86_64-pc-windows-msvc/rumdl-v0.3.4-x86_64-pc-windows-msvc.zip",
            windows.url("https://mirror.example.org/raw/rumdl/{version}/{target}/"),
        )
    }
}
