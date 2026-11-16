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

package io.github.wolfandrei.repodocs.tasks

import io.github.wolfandrei.repodocs.lint.Rumdl
import java.io.File
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity

/**
 * The rumdl Markdown linter of a task: the configuration files it may read and the rules the plugin turns off. See
 * `DocumentationSiteExtension.Checks`. rumdl itself is part of the plugin.
 */
internal interface MarkdownLintSettings {
    /** `.rumdl.toml` files rumdl may find for the pages; missing ones are skipped. */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    val discoveredConfigFiles: ConfigurableFileCollection

    /** Rules turned off over the configuration: those the plugin checks itself. */
    @get:Input
    val disabledRules: ListProperty<String>
}

/** rumdl with these settings on the pages of [repositoryRoot]. */
internal fun MarkdownLintSettings.rumdl(repositoryRoot: File): Rumdl =
    Rumdl(repositoryRoot, disabledRules.get())
