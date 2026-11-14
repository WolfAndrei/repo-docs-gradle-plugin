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

package io.github.wolfandrei.docssite.tasks

import io.github.wolfandrei.docssite.lint.Rumdl
import io.github.wolfandrei.docssite.lint.RumdlAsset
import io.github.wolfandrei.docssite.lint.RumdlDistribution
import io.github.wolfandrei.docssite.lint.RumdlInstaller
import java.io.File
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity

/**
 * The rumdl Markdown linter of a task: which binary, which configuration, which rules are turned off. See
 * `DocumentationSiteExtension.MarkdownLint` for the meaning of the settings.
 */
internal interface MarkdownLintSettings {
    @get:Input
    val enabled: Property<Boolean>

    /** A local binary; without it the release archive is downloaded. */
    @get:Optional
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    val executable: RegularFileProperty

    @get:Input
    val version: Property<String>

    @get:Input
    val distributionUrl: Property<String>

    /** SHA-256 of the release archives by target; with the checksum the version identifies the binary. */
    @get:Input
    val checksums: MapProperty<String, String>

    @get:Optional
    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    val configFile: RegularFileProperty

    /** Configuration files rumdl finds in the repository root by itself; missing ones are skipped. */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    val discoveredConfigFiles: ConfigurableFileCollection

    /** Rules turned off in addition to the configuration: the settings and the rules the plugin checks itself. */
    @get:Input
    val disabledRules: ListProperty<String>

    /** Where the downloaded binaries are kept, under the Gradle user home. */
    @get:Internal
    val cacheDirectory: DirectoryProperty

    @get:Internal
    val offline: Property<Boolean>
}

/** The rumdl command line of these settings: the configured binary, or the release archive installed in the cache. */
internal fun MarkdownLintSettings.rumdl(repositoryRoot: File): Rumdl {
    val binary = executable.orNull?.asFile ?: run {
        val asset = RumdlAsset(
            version.get(),
            RumdlDistribution.target(System.getProperty("os.name"), System.getProperty("os.arch")),
        )
        RumdlInstaller(cacheDirectory.get().asFile, distributionUrl.get(), offline.get())
            .install(asset, checksums.get()[asset.target])
    }

    return Rumdl(binary, repositoryRoot, configFile.orNull?.asFile, disabledRules.get())
}
