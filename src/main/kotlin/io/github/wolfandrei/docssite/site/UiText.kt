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

package io.github.wolfandrei.docssite.site

/**
 * Strings of the site interface in the language of the site: the header, the search, the sidebar groups, the sections
 * of the API pages, the kinds of declarations and the labels of the diagram viewer.
 */
internal class UiText private constructor(val language: String, private val strings: Map<String, String>) {

    operator fun get(key: String): String = strings[key] ?: EN.getValue(key)

    /** Label of a declaration kind, such as «Class». */
    fun kind(kind: SymbolKind): String = get("kind." + kind.name)

    /** Title of a Dokka member table, such as `Functions`; an unknown title stays as Dokka wrote it. */
    fun section(dokkaTitle: String): String = strings["section.$dokkaTitle"] ?: dokkaTitle

    /** Title of a KDoc section, such as `Return`; an unknown title stays as Dokka wrote it. */
    fun tag(dokkaTitle: String): String = strings["tag.$dokkaTitle"] ?: dokkaTitle

    /** Titles of the sidebar groups by the lowercased heading of the table of contents, see [groupTitle]. */
    fun groupTitles(): Map<String, String> =
        strings.filterKeys { it.startsWith(GROUP) }.mapKeys { it.key.removePrefix(GROUP) }

    /** Strings of the scripts of the site. */
    fun scriptStrings(): Map<String, String> =
        strings.filterKeys { it.startsWith(SCRIPT) }.mapKeys { it.key.removePrefix(SCRIPT) }

    companion object {
        const val GROUP = "group."
        private const val SCRIPT = "js."

        private val EN = mapOf(
            "navigation" to "Navigation",
            "search" to "Search",
            "noPages" to "No pages",
            "otherPages" to "Other pages",
            "api" to "API",
            "declaration" to "Declaration",
            "overview" to "Overview",
            "topics" to "Topics",
            "breadcrumbs" to "Path",
            "platforms" to "Platforms",
            "diagramSource" to "Source",
            "diagramIncludes" to "Includes:",
            "interactiveOpen" to "Open in a separate tab",
            "kind.CLASS" to "Class",
            "kind.DATA_CLASS" to "Data class",
            "kind.OBJECT" to "Object",
            "kind.INTERFACE" to "Interface",
            "kind.ENUM" to "Enum",
            "kind.ENUM_ENTRY" to "Enum entry",
            "kind.ANNOTATION" to "Annotation",
            "kind.TYPEALIAS" to "Type alias",
            "kind.FUNCTION" to "Function",
            "kind.CONSTRUCTOR" to "Constructor",
            "kind.PROPERTY" to "Property",
            "kind.PACKAGE" to "Package",
            "kind.MODULE" to "Module",
            "tag.Return" to "Return value",
            "group.how-to" to "How-to guides",
            "group.how to" to "How-to guides",
            "group.reference" to "Reference",
            "group.explanation" to "Explanation",
            "group.tutorials" to "Tutorials",
            "group.tutorial" to "Tutorials",
            "group.adr" to "Architecture decisions",
            "group.diagrams" to "Diagrams",
            "group.interactive" to "Interactive pages",
            "js.noResults" to "No results",
            "js.headingLink" to "Link to this section",
            "js.diagram" to "Diagram: + and - zoom, 0 fits, F opens full screen",
            "js.zoomOut" to "Zoom out",
            "js.zoomIn" to "Zoom in",
            "js.fit" to "Fit",
            "js.fullScreen" to "Full screen",
            "js.close" to "Close",
            "js.closeHint" to "Close (Esc)",
        )

        private val RU = mapOf(
            "navigation" to "Навигация",
            "search" to "Поиск",
            "noPages" to "Нет страниц",
            "otherPages" to "Другие страницы",
            "api" to "API",
            "declaration" to "Объявление",
            "overview" to "Обзор",
            "topics" to "Темы",
            "breadcrumbs" to "Путь",
            "platforms" to "Платформы",
            "diagramSource" to "Исходник",
            "diagramIncludes" to "Подключает:",
            "interactiveOpen" to "Открыть отдельно",
            "kind.CLASS" to "Класс",
            "kind.DATA_CLASS" to "Класс данных",
            "kind.OBJECT" to "Объект",
            "kind.INTERFACE" to "Интерфейс",
            "kind.ENUM" to "Перечисление",
            "kind.ENUM_ENTRY" to "Элемент перечисления",
            "kind.ANNOTATION" to "Аннотация",
            "kind.TYPEALIAS" to "Псевдоним типа",
            "kind.FUNCTION" to "Функция",
            "kind.CONSTRUCTOR" to "Конструктор",
            "kind.PROPERTY" to "Свойство",
            "kind.PACKAGE" to "Пакет",
            "kind.MODULE" to "Модуль",
            "section.Packages" to "Пакеты",
            "section.Types" to "Типы",
            "section.Constructors" to "Конструкторы",
            "section.Properties" to "Свойства",
            "section.Functions" to "Функции",
            "section.Entries" to "Элементы",
            "section.Inheritors" to "Наследники",
            "section.Members" to "Члены",
            "section.Modules" to "Модули",
            "tag.Return" to "Возвращаемое значение",
            "tag.Parameters" to "Параметры",
            "tag.Throws" to "Исключения",
            "tag.See also" to "См. также",
            "tag.Since" to "Начиная с версии",
            "tag.Samples" to "Примеры",
            "tag.Inheritors" to "Наследники",
            "tag.Receiver" to "Получатель",
            "tag.Author" to "Автор",
            "tag.Deprecated" to "Устарело",
            "group.how-to" to "Инструкции",
            "group.how to" to "Инструкции",
            "group.reference" to "Справочник",
            "group.explanation" to "Устройство",
            "group.tutorials" to "Обучение",
            "group.tutorial" to "Обучение",
            "group.adr" to "Архитектурные решения",
            "group.diagrams" to "Схемы",
            "group.interactive" to "Интерактивные страницы",
            "js.noResults" to "Ничего не найдено",
            "js.headingLink" to "Ссылка на раздел",
            "js.diagram" to "Схема: + и - масштаб, 0 вписать, F на весь экран",
            "js.zoomOut" to "Уменьшить",
            "js.zoomIn" to "Увеличить",
            "js.fit" to "Вписать",
            "js.fullScreen" to "На весь экран",
            "js.close" to "Закрыть",
            "js.closeHint" to "Закрыть (Esc)",
        )

        /** Languages of the interface. */
        val LANGUAGES = setOf("en", "ru")

        fun of(language: String): UiText = when (language.lowercase()) {
            "ru" -> UiText("ru", RU)
            "en" -> UiText("en", EN)
            else -> throw IllegalArgumentException("Unknown language '$language', expected one of $LANGUAGES")
        }
    }
}
