# How to set up, build, serve and check the site

Setting up the plugin, building the site, serving it with live reload, checking and linting the documentation, and
fixing the usual problems. Every setting is described in [Configuration and tasks](../reference/configuration.md).

## Contents

- [Set up](#set-up)
- [Write the documentation](#write-the-documentation)
- [Build the site](#build-the-site)
- [Serve with live reload](#serve-with-live-reload)
- [Check the documentation](#check-the-documentation)
- [Lint the Markdown pages](#lint-the-markdown-pages)
- [Troubleshooting](#troubleshooting)

## Set up

1. Declare the repositories in `settings.gradle.kts`:

   ```kotlin
   pluginManagement {
       repositories {
           gradlePluginPortal()
           mavenCentral()
       }
   }

   dependencyResolutionManagement {
       repositories {
           mavenCentral()
           ivy {
               url = uri("https://registry.npmjs.org")
               patternLayout { artifact("@plantuml/[module]/-/[module]-[revision].[ext]") }
               metadataSources { artifact() }
               content { includeGroup("plantuml-npm") }
           }
       }
   }
   ```

   Maven Central gives PlantUML and the Dokka command line; the Ivy repository gives the npm package
   `@plantuml/core`, the browser build of PlantUML. A company mirror of the npm registry works the same way: put its
   address in `url`. Without the Ivy repository the site still builds and draws every diagram at build time.

   A build that declares repositories in each project instead of `dependencyResolutionManagement` needs the same
   repositories in the root project.

2. Apply the plugin to the root project:

   ```kotlin
   plugins {
       id("io.github.wolfandrei.docs-site") version "0.1.0"
   }
   ```

   Applied to another project, the plugin stops the configuration with
   `io.github.wolfandrei.docs-site is applied to the root project, not to :<project>`.

3. Optionally set the language of the interface, the link prefix of the repository files that are not on the
   site, and, for a repository hosted on GitLab, the GitLab rules of the heading anchors (the default is GitHub):

   ```kotlin
   docs {
       language.set("ru")
       sourceBrowseUrl.set("https://gitlab.example.com/group/repo/-/blob/main/")
       anchorStyle.set("gitlab")
   }
   ```

   The line length limit of the check comes from `max_line_length` of `.editorconfig` for Markdown files; without
   it lines are not measured. `checks { maxLineLength.set(120) }` sets it explicitly.

4. For the API reference, apply Dokka `2.1.0` to each module to document:

   ```kotlin
   plugins {
       id("org.jetbrains.dokka") version "2.1.0"
   }
   ```

   The plugin does not apply Dokka itself and needs no Dokka in the root project. Modules without Dokka are not in
   the reference; `apiReference.modules` and `apiReference.excludedModules` narrow the set further.

## Write the documentation

The plugin reads the layout below, with `docs/` in place of `documentation/` if the repository has only `docs/`;
other layouts work with the paths of
[the docs block](../reference/configuration.md#the-docs-block).

```text
README.md                         the home page
documentation/
├── index.md                      the table of contents: the root of the sidebar
└── lib/                         a library: its directory name matches its Dokka module
    ├── index.md                  the library page, shown with a large title
    ├── how-to/usage.md
    ├── reference/api.md
    ├── explanation/design.md
    ├── diagrams/context.puml     drawn from source; context.svg next to it is for the Git hosting
    └── interactive/modes.html    a self-contained interactive page
```

- `index.md` lists its pages as a list of links under headings: a heading such as `## How-to` or `## Reference`
  becomes a group of the sidebar, a link to another `index.md` expands it in place.
- The quote right after the title of a page is its abstract: `> One sentence about the page.`
- A quote that starts with `⚠️` is a warning, a quote that starts with `Note` or `Примечание` is a note.
- In the pages of `documentation/lib/`, an inline code with the name of a type or of `Type.member` of the Dokka
  module `lib` links to its API page: write `Greeter` or `Greeter.greet`, not a link. A link to a
  `.kt` file named as a type, such as `[Greeter.kt](../../src/.../Greeter.kt)`, also leads to the API
  page of the type. The rules are in [Links to the API](../explanation/architecture.md#links-to-the-api).

## Build the site

```shell
./gradlew docsBuild
```

Expected result, in the log:

```text
Documentation site: file:/<repository>/build/docs-site/index.html
```

The site opens from the file system at this address. Links to repository files that are not on the site open only
through the server, unless `sourceBrowseUrl` is set.

## Serve with live reload

Run the server and the rebuild in two terminals in the root of the repository.

1. In the first terminal, start the server:

   ```shell
   ./gradlew docs
   ```

   Another port: `./gradlew docs --port=8081` or `-Pdocs.port=8081`.

   Expected result: `Documentation site is served at http://localhost:8000/`.

2. In the second terminal, start the continuous build:

   ```shell
   ./gradlew docsBuild --continuous
   ```

   Expected result: `Waiting for changes to input files...`.

3. Open the address of step 1. After a change of a `.md`, `.puml` or `.html` file the second build rebuilds the
   site, and the open page reloads by itself.

4. Stop both builds with Ctrl+C.

## Check the documentation

```shell
./gradlew docsCheck
```

Expected result without problems: `Documentation check: <N> pages, no problems`. With problems the task fails and
lists them with the file and the line:

```text
Documentation has 2 problem(s):
  documentation/lib/how-to/usage.md:12: anchor `#format` of `../reference/api.md#format` is not a heading of documentation/lib/reference/api.md
  documentation/lib/diagrams/context.svg: out of date with its PlantUML source: texts differ (...)
```

The rules are in [Check rules](../reference/configuration.md#check-rules); the report of the last run is
`build/docs-site-check/report.txt`. To run the check with `./gradlew check`:

```kotlin
docs {
    checks {
        runWithCheck.set(true)
    }
}
```

## Lint the Markdown pages

`docsCheck` also runs the [rumdl](https://github.com/rvben/rumdl) Markdown linter on the pages it checks.
Its findings are in the same list, with the rule:

```text
  documentation/lib/how-to/usage.md:12: [MD040] Code block (```) missing language
```

On the first run the plugin downloads the rumdl binary for the machine from the GitHub releases of rumdl, checks its
SHA-256 and keeps it in the Gradle user home; later builds, `--offline` ones too, take it from there. The linter is
on by default; `checks { markdownLint { enabled.set(false) } }` turns it off.

### Configure the rules

The rules are configured in the files of the repository that rumdl finds itself, so an editor with a rumdl or
markdownlint extension shows the same findings as the build. Two kinds of files work:

- `.markdownlint.json` (also `.jsonc`, `.yaml`, `.yml`) - the markdownlint format. The markdownlint extensions of the
  editors read it too. It only turns rules on and off and sets their options:

  ```json
  {
    "default": true,
    "MD013": false,
    "MD033": false,
    "MD007": { "indent": 2 }
  }
  ```

- `.rumdl.toml` - the rumdl format, for what markdownlint files cannot say: excluded files, rules per file,
  inheritance and the rules of rumdl only.

  ```toml
  [global]
  disable = ["MD013", "MD033"]
  exclude = ["documentation/generated/**"]

  [per-file-ignores]
  "CHANGELOG.md" = ["MD024"]

  [MD007]
  indent = 2

  [MD060]
  enabled = true
  style = "aligned"
  ```

rumdl looks for its configuration in the directory of each page and then upwards: `.rumdl.toml`, `rumdl.toml`,
`.config/rumdl.toml`, `pyproject.toml` with `[tool.rumdl]`, and only without them the markdownlint files. The first
file found wins, so a `documentation/.rumdl.toml` replaces the root one for the pages under `documentation/`. To
inherit the root one instead, start it with `extends`:

```toml
extends = "../.rumdl.toml"

[global]
extend-disable = ["MD036"]
```

`rumdl import .markdownlint.json` converts a markdownlint file to `.rumdl.toml`; `--dry-run` prints the result
instead of writing it.

One place in a page is exempted with a comment; the markdownlint form works as well as the rumdl one:

```markdown
<!-- markdownlint-disable MD033 -->
<details><summary>Logs</summary>...</details>
<!-- markdownlint-enable MD033 -->

<!-- rumdl-disable-next-line MD036 -->
**Not a heading**
```

Two settings of the plugin change the rules without a file:

```kotlin
docs {
    checks {
        markdownLint {
            configFile.set(file("config/markdownlint.json")) // only this file, the found ones are not read
            disable("MD041")                                  // in addition to the configuration
        }
    }
}
```

The plugin turns off MD051 and MD057, and MD013 while `maxLineLength` is not `0`: it checks anchors, links and
line length itself, see [Rules shared with rumdl](../reference/configuration.md#rules-shared-with-rumdl).

rumdl has more rules than markdownlint: the rules above MD060, such as MD064 (multiple consecutive spaces) or MD077
(list continuation indentation), exist only in rumdl. The markdownlint extensions of the editors do not show them;
the rumdl extension does.

### Fix the pages

```shell
./gradlew docsFormat
```

The task runs `rumdl fmt` on the pages of the check, with the same configuration, and rewrites them in place:
trailing spaces, blank lines around headings and lists, code fences without a language (they get `text`), the
indentation of lists. What cannot be fixed, such as a picture without alternative text, stays for
`docsCheck`. Review the changes with `git diff` before committing.

Tables are aligned only with MD060 on, as in the `.rumdl.toml` above: the columns are padded by the display width of
the text, so Cyrillic text lines up as well as Latin, and CJK characters count as two columns.

### Use a mirror or work offline

A machine without access to GitHub takes the archive from a mirror, such as a raw repository of an artifact
manager that holds the release files under their names:

```kotlin
docs {
    checks {
        markdownLint {
            distributionUrl.set("https://repo.example.org/repository/raw/rumdl/v{version}/{asset}")
        }
    }
}
```

The archive must be the release archive itself: its SHA-256 is checked against the one the plugin knows. Where
neither GitHub nor a mirror is reachable, run the build once with the network, or install rumdl and set
`executable.set(file("/usr/local/bin/rumdl"))`; then nothing is downloaded at all.

### Migrate from the com.appmattus.markdown plugin

The rules of `com.appmattus.markdown` are those of markdownlint under other names. To keep a configuration, turn the
same rules off in `.markdownlint.json` or `.rumdl.toml` and remove that plugin:

| appmattus rule      | rumdl                                                                      |
| ------------------- | -------------------------------------------------------------------------- |
| `LineLengthRule`    | MD013; the plugin's `maxLineLength` replaces it                            |
| `NoInlineHtmlRule`  | MD033                                                                      |
| `OlPrefixRule`      | MD029                                                                      |
| `ProperNamesRule`   | MD044                                                                      |
| `FirstLineH1Rule`   | MD041                                                                      |
| `FirstHeaderH1Rule` | MD002 of the old markdownlint; rumdl has no such rule, nothing to turn off |

## Troubleshooting

### anchor ... is not a heading

```text
anchor `#release-notes---v2` of `#release-notes---v2` is not a heading of docs/changes.md, did you mean `#release-notes-v2`?
```

Cause: the anchor follows other rules than `anchorStyle`, for example GitHub rules, which keep `---`, on a site
with GitLab rules, which shorten it to `-`.

Fix: use the suggested anchor, or set `anchorStyle` to the rules of the Git hosting of the repository: the default
is `github`, a GitLab repository sets `anchorStyle.set("gitlab")`. The rules
are in [Heading anchors](../explanation/architecture.md#heading-anchors).

### out of date with its PlantUML source

Cause: the `.puml` or an included `.iuml` changed, and the committed SVG was drawn from the old source. The site
never shows the committed SVG; the check exists for the Git hosting and runs only with
`checks { committedDiagrams.set(true) }`.

Fix: draw the SVG again and commit it:

```shell
./gradlew docsRenderDiagrams
cp build/docs-site-diagrams/svg/<dir>/<name>.svg <dir>/<name>.svg
```

where `<dir>` is the directory of the diagram relative to the repository root, such as
`documentation/lib/diagrams`, and `<name>` is the name of the `.puml` without the extension.

Check: `./gradlew docsCheck` no longer reports the diagram.

### PlantUML cannot render the diagram

```text
documentation/lib/diagrams/context.puml:12: PlantUML cannot render the diagram, see the SVG for the error
```

Cause: a syntax error in line 12 or in an included file. Fix: open
`build/docs-site-diagrams/svg/<dir>/<name>.svg`, PlantUML writes the error into the picture.

### The browser build of PlantUML is not available

Cause: the configuration `documentationPlantUmlJs` did not find the npm package `@plantuml/core`: no Ivy
repository of [step 1](#set-up), or the registry is not reachable.

Fix: add the repository. Check: after `./gradlew docsBuild` the directory `build/docs-site/_assets/plantuml/`
exists.

### Address already in use

```text
java.net.BindException: Address already in use
```

Cause: port `8000` is taken, for example by another `docs`. Fix:
`./gradlew docs --port=8081`.

### SHA-256 of rumdl is not the expected one

```text
SHA-256 of rumdl from https://repo.example.org/.../rumdl-v0.3.4-x86_64-unknown-linux-musl.tar.gz is 5f1c..., expected 2e64...
```

Cause: the file at `distributionUrl` is not the release archive: a mirror with another file, an error page, or a
`version` changed without `checksums`. Nothing is installed. Fix: put the release archive on the mirror unchanged,
or set `checksums` for the new version from the `.sha256` files of its release.

### rumdl is not in the cache and Gradle is offline

Cause: the binary has never been downloaded on this machine, and the build runs with `--offline`. Fix: run
`./gradlew docsCheck` once without `--offline`, or set `markdownLint.executable`.

### No SHA-256 of the rumdl archive

Cause: `version` is not the one of the plugin and `checksums` has no entry for the target of this machine. Fix: add
it with `checksums.put("<target>", "<sha256>")`; the message names the target and the `.sha256` file to take it
from.
