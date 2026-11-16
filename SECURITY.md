# Security

## Reporting a vulnerability

Report a vulnerability privately through the GitHub private vulnerability reporting of this repository
(Security -> Report a vulnerability), not in a public issue. Include the version of the plugin, the steps to
reproduce and what an attacker gains.

## What the plugin trusts

The plugin builds a site from the documentation of the repository it is applied to, on the machine of the build.
The Markdown pages, the diagrams and the HTML pages are trusted like the code of the repository: their HTML
and scripts go to the site as they are, and the site is meant to be opened locally.

What the plugin protects against:

- **A diagram that reads the machine.** PlantUML lets a diagram include any file, such as `~/.gradle/gradle.properties`,
  and its text would end in the SVG that `docsDiagrams` writes for a commit. Before PlantUML runs, the plugin
  accepts only the standard library of PlantUML and files inside the repository, and fails on an address, a path
  outside the repository or a computed path. PlantUML runs in a separate JVM.
- **Downloads at the build of a project.** PlantUML and the rumdl linter are part of the plugin;
  a project downloads nothing for them. rumdl is WebAssembly run by Chicory without any access to files or the
  network: the plugin passes it the text of the pages and of the configuration.
- **Tampered dependencies of the plugin build.** `gradle/verification-metadata.xml` pins the SHA-256 of every
  artifact of the build, the bundled npm package `rumdl-wasm` included; its hash matches the integrity of the npm
  registry.
  The Gradle distribution of the wrapper is pinned by `distributionSha256Sum`.
- **Deleting sources by mistake.** `docsBuild` clears its output directory, so it fails when that directory holds the
  repository or a source of the site.

Out of scope: Dokka, which the project declares itself and resolves from its own repositories, and
Graphviz `dot`, run from `GRAPHVIZ_DOT` or `PATH` when the layout asks for it.
