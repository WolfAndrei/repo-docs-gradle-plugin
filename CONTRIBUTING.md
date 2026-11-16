# Contributing

- Build and test: `./gradlew test`. The build of the plugin downloads what it bundles from Maven Central and the npm
  registry (`settings.gradle.kts`); the tests run Gradle builds with TestKit, those with a Kotlin module also need
  Maven Central and the Gradle Plugin Portal.
- Try a change on a real repository: `./gradlew publishToMavenLocal`, then use version `0.1.0-SNAPSHOT` with
  `mavenLocal()` in the `pluginManagement` repositories of that repository.
- Every source file starts with the Apache License 2.0 header of the other files.
- Code comments and KDoc are in English; describe what the code does and why, not the history of the change.
- A change of behaviour comes with a test, with the KDoc of the setting it touches and, if it changes a convention,
  with [docs/conventions.md](docs/conventions.md).
- Commit messages: a short imperative subject line, then the reason of the change.
- Releases: see [RELEASING.md](RELEASING.md).

## Where things are

| What                                    | Where, under `src/main/kotlin/io/github/wolfandrei/repodocs/`              |
|-----------------------------------------|----------------------------------------------------------------------------|
| Plugin, tasks, the `docs` block         | `DocumentationSitePlugin.kt`, `DocumentationSiteExtension.kt`, `tasks/`    |
| Bundled PlantUML                        | `Bundled.kt`; packed by `bundleFiles` in `build.gradle.kts`                |
| Markdown, links, anchors                | `markdown/`                                                                |
| Template, sidebar, search, API pages    | `site/`; styles and script in `src/main/resources/.../site.css`, `site.js` |
| Checks, SVG fingerprints                | `check/`, `plantuml/`                                                      |
| API reference: Dokka output             | `DokkaApiReference.kt`, `site/DokkaReference.kt`, `site/ApiSymbols.kt`     |
| rumdl: WebAssembly glue, configurations | `lint/`; compiled by `compileRumdl` from `src/wasmCompiler/`               |

rumdl is the npm package `rumdl-wasm`, compiled to JVM classes by the build-time compiler of Chicory. `RumdlWasm.kt`
is the JVM side of its wasm-bindgen glue `rumdl_lib.js`: a new version of rumdl may change the imports, and the check
in `RumdlWasm` fails until the glue follows them.
