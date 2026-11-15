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
 * Settings of the documentation site, the `docs { ... }` block of the root project. Paths are relative to
 * the root project directory.
 */
abstract class DocumentationSiteExtension @Inject constructor(objects: ObjectFactory) {
    /** Title in the header of every page; by default the name of the root project. */
    abstract val title: Property<String>

    /** The home page of the site; by default `README.md`. A missing file is skipped. */
    abstract val readme: RegularFileProperty

    /**
     * Directory of the documentation; by default `documentation` if it exists, otherwise `docs` if it exists,
     * otherwise `documentation`.
     */
    abstract val documentationDirectory: DirectoryProperty

    /** Table of contents, the root of the sidebar; by default `index.md` of [documentationDirectory]. */
    abstract val index: RegularFileProperty

    /** Markdown pages outside [documentationDirectory], such as README files of modules. */
    abstract val additionalPages: ConfigurableFileCollection

    /** Directory of the site; by default `build/docs-site`. */
    abstract val outputDirectory: DirectoryProperty

    /**
     * Prefix of the address of a repository file that is not part of the site, such as
     * `https://gitlab.example.com/group/repo/-/blob/main/`. Without it such links stay relative, and only
     * `docs` opens them, from the repository.
     */
    abstract val sourceBrowseUrl: Property<String>

    /** Language of the site interface: `en` (default) or `ru`. The pages themselves are not translated. */
    abstract val language: Property<String>

    /**
     * Rules of the heading ids: `github` (default) or `gitlab`. They differ only for a heading with a run of
     * hyphens or spaces, such as `A - B`, and for a heading of digits only. A repository hosted on GitLab sets
     * `anchorStyle.set("gitlab")`.
     */
    abstract val anchorStyle: Property<String>

    val plantUml: PlantUml = objects.newInstance(PlantUml::class.java)

    val apiReference: ApiReference = objects.newInstance(ApiReference::class.java)

    val navigation: Navigation = objects.newInstance(Navigation::class.java)

    val checks: Checks = objects.newInstance(Checks::class.java)

    val serve: Serve = objects.newInstance(Serve::class.java)

    fun plantUml(action: Action<PlantUml>) = action.execute(plantUml)

    fun apiReference(action: Action<ApiReference>) = action.execute(apiReference)

    fun navigation(action: Action<Navigation>) = action.execute(navigation)

    fun checks(action: Action<Checks>) = action.execute(checks)

    fun serve(action: Action<Serve>) = action.execute(serve)

    /** Diagrams from the `.puml` files of the documentation; the site never shows their committed SVG. */
    interface PlantUml {
        /**
         * Version of PlantUML: of `net.sourceforge.plantuml:plantuml-epl` for the build and of the npm package
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
         * Where the diagrams are drawn: `build` (default) - at build time; `auto` - in the browser, except the
         * diagrams with sprites of the standard library (C4 icons) or the ELK layout, which are drawn at build time;
         * `browser`.
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

        /**
         * Directories of the libraries under the documentation directory by Dokka module name, such as
         * `"lib" to "greetings"`; by default a module belongs to the directory of its own name. The API of a module
         * goes under its library in the sidebar, and the inline code of the pages of the library links to it first.
         */
        val libraries: MapProperty<String, String>

        /**
         * Headings of the group or the entry of a library that gets its API, case-insensitive prefixes; by default
         * `Reference` and `Справочник`, the title of the `reference` group in the language counts too.
         */
        val referenceHeadings: ListProperty<String>
    }

    /** The sidebar: how the tables of contents are read. */
    interface Navigation {
        /**
         * Titles of the sidebar groups by the heading of the table of contents, case-insensitive: `how-to`,
         * `reference`, `explanation`, `tutorials`, `adr`, `diagrams`, `interactive`. The language gives the
         * defaults, such as `Reference`; an entry here replaces one of them or names another heading.
         */
        val groupTitles: MapProperty<String, String>

        /**
         * Where the titles of the entries come from: `heading` (default) - the heading of the page, the link text for a
         * page without headings; `link` - the link text, the heading for a link such as «here».
         */
        val titleSource: Property<String>

        /**
         * Globs of the pages that are tables of contents and are expanded in place; a pattern without `/` matches the
         * file name, another one the path from the repository root. By default `index.md`, `README.md`,
         * `*-index.md`, `*_index.md`.
         */
        val hubs: ListProperty<String>

        /**
         * Whether a page that links to at least 2 pages not listed yet and has at most 60 words of its own text is a
         * table of contents too; by default `true`.
         */
        val detectHubs: Property<Boolean>

        /**
         * Headings of a table of contents inside a page, such as `## Contents`: they open no sidebar group and give
         * no title. By default `Contents`, `Table of contents`, `Содержание`, `Оглавление`.
         */
        val contentsHeadings: ListProperty<String>
    }

    /** Settings of `docsCheck`. */
    abstract class Checks @Inject constructor(objects: ObjectFactory) {
        /** Whether `check` depends on `docsCheck`; by default `false`. */
        abstract val runWithCheck: Property<Boolean>

        /**
         * Longest Markdown line outside code blocks, `0` turns the check off. By default the `max_line_length` of
         * `.editorconfig` for the Markdown files of the documentation (`off` is `0`), and `0` without it. While the
         * limit is not `0`, the rule MD013 of the Markdown linter is off.
         */
        abstract val maxLineLength: Property<Int>

        /** Whether the rows of tables are checked for [maxLineLength]; by default `false`. */
        abstract val lineLengthInTables: Property<Boolean>

        /** Characters a Markdown page must not contain outside code, such as the long dash `—`; by default none. */
        abstract val forbiddenCharacters: ListProperty<String>

        /** Whether a committed SVG next to its `.puml` must match the source; by default `false`. */
        abstract val committedDiagrams: Property<Boolean>

        /** Directory names that are not part of the repository for the links; by default build and cache dirs. */
        abstract val excludedDirectories: SetProperty<String>

        /** The rumdl Markdown linter of `docsCheck` and `docsFormat`. */
        val markdownLint: MarkdownLint = objects.newInstance(MarkdownLint::class.java)

        fun markdownLint(action: Action<MarkdownLint>) = action.execute(markdownLint)
    }

    /**
     * The [rumdl](https://github.com/rvben/rumdl) Markdown linter: `docsCheck` reports its findings,
     * `docsFormat` applies its fixes. The rules are configured in the files rumdl finds in the repository
     * root itself, `.rumdl.toml` or `.markdownlint.json` and others, so that the editors see the same rules.
     *
     * The plugin checks the relative links and the anchors itself, so the rules MD051 and MD057 are always off, and
     * MD013 is off while the limit of [Checks.maxLineLength] is not `0`.
     */
    abstract class MarkdownLint {
        /** Whether the pages are checked with rumdl; by default `true`. */
        abstract val enabled: Property<Boolean>

        /** A local rumdl binary; without it the release archive of [version] is downloaded and cached. */
        abstract val executable: RegularFileProperty

        /** Version of rumdl to download; by default [DocumentationSitePlugin.RUMDL_VERSION]. */
        abstract val version: Property<String>

        /**
         * Address of the release archive, by default the GitHub releases of rumdl:
         * `https://github.com/rvben/rumdl/releases/download/v{version}/{asset}`. `{version}`, `{target}` (such as
         * `aarch64-apple-darwin`) and `{asset}` (the archive name, such as `rumdl-v0.3.4-aarch64-apple-darwin.tar.gz`)
         * are replaced; without `{asset}` the address is a directory of the archives. A mirror with authentication
         * takes the credentials in the address, `https://user:password@host/...`.
         */
        abstract val distributionUrl: Property<String>

        /**
         * SHA-256 of the release archives by target, such as `"x86_64-unknown-linux-musl" to "<sha256>"`; the plugin
         * knows them for its own [version]. A downloaded archive with another checksum fails the build.
         */
        abstract val checksums: MapProperty<String, String>

        /**
         * Configuration file of rumdl, `.rumdl.toml` or a markdownlint file such as `.markdownlint.json`; without it
         * rumdl finds its configuration itself.
         */
        abstract val configFile: RegularFileProperty

        /** Rules turned off in addition to the configuration, such as `MD041`; by default none. */
        abstract val disabledRules: ListProperty<String>

        /** Turns off [rules] in addition to the configuration. */
        fun disable(vararg rules: String) = disabledRules.addAll(*rules)
    }

    /** Settings of `docs`. */
    interface Serve {
        /** Port on `localhost`; by default `8000`, `-Pdocs.port=<port>` replaces it. */
        val port: Property<Int>
    }

    internal companion object {
        const val NAME = "docs"
    }
}
