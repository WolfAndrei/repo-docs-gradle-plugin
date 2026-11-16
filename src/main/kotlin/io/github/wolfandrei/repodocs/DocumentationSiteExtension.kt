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

package io.github.wolfandrei.repodocs

import javax.inject.Inject
import org.gradle.api.Action
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty

/**
 * Settings of the documentation site, the `docs { ... }` block of the root project. Paths are relative to
 * the root project directory.
 */
abstract class DocumentationSiteExtension @Inject constructor(objects: ObjectFactory) {
    /**
     * Root of the repository: the paths of the site and the links between files are counted from it; by default the
     * directory of the root project. A repository whose Gradle build lies in a subdirectory, with the documentation
     * above it, sets it, such as `repositoryRoot.set(layout.projectDirectory.dir(".."))`.
     */
    abstract val repositoryRoot: DirectoryProperty

    /** Title in the header of every page; by default the name of the root project. */
    abstract val title: Property<String>

    /** The home page of the site; by default `README.md` of [repositoryRoot]. A missing file is skipped. */
    abstract val readme: RegularFileProperty

    /**
     * Directory of the documentation; by default the first of `documentation`, `docs` and `Documentation` that exists in
     * [repositoryRoot], its name compared with the case, otherwise `documentation`.
     */
    abstract val documentationDirectory: DirectoryProperty

    /** Table of contents, the root of the sidebar; by default `index.md` of [documentationDirectory]. */
    abstract val index: RegularFileProperty

    /** Markdown pages outside [documentationDirectory], such as README files of modules. */
    abstract val additionalPages: ConfigurableFileCollection

    /** Directory of the site; by default `build/repo-docs`. */
    abstract val outputDirectory: DirectoryProperty

    /** Language of the site interface: `en` (default) or `ru`. The pages themselves are not translated. */
    abstract val language: Property<String>

    /**
     * Rules of the heading ids: [AnchorStyle.GITHUB] (default) or [AnchorStyle.GITLAB]. They differ only for a heading
     * with a run of hyphens or spaces, such as `A - B`, and for a heading of digits only. A repository hosted on
     * GitLab sets `anchorStyle.set(AnchorStyle.GITLAB)`.
     */
    abstract val anchorStyle: Property<AnchorStyle>

    val plantUml: PlantUml = objects.newInstance(PlantUml::class.java)

    val navigation: Navigation = objects.newInstance(Navigation::class.java)

    val checks: Checks = objects.newInstance(Checks::class.java)

    fun plantUml(action: Action<PlantUml>) = action.execute(plantUml)

    fun navigation(action: Action<Navigation>) = action.execute(navigation)

    fun checks(action: Action<Checks>) = action.execute(checks)

    /**
     * Diagrams from the `.puml` files of the documentation, drawn at build time by the EPL build of PlantUML that is
     * part of the plugin; the site never shows their committed SVG.
     */
    interface PlantUml {
        /**
         * Layout engine of the diagrams; by default [DiagramLayout.SMETANA], built into PlantUML,
         * so that a diagram is the same on every machine. Graphviz is found by `GRAPHVIZ_DOT`, otherwise on `PATH`.
         */
        val layout: Property<DiagramLayout>
    }

    /** The sidebar: how the tables of contents are read. */
    interface Navigation {
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

    /**
     * Settings of `docsCheck`. It checks the links, the anchors and the diagrams, a committed SVG next to its `.puml`
     * included, and the style of the pages with the [rumdl](https://github.com/rvben/rumdl) Markdown linter, part of
     * the plugin; `docsFormat` applies the fixes of rumdl. The rules of rumdl come only from `.rumdl.toml`: the one of
     * the nearest directory of a page, with its `extends`, so that the rumdl extensions of the editors see the same
     * rules. The plugin checks the relative links and the anchors itself, so the rules MD051 and MD057 are always off.
     */
    abstract class Checks {
        /** Whether `check` depends on `docsCheck`; by default `false`. */
        abstract val runWithCheck: Property<Boolean>

        /** Directory names that are not part of the repository for the links; by default build and cache dirs. */
        abstract val excludedDirectories: SetProperty<String>
    }

    internal companion object {
        const val NAME = "docs"
    }
}
