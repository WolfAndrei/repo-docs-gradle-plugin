# Releasing

The plugin goes to the [Gradle Plugin Portal](https://plugins.gradle.org) with `com.gradle.plugin-publish`: the jar,
its sources and Javadoc and the plugin marker `io.github.wolfandrei.repo-docs`. A project then needs only
`id("io.github.wolfandrei.repo-docs") version "<version>"`. Once:

1. Sign in to the Plugin Portal with the GitHub account `wolfandrei`.
2. Take the key and the secret from the API Keys tab of the profile and put them into `~/.gradle/gradle.properties`,
   never into the repository, or let `./gradlew login` write them there:

   ```properties
   gradle.publish.key=<key>
   gradle.publish.secret=<secret>
   ```

Each release:

1. Set `version` in `gradle.properties` to the release version, without `-SNAPSHOT`, by semantic versioning; the
   Portal does not accept snapshots.
2. Check and publish:

   ```shell
   ./gradlew build --dependency-verification strict
   ./gradlew publishPlugins --validate-only
   ./gradlew publishPlugins
   ```

   The Portal reviews the first version of a plugin by hand, and checks that its website and repository are on
   `github.com/wolfandrei` (`gradlePlugin` in `build.gradle.kts`); later versions are published at once.
3. Tag the commit `v<version>` and set `version` to the next `-SNAPSHOT`.
