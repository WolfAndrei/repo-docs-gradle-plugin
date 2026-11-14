# docs-site documentation

> The Gradle plugin `io.github.wolfandrei.docs-site` builds a static site from the documentation of a repository:
> Markdown pages, PlantUML diagrams, interactive pages, the Dokka API reference and the search. It is applied to the
> root project.

## How-to

- [How to set up, build, serve and check the site](how-to/usage.md) - repositories, plugin, layout of the
  documentation, build, server with live reload, checks, Markdown linter, troubleshooting

## Reference

- [Configuration and tasks](reference/configuration.md) - the `documentationSite` block, tasks, properties, files,
  check rules, Markdown linter, dependencies

## Explanation

- [How the plugin works](explanation/architecture.md) - data flow, anchors, sidebar, diagrams and their viewer, API
  pages and links to them, interactive pages, highlighting, search, server, Markdown linter, limitations
