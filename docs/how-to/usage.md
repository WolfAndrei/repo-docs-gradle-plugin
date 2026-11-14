# How to set up, build, serve and check the site

Setting up the plugin, building the site, serving it with live reload, checking the documentation, and fixing the
usual problems. Every setting is described in [Configuration and tasks](../reference/configuration.md).

## Contents

- [Set up](#set-up)
- [Write the documentation](#write-the-documentation)
- [Build the site](#build-the-site)
- [Serve with live reload](#serve-with-live-reload)
- [Check the documentation](#check-the-documentation)
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

3. Optionally set the language of the interface and the link prefix of the repository files that are not on the
   site:

   ```kotlin
   documentationSite {
       language.set("ru")
       sourceBrowseUrl.set("https://gitlab.example.com/group/repo/-/blob/main/")
   }
   ```

4. For the API reference, apply Dokka `2.1.0` to each module to document:

   ```kotlin
   plugins {
       id("org.jetbrains.dokka") version "2.1.0"
   }
   ```

   The plugin does not apply Dokka itself and needs no Dokka in the root project. Modules without Dokka are not in
   the reference; `apiReference.modules` and `apiReference.excludedModules` narrow the set further.

## Write the documentation

The plugin reads the layout below; other layouts work with the paths of
[the documentationSite block](../reference/configuration.md#the-documentationsite-block).

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
./gradlew documentationSite
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
   ./gradlew documentationServe
   ```

   Another port: `./gradlew documentationServe --port=8081` or `-PdocumentationSite.port=8081`.

   Expected result: `Documentation site is served at http://localhost:8000/`.

2. In the second terminal, start the continuous build:

   ```shell
   ./gradlew documentationSite --continuous
   ```

   Expected result: `Waiting for changes to input files...`.

3. Open the address of step 1. After a change of a `.md`, `.puml` or `.html` file the second build rebuilds the
   site, and the open page reloads by itself.

4. Stop both builds with Ctrl+C.

## Check the documentation

```shell
./gradlew documentationCheck
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
documentationSite {
    checks {
        runWithCheck.set(true)
    }
}
```

## Troubleshooting

### anchor ... is not a heading

```text
anchor `#release-notes---v2` of `#release-notes---v2` is not a heading of docs/changes.md, did you mean `#release-notes-v2`?
```

Cause: the anchor follows other rules than `anchorStyle`, for example GitHub rules, which keep `---`, on a site
with GitLab rules, which shorten it to `-`.

Fix: use the suggested anchor, or set `anchorStyle` to the rules of the Git hosting of the repository. The rules
are in [Heading anchors](../explanation/architecture.md#heading-anchors).

### out of date with its PlantUML source

Cause: the `.puml` or an included `.iuml` changed, and the committed SVG was drawn from the old source. The site
never shows the committed SVG; the check exists for the Git hosting.

Fix: draw the SVG again and commit it:

```shell
./gradlew documentationRenderDiagrams
cp build/docs-site-diagrams/svg/<dir>/<name>.svg <dir>/<name>.svg
```

where `<dir>` is the directory of the diagram relative to the repository root, such as
`documentation/lib/diagrams`, and `<name>` is the name of the `.puml` without the extension.

Check: `./gradlew documentationCheck` no longer reports the diagram.

### PlantUML cannot render the diagram

```text
documentation/lib/diagrams/context.puml:12: PlantUML cannot render the diagram, see the SVG for the error
```

Cause: a syntax error in line 12 or in an included file. Fix: open
`build/docs-site-diagrams/svg/<dir>/<name>.svg`, PlantUML writes the error into the picture.

### The browser build of PlantUML is not available

Cause: the configuration `documentationPlantUmlJs` did not find the npm package `@plantuml/core`: no Ivy
repository of [step 1](#set-up), or the registry is not reachable.

Fix: add the repository. Check: after `./gradlew documentationSite` the directory `build/docs-site/_assets/plantuml/`
exists.

### Address already in use

```text
java.net.BindException: Address already in use
```

Cause: port `8000` is taken, for example by another `documentationServe`. Fix:
`./gradlew documentationServe --port=8081`.
