/*
 * Copyright 2026 Andrei Volkau
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.security.MessageDigest

plugins {
    `kotlin-dsl`
    `maven-publish`
    kotlin("plugin.serialization") version embeddedKotlinVersion
    alias(libs.plugins.plugin.publish)
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(providers.gradleProperty("JAVA_VERSION").get()))
    withSourcesJar()
}

val testPlugins: Configuration = configurations.create("testPlugins") {
    isCanBeConsumed = false
    isCanBeResolved = true
}

/** PlantUML of the build of the diagrams: files inside the plugin, not its dependencies (they contain Guava). */
val bundledPlantUml: Configuration = configurations.create("bundledPlantUml") {
    isCanBeConsumed = false
    isTransitive = false
}

/** The npm package of the WebAssembly build of rumdl, compiled into the plugin. */
val bundledNpm: Configuration = configurations.create("bundledNpm") {
    isCanBeConsumed = false
    isTransitive = false
}

val wasmCompiler: SourceSet = sourceSets.create("wasmCompiler")

dependencies {
    implementation(libs.bundles.commonmark)
    implementation(libs.jsoup)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.chicory.runtime)
    // The API of Dokka only: the project chooses its version by declaring Dokka in its root.
    compileOnly(libs.dokka.gradle.plugin)

    bundledPlantUml(libs.plantuml.epl)
    bundledPlantUml(libs.xbase.lib)
    bundledNpm(variantOf(libs.npm.rumdl.wasm) { artifactType("tgz") })
    "wasmCompilerImplementation"(libs.chicory.build.time.compiler)

    testPlugins(libs.kotlin.gradle.plugin)
    testPlugins(libs.dokka.gradle.plugin)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

val bundledDir = layout.buildDirectory.dir("generated/bundled")
val bundledPackage = "io/github/wolfandrei/repodocs/bundled"

/** The files the plugin carries, with `bundled.properties`: their names and a hash for the cache directory. */
val bundleFiles = tasks.register("bundleFiles") {
    val plantUml = bundledPlantUml
    val npm = bundledNpm
    inputs.files(plantUml, npm)
    outputs.dir(bundledDir)
    doLast {
        val target = bundledDir.get().asFile.resolve(bundledPackage).apply {
            deleteRecursively()
            mkdirs()
        }
        plantUml.files.forEach { it.copyTo(target.resolve(it.name)) }
        // The MIT License of rumdl goes with its compiled copy.
        copy {
            from(tarTree(npm.files.single { it.name.startsWith("rumdl-wasm-") }))
            include("package/LICENSE")
            eachFile { path = "rumdl-LICENSE" }
            includeEmptyDirs = false
            into(target)
        }
        val digest = MessageDigest.getInstance("SHA-256")
        plantUml.files.sortedBy { it.name }.forEach { digest.update(it.readBytes()) }
        target.resolve("bundled.properties").writeText(
            "plantuml.classpath=" + plantUml.files.map { it.name }.sorted().joinToString(",") + "\n" +
                "plantuml.sha256=" + digest.digest().joinToString("") { "%02x".format(it) } + "\n" +
                "plantuml.version=" + libs.versions.plantuml.get() + "\n" +
                "rumdl.version=" + libs.versions.rumdl.get() + "\n"
        )
    }
}

val rumdlWasm = tasks.register<Sync>("unpackRumdlWasm") {
    from(bundledNpm.elements.map { files -> files.filter { it.asFile.name.startsWith("rumdl-wasm-") }.map { tarTree(it) } })
    include("package/rumdl_lib_bg.wasm")
    eachFile { path = name }
    includeEmptyDirs = false
    into(layout.buildDirectory.dir("rumdl-wasm"))
}

val rumdlClasses = layout.buildDirectory.dir("generated/rumdl/classes")
val rumdlSources = layout.buildDirectory.dir("generated/rumdl/sources")
val rumdlResources = layout.buildDirectory.dir("generated/rumdl/resources")

/** rumdl compiled from WebAssembly to JVM classes, so that it neither downloads nor compiles at a build. */
val compileRumdl = tasks.register<JavaExec>("compileRumdl") {
    val wasm = rumdlWasm.map { it.destinationDir.resolve("rumdl_lib_bg.wasm") }
    inputs.file(wasm)
    outputs.dirs(rumdlClasses, rumdlSources, rumdlResources)
    classpath = wasmCompiler.runtimeClasspath
    mainClass.set("CompileWasm")
    maxHeapSize = "2g"
    doFirst { listOf(rumdlClasses, rumdlSources, rumdlResources).forEach { project.delete(it) } }
    argumentProviders += CommandLineArgumentProvider {
        listOf(
            wasm.get().path,
            "io.github.wolfandrei.repodocs.lint.wasm.RumdlModule",
            rumdlClasses.get().asFile.path,
            rumdlSources.get().asFile.path,
            rumdlResources.get().asFile.path,
        )
    }
}

sourceSets.main {
    java.srcDir(compileRumdl.map { rumdlSources.get() })
    resources.srcDir(compileRumdl.map { rumdlResources.get() })
    resources.srcDir(bundleFiles.map { bundledDir.get() })
    output.dir(mapOf("builtBy" to compileRumdl), rumdlClasses)
}

dependencies {
    compileOnly(files(rumdlClasses).builtBy(compileRumdl))
}

// The licenses of the plugin and of what it bundles go with every artifact.
tasks.withType<Jar>().configureEach { from(files("LICENSE", "NOTICE")) { into("META-INF") } }

// The generated wrapper of the compiled rumdl has no documentation of its own.
tasks.javadoc { exclude("io/github/wolfandrei/repodocs/lint/wasm/**") }

tasks.pluginUnderTestMetadata {
    pluginClasspath.from(testPlugins)
}

tasks.test {
    useJUnitPlatform()
    maxHeapSize = "1g"
}

gradlePlugin {
    website.set("https://github.com/wolfandrei/repo-docs-gradle-plugin")
    vcsUrl.set("https://github.com/wolfandrei/repo-docs-gradle-plugin")
    plugins {
        create("repoDocs") {
            id = "io.github.wolfandrei.repo-docs"
            implementationClass = "io.github.wolfandrei.repodocs.DocumentationSitePlugin"
            displayName = "Repository documentation"
            description = "A static site of the repository documentation: Markdown pages, PlantUML diagrams, " +
                "interactive pages, the Dokka API reference merged into one navigation, search and live reload."
            tags.set(listOf("documentation", "markdown", "plantuml", "dokka", "static-site"))
        }
    }
}
