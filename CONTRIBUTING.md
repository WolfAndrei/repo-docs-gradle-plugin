# Contributing

- Build and test: `./gradlew test`. The tests run Gradle builds with TestKit and need Maven Central, the Gradle
  Plugin Portal and the npm registry.
- Try a change on a real repository: `./gradlew publishToMavenLocal`, then use version `0.1.0-SNAPSHOT` with
  `mavenLocal()` in the `pluginManagement` repositories of that repository.
- Every source file starts with the Apache License 2.0 header of the other files.
- Code comments and KDoc are in English; describe what the code does and why, not the history of the change.
- A change of behaviour comes with a test and with an update of `docs/`: each fact is written in one place, the
  other pages link to it.
- Commit messages: a short imperative subject line, then the reason of the change.
