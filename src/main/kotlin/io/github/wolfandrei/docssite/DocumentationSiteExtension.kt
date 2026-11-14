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

package io.github.wolfandrei.docssite

import javax.inject.Inject
import org.gradle.api.Action
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty

/**
 * Settings of the documentation site, the `documentationSite { ... }` block of the root project. Paths are relative to
 * the root project directory.
 */
abstract class DocumentationSiteExtension @Inject constructor(objects: ObjectFactory) {
    /** Title in the header of every page; by default the name of the root project. */
    abstract val title: Property<String>

    /** The home page of the site; by default `README.md`. A missing file is skipped. */
    abstract val readme: RegularFileProperty

    /** Directory of the documentation; by default `documentation`. */
    abstract val documentationDirectory: DirectoryProperty

    /** Table of contents, the root of the sidebar; by default `documentation/index.md`. */
    abstract val index: RegularFileProperty

    /** Markdown pages outside [documentationDirectory], such as README files of modules. */
    abstract val additionalPages: ConfigurableFileCollection

    /** Directory of the site; by default `build/docs-site`. */
    abstract val outputDirectory: DirectoryProperty

    /**
     * Prefix of the address of a repository file that is not part of the site, such as
     * `https://gitlab.example.com/group/repo/-/blob/main/`. Without it such links stay relative, and only
     * `documentationServe` opens them, from the repository.
     */
    abstract val sourceBrowseUrl: Property<String>

    /** Language of the site interface: `en` (default) or `ru`. The pages themselves are not translated. */
    abstract val language: Property<String>

    /**
     * Titles of the sidebar groups by the heading of the table of contents, case-insensitive: `how-to`, `reference`,
     * `explanation`, `tutorials`, `adr`, `diagrams`, `interactive`. The [language] gives the defaults, such as
     * `Reference`; an entry here replaces one of them or names another heading.
     */
    abstract val navigationTitles: MapProperty<String, String>

    /**
     * Rules of the heading ids: `gitlab` (default) or `github`. They differ only for a heading with a run of
     * hyphens or spaces, such as `A - B`, and for a heading of digits only.
     */
    abstract val anchorStyle: Property<String>

    val plantUml: PlantUml = objects.newInstance(PlantUml::class.java)

    val apiReference: ApiReference = objects.newInstance(ApiReference::class.java)

    val checks: Checks = objects.newInstance(Checks::class.java)

    val serve: Serve = objects.newInstance(Serve::class.java)

    fun plantUml(action: Action<PlantUml>) = action.execute(plantUml)

    fun apiReference(action: Action<ApiReference>) = action.execute(apiReference)

    fun checks(action: Action<Checks>) = action.execute(checks)

    fun serve(action: Action<Serve>) = action.execute(serve)

    /** Diagrams from the `.puml` files of the documentation; the site never shows their committed SVG. */
    interface PlantUml {
        /**
         * Version of PlantUML: of `net.sourceforge.plantuml:plantuml-mit` for the build and of the npm package
         * `@plantuml/core` for the browser; by default [DocumentationSitePlugin.PLANTUML_VERSION].
         */
        val version: Property<String>

        /**
         * Layout engine of the build: `auto` (default) - Graphviz `dot` if it is found, otherwise Smetana; `dot`;
         * `smetana` - built into PlantUML.
         */
        val layout: Property<String>

        /** Path of Graphviz `dot`; without it `GRAPHVIZ_DOT` and `PATH` are searched. */
        val dotExecutable: Property<String>

        /**
         * Where the diagrams are drawn: `auto` (default) - in the browser, except the diagrams with sprites of the
         * standard library (C4 icons), which are drawn at build time; `browser`; `build`.
         */
        val render: Property<String>
    }

    /**
     * API reference: the Dokka HTML of the modules that apply `org.jetbrains.dokka` themselves, joined under
     * `api/<module>/` of the site. The plugin does not apply Dokka.
     */
    interface ApiReference {
        /** Whether the site has the API reference; by default `true`. */
        val enabled: Property<Boolean>

        /** Modules by Gradle path, such as `:lib`; if not empty, only they are documented. */
        val modules: SetProperty<String>

        /** Modules that are not documented, by Gradle path; a path also excludes its nested modules. */
        val excludedModules: SetProperty<String>
    }

    /** Settings of `documentationCheck`. */
    interface Checks {
        /** Whether `check` depends on `documentationCheck`; by default `false`. */
        val runWithCheck: Property<Boolean>

        /** Longest Markdown line outside code blocks; by default `120`, `0` turns the check off. */
        val maxLineLength: Property<Int>

        /** Whether the rows of tables are checked for [maxLineLength]; by default `false`. */
        val lineLengthInTables: Property<Boolean>

        /** Characters a Markdown page must not contain outside code, such as the long dash `—`; by default none. */
        val forbiddenCharacters: ListProperty<String>

        /** Whether a committed SVG next to its `.puml` must match the source; by default `true`. */
        val committedDiagrams: Property<Boolean>

        /** Directory names that are not part of the repository for the links; by default build and cache dirs. */
        val excludedDirectories: SetProperty<String>
    }

    /** Settings of `documentationServe`. */
    interface Serve {
        /** Port on `localhost`; by default `8000`, `-PdocumentationSite.port=<port>` replaces it. */
        val port: Property<Int>
    }

    internal companion object {
        const val NAME = "documentationSite"
    }
}
