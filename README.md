# repo-docs

A Gradle plugin that turns the documentation of a repository into a static site: the Markdown pages of
`README.md` and `documentation/` (or `docs/`, `Documentation/`), PlantUML diagrams drawn from their sources, self-contained HTML pages
and the Dokka API reference of the modules, under one navigation with one search. The site works offline, from the
file system with live reload, and `docsCheck` finds broken links, anchors and stale
diagrams and lints the Markdown with rumdl.

![An API page of a generated site](docs/images/site.png)

## Features

- **Markdown as on GitLab and GitHub.** Relative links between `.md` files, heading anchors by the rules of GitHub
  or GitLab, GFM tables, task lists and autolinks. The site keeps the layout of the repository, so the links stay
  valid.
- **Diagrams from source.** Every `.puml` is drawn to SVG at build time, the ELK layout included; the committed SVG
  is never shown. `docsDiagrams` writes the same SVG next to the source for the Git hosting. Diagrams can be zoomed,
  panned and opened full screen.
- **API reference inside the documentation.** The Dokka pages of the modules that apply Dokka are shown in the
  site layout, under the library they belong to. Inline code such as `Greeter` or `Greeter.greet` in the Markdown
  pages links to the declaration.
- **HTML pages.** A self-contained `.html` of the documentation, such as an interactive explanation, lies with the
  Markdown pages of its kind, is listed in a table of contents as any page and opens inside the site, its own scripts
  and styles untouched.
- **Code highlighting at build time**, **search** over the pages, the declarations and the HTML pages.
- **Checks**: links, anchors, PlantUML errors, committed SVG out of date with its source, and the rules of the
  [rumdl](https://github.com/rvben/rumdl) Markdown linter from the repository's own `.rumdl.toml`; `docsFormat`
  applies the fixes of rumdl.
- English or Russian interface; light and dark theme, as the system or chosen with the button in the header, kept
  for every page; works on a phone.

The plugin carries what it runs: PlantUML, and rumdl compiled from WebAssembly to JVM classes. A project declares no
repositories for it; only the API reference needs Dokka, which the project sets up itself.

## Set up

`build.gradle.kts` of the root project:

```kotlin
plugins {
    id("io.github.wolfandrei.repo-docs") version "0.1.0"
}
```

The site has the API reference when the project joins the API of its modules with Dokka, as the
[Dokka documentation](https://kotlinlang.org/docs/dokka-gradle.html) describes in «Aggregate documentation output in
multi-project builds»:

1. The root project applies Dokka and names the modules to join:

   ```kotlin
   plugins {
       id("io.github.wolfandrei.repo-docs") version "0.1.0"
       id("org.jetbrains.dokka") version "2.3.0"
   }

   dependencies {
       dokka(project(":lib"))
   }
   ```

2. Each of these modules applies Dokka, `id("org.jetbrains.dokka")`, without a version.

The site then takes the HTML that `dokkaGeneratePublicationHtml` of the root writes. The plugin does not configure
Dokka and does not bring it: the version and the repositories are those of the project. Without Dokka in the root the
site has no API reference.

The settings live in the `docs { }` block; each of them is described in its KDoc, which the IDE shows. A typical
block:

```kotlin
import io.github.wolfandrei.repodocs.AnchorStyle

docs {
    language.set("ru")                         // the interface, not the pages
    anchorStyle.set(AnchorStyle.GITLAB)        // heading ids of a GitLab repository
    checks {
        runWithCheck.set(true)                 // ./gradlew check runs docsCheck
    }
}
```

A repository whose Gradle build lies in a subdirectory, with `README.md` and the documentation above it, sets
`repositoryRoot.set(layout.projectDirectory.dir(".."))` in the `docs` block.

The style rules come only from `.rumdl.toml` in the repository; each rule and its options are described in the
[rumdl rules reference](https://rumdl.dev/rules/):

```toml
[global]
disable = ["MD033"]

[MD013]
line-length = 120
code-blocks = false

[MD061]
terms = ["—"]
```

## Tasks

| Task                 | What it does                                                                  |
|----------------------|-------------------------------------------------------------------------------|
| `docs`               | Builds the site and opens it in the browser                                   |
| `docsBuild`          | Builds the site in `build/repo-docs/`                                         |
| `docsCheck`          | Checks the documentation, fails on errors; report in `build/repo-docs-check/` |
| `docsFormat`         | Applies the fixes of rumdl to the pages in place                              |
| `docsDiagrams`       | Writes the SVG of every `.puml` next to it; fails on a diagram error          |
| `docsRenderDiagrams` | Draws the `.puml` files to SVG, for the other tasks                           |

The site is a directory of static files that works from the file system: no server is needed. While
`./gradlew docsBuild --continuous` runs, an open page reloads itself after each rebuild.

All tasks but `docs`, `docsFormat` and `docsDiagrams` are cacheable; all of them are compatible with the
configuration cache.

## Documentation

[Conventions](docs/conventions.md): how the plugin reads the layout of the documentation, what the checks report,
the limitations and the usual problems.

## License

Apache License 2.0, see [LICENSE](LICENSE). Third-party components and their licenses are listed in
[NOTICE](NOTICE).
