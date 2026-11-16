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

package io.github.wolfandrei.repodocs.site

import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * A declaration of the API reference.
 *
 * @property module name of its Dokka module.
 * @property qualifiedName the fully qualified name, such as `com.example.Greeter.greet`.
 * @property path its page relative to the site root, `api/<module>/...`.
 * @property type whether it is a type (class, interface, object, enum, type alias) rather than a member.
 * @property packageName its package, such as `com.example`.
 */
internal data class ApiSymbol(
    val module: String,
    val qualifiedName: String,
    val path: String,
    val type: Boolean,
    val packageName: String,
)

/**
 * Names of the API reference for the links from the Markdown pages. A type is found by its simple name
 * (`Greeter`), by its name inside the package (`Greeting.Formal`) and by its qualified name; a member by `Type.member`
 * and by its qualified name, never by its bare name, which is too common a word.
 *
 * A name is resolved for a page of a library: the declarations of the Dokka modules of the library win; a name with
 * several pages there is ambiguous and is not linked. Outside the modules of the library, or for a page outside any
 * library, a name is linked only if it has a single page in the whole reference.
 */
internal class ApiSymbols(symbols: List<ApiSymbol>) {
    private val byName: Map<String, List<ApiSymbol>> = symbols
        .flatMap { symbol -> names(symbol).map { it to symbol } }
        .groupBy({ it.first }, { it.second })

    /**
     * Page of the declaration that the code text [text] names, for a page of the library of the Dokka modules
     * [modules]; `null` if none or several.
     */
    fun resolve(text: String, modules: Set<String> = emptySet()): ApiSymbol? {
        val name = normalize(text) ?: return null
        val candidates = byName[name].orEmpty()
        val own = candidates.filter { it.module in modules }.distinctBy { it.path }
        if (own.isNotEmpty()) return own.singleOrNull()
        return candidates.distinctBy { it.path }.singleOrNull()
    }

    /** Page of the type named [name], such as the name of a `.kt` file, for a page of the library of [modules]. */
    fun resolveType(name: String, modules: Set<String> = emptySet()): ApiSymbol? =
        resolve(name, modules)?.takeIf { it.type }

    private fun names(symbol: ApiSymbol): List<String> {
        val inPackage = symbol.qualifiedName.removePrefix("${symbol.packageName}.").split('.')
        val names = mutableListOf(symbol.qualifiedName)
        if (symbol.type) {
            for (start in inPackage.indices) names += inPackage.drop(start).joinToString(".")
        } else if (inPackage.size >= 2) {
            names += inPackage.takeLast(2).joinToString(".")
            names += inPackage.joinToString(".")
        }
        return names.distinct()
    }

    companion object {
        private val NAME = Regex("[A-Za-z_][A-Za-z0-9_]*(?:\\.[A-Za-z_][A-Za-z0-9_]*)*")
        private val json = Json { ignoreUnknownKeys = true }

        /** [text] of an inline code as a name: `greet()` and `Type::member` are accepted, other code is not. */
        fun normalize(text: String): String? {
            val name = text.trim().removeSuffix("()").replace("::", ".")
            return name.takeIf { NAME.matches(it) }
        }

        /**
         * The declarations of the Dokka search index `scripts/pages.json` of [apiReference]; [modules] give the
         * directories of the modules and their names.
         */
        fun read(apiReference: File, modules: List<ApiModuleEntry>): ApiSymbols {
            val file = apiReference.resolve("scripts/pages.json")
            if (!file.isFile) return ApiSymbols(emptyList())
            val symbols = json.parseToJsonElement(file.readText()).jsonArray.mapNotNull { element ->
                val entry = element.jsonObject
                val location = entry["location"]?.jsonPrimitive?.content ?: return@mapNotNull null
                val qualified = entry["description"]?.jsonPrimitive?.content ?: return@mapNotNull null
                val module = modules.firstOrNull { location.startsWith(it.directory + "/") } ?: return@mapNotNull null
                ApiSymbol(
                    module = module.name,
                    qualifiedName = qualified,
                    path = "${SiteNavigation.API_DIR}/${location.substringBefore('#')}",
                    type = location.endsWith("/index.html"),
                    packageName = location.removePrefix(module.directory + "/").substringBefore('/'),
                )
            }
            return ApiSymbols(symbols)
        }
    }
}
