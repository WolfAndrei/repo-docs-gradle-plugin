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

plugins {
    `kotlin-dsl`
    `maven-publish`
    kotlin("plugin.serialization") version embeddedKotlinVersion
    alias(libs.plugins.plugin.publish)
}

group = "io.github.wolfandrei"
version = "0.1.0-SNAPSHOT"

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(libs.versions.java.get()))
    withSourcesJar()
}

/** Plugins that the test fixtures apply: they join the classpath of the plugin under test, not the plugin itself. */
val testPlugins: Configuration by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
}

dependencies {
    implementation(libs.bundles.commonmark)
    implementation(libs.jsoup)
    implementation(libs.kotlinx.serialization.json)

    testPlugins(libs.kotlin.gradle.plugin)
    testPlugins(libs.dokka.gradle.plugin)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.pluginUnderTestMetadata {
    pluginClasspath.from(testPlugins)
}

tasks.test {
    useJUnitPlatform()
    maxHeapSize = "1g"
}

gradlePlugin {
    website.set("https://github.com/wolfandrei/gradle-docs-site")
    vcsUrl.set("https://github.com/wolfandrei/gradle-docs-site")
    plugins {
        create("docsSite") {
            id = "io.github.wolfandrei.docs-site"
            implementationClass = "io.github.wolfandrei.docssite.DocumentationSitePlugin"
            displayName = "Documentation site"
            description = "A static site of the repository documentation: Markdown pages, PlantUML diagrams, " +
                "interactive pages, the Dokka API reference merged into one navigation, search and live reload."
            tags.set(listOf("documentation", "markdown", "plantuml", "dokka", "static-site"))
        }
    }
}
