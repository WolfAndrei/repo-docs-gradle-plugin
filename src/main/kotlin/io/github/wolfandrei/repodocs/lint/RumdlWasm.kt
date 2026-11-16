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

package io.github.wolfandrei.repodocs.lint

import com.dylibso.chicory.runtime.HostFunction
import com.dylibso.chicory.runtime.ImportFunction
import com.dylibso.chicory.runtime.ImportValues
import com.dylibso.chicory.runtime.Instance
import com.dylibso.chicory.runtime.Memory
import com.dylibso.chicory.wasm.types.FunctionImport
import io.github.wolfandrei.repodocs.lint.wasm.RumdlModule
import java.math.BigInteger

/**
 * rumdl, the WebAssembly build of the npm package `rumdl-wasm` compiled to JVM classes at the build of the plugin, run
 * by Chicory in the JVM of the build.
 *
 * The module is built with wasm-bindgen and expects the JavaScript glue `rumdl_lib.js` of the package: its imports
 * work on JavaScript values that the module holds by index in a heap. This class is that glue on the JVM. A
 * JavaScript object is a [Map] with [String] keys, an array a [List], a number a [Double], a bigint a [BigInteger];
 * `undefined` is [Undefined] and `null` is `null`.
 *
 * An instance is not thread-safe.
 */
internal class RumdlWasm {
    private object Undefined

    private object IteratorSymbol

    private class JsError(val message: String) {
        override fun toString() = message
    }

    private class JsFunction(val call: (Any?) -> Any?)

    private class JsIterator(val items: Iterator<Any?>) {
        fun next(): IterResult = if (items.hasNext()) IterResult(false, items.next()) else IterResult(true, Undefined)
    }

    private class IterResult(val done: Boolean, val value: Any?)

    /** The heap of `rumdl_lib.js`: 1024 reserved slots, then `undefined`, `null`, `true`, `false`. */
    private val heap = ArrayList<Any?>().apply {
        repeat(RESERVED) { add(Undefined) }
        add(Undefined)
        add(null)
        add(true)
        add(false)
    }
    private var heapNext = heap.size

    private val instance: Instance = run {
        val module = RumdlModule.load()
        val names = module.importSection().stream().map { it.name() }.toList()
        // Two imports share a name and a signature and differ only by their hash, which follows the glue code.
        check(names.any { it.endsWith(NEXT_CALL) } && names.any { it.endsWith(GET_INDEX) }) {
            "The imports of rumdl-wasm changed: update RumdlWasm to its rumdl_lib.js"
        }
        val imports: List<ImportFunction> = module.importSection().stream().map { import ->
            val type = module.typeSection().getType((import as FunctionImport).typeIndex())
            HostFunction(import.module(), import.name(), type) { _, args -> host(import.name(), args) }
        }.toList()
        Instance.builder(module)
            .withImportValues(ImportValues.builder().withFunctions(imports).build())
            .withMachineFactory(RumdlModule::create)
            .build()
    }

    private val memory: Memory get() = instance.memory()

    init {
        call("__wbindgen_start")
    }

    /** The version of rumdl. */
    fun version(): String = stringResult("get_version")

    /** A linter with the options of `new Linter(options)`: the `[global]` keys of `.rumdl.toml` and rule tables. */
    fun linter(options: Map<String, Any?>): Linter {
        val result = withReturnArea("linter_new", add(options).toLong())
        if (result[2] != 0) throw RumdlException(take(result[1]).toString())
        return Linter(result[0])
    }

    /**
     * The config chain of [root] among [files] (path to content, `null` for a missing file), as
     * `resolve_config_chain` reports it: `{"status": "need-file", "path": ...}`, `complete` or `error`.
     */
    fun resolveConfigChain(root: String, files: Map<String, String?>): String {
        val result = withReturnArea("resolve_config_chain", add(mapOf("root" to root, "files" to files)).toLong())
        if (result[3] != 0) throw RumdlException(take(result[2]).toString())
        return takeString(result[0], result[1])
    }

    /** A linter of the complete config chain of [root] among [files], see [resolveConfigChain]. */
    fun linter(root: String, files: Map<String, String?>): Linter {
        val result = withReturnArea("linter_from_config_files", add(mapOf("root" to root, "files" to files)).toLong())
        if (result[2] != 0) throw RumdlException(take(result[1]).toString())
        return Linter(result[0])
    }

    /** A linter of the module, by its pointer in the memory of the module. */
    inner class Linter(private val pointer: Int) {
        /** The warnings of rumdl for [content] as a JSON array; [path] selects `exclude` and `per-file-ignores`. */
        fun check(content: String, path: String): String = text("linter_check", content, path)

        /** [content] with all the fixes of rumdl applied. */
        fun fix(content: String, path: String): String = text("linter_fix", content, path)

        /** The warnings of the configuration as a JSON array of strings, such as unknown rule options. */
        fun configWarnings(): String = stringResult("linter_get_config_warnings", pointer.toLong())

        private fun text(export: String, content: String, path: String): String {
            val (contentPointer, contentLength) = pass(content)
            val (pathPointer, pathLength) = pass(path)
            return stringResult(
                export,
                pointer.toLong(),
                contentPointer.toLong(),
                contentLength.toLong(),
                pathPointer.toLong(),
                pathLength.toLong(),
            )
        }
    }

    private fun call(name: String, vararg args: Long): LongArray = instance.export(name).apply(*args) ?: LongArray(0)

    /** Calls [export] with a return area of four i32 on the shadow stack of the module, as the glue does. */
    private fun withReturnArea(export: String, vararg args: Long): IntArray {
        val area = call("__wbindgen_add_to_stack_pointer", -RETURN_AREA.toLong())[0]
        try {
            call(export, area, *args)
            return IntArray(4) { memory.readInt(area.toInt() + 4 * it) }
        } finally {
            call("__wbindgen_add_to_stack_pointer", RETURN_AREA.toLong())
        }
    }

    private fun stringResult(export: String, vararg args: Long): String {
        val (pointer, length) = withReturnArea(export, *args)
        return takeString(pointer, length)
    }

    /** A string the module returned; the module memory of it is freed. */
    private fun takeString(pointer: Int, length: Int): String =
        readString(pointer.toLong(), length.toLong()).also {
            call("__wbindgen_export4", pointer.toLong(), length.toLong(), 1)
        }

    /** [text] in the module memory, allocated by the module: pointer and length in bytes. */
    private fun pass(text: String): Pair<Int, Int> {
        val bytes = text.toByteArray(Charsets.UTF_8)
        val pointer = call("__wbindgen_export", bytes.size.toLong(), 1)[0].toInt()
        memory.write(pointer, bytes)
        return pointer to bytes.size
    }

    private fun readString(pointer: Long, length: Long): String =
        String(memory.readBytes(pointer.toInt(), length.toInt()), Charsets.UTF_8)

    /** Writes the pointer and the length of [text] (`0, 0` for `null`) at [area], as `passStringToWasm0` does. */
    private fun writeString(area: Long, text: String?) {
        val (pointer, length) = if (text == null) 0 to 0 else pass(text)
        memory.writeI32(area.toInt() + 4, length)
        memory.writeI32(area.toInt(), pointer)
    }

    private fun add(value: Any?): Int {
        if (heapNext == heap.size) heap.add(heap.size + 1)
        val index = heapNext
        heapNext = heap[index] as Int
        heap[index] = value
        return index
    }

    private fun get(index: Long): Any? = heap[index.toInt()]

    private fun take(index: Int): Any? {
        val value = heap[index]
        if (index >= FIRST_FREE) {
            heap[index] = heapNext
            heapNext = index
        }
        return value
    }

    private fun bool(value: Boolean) = longArrayOf(if (value) 1 else 0)

    private fun ref(value: Any?) = longArrayOf(add(value).toLong())

    private val none = LongArray(0)

    /** `typeof value === 'object' && value !== null`. */
    private fun isObject(value: Any?): Boolean =
        value is Map<*, *> || value is List<*> || value is JsError || value is JsIterator || value is IterResult ||
            value is ByteArray

    /** `String(value)`. */
    private fun jsString(value: Any?): String = when (value) {
        null -> "null"
        Undefined -> "undefined"
        is Double -> if (value % 1.0 == 0.0 && Math.abs(value) < 1e21) value.toLong().toString() else value.toString()
        is Map<*, *> -> "[object Object]"
        is List<*> -> value.joinToString(",") { if (it == null || it === Undefined) "" else jsString(it) }
        else -> value.toString()
    }

    /** `Reflect.get(target, key)` for the values the glue meets. */
    private fun property(target: Any?, key: Any?): Any? = when {
        key === IteratorSymbol && target is List<*> -> JsFunction { JsIterator((it as List<*>).iterator()) }
        target is JsIterator && key == "next" -> JsFunction { (it as JsIterator).next() }
        target is IterResult && key == "done" -> target.done
        target is IterResult && key == "value" -> target.value
        target is List<*> && key is Double -> target.getOrElse(key.toInt()) { Undefined }
        target is List<*> && key == "length" -> target.size.toDouble()
        target is Map<*, *> -> if (target.containsKey(key)) target[key] else Undefined
        else -> Undefined
    }

    /** `handleError` of the glue: an exception becomes a JavaScript exception that the module receives. */
    private fun guarded(block: () -> LongArray): LongArray = try {
        block()
    } catch (e: RuntimeException) {
        call("__wbindgen_export3", add(JsError(e.message ?: e.toString())).toLong())
        longArrayOf(0)
    }

    /** The import [name] of `rumdl_lib.js`, by its name without the hash suffix of wasm-bindgen. */
    @Suppress("CyclomaticComplexMethod", "LongMethod")
    private fun host(name: String, args: LongArray): LongArray = when (if (name.startsWith(CAST)) name else name.replace(HASH, "")) {
        "__wbindgen_object_drop_ref" -> { take(args[0].toInt()); none }
        "__wbindgen_object_clone_ref" -> ref(get(args[0]))
        "__wbg_next" -> if (name.endsWith(NEXT_CALL)) {
            guarded { ref((get(args[0]) as JsIterator).next()) }
        } else {
            ref(property(get(args[0]), "next"))
        }
        "__wbg_done" -> bool((get(args[0]) as IterResult).done)
        "__wbg_value" -> ref((get(args[0]) as IterResult).value)
        "__wbg_length" -> longArrayOf(
            when (val value = get(args[0])) {
                is List<*> -> value.size
                is ByteArray -> value.size
                is String -> value.length
                else -> 0
            }.toLong()
        )
        "__wbg_get_unchecked" -> ref((get(args[0]) as List<*>).getOrElse(args[1].toInt()) { Undefined })
        "__wbg_get" -> if (name.endsWith(GET_INDEX)) {
            ref((get(args[0]) as List<*>).getOrElse(args[1].toInt()) { Undefined })
        } else {
            guarded { ref(property(get(args[0]), get(args[1]))) }
        }
        "__wbg_get_with_ref_key" -> ref(property(get(args[0]), get(args[1])))
        "__wbg_call" -> guarded { ref((get(args[0]) as JsFunction).call(get(args[1]))) }
        "__wbg_entries" -> ref((get(args[0]) as Map<*, *>).entries.map { listOf(it.key, it.value) })
        "__wbg_iterator" -> ref(IteratorSymbol)
        "__wbg_isArray" -> bool(get(args[0]) is List<*>)
        "__wbg_isSafeInteger" -> bool((get(args[0]) as? Double)?.let { it % 1.0 == 0.0 && Math.abs(it) <= MAX_SAFE } == true)
        "__wbg_instanceof_Map" -> bool(false)
        "__wbg_instanceof_ArrayBuffer" -> bool(false)
        "__wbg_instanceof_Uint8Array" -> bool(get(args[0]) is ByteArray)
        "__wbg_new" -> if (args.isEmpty()) ref(JsError("")) else ref(get(args[0]) as? ByteArray ?: ByteArray(0))
        "__wbg_prototypesetcall" -> { memory.write(args[0].toInt(), get(args[2]) as ByteArray); none }
        "__wbg_String" -> { writeString(args[0], jsString(get(args[1]))); none }
        "__wbg_Number" -> longArrayOf(java.lang.Double.doubleToRawLongBits(number(get(args[0]))))
        "__wbg_Error" -> ref(JsError(readString(args[0], args[1])))
        "__wbg_stack" -> { writeString(args[0], ""); none }
        "__wbg_error" -> {
            val message = readString(args[0], args[1])
            call("__wbindgen_export4", args[0], args[1], 1)
            throw RumdlException("rumdl failed: $message")
        }
        "__wbg___wbindgen_throw" -> throw RumdlException(readString(args[0], args[1]))
        "__wbg___wbindgen_string_get" -> { writeString(args[0], get(args[1]) as? String); none }
        "__wbg___wbindgen_number_get" -> {
            val value = get(args[1]) as? Double
            memory.writeF64(args[0].toInt() + 8, value ?: 0.0)
            memory.writeI32(args[0].toInt(), if (value != null) 1 else 0)
            none
        }
        "__wbg___wbindgen_bigint_get_as_i64" -> {
            val value = get(args[1]) as? BigInteger
            memory.writeLong(args[0].toInt() + 8, value?.toLong() ?: 0L)
            memory.writeI32(args[0].toInt(), if (value != null) 1 else 0)
            none
        }
        "__wbg___wbindgen_boolean_get" -> longArrayOf(
            when (get(args[0])) {
                true -> 1L
                false -> 0L
                else -> NOT_BOOLEAN
            }
        )
        "__wbg___wbindgen_in" -> bool((get(args[1]) as? Map<*, *>)?.containsKey(get(args[0])) == true)
        "__wbg___wbindgen_is_null" -> bool(get(args[0]) == null)
        "__wbg___wbindgen_is_undefined" -> bool(get(args[0]) === Undefined)
        "__wbg___wbindgen_is_object" -> bool(isObject(get(args[0])))
        "__wbg___wbindgen_is_function" -> bool(get(args[0]) is JsFunction)
        "__wbg___wbindgen_is_bigint" -> bool(get(args[0]) is BigInteger)
        "__wbg___wbindgen_jsval_eq" -> bool(strictEquals(get(args[0]), get(args[1])))
        "__wbg___wbindgen_jsval_loose_eq" -> bool(looseEquals(get(args[0]), get(args[1])))
        "__wbg___wbindgen_debug_string" -> { writeString(args[0], jsString(get(args[1]))); none }
        "__wbindgen_cast_0000000000000001" -> ref(BigInteger.valueOf(args[0]))
        "__wbindgen_cast_0000000000000002" -> ref(readString(args[0], args[1]))
        "__wbindgen_cast_0000000000000003" -> ref(BigInteger(java.lang.Long.toUnsignedString(args[0])))
        else -> throw RumdlException("rumdl imports an unknown function $name")
    }

    private fun number(value: Any?): Double = when (value) {
        is Double -> value
        is Boolean -> if (value) 1.0 else 0.0
        is String -> value.trim().toDoubleOrNull() ?: Double.NaN
        is BigInteger -> value.toDouble()
        null -> 0.0
        else -> Double.NaN
    }

    private fun strictEquals(a: Any?, b: Any?): Boolean =
        if (a is Double || a is String || a is Boolean || a is BigInteger) a == b else a === b

    private fun looseEquals(a: Any?, b: Any?): Boolean =
        ((a == null || a === Undefined) && (b == null || b === Undefined)) || strictEquals(a, b)

    private companion object {
        const val RESERVED = 1024

        /** The first index of the heap that is not a constant. */
        const val FIRST_FREE = RESERVED + 4
        const val RETURN_AREA = 16
        const val NOT_BOOLEAN = 0xFFFFFFL
        const val MAX_SAFE = 9007199254740991.0
        val HASH = Regex("_[0-9a-f]{16}$")

        /** The casts of wasm-bindgen are numbered, not hashed. */
        const val CAST = "__wbindgen_cast_"

        /** `iterator.next()`, a call; the other `__wbg_next` reads the `next` property. */
        const val NEXT_CALL = "_0340c4ae324393c3"

        /** `array[index]`; the other `__wbg_get` is `Reflect.get`. */
        const val GET_INDEX = "_8360291721e2339f"
    }
}

/** rumdl reported an error: a configuration it cannot honour, or a failure of the module. */
internal class RumdlException(message: String) : RuntimeException(message)
