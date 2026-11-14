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

/**
 * A release archive of rumdl for one platform: [target] is the Rust target triple of the release assets, such as
 * `aarch64-apple-darwin`.
 */
internal data class RumdlAsset(val version: String, val target: String) {
    val windows: Boolean get() = "windows" in target

    /** File name of the archive, such as `rumdl-v0.3.4-aarch64-apple-darwin.tar.gz`. */
    val archiveName: String get() = "rumdl-v$version-$target.${if (windows) "zip" else "tar.gz"}"

    /** File name of the binary inside the archive. */
    val binaryName: String get() = if (windows) "rumdl.exe" else "rumdl"

    /**
     * Address of the archive from the [template]: `{version}`, `{target}` and `{asset}` (the archive name) are
     * replaced; a template without `{asset}` is a directory, and the archive name is appended to it.
     */
    fun url(template: String): String {
        val expanded = template.replace("{version}", version).replace("{target}", target)
        return if ("{asset}" in expanded) {
            expanded.replace("{asset}", archiveName)
        } else {
            expanded.trimEnd('/') + "/" + archiveName
        }
    }
}

/** The rumdl releases the plugin knows: the pinned version, its checksums and the choice of the platform. */
internal object RumdlDistribution {
    const val VERSION = "0.3.4"

    /** The GitHub releases of rumdl. */
    const val DEFAULT_URL = "https://github.com/rvben/rumdl/releases/download/v{version}/{asset}"

    /** SHA-256 of the release archives of [VERSION] by target, from the `.sha256` files of the release. */
    val CHECKSUMS: Map<String, String> = mapOf(
        "aarch64-apple-darwin" to "51f7bce0d56ea632ed65e7e42a99effb7ce7a23923fe0738ecda0e1d11bcb788",
        "x86_64-apple-darwin" to "e08cce7484b30f755b00b24d0629c2f87ab418cf1c24550e2466d2ac754dc97d",
        "aarch64-unknown-linux-musl" to "712d7b0c5413a5bc0315c939f29a9cc87dbee43fb9a119138fff1d6599a41f92",
        "x86_64-unknown-linux-musl" to "2e64c7654906f0df82218fcc73cbdded2e41f37961f5be3e876e708bdf42e367",
        "aarch64-unknown-linux-gnu" to "d51bfff8c4aacebb786f701918389b3b568129cb9c16aee12567ede6869a61ad",
        "x86_64-unknown-linux-gnu" to "36f1524f0cfaf33f5dfe68ac19220e1bd57ded1f0f9205931f6ee1de32f4a040",
        "x86_64-pc-windows-msvc" to "d059ae097c7d23ac627abb92a4496eb25aecc513f901a8e907662de98df0e875",
    )

    /**
     * Target triple for the operating system [osName] and the architecture [osArch], the values of the `os.name` and
     * `os.arch` system properties. Linux gets the static musl build, which runs with any C library; Windows on ARM gets
     * the x64 build, which it emulates.
     *
     * @throws UnsupportedOperationException for a platform without a release archive.
     */
    fun target(osName: String, osArch: String): String {
        val os = osName.lowercase()
        val arch = when (osArch.lowercase()) {
            "aarch64", "arm64" -> "aarch64"
            "x86_64", "amd64", "x64" -> "x86_64"
            else -> null
        }
        val target = when {
            arch == null -> null
            os.startsWith("mac") || os.startsWith("darwin") -> "$arch-apple-darwin"
            os.startsWith("linux") -> "$arch-unknown-linux-musl"
            os.startsWith("windows") -> "x86_64-pc-windows-msvc"
            else -> null
        }

        return target ?: throw UnsupportedOperationException(
            "rumdl has no release archive for $osName ($osArch): install rumdl and set " +
                "documentationSite.checks.markdownLint.executable to its path"
        )
    }
}
