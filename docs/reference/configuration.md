# Configuration and tasks

The `docs { ... }` block of the root project, the Gradle tasks, the command line properties, the files
and the rules of `docsCheck`. How to use them is in [How to set up, build, serve and check the
site](../how-to/usage.md).

## Contents

- [The docs block](#the-docs-block)
- [plantUml](#plantuml)
- [apiReference](#apireference)
- [navigation](#navigation)
- [Layout conventions](#layout-conventions)
- [checks](#checks)
- [checks.markdownLint](#checksmarkdownlint)
- [serve](#serve)
- [Tasks](#tasks)
- [Command line properties](#command-line-properties)
- [Files](#files)
- [Check rules](#check-rules)
- [Dependencies](#dependencies)

## The docs block

Paths are relative to the directory of the root project.

| Property                 | Type                         | Default                             | Purpose                                                     |
| ------------------------ | ---------------------------- | ----------------------------------- | ----------------------------------------------------------- |
| `title`                  | `Property<String>`           | name of the root project            | Title in the header of every page                           |
| `readme`                 | `RegularFileProperty`        | `README.md`                         | Home page; skipped if the file is missing                   |
| `documentationDirectory` | `DirectoryProperty`          | `documentation` or `docs`           | Directory of the documentation                              |
| `index`                  | `RegularFileProperty`        | `<documentationDirectory>/index.md` | Table of contents, the root of the sidebar                  |
| `additionalPages`        | `ConfigurableFileCollection` | empty                               | Markdown pages outside the documentation, such as READMEs   |
| `outputDirectory`        | `DirectoryProperty`          | `build/docs-site`                   | Directory of the site                                       |
| `sourceBrowseUrl`        | `Property<String>`           | not set                             | Address prefix of the repository files that are not on site |
| `language`               | `Property<String>`           | `en`                                | Language of the interface: `en` or `ru`                     |
| `anchorStyle`            | `Property<String>`           | `github`                            | Rules of the heading ids: `github` or `gitlab`              |

`documentationDirectory` is by default `documentation` if that directory exists, otherwise `docs` if it exists,
otherwise `documentation`. Creating or removing one of them reconfigures the build, the configuration cache
included.

`sourceBrowseUrl` has the form `https://gitlab.example.com/group/repo/-/blob/main/`: a link to
`lib/build.gradle.kts` becomes `<sourceBrowseUrl>lib/build.gradle.kts`. Without it such a link stays relative and
only `docs` opens it, from the repository.

`language` sets every string of the interface: the header, the search, the sidebar groups, the sections and kinds of
the API pages, the diagram buttons. The pages themselves are not translated.

`anchorStyle` selects the rules of [Heading anchors](../explanation/architecture.md#heading-anchors). The default
follows GitHub; a repository hosted on GitLab sets the GitLab rules, so that its `#anchor` links are checked as
GitLab resolves them:

```kotlin
docs {
    anchorStyle.set("gitlab")
}
```

## plantUml

| Property        | Type               | Default    | Purpose                                                        |
| --------------- | ------------------ | ---------- | -------------------------------------------------------------- |
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

Without the npm package every diagram is drawn at build time and `docsBuild` warns
`The browser build of PlantUML is not available`. Details are in
[PlantUML diagrams](../explanation/architecture.md#plantuml-diagrams).

## apiReference

| Property            | Type                          | Default                   | Purpose                                                |
| ------------------- | ----------------------------- | ------------------------- | ------------------------------------------------------ |
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
| ------------------ | ----------------------------- | ----------------------------------------------------------- | ---------------------------------------------- |
| `groupTitles`      | `MapProperty<String, String>` | empty                                                       | Titles of the sidebar groups by heading        |
| `titleSource`      | `Property<String>`            | `heading`                                                   | Title of an entry: `heading` or `link`         |
| `hubs`             | `ListProperty<String>`        | `index.md`, `README.md`, `*-index.md`, `*_index.md`         | Globs of the tables of contents                |
| `detectHubs`       | `Property<Boolean>`           | `true`                                                      | Whether a page of links is a table of contents |
| `contentsHeadings` | `ListProperty<String>`        | `Contents`, `Table of contents`, `Содержание`, `Оглавление` | Headings of an in-page contents list           |

`groupTitles` maps a heading of a table of contents, compared case-insensitively, to the title of its sidebar group.
The defaults of the language:

| Heading                 | `en`                   | `ru`                   |
| ----------------------- | ---------------------- | ---------------------- |
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
docs {
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
| ------------------------------------------------------------------------------------------------ | ------------------------------------------ |
| The documentation is in `documentation/` or `docs/`, its table of contents is `index.md`         | `documentationDirectory`, `index`          |
| README is the home page                                                                          | `readme`                                   |
| A table of contents is `index.md`, `README.md`, `*-index.md` or a page of links                  | `navigation.hubs`, `navigation.detectHubs` |
| An entry shows the heading of its page                                                           | `navigation.titleSource`                   |
| `## Contents` and the like is an in-page contents list                                           | `navigation.contentsHeadings`              |
| `## How-to`, `## Reference` and the like are groups with known titles                            | `navigation.groupTitles`                   |
| A library is a directory under the documentation directory named as its Dokka module             | `apiReference.libraries`                   |
| The API of a library goes under its `Reference` group or entry; the group may have no pages      | `apiReference.referenceHeadings`           |
| The page of a library is a table of contents directly in its directory, shown with a large title | `navigation.hubs`                          |
| Any `.html` of the documentation is an interactive page                                          | none                                       |
| A `.puml` next to a `.svg` of the same name is the source of that diagram                        | none                                       |
| The quote right after the title is the abstract                                                  | none                                       |
| A first paragraph that is a single link is the back link                                         | none                                       |
| A quote that starts with `⚠️` or a bold Warning, Important, Внимание or Важно is a warning       | none                                       |
| A quote that starts with Note or Примечание is a note                                            | none                                       |
| Heading ids follow GitHub                                                                        | `anchorStyle`                              |

The directory names of the Diátaxis layout (`how-to/`, `reference/`, `explanation/`, `diagrams/`, `interactive/`)
mean nothing to the plugin: the headings of the tables of contents make the groups, and every diagram and
interactive page is found by its file type wherever it lies.

## checks

| Property              | Type                   | Default                                        | Purpose                                         |
| --------------------- | ---------------------- | ---------------------------------------------- | ----------------------------------------------- |
| `runWithCheck`        | `Property<Boolean>`    | `false`                                        | Whether `check` depends on `docsCheck`          |
| `maxLineLength`       | `Property<Int>`        | `max_line_length` of `.editorconfig`, else `0` | Longest Markdown line; `0` turns the check off  |
| `lineLengthInTables`  | `Property<Boolean>`    | `false`                                        | Whether the rows of tables are checked          |
| `forbiddenCharacters` | `ListProperty<String>` | empty                                          | Characters a page must not contain, such as `—` |
| `committedDiagrams`   | `Property<Boolean>`    | `false`                                        | Whether a committed SVG must match its `.puml`  |
| `excludedDirectories` | `SetProperty<String>`  | `.git`, `.gradle`, `build`, more               | Directories the links do not see as files       |

`runWithCheck` works only if the root project has a `check` task, for example from the `base` plugin.

`maxLineLength` is by default the `max_line_length` that `.editorconfig` gives the Markdown files: the value for the
table of contents `index`, from the `.editorconfig` files of its directory and the directories above it up to the
one with `root = true`. A later section overrides an earlier one, a closer file a farther one; section globs such as
`[*]`, `[*.md]` and `[*.{md,kt}]` work. `off`, no value or no `.editorconfig` mean no limit, `0`. The files are
inputs of `docsCheck`, and an explicit `maxLineLength.set(...)` wins over them:

```editorconfig
[*]
max_line_length = 120
```

`committedDiagrams.set(true)` checks the SVG committed next to a `.puml` for a Git hosting that shows only the
committed pictures, see [Stale diagram](#check-rules).

While `maxLineLength` is not `0`, the rule MD013 of the Markdown linter is off: the plugin measures the lines itself,
with the exceptions of [Check rules](#check-rules). With `maxLineLength.set(0)` MD013 works as its configuration
says.

The default `excludedDirectories`: `.git`, `.gradle`, `.idea`, `.kotlin`, `.cxx`, `build`, `node_modules`, `Pods`,
`DerivedData`. A directory is excluded by its name at any depth.

## checks.markdownLint

The [rumdl](https://github.com/rvben/rumdl) Markdown linter of `docsCheck` and `docsFormat`. The
rules themselves are configured in the files of the repository, see
[Lint the Markdown pages](../how-to/usage.md#lint-the-markdown-pages).

| Property          | Type                          | Default             | Purpose                                             |
| ----------------- | ----------------------------- | ------------------- | --------------------------------------------------- |
| `enabled`         | `Property<Boolean>`           | `true`              | Whether the pages are checked with rumdl            |
| `executable`      | `RegularFileProperty`         | not set             | A local rumdl binary; nothing is downloaded with it |
| `version`         | `Property<String>`            | `0.3.4`             | Version of rumdl to download                        |
| `distributionUrl` | `Property<String>`            | the GitHub releases | Address of the release archive, a template          |
| `checksums`       | `MapProperty<String, String>` | SHA-256 of `0.3.4`  | SHA-256 of the release archives by target           |
| `configFile`      | `RegularFileProperty`         | not set             | Configuration file; without it rumdl finds its own  |
| `disabledRules`   | `ListProperty<String>`        | empty               | Rules turned off in addition to the configuration   |

`disable("MD041", "MD036")` adds to `disabledRules`.

```kotlin
docs {
    checks {
        markdownLint {
            distributionUrl.set("https://repo.example.org/repository/raw/rumdl/v{version}/{asset}")
            disable("MD041")
        }
    }
}
```

`distributionUrl` is by default `https://github.com/rvben/rumdl/releases/download/v{version}/{asset}`. The
placeholders:

| Placeholder | Replaced with                    | Example                                         |
| ----------- | -------------------------------- | ----------------------------------------------- |
| `{version}` | `version`                        | `0.3.4`                                         |
| `{target}`  | Target of the machine, see below | `x86_64-unknown-linux-musl`                     |
| `{asset}`   | File name of the release archive | `rumdl-v0.3.4-x86_64-unknown-linux-musl.tar.gz` |

An address without `{asset}` is a directory: the file name is appended to it. A mirror with basic authentication
takes the credentials in the address, `https://user:password@repo.example.org/...`; they are not printed in the
messages. `file:` addresses work too.

The target is chosen by `os.name` and `os.arch` of the Gradle JVM:

| Machine                | Target                       | Archive   |
| ---------------------- | ---------------------------- | --------- |
| macOS on Apple silicon | `aarch64-apple-darwin`       | `.tar.gz` |
| macOS on Intel         | `x86_64-apple-darwin`        | `.tar.gz` |
| Linux x64              | `x86_64-unknown-linux-musl`  | `.tar.gz` |
| Linux arm64            | `aarch64-unknown-linux-musl` | `.tar.gz` |
| Windows x64 and arm64  | `x86_64-pc-windows-msvc`     | `.zip`    |

Linux gets the static musl build, which runs with any C library. On another platform the task fails and asks for
`executable`.

`checksums` holds the SHA-256 of the release archives of `0.3.4`, taken from the `.sha256` files of the release;
a changed `version` has no checksums until they are set, for the targets the builds run on:

```kotlin
docs {
    checks {
        markdownLint {
            version.set("0.3.5")
            checksums.put("aarch64-apple-darwin", "<the content of rumdl-v0.3.5-aarch64-apple-darwin.tar.gz.sha256>")
            checksums.put("x86_64-unknown-linux-musl", "<...>")
        }
    }
}
```

`configFile` is a `.rumdl.toml` or a markdownlint file such as `.markdownlint.json`. With it rumdl reads only this
file: the configuration files of the repository and of its directories are not read.

The plugin also turns off the rules it checks itself, see [Rules shared with rumdl](#rules-shared-with-rumdl).

## serve

| Property | Type            | Default | Purpose                           |
| -------- | --------------- | ------- | --------------------------------- |
| `port`   | `Property<Int>` | `8000`  | Port of the server on `localhost` |

## Tasks

| Task                 | What it does                                                           |
| -------------------- | ---------------------------------------------------------------------- |
| `docsBuild`          | Builds the site in `outputDirectory`                                   |
| `docs`               | Builds the site and serves it on `localhost:<port>` until Ctrl+C       |
| `docsCheck`          | Checks the documentation by the [rules](#check-rules), fails on errors |
| `docsFormat`         | Fixes the Markdown pages in place with `rumdl fmt`                     |
| `docsRenderDiagrams` | Draws the `.puml` files to SVG and writes preprocessed sources         |
| `docsApiReference`   | Joins the Dokka HTML of the modules into one reference                 |

All tasks but `docs` and `docsFormat` are cacheable and compatible with the configuration
cache and with isolated projects. `docsFormat` changes the sources, so it is never up to date and is not
cached; it is compatible with the configuration cache too. `docs` does not rebuild the site:
`./gradlew docsBuild --continuous` in a second terminal does. `docsRenderDiagrams` without
`.puml` files and `docsApiReference` without modules with Dokka are skipped (`NO-SOURCE`) and download
nothing. The tasks are in the `documentation` group of `./gradlew tasks`.

## Command line properties

| Parameter            | Effect                                  |
| -------------------- | --------------------------------------- |
| `-Pdocs.port=<port>` | Replaces the default port `8000`        |
| `--port=<port>`      | Option of `docs`, replaces `serve.port` |

## Files

| Path                                   | Content                                                                        |
| -------------------------------------- | ------------------------------------------------------------------------------ |
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
| `build/docs-site-check/report.txt`     | All problems of the last `docsCheck`                                           |

Outside the repository:

| Path                                                            | Content                                                    |
| --------------------------------------------------------------- | ---------------------------------------------------------- |
| `<Gradle user home>/caches/docs-site/rumdl/<version>/<target>/` | The downloaded rumdl binary and the SHA-256 of its archive |

The Gradle user home is `~/.gradle` unless `GRADLE_USER_HOME` or `--gradle-user-home` says otherwise.

The configuration files rumdl reads in the directory of a page and in every directory above it, up to the root
project, are inputs of `docsCheck`: `.rumdl.toml`, `rumdl.toml`, `.config/rumdl.toml`, `pyproject.toml`,
`.markdownlint.json`, `.markdownlint.jsonc`, `.markdownlint.yaml`, `.markdownlint.yml` and `.markdownlintignore`.

`_assets/plantuml/` takes 6.7 MB with version `1.2026.9` and is copied only if at least one diagram is drawn in the
browser.

## Check rules

| Rule                | Error                                                                                 |
| ------------------- | ------------------------------------------------------------------------------------- |
| Link to a file      | The target of a relative link or image, `href` and `src` of HTML included, is missing |
| Table of contents   | An `index.md` links to a missing page                                                 |
| Anchor              | `#anchor` is not a heading of the target page by the rules of `anchorStyle`           |
| PlantUML error      | PlantUML cannot draw a `.puml`; the line of the source is given                       |
| Stale diagram       | A committed SVG next to its `.puml` shows other texts, elements, links or colours     |
| Line length         | A line is longer than `maxLineLength`                                                 |
| Forbidden character | A line contains a character of `forbiddenCharacters`                                  |
| Markdown lint       | rumdl reports a rule of its configuration, such as `[MD009] 3 trailing spaces found`  |

Line length and characters are not checked in code blocks, characters not in inline code either. Line length is not
checked for table rows (unless `lineLengthInTables`) and for lines with an address (`://`); other lines are measured
without the link targets `](...)`. For an anchor with a wrong case or hyphens the check suggests an existing one:
`did you mean #storage-format?`. A page that no other page links to, except README and `index.md`, is reported as a
warning and does not fail the build.

A finding of rumdl is reported as `<path>:<line>: [<rule>] <message>` and fails the build whatever its rumdl
severity; a rule that should not fail the build is turned off in the configuration.

### Rules shared with rumdl

Some rules of rumdl check what the plugin checks itself. The plugin keeps its own check and turns the rule off, so
that a problem is reported once:

| rumdl rule           | Plugin check   | Why the plugin check stays                                                           |
| -------------------- | -------------- | ------------------------------------------------------------------------------------ |
| MD051 link fragments | Anchor         | MD051 follows the GitHub anchors; the plugin follows `anchorStyle`, GitLab or GitHub |
| MD057 relative links | Link to a file | The plugin also checks `index.md` entries, HTML pages and diagrams not committed     |
| MD013 line length    | Line length    | Off only while `maxLineLength` is not `0`; the plugin skips link targets and tables  |

The other plugin checks have no rumdl rule, and the other rumdl rules have no plugin check: trailing spaces (MD009),
code block languages (MD040) and the like come only from rumdl. MD061 (forbidden terms) is empty by default and
does not overlap `forbiddenCharacters` unless both are configured.

## Dependencies

| Dependency                                         | Version    | Where                                     |
| -------------------------------------------------- | ---------- | ----------------------------------------- |
| `org.commonmark:commonmark` and GFM extensions     | `0.31.0`   | The plugin: Markdown                      |
| `org.jsoup:jsoup`                                  | `1.16.1`   | The plugin: Dokka and interactive pages   |
| `org.jetbrains.kotlinx:kotlinx-serialization-json` | `1.7.1`    | The plugin: indexes and reports           |
| `net.sourceforge.plantuml:plantuml-mit`            | `1.2026.9` | Configuration `documentationPlantUml`     |
| npm `@plantuml/core` as `plantuml-npm:core@tgz`    | `1.2026.9` | Configuration `documentationPlantUmlJs`   |
| `org.jetbrains.dokka:dokka-cli`                    | `2.1.0`    | Configuration `documentationDokkaCli`     |
| Dokka plugins for the aggregation                  | `2.1.0`    | Configuration `documentationDokkaPlugins` |
| rumdl binary, from its release archive             | `0.3.4`    | Downloaded by the lint tasks, not Maven   |

The Dokka plugins are `dokka-base`, `templating-plugin`, `all-modules-page-plugin` and `analysis-kotlin-symbols`;
the modules apply Dokka of the same version `2.1.0`. The licenses of the components are in
[NOTICE](../../NOTICE).
