# How the plugin works

What the site is built from, how the heading anchors, the sidebar, the diagrams, the API reference, the links to it
and the interactive pages come about, how the search, the server and the reload work, and what the plugin cannot
do. The settings are in [Configuration and tasks](../reference/configuration.md).

## Contents

- [Purpose](#purpose)
- [Data flow](#data-flow)
- [Markdown pages](#markdown-pages)
- [Heading anchors](#heading-anchors)
- [Sidebar](#sidebar)
- [PlantUML diagrams](#plantuml-diagrams)
- [API reference](#api-reference)
- [Links to the API](#links-to-the-api)
- [Interactive pages](#interactive-pages)
- [Code highlighting](#code-highlighting)
- [Search](#search)
- [Server and live reload](#server-and-live-reload)
- [Caching](#caching)
- [Limitations](#limitations)
- [Source layout](#source-layout)

## Purpose

The documentation of a repository is written for its Git hosting: relative links between `.md` files, `#anchor`
links to headings, `.puml` diagrams with their SVG next to them, interactive `.html` pages. The plugin shows the same
documentation locally and offline, adds the API reference and the search, and `documentationCheck` catches what
breaks the documentation on the hosting: broken links and anchors, stale SVG.

## Data flow

```text
README.md, documentation/**/*.md ──────────────────────────────────────┐
documentation/** (png, html, puml, iuml) ── copy ──────────────────────┤
documentation/**/*.puml ── documentationRenderDiagrams ── svg, preproc ┼── documentationSite ── build/docs-site
modules with Dokka ── dokkaGenerateModuleHtml ── documentationApiReference ┘
                                   │
                                   └── report.json ── documentationCheck ── report.txt
```

The site keeps the layout of the repository: `documentation/lib/how-to/usage.md` becomes
`documentation/lib/how-to/usage.html`, pictures and interactive pages keep their paths. The relative links stay
valid; only `.md` becomes `.html`.

The plugin configures the root project only: it adds nothing to the other projects and reads nothing from them but
the paths of the subprojects. A build with `-Dorg.gradle.unsafe.isolated-projects=true` passes without problems
(covered by a test).

## Markdown pages

commonmark-java parses Markdown with the GFM extensions: tables, strikethrough, task lists, autolinks. HTML inside
Markdown is kept as it is. In links, images and the `href`, `src` of inline HTML the plugin changes only relative
addresses:

| Target of the link                        | On the site                                                         |
|-------------------------------------------|---------------------------------------------------------------------|
| a Markdown page of the site               | `.md` -> `.html`, the anchor stays                                  |
| a `.kt` file named as a type of the API   | the API page of the type, see [Links to the API](#links-to-the-api) |
| a file or directory of the site           | unchanged                                                           |
| a repository file that is not on the site | `sourceBrowseUrl` + path; without it, unchanged                     |
| an external address, `mailto:`            | unchanged                                                           |

The root `README.md` becomes `README.html`, and its copy is the home page `index.html`. The source files are never
changed: on the Git hosting the links keep pointing to the `.md` and `.kt` files.

Three things change the look of a page without any markup: the quote right after the title is shown as the abstract
of the page, a quote that starts with `⚠️` is a warning and a quote that starts with `Note` or `Примечание` is a note.
The index page of a library, `documentation/<library>/index.md`, gets a large title. Tables scroll inside their own
box on a narrow screen.

## Heading anchors

Every heading gets an `id` by the rules of `anchorStyle`, so that the `#anchor` links written for the hosting work
on the site. Both styles:

1. lowercase the text of the heading, without markup, code included as text;
2. remove everything except letters, marks, decimal digits, `_`, `-` and the space;
3. turn each space into `-`;
4. number a repeated id of a page with `-1`, `-2` and so on in the order of the headings.

`gitlab` (default) also turns runs of `-` into one `-` and gives an id of digits only the `anchor-` prefix, as GitLab
Flavored Markdown documents in «Heading IDs and links». `github` does neither. The styles differ only for headings
with a run of hyphens or spaces, `A - B` is `a-b` on GitLab and `a---b` on GitHub, and for headings of digits only.
`Storage format (v2)` is `storage-format-v2` in both.

## Sidebar

The sidebar is built from the table of contents `documentation/index.md`:

- a heading of the second or a lower level opens a group; its title comes from
  [navigation.groupTitles](../reference/configuration.md#navigation) when the heading is a known one, such as
  `How-to` or `Reference`; a heading of an in-page contents list, such as `## Contents`, opens no group;
- a list item with a link to a file of the repository is an entry, wherever the link stands in the item, so
  `[Usage](usage.md) - ...` and `Then apply the plugin - see [here](usage.md)` both work; a nested list gives
  nested entries;
- an entry shows the heading of its page, so that a link text such as «here» does not become a title;
- a page that is a table of contents itself (a **hub**) is expanded in place: its entries become nested entries.

A hub is a page named `index.md`, `README.md`, `*-index.md` or `*_index.md`, or a page that links to at least 2
Markdown pages that the sidebar does not list yet and has at most 60 words of its own text. The second rule finds a
page such as «First set up the environment - see here; then apply the plugin - see here» without any naming
convention, and leaves out an article that happens to end with a list of links. Each page is listed once: a hub
lists only the pages that are not listed yet, so the tables of contents cannot form a cycle.

README is above the table of contents. The API of a Dokka module goes under its library, the directory
`documentation/<module>/` (or the one of `apiReference.libraries`): as `API -> package -> types` (nested types under
their outer type, each with a badge of its kind) inside the group named `Reference`, under its entry if the group
has one entry, or at the end of the page of the library without such a group. The page of a library is the first
entry whose page lies directly in the library directory. Modules without a library form the top-level group «API».
The interactive pages of a library that no table of contents mentions go into its group «Interactive pages». The
pages that no table of contents mentions are in «Other pages», titled by their headings. A page that the sidebar
does not list, such as a member of a type, highlights its closest listed parent. All these conventions and their
settings are listed in [Layout conventions](../reference/configuration.md#layout-conventions).

## PlantUML diagrams

The source of a diagram is always the `.puml`. The SVG next to it in Git is for the hosting and is not copied to
the site: the site shows an SVG drawn from the source. Under each diagram a page shows a «Source» block with the
text of the `.puml` and the list of its includes, local ones by repository path and those of the standard library,
such as `<C4/C4_Context>`.

### Drawn at build time

`documentationRenderDiagrams` runs PlantUML from the `plantuml-mit` jar in a separate JVM, once per directory with
diagrams, twice: `-tsvg` draws SVG and `-preproc` writes the **preprocessed source**, the text of the diagram with
every `!include` and macro expanded. No installed PlantUML is needed.

PlantUML lays a diagram out with Graphviz `dot` or with its built-in Smetana engine. With `layout = "auto"` the task
looks for `dot` (`dotExecutable`, `GRAPHVIZ_DOT`, `PATH`) and uses it if found, otherwise Smetana. The `dot` found
and its version are an input of the task, so machines with and without Graphviz do not share cached SVG. An error
in a diagram does not stop the build: PlantUML draws the error into the SVG, and the task writes it to the report
that `documentationCheck` reads.

### Drawn in the browser

The browser build of PlantUML is the npm package `@plantuml/core`: PlantUML compiled to JavaScript with TeaVM, with
Viz.js (Graphviz in WebAssembly) for the `dot` layout. Gradle downloads the package through an Ivy repository over
the npm registry, and `documentationSite` unpacks the needed files to `_assets/plantuml/`. `plantuml.js` is an ES2015
module, which a browser does not load from `file://`, so its `export{...}` becomes an assignment to
`window.PlantUmlJs`; no network is needed to view the site.

The page holds the preprocessed source of each diagram drawn in the browser; the site script draws it with
`renderToString` and replaces the build SVG; on an error the build SVG stays. The browser build cannot read files:
the preprocessed source has no `!include` left, but the sprites of the standard library stay references
(`sprite $person #c4#c4_context:person`) that the browser build cannot resolve. Hence `render = "auto"` draws the
diagrams with such sprites (C4 with `Person`, `System`) at build time and the others in the browser.

### Zoom and pan

Every SVG picture of an article gets a viewer: a toolbar with zoom out, the scale, zoom in, fit and full screen.
The picture is moved by a CSS transform, so it stays vector and sharp at any scale and is never redrawn; a diagram
that the browser draws later replaces the picture inside the same viewer.

| Input                               | Inline                                             | Full screen             |
|-------------------------------------|----------------------------------------------------|-------------------------|
| Ctrl or Cmd + wheel, trackpad pinch | zoom around the pointer                            | zoom around the pointer |
| Wheel                               | scrolls the page                                   | zoom around the pointer |
| Drag with the mouse                 | pan                                                | pan                     |
| One finger                          | scrolls the page, pans once zoomed                 | pan                     |
| Two fingers                         | pinch zoom and pan                                 | pinch zoom and pan      |
| Double click, Shift + double click  | zoom in, zoom out                                  | zoom in, zoom out       |
| Keys on the focused diagram         | `+` `-` zoom, `0` fit, `F` full screen, arrows pan | the same, `Esc` closes  |

Inline, a diagram fits the width of the text at most at its own size and at most 80 % of the window height. In the
dark theme the diagram lies on a light card, since PlantUML draws for a light background.

### Committed SVG check

A byte comparison with the committed SVG does not work: the coordinates depend on the layout engine, on the font
metrics of the JVM and on the PlantUML version. The check compares **fingerprints**, what the diagram shows without
its geometry: the texts of all `<text>`, the element names of `data-qualified-name`, the number of links
(`class="link"`), and the fill colours of rectangles, ellipses and texts except the background, in long form
(`#FFF` = `#FFFFFF`). The background is left out because some PlantUML runs draw a full-canvas rectangle and others
do not. The SVG of one source from `dot` and from Smetana have the same fingerprint; a change of a text, element,
link or colour in the `.puml` or in an included `.iuml` changes it, a moved element does not.

## API reference

The plugin does not apply Dokka. A module that applies Dokka publishes, for aggregation, a variant with the output of
`dokkaGenerateModuleHtml` (attributes `org.gradle.usage=org.jetbrains.dokka`, `org.jetbrains.dokka.format=html`,
`org.jetbrains.dokka.module-component=ModuleOutputDirectories`). `documentationApiReference` asks every subproject
for this variant by a project dependency, leniently, so a project without it is skipped, and keeps the directories
with `module-descriptor.json`. The dependencies live in a detached configuration with a root component of its own:
in a configuration of the root project a module named as the root project has the coordinates of the root and would
resolve to the root itself. The Dokka command line `dokka-cli` with `dokka-base`, `templating-plugin` and
`all-modules-page-plugin` joins the module outputs, the same step as `dokkaGeneratePublicationHtml` of a root
project with Dokka.

`documentationSite` does not copy the Dokka site. It parses each Dokka page with jsoup, takes its main content
(`.main-content`) and drops the navigation, header, footer, scripts, copy buttons and platform tabs of Dokka. The
content is rebuilt as a page of a symbol in the site template, at the same path under `api/`, so the links between
the Dokka pages and their anchors keep working:

- the breadcrumbs, the kind of the declaration (Class, Interface, Function...), its name, the first sentence of its
  documentation as the abstract, and its platforms;
- «Declaration»: the signature for each source set, labelled with its platform (`common`, `android`, `jvm`, `ios`,
  `native`... as the source set without `Main`) when there are several;
- «Overview»: the rest of the documentation, and a section for each KDoc tag (Parameters, Return value, Throws,
  See also...);
- «Topics»: the members grouped by kind, each with a badge of its kind, its signature, its abstract and its
  platforms.

The tokens that Dokka marks in a signature are coloured in the palette of the site. Errors of the Dokka
analysis in a signature, such as `<ERROR CLASS> ERROR CLASS: Symbol not found for Foo` for a supertype of another
source set, are shown as `Foo`.

## Links to the API

`scripts/pages.json` of the Dokka output lists every declaration with its qualified name and page. A type is found
by its simple name (`Greeter`), by its name inside the package (`Result.Failure`) and by its qualified name;
a member by `Type.member` and by its qualified name, never by its bare name, which is too common a word. A trailing
`()` and `::` instead of `.` are accepted.

An inline code of a Markdown page becomes a link when its text resolves for the library of the page, the directory
`documentation/<library>/`:

1. the declarations of the Dokka module named as the library win;
2. a name with several pages in that module is ambiguous and stays plain;
3. without a match there, or on a page outside any library, a name is linked only if it has one page in the whole
   reference.

Inline code inside a link or a heading stays as it is, code blocks are never touched. A Markdown link to a `.kt`
file named as a type, `[Greeter.kt](...)`, leads to the page of the type by the same rules.

## Interactive pages

An interactive page is a self-contained `.html` of the documentation with its own styles and scripts. The site shows
it inside a site page at the same path, with the header, the sidebar and the search around it, and the page itself in
an `<iframe>`:

- the page is copied next to its source as `<name>.frame.html`, with its links to Markdown pages rewritten to
  `.html`; links leave the frame (`target="_top"`), anchors inside the page stay inside it;
- a small script added to the copy sends the height of the page to the site page on load and on every change, and
  the frame takes that height, so the page does not scroll inside the frame;
- the copy lies on the background of the site and follows its colour scheme, so it does not show as a box in the
  dark theme; its own styles and scripts stay isolated from the site, and the site styles from it;
- the title in the sidebar and in the search is the `<title>` of the page; the search indexes its visible text.

The source files in the repository stay unchanged and keep working on their own; «Open in a separate tab» under the
frame opens the copy alone.

## Code highlighting

Fenced code blocks of the Markdown pages and code blocks of the API pages are highlighted at build time, so the
highlighting needs no script. A small scanner per language family marks keywords, types (capitalised names),
strings, numbers, comments, annotations and directives, keys and attributes, variables, tags, and added and removed
lines; an unknown construct stays plain text. The languages by the tag of the block: Kotlin (`kotlin`, `kt`, `kts`),
Swift, Java, Groovy (`groovy`, `gradle`), shell (`bash`, `sh`, `shell`, `zsh`, `console`), JSON, YAML, XML and HTML,
the C family (`c`, `cpp`, `h`, `objc`...), CMake, properties, TOML and INI, PlantUML, diff. `text` and unknown tags
are not highlighted, and neither is inline code. The token colours follow Xcode and have at least 4.5:1 contrast
(WCAG AA) on the code background in the light and the dark theme.

## Search

`documentationSite` writes the search index: an entry per section of each page (the heading, the address with the
anchor, the text up to 4000 characters), an entry per declaration of the API reference (`Type.member (Function,
module)` with its documentation) and an entry per interactive page. The index lies in `_assets/search-index.json`
and `_assets/search-index.js`; a page loads the `.js` with a `<script>` tag, so the search works from the file
system too. The search finds all words of the query in the title and the text, ignoring case, `ё` equals `е`.

## Server and live reload

`documentationServe` is a plain task: it starts `com.sun.net.httpserver.HttpServer` of the `jdk.httpserver` module on
`localhost`, serves the site directory and waits until the build is stopped with Ctrl+C. A path that is not on the
site is looked up in the repository and served as text, so that links to sources open; paths with hidden directories
(`.git`) are not served.

A second build, `./gradlew documentationSite --continuous`, rebuilds the site. The last file `documentationSite`
writes is `_assets/build-stamp`, a SHA-256 hash of all files of the site. A page opened from the server polls it once
a second and reloads when it changes. The hash depends only on the content, so a rebuild without changes does not
reload the page, and a result from the build cache gives the same hash.

## Caching

- `documentationSite`, `documentationCheck`, `documentationRenderDiagrams` and `documentationApiReference` declare
  their inputs and outputs, are cacheable and compatible with the configuration cache; their input paths are
  relative, so the cache moves between clones.
- The list of repository files for the link check and the Graphviz found come from `ValueSource`s that are not read
  at configuration time: a new file or a Graphviz installation does not drop the configuration cache, it reruns the
  task that depends on it.

## Limitations

- Diagrams with sprites of the standard library are drawn in the browser only without their icons
  (`render = "browser"`).
- An emoji in a heading (`:ok:`) is replaced by GitLab with a character and dropped from the anchor; the plugin keeps
  `ok` in the anchor.
- Links to lines of files (`file.kt#L10`) and anchors inside interactive pages are not checked.
- An `!include` of a file outside the documentation directory works, but a change of that file does not redraw the
  diagram: it is not an input of the task.
- The API reference is joined from modules with Dokka `2.1.0`; other Dokka versions are not tested.
- Inline code that names a member by its bare name (`greet`) is not linked; write `Type.member`.
- An interactive page whose height follows the window (`height: 100vh`) grows with its frame up to 20000 px.
- The site opens from the file system, but links to sources outside the site without `sourceBrowseUrl` and the live
  reload work only through `documentationServe`.

## Source layout

| What                                | File in `src/main/kotlin/io/github/wolfandrei/docssite/`                   |
|-------------------------------------|----------------------------------------------------------------------------|
| Plugin, tasks, configurations       | `DocumentationSitePlugin.kt`                                               |
| The `documentationSite` block       | `DocumentationSiteExtension.kt`                                            |
| Markdown, links, anchors            | `markdown/`                                                                |
| Template, sidebar, search, diagrams | `site/`; styles and script in `src/main/resources/.../site.css`, `site.js` |
| API pages and links to them         | `site/DokkaReference.kt`, `site/ApiSymbols.kt`                             |
| Interactive pages                   | `site/InteractivePages.kt`                                                 |
| Code highlighting                   | `site/CodeHighlighter.kt`                                                  |
| Interface strings                   | `site/UiText.kt`                                                           |
| Checks                              | `check/DocumentationChecker.kt`                                            |
| PlantUML, Graphviz, fingerprints    | `tasks/RenderPlantUmlTask.kt`, `plantuml/`                                 |
| Joining the Dokka output            | `tasks/AggregateApiReferenceTask.kt`                                       |
| Server                              | `serve/DocumentationServer.kt`, `tasks/ServeDocumentationSiteTask.kt`      |

