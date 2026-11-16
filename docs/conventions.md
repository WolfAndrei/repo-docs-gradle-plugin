[Back to contents](index.md)

# Conventions

> How the plugin reads the layout of the documentation, what the checks report, the limitations and the usual
> problems. The settings are described in the KDoc of the `docs { }` block.

## Contents

- [Layout](#layout)
- [Links](#links)
- [Sidebar](#sidebar)
- [Links to the API](#links-to-the-api)
- [Heading anchors](#heading-anchors)
- [Diagrams](#diagrams)
- [HTML pages](#html-pages)
- [Markdown linter](#markdown-linter)
- [Checks](#checks)
- [Limitations](#limitations)
- [Troubleshooting](#troubleshooting)

## Layout

```text
README.md                         the home page
documentation/                    or docs/, Documentation/: the first that exists, with its case
├── index.md                      the table of contents: the root of the sidebar
└── lib/                          a library
    ├── index.md                  the library page, shown with a large title
    ├── how-to/usage.md
    ├── reference/api.md
    ├── explanation/design.md
    ├── explanation/flow.html     a self-contained HTML page, listed in index.md as any page
    └── diagrams/context.puml     drawn from source; context.svg next to it is for the Git hosting
```

The repository is the directory of the root project, or `repositoryRoot` of the `docs` block when the Gradle build
lies deeper. The site keeps the paths of the repository: `documentation/lib/how-to/usage.md` becomes
`documentation/lib/how-to/usage.html`, pictures and HTML pages keep their paths. The source files are never
changed.

A page needs no markup of the plugin: the quote right after the title is the abstract of the page, a quote that
starts with `⚠️` or a bold Warning, Important, Внимание or Важно is a warning, a quote that starts with Note or
Примечание is a note.

## Links

| Target of a relative link                 | On the site                                    |
| ----------------------------------------- | ---------------------------------------------- |
| a Markdown page of the site               | `.md` -> `.html`, the anchor stays             |
| a `.kt` file named as a type of the API   | the API page of the type                       |
| a file or directory of the site           | unchanged                                      |
| a repository file that is not on the site | a relative path to the file in the repository  |

The relative path leads from `build/repo-docs/` back to the repository, so it works while the site lies where it was
built.

## Sidebar

- The table of contents is `index.md` of the documentation directory; without it, `README.md` is the table of
  contents.
- A heading of a table of contents opens a group titled as the heading; a heading of an in-page contents list, such
  as `## Contents`, opens none.
- A list item with a link to a file is an entry, wherever the link stands in the item, so `[Usage](usage.md) - ...`
  and `Then apply the plugin - see [here](usage.md)` both work. An entry shows the heading of its page, the link text
  only for a page without headings.
- A **hub** is a page that is itself a table of contents. An entry that leads to a hub shows the entries of the hub
  under it instead of a single link. A hub is a page named `index.md`, `README.md`, `*-index.md` or `*_index.md`, or
  any page that links to at least 2 pages not listed yet and has at most 60 words besides its links. A page met a
  second time is not listed again.
- A **library** is a directory directly under the documentation directory. A Dokka module belongs to the library
  named as the module, otherwise to the one named as the nearest directory of its path: `:<family>:<module>` goes
  to `documentation/<family>/` unless `documentation/<module>/` exists.
- The API of a library goes into its **reference group**: the group whose heading starts with `Reference` or
  `Справочник`, or that lists a page of a `reference/` directory. The API goes there as the entry «API»: under the only
  page of the group, or after its pages. Without a reference group «API» is the last entry under the page of the
  library. In «API» a library of one module lists its packages and their types; a library of several modules lists
  the modules first: `API -> module -> package -> types`. Modules of no library, and of a library whose page the
  sidebar does not list, go into the top-level entry «API».
- Pages that no table of contents lists are in «Other pages», titled by their headings, the HTML pages by their
  `<title>`. A table of contents lists an HTML page as any page: `[Title](explanation/flow.html)`.

## Links to the API

An inline code of a page links to the API when it names a declaration: a type by its simple name (`Greeter`), by its
name inside the package (`Result.Failure`) or by its qualified name; a member by `Type.member` or by its qualified
name, never by its bare name. A trailing `()` and `::` instead of `.` are accepted.

1. On a page of a library the declarations of the modules of the library win.
2. A name with several pages there is ambiguous and stays plain.
3. Otherwise, or on a page outside any library, a name is linked only if it has one page in the whole reference.
4. Names of other libraries, such as `String` or `Context`, have no page in the reference and stay plain; use a
   Markdown link to their documentation.

Code inside a link or a heading and code blocks stay as they are.

## Heading anchors

Both rules lowercase the heading text, keep letters, digits, `_`, `-` and spaces, turn each space into `-` and number
repeated ids with `-1`, `-2`. `AnchorStyle.GITLAB` also turns a run of `-` into one and gives an id of digits only the
`anchor-` prefix, as GitLab does: `A - B` is `a-b` on GitLab and `a---b` on GitHub.

## Diagrams

The `.puml` is the source; the site draws it and never shows the committed SVG. A page shows the source under each
diagram, with its includes.

- The layout engine is Smetana, built into PlantUML, so a diagram is the same on every machine. Smetana ignores
  `skinparam linetype ortho`: a repository whose class diagrams need right-angled lines sets
  `layout.set(DiagramLayout.DOT)` and needs Graphviz on every machine, found by `GRAPHVIZ_DOT` or on `PATH`.
  `!pragma layout elk` in a diagram chooses ELK, also built in, whatever the setting.
- `docsDiagrams` writes the SVG of the site next to each `.puml`, for the Git hosting that shows only committed
  pictures.
- `docsCheck` compares every SVG that lies next to its `.puml` with the one drawn from the source. Positions are not
  compared, since they depend on the engine and the fonts: the texts, the element names, the number of links and the
  colours are. A repository that commits no SVG has nothing to compare; an SVG without a `.puml` of its name is not a
  diagram.

## HTML pages

Any `.html` of the documentation is a page of the site, such as an interactive explanation. Put it in the directory
of its kind, such as `explanation/` or `how-to/`, and list it in a table of contents as any page. The site shows it
inside a site page at the same path, in a frame that takes the height of the page; its own styles and scripts stay
isolated. A page with a background of its own keeps it; one without lies on the background of the site and follows
its light or dark theme. Its links open in the whole window, whatever the quotes of their `href`.
The title in the sidebar and the search is its `<title>`. «Open in a separate tab» opens it alone.

## Markdown linter

The rules come only from `.rumdl.toml`: the one of the nearest directory of a page, up to the repository root. A
nested one replaces the outer one for its pages, or inherits it with `extends = "../.rumdl.toml"`. Other files, such
as `.markdownlint.json` or `pyproject.toml`, are not read. Without any `.rumdl.toml` the defaults of rumdl apply;
`disable = ["all"]` in `[global]` turns every rule off. The rules and their options are in the
[rumdl rules reference](https://rumdl.dev/rules/).

The plugin turns off MD051 (link fragments) and MD057 (relative links) over the configuration: it checks them
itself, with the anchor rules of the repository, the tables of contents and the diagrams, while rumdl inside the
plugin does not see the files.

## Checks

| Problem           | Reported when                                                                         |
| ----------------- | ------------------------------------------------------------------------------------- |
| Link to a file    | The target of a relative link or image, `href` and `src` of HTML included, is missing |
| Table of contents | An `index.md` links to a missing page                                                 |
| Anchor            | `#anchor` is not a heading of the target page by the rules of `anchorStyle`           |
| PlantUML error    | PlantUML cannot draw a `.puml`; the line of the source is given                       |
| Stale diagram     | A committed SVG next to its `.puml` shows other texts, elements, links or colours     |
| Markdown lint     | rumdl reports a rule, such as `[MD009] 3 trailing spaces found`                       |

A page that no other page links to, except README and `index.md`, is a warning and does not fail the check.

## Limitations

- An emoji in a heading (`:ok:`) is dropped from the anchor by GitLab; the plugin keeps `ok`.
- Links to lines of files (`file.kt#L10`) and anchors inside HTML pages are not checked.
- A change of a file that a diagram includes from outside the documentation directory does not redraw the diagram.
- The API reference is tested with Dokka `2.3.0`. It has the modules that the root joins with `dokka(project(...))`;
  a module that applies Dokka without being joined is not in it.
- An HTML page whose height follows the window (`height: 100vh`) grows with its frame up to 20000 px.

## Troubleshooting

### anchor ... is not a heading

```text
anchor `#release-notes---v2` of `#release-notes---v2` is not a heading of docs/changes.md, did you mean `#release-notes-v2`?
```

Cause: the anchor follows other rules than `anchorStyle`, such as GitHub rules on a GitLab site. Fix: use the
suggested anchor, or set `anchorStyle` to the rules of the hosting of the repository.

### out of date with its PlantUML source

Cause: the `.puml` or an included file changed, and the committed SVG was drawn from the old source. Fix: run
`./gradlew docsDiagrams` and commit the changed SVG.

### PlantUML cannot render the diagram

```text
documentation/lib/diagrams/context.puml:12: PlantUML cannot render the diagram, see the SVG for the error
```

Cause: a syntax error in that line or in an included file. Fix: open `build/repo-docs-diagrams/svg/<dir>/<name>.svg`,
PlantUML draws the error into it.

### PlantUML is not run: a diagram may read only the repository

```text
PlantUML is not run: a diagram may read only the repository and the PlantUML standard library
  documentation/lib/diagrams/context.puml:3: `~/.gradle/gradle.properties` is outside the repository
```

Cause: an `!include`, `!import`, `%load_json` or `!theme ... from` of a diagram reads an address, a file outside the
repository or a path computed by the preprocessor. Fix: keep the included files in the repository and name them by a
plain relative path.

### docsBuild would delete them

Cause: `outputDirectory` of the `docs` block contains the repository or a source of the site, and `docsBuild` clears
it before a build. Fix: choose a directory under `build/`, the default is `build/repo-docs`.

### rumdl cannot use the configuration

Cause: a `.rumdl.toml` that rumdl cannot read, such as broken TOML or an `extends` to a missing file; the message
names the file. Fix: correct the file.
