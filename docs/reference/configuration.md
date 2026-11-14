# Configuration and tasks

The `documentationSite { ... }` block of the root project, the Gradle tasks, the command line properties, the files
and the rules of `documentationCheck`. How to use them is in [How to set up, build, serve and check the
site](../how-to/usage.md).

## Contents

- [The documentationSite block](#the-documentationsite-block)
- [plantUml](#plantuml)
- [apiReference](#apireference)
- [navigation](#navigation)
- [Layout conventions](#layout-conventions)
- [checks](#checks)
- [serve](#serve)
- [Tasks](#tasks)
- [Command line properties](#command-line-properties)
- [Files](#files)
- [Check rules](#check-rules)
- [Dependencies](#dependencies)

## The documentationSite block

Paths are relative to the directory of the root project.

| Property                 | Type                         | Default                  | Purpose                                                     |
|--------------------------|------------------------------|--------------------------|-------------------------------------------------------------|
| `title`                  | `Property<String>`           | name of the root project | Title in the header of every page                           |
| `readme`                 | `RegularFileProperty`        | `README.md`              | Home page; skipped if the file is missing                   |
| `documentationDirectory` | `DirectoryProperty`          | `documentation`          | Directory of the documentation                              |
| `index`                  | `RegularFileProperty`        | `documentation/index.md` | Table of contents, the root of the sidebar                  |
| `additionalPages`        | `ConfigurableFileCollection` | empty                    | Markdown pages outside the documentation, such as READMEs   |
| `outputDirectory`        | `DirectoryProperty`          | `build/docs-site`        | Directory of the site                                       |
| `sourceBrowseUrl`        | `Property<String>`           | not set                  | Address prefix of the repository files that are not on site |
| `language`               | `Property<String>`           | `en`                     | Language of the interface: `en` or `ru`                     |
| `anchorStyle`            | `Property<String>`           | `gitlab`                 | Rules of the heading ids: `gitlab` or `github`              |

`sourceBrowseUrl` has the form `https://gitlab.example.com/group/repo/-/blob/main/`: a link to
`lib/build.gradle.kts` becomes `<sourceBrowseUrl>lib/build.gradle.kts`. Without it such a link stays relative and
only `documentationServe` opens it, from the repository.

`language` sets every string of the interface: the header, the search, the sidebar groups, the sections and kinds of
the API pages, the diagram buttons. The pages themselves are not translated.

`anchorStyle` selects the rules of [Heading anchors](../explanation/architecture.md#heading-anchors).

## plantUml

| Property        | Type               | Default    | Purpose                                                        |
|-----------------|--------------------|------------|----------------------------------------------------------------|
| `version`       | `Property<String>` | `1.2026.9` | Version of PlantUML for the build and for the browser          |
| `layout`        | `Property<String>` | `auto`     | Layout engine of the build: `auto`, `dot`, `smetana`           |
| `dotExecutable` | `Property<String>` | not set    | Path of Graphviz `dot`; without it `GRAPHVIZ_DOT`, then `PATH` |
| `render`        | `Property<String>` | `auto`     | Where the diagrams are drawn: `auto`, `browser`, `build`       |

`version` is the version of two artifacts: `net.sourceforge.plantuml:plantuml-mit` for the build and the npm package
`@plantuml/core` for the browser. `@plantuml/core` is MIT-licensed since `1.2026.6`, earlier versions are GPL.

`layout`:

- `auto` - Graphviz `dot` if it is found, otherwise the built-in Smetana engine;
- `dot` - Graphviz only; without it the task fails with `plantUml.layout is dot, but Graphviz dot is not found`;
- `smetana` - Smetana only.

`render`:

- `auto` - in the browser, except the diagrams with sprites of the standard library (the C4 icons of `Person`,
  `System`), which the build draws;
- `browser` - every diagram in the browser; the sprites of the standard library are left out;
- `build` - every diagram at build time; the browser engine is not copied to the site.

Without the npm package every diagram is drawn at build time and `documentationSite` warns
`The browser build of PlantUML is not available`. Details are in
[PlantUML diagrams](../explanation/architecture.md#plantuml-diagrams).

## apiReference

| Property            | Type                          | Default                   | Purpose                                                |
|---------------------|-------------------------------|---------------------------|--------------------------------------------------------|
| `enabled`           | `Property<Boolean>`           | `true`                    | Whether the site has the API reference                 |
| `modules`           | `SetProperty<String>`         | empty                     | If not empty, only these modules are documented        |
| `excludedModules`   | `SetProperty<String>`         | empty                     | Excluded modules; a path also excludes its nested ones |
| `libraries`         | `MapProperty<String, String>` | empty                     | Library directory by Dokka module name                 |
| `referenceHeadings` | `ListProperty<String>`        | `Reference`, `Справочник` | Headings of the group that gets the API                |

Only the modules that apply `org.jetbrains.dokka` themselves are in the reference; the root project only if it
applies Dokka too. `modules` and `excludedModules` take Gradle paths such as `:lib`.

`libraries` maps a Dokka module to its directory under the documentation directory when the names differ:
`libraries.put("lib", "greetings")` puts the API of `lib` under `documentation/greetings/` and links the inline code
of its pages to `lib` first. `referenceHeadings` are case-insensitive prefixes; the title of the `reference` group
in the language counts too.

## navigation

| Property           | Type                          | Default                                                     | Purpose                                        |
|--------------------|-------------------------------|-------------------------------------------------------------|------------------------------------------------|
| `groupTitles`      | `MapProperty<String, String>` | empty                                                       | Titles of the sidebar groups by heading        |
| `titleSource`      | `Property<String>`            | `heading`                                                   | Title of an entry: `heading` or `link`         |
| `hubs`             | `ListProperty<String>`        | `index.md`, `README.md`, `*-index.md`, `*_index.md`         | Globs of the tables of contents                |
| `detectHubs`       | `Property<Boolean>`           | `true`                                                      | Whether a page of links is a table of contents |
| `contentsHeadings` | `ListProperty<String>`        | `Contents`, `Table of contents`, `Содержание`, `Оглавление` | Headings of an in-page contents list           |

`groupTitles` maps a heading of a table of contents, compared case-insensitively, to the title of its sidebar group.
The defaults of the language:

| Heading                 | `en`                   | `ru`                   |
|-------------------------|------------------------|------------------------|
| `how-to`, `how to`      | How-to guides          | Инструкции             |
| `reference`             | Reference              | Справочник             |
| `explanation`           | Explanation            | Устройство             |
| `tutorials`, `tutorial` | Tutorials              | Обучение               |
| `adr`                   | Architecture decisions | Архитектурные решения  |
| `diagrams`              | Diagrams               | Схемы                  |
| `interactive`           | Interactive pages      | Интерактивные страницы |

`interactive` is also the title of the group that lists the interactive pages of a library that no table of
contents mentions.

```kotlin
documentationSite {
    navigation {
        groupTitles.put("explanation", "Design")
        hubs.add("docs/guide.md")
        titleSource.set("link")
    }
}
```

`titleSource`:

- `heading` - the first-level heading of the page, otherwise its first heading that is not in `contentsHeadings`,
  otherwise the link text, otherwise the file name without `.md`;
- `link` - the link text, otherwise as `heading`.

A link text that says nothing about the page, such as `here`, `click here`, `link`, `здесь`, `тут`, `подробнее`, is
skipped in both cases.

`hubs`: a pattern without `/` matches the file name, another one the path from the repository root. With
`detectHubs` a page is a table of contents also when it links to at least 2 Markdown pages that the sidebar does not
list yet and has at most 60 words of its own text: paragraphs and tables count; lists, headings, quotes, code and
paragraphs of links or pictures only do not. The rules of the sidebar are in
[Sidebar](../explanation/architecture.md#sidebar).

`contentsHeadings`: a heading such as `## Contents` before an in-page list of anchors opens no sidebar group and is
never the title of the page.

## Layout conventions

What the plugin expects from the layout of the documentation, and how to change it.

| Convention                                                                                       | Override                                   |
|--------------------------------------------------------------------------------------------------|--------------------------------------------|
| The documentation is in `documentation/`, its table of contents is `index.md`                    | `documentationDirectory`, `index`          |
| README is the home page                                                                          | `readme`                                   |
| A table of contents is `index.md`, `README.md`, `*-index.md` or a page of links                  | `navigation.hubs`, `navigation.detectHubs` |
| An entry shows the heading of its page                                                           | `navigation.titleSource`                   |
| `## Contents` and the like is an in-page contents list                                           | `navigation.contentsHeadings`              |
| `## How-to`, `## Reference` and the like are groups with known titles                            | `navigation.groupTitles`                   |
| A library is a directory under the documentation directory named as its Dokka module             | `apiReference.libraries`                   |
| The API of a library goes under its `Reference` group or entry                                   | `apiReference.referenceHeadings`           |
| The page of a library is a table of contents directly in its directory, shown with a large title | `navigation.hubs`                          |
| Any `.html` of the documentation is an interactive page                                          | none                                       |
| A `.puml` next to a `.svg` of the same name is the source of that diagram                        | none                                       |
| The quote right after the title is the abstract                                                  | none                                       |
| A first paragraph that is a single link is the back link                                         | none                                       |
| A quote that starts with `⚠️` or a bold Warning, Important, Внимание or Важно is a warning       | none                                       |
| A quote that starts with Note or Примечание is a note                                            | none                                       |
| Heading ids follow GitLab                                                                        | `anchorStyle`                              |

The directory names of the Diátaxis layout (`how-to/`, `reference/`, `explanation/`, `diagrams/`, `interactive/`)
mean nothing to the plugin: the headings of the tables of contents make the groups, and every diagram and
interactive page is found by its file type wherever it lies.

## checks

| Property              | Type                   | Default                          | Purpose                                         |
|-----------------------|------------------------|----------------------------------|-------------------------------------------------|
| `runWithCheck`        | `Property<Boolean>`    | `false`                          | Whether `check` depends on `documentationCheck` |
| `maxLineLength`       | `Property<Int>`        | `120`                            | Longest Markdown line; `0` turns the check off  |
| `lineLengthInTables`  | `Property<Boolean>`    | `false`                          | Whether the rows of tables are checked          |
| `forbiddenCharacters` | `ListProperty<String>` | empty                            | Characters a page must not contain, such as `—` |
| `committedDiagrams`   | `Property<Boolean>`    | `true`                           | Whether a committed SVG must match its `.puml`  |
| `excludedDirectories` | `SetProperty<String>`  | `.git`, `.gradle`, `build`, more | Directories the links do not see as files       |

`runWithCheck` works only if the root project has a `check` task, for example from the `base` plugin.

The default `excludedDirectories`: `.git`, `.gradle`, `.idea`, `.kotlin`, `.cxx`, `build`, `node_modules`, `Pods`,
`DerivedData`. A directory is excluded by its name at any depth.

## serve

| Property | Type            | Default | Purpose                           |
|----------|-----------------|---------|-----------------------------------|
| `port`   | `Property<Int>` | `8000`  | Port of the server on `localhost` |

## Tasks

| Task                          | What it does                                                           |
|-------------------------------|------------------------------------------------------------------------|
| `documentationSite`           | Builds the site in `outputDirectory`                                   |
| `documentationServe`          | Builds the site and serves it on `localhost:<port>` until Ctrl+C       |
| `documentationCheck`          | Checks the documentation by the [rules](#check-rules), fails on errors |
| `documentationRenderDiagrams` | Draws the `.puml` files to SVG and writes preprocessed sources         |
| `documentationApiReference`   | Joins the Dokka HTML of the modules into one reference                 |

All tasks but `documentationServe` are cacheable and compatible with the configuration cache and with isolated
projects. `documentationServe` does not rebuild the site: `./gradlew documentationSite --continuous` in a second
terminal does. `documentationRenderDiagrams` without `.puml` files and `documentationApiReference` without modules
with Dokka are skipped (`NO-SOURCE`) and download nothing. The tasks are in the `documentation` group of
`./gradlew tasks`.

## Command line properties

| Parameter                         | Effect                                                |
|-----------------------------------|-------------------------------------------------------|
| `-PdocumentationSite.port=<port>` | Replaces the default port `8000`                      |
| `--port=<port>`                   | Option of `documentationServe`, replaces `serve.port` |

## Files

| Path                                   | Content                                                                        |
|----------------------------------------|--------------------------------------------------------------------------------|
| `build/docs-site/`                     | The site; its directories repeat those of the repository                       |
| `build/docs-site/index.html`           | Home page: a copy of `README.html`                                             |
| `build/docs-site/api/`                 | API pages, `api/<module>/<package>/...`                                        |
| `build/docs-site/**/<name>.frame.html` | Copy of the interactive page `<name>.html` shown in its frame                  |
| `build/docs-site/_assets/`             | Styles, script, interface strings `ui-text.js`, search index `.json` and `.js` |
| `build/docs-site/_assets/build-stamp`  | Hash of the content of the site; the pages poll it to reload                   |
| `build/docs-site/_assets/plantuml/`    | Browser build of PlantUML: `plantuml.js`, `viz-global.js` and parts            |
| `build/docs-site-diagrams/svg/`        | SVG drawn from the `.puml` files, by the paths of the sources                  |
| `build/docs-site-diagrams/source/`     | Preprocessed sources `.preproc`: every `!include` expanded                     |
| `build/docs-site-diagrams-report.json` | PlantUML errors and the layout engine used                                     |
| `build/docs-site-api/`                 | The joined Dokka output before it becomes API pages                            |
| `build/docs-site-check/report.txt`     | All problems of the last `documentationCheck`                                  |

`_assets/plantuml/` takes 6.7 MB with version `1.2026.9` and is copied only if at least one diagram is drawn in the
browser.

## Check rules

| Rule                | Error                                                                                 |
|---------------------|---------------------------------------------------------------------------------------|
| Link to a file      | The target of a relative link or image, `href` and `src` of HTML included, is missing |
| Table of contents   | An `index.md` links to a missing page                                                 |
| Anchor              | `#anchor` is not a heading of the target page by the rules of `anchorStyle`           |
| PlantUML error      | PlantUML cannot draw a `.puml`; the line of the source is given                       |
| Stale diagram       | A committed SVG next to its `.puml` shows other texts, elements, links or colours     |
| Line length         | A line is longer than `maxLineLength`                                                 |
| Forbidden character | A line contains a character of `forbiddenCharacters`                                  |

Line length and characters are not checked in code blocks, characters not in inline code either. Line length is not
checked for table rows (unless `lineLengthInTables`) and for lines with an address (`://`); other lines are measured
without the link targets `](...)`. For an anchor with a wrong case or hyphens the check suggests an existing one:
`did you mean #storage-format?`. A page that no other page links to, except README and `index.md`, is reported as a
warning and does not fail the build.

## Dependencies

| Dependency                                         | Version    | Where                                     |
|----------------------------------------------------|------------|-------------------------------------------|
| `org.commonmark:commonmark` and GFM extensions     | `0.31.0`   | The plugin: Markdown                      |
| `org.jsoup:jsoup`                                  | `1.16.1`   | The plugin: Dokka and interactive pages   |
| `org.jetbrains.kotlinx:kotlinx-serialization-json` | `1.7.1`    | The plugin: indexes and reports           |
| `net.sourceforge.plantuml:plantuml-mit`            | `1.2026.9` | Configuration `documentationPlantUml`     |
| npm `@plantuml/core` as `plantuml-npm:core@tgz`    | `1.2026.9` | Configuration `documentationPlantUmlJs`   |
| `org.jetbrains.dokka:dokka-cli`                    | `2.1.0`    | Configuration `documentationDokkaCli`     |
| Dokka plugins for the aggregation                  | `2.1.0`    | Configuration `documentationDokkaPlugins` |

The Dokka plugins are `dokka-base`, `templating-plugin`, `all-modules-page-plugin` and `analysis-kotlin-symbols`;
the modules apply Dokka of the same version `2.1.0`. The licenses of the components are in
[NOTICE](../../NOTICE).
