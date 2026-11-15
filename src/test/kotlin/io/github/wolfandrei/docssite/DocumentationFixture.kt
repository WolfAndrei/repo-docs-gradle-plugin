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

package io.github.wolfandrei.docssite

import java.io.File
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner

/**
 * A repository with documentation of a multi-library repository: README, the table of contents, a library index with
 * Russian headings, a C4 diagram with an include, an interactive page; optionally a Kotlin Multiplatform module. The
 * Markdown linter is off unless [write] gets its settings: it needs the rumdl binary.
 */
internal class DocumentationFixture(val projectDir: File) {

    fun write(
        module: Boolean = false,
        siteSettings: String = "apiReference { enabled.set(false) }\n    language.set(\"ru\")",
        markdownLint: String = "enabled.set(false)",
    ) {
        val include = if (module) "include(\":lib\", \":plain\")" else ""
        file("settings.gradle.kts").writeText(
            """
            |rootProject.name = "fixture"
            |$include
            |
            |dependencyResolutionManagement {
            |    @Suppress("UnstableApiUsage")
            |    repositories {
            |        mavenCentral()
            |        gradlePluginPortal()
            |        ivy {
            |            url = uri("https://registry.npmjs.org")
            |            patternLayout { artifact("@plantuml/[module]/-/[module]-[revision].[ext]") }
            |            metadataSources { artifact() }
            |            content { includeGroup("plantuml-npm") }
            |        }
            |    }
            |}
            """.trimMargin()
        )
        file("build.gradle.kts").writeText(
            """
            |plugins {
            |    base
            |    id("io.github.wolfandrei.docs-site")
            |}
            |
            |docs {
            |    checks { markdownLint { $markdownLint } }
            |}
            |
            |docs {
            |    $siteSettings
            |}
            """.trimMargin()
        )
        if (module) {
            file("lib/build.gradle.kts").writeText(
                """
                |plugins {
                |    kotlin("multiplatform")
                |    id("org.jetbrains.dokka")
                |}
                |
                |kotlin {
                |    jvm()
                |}
                """.trimMargin()
            )
            file("plain/build.gradle.kts").writeText(
                """
                |plugins {
                |    kotlin("jvm")
                |}
                """.trimMargin()
            )
            file("plain/src/main/kotlin/com/example/Plain.kt").writeText("package com.example\n\nclass Plain\n")
            file("lib/src/commonMain/kotlin/com/example/Greeter.kt").writeText(
                """
                |package com.example
                |
                |/** Хранилище секретов. */
                |interface Greeter {
                |    /** Значение по ключу [key]. */
                |    fun get(key: String): String?
                |}
                """.trimMargin()
            )
        }

        // The fixture lies inside this repository: `root = true` keeps its .editorconfig out.
        file(".editorconfig").writeText("root = true\n\n[*]\nindent_style = space\n\n[*.md]\nmax_line_length = 120\n")
        file("README.md").writeText(
            """
            |# Fixture
            |
            |Документация - в [оглавлении](documentation/index.md).
            |
            """.trimMargin()
        )
        file("documentation/index.md").writeText(
            """
            |[⇦&nbsp;К&nbsp;README](../README.md)
            |
            |# Fixture: документация
            |
            |> Оглавление библиотек.
            |
            |## Библиотеки
            |
            |- [lib](lib/index.md) - библиотека.
            |
            """.trimMargin()
        )
        file("documentation/lib/index.md").writeText(
            """
            |[⇦&nbsp;К&nbsp;оглавлению](../index.md)
            |
            |# lib: документация
            |
            |## How-to
            |
            |- [Как подключить lib](how-to/usage.md) - зависимость и настройка.
            |
            |## Интерактивные страницы
            |
            |- [modes.html](interactive/modes.html) - режимы шифрования.
            |
            """.trimMargin()
        )
        file("documentation/lib/how-to/usage.md").writeText(
            """
            |[⇦&nbsp;К&nbsp;оглавлению](../index.md)
            |
            |# Как подключить lib
            |
            |## Установка и настройка
            |
            |Формат описан в [разделе](#формат-хранения-v2), схема - в [context.svg](../diagrams/context.svg).
            |
            |## Формат хранения (v2)
            |
            |![Контекст](../diagrams/context.svg)
            |
            |![Последовательность](../diagrams/put.svg)
            |
            |## Установка и настройка
            |
            |Повтор заголовка: [первый](#установка-и-настройка), [второй](#установка-и-настройка-1).
            |
            |- [x] выполнено
            |- [ ] не выполнено
            |
            || Ключ | Значение |
            ||------|----------|
            || `a`  | 1        |
            |
            |```kotlin
            |val veryLongLineInCodeThatIsLongerThanTheLimitOfTheLineLengthCheckButIsIgnoredBecauseItIsCode = "—"
            |```
            |
            """.trimMargin()
        )
        file("documentation/lib/diagrams/defines.iuml").writeText(
            """
            |skinparam backgroundColor #FFFFFF
            |
            """.trimMargin()
        )
        file("documentation/lib/diagrams/context.puml").writeText(
            """
            |@startuml
            |!include <C4/C4_Context>
            |!include defines.iuml
            |
            |title lib: контекст
            |
            |System_Ext(app, "Приложение", "Android, iOS")
            |System(lib, "lib", "Хранит секреты")
            |Rel(app, lib, "хранит токены")
            |@enduml
            |
            """.trimMargin()
        )
        file("documentation/lib/diagrams/put.puml").writeText(
            """
            |@startuml
            |!include_once defines.iuml
            |participant "Приложение" as app
            |participant "lib" as lib
            |app -> lib : put(key, value)
            |@enduml
            |
            """.trimMargin()
        )
        file("documentation/lib/interactive/modes.html").writeText(
            """
            |<!DOCTYPE html>
            |<html lang="ru"><head><meta charset="utf-8"><title>Режимы</title></head>
            |<body><a href="../how-to/usage.md#формат-хранения-v2">Формат</a></body></html>
            |
            """.trimMargin()
        )
    }

    fun file(path: String): File = projectDir.resolve(path).apply { parentFile.mkdirs() }

    fun site(path: String): File = projectDir.resolve("build/docs-site/$path")

    /** Renders the diagrams and commits their SVG next to the sources, as the authors do. */
    fun commitRenderedDiagrams() {
        run("docsDiagrams")
    }

    fun run(vararg arguments: String, expectFailure: Boolean = false): BuildResult {
        val runner = GradleRunner.create()
            .withPluginClasspath()
            .withProjectDir(projectDir)
            .withArguments(*arguments, "--stacktrace")

        return if (expectFailure) runner.buildAndFail() else runner.build()
    }
}
