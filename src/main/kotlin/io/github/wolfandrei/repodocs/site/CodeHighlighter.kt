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

/**
 * Syntax highlighting of code blocks at build time, so that it needs no script. A block becomes spans with the classes
 * `tok-keyword`, `tok-type`, `tok-string`, `tok-number`, `tok-comment`, `tok-meta` (annotations, preprocessor,
 * directives), `tok-property` (keys and attributes), `tok-variable`, `tok-tag`, `tok-inserted`, `tok-deleted`; the
 * style sheet colours them. The scanners are small and forgiving: an unknown construct stays plain text.
 */
internal object CodeHighlighter {

    /** Languages by the tag of a fenced code block; `text` and unknown tags are not highlighted. */
    private val ALIASES = mapOf(
        "kotlin" to "kotlin", "kt" to "kotlin", "kts" to "kotlin", "gradle.kts" to "kotlin",
        "swift" to "swift",
        "java" to "java",
        "groovy" to "groovy", "gradle" to "groovy",
        "bash" to "shell", "sh" to "shell", "shell" to "shell", "zsh" to "shell", "console" to "shell",
        "json" to "json", "jsonc" to "json",
        "yaml" to "yaml", "yml" to "yaml",
        "xml" to "xml", "html" to "xml", "plist" to "xml", "svg" to "xml",
        "c" to "c", "h" to "c", "cpp" to "c", "c++" to "c", "hpp" to "c", "cc" to "c", "objc" to "c",
        "objective-c" to "c", "objectivec" to "c", "m" to "c", "mm" to "c",
        "cmake" to "cmake",
        "properties" to "properties", "toml" to "properties", "ini" to "properties",
        "plantuml" to "plantuml", "puml" to "plantuml",
        "diff" to "diff", "patch" to "diff",
    )

    /** Whether code blocks with the tag [language] are highlighted. */
    fun supports(language: String): Boolean = language.lowercase() in ALIASES

    /** HTML of [code] with the tokens of [language] in spans, or `null` for an unknown language. */
    fun highlight(code: String, language: String): String? {
        val tokens = when (ALIASES[language.lowercase()] ?: return null) {
            "kotlin" -> CLike(KOTLIN, annotations = true, tripleQuotes = true, templates = true).scan(code)
            "swift" -> CLike(SWIFT, annotations = true, tripleQuotes = true, attributes = true).scan(code)
            "java" -> CLike(JAVA, annotations = true).scan(code)
            "groovy" -> CLike(GROOVY, annotations = true, tripleQuotes = true).scan(code)
            "c" -> CLike(C, preprocessor = true, atKeywords = true).scan(code)
            "shell" -> shell(code)
            "json" -> json(code)
            "yaml" -> yaml(code)
            "xml" -> xml(code)
            "cmake" -> cmake(code)
            "properties" -> properties(code)
            "plantuml" -> plantUml(code)
            "diff" -> diff(code)
            else -> return null
        }
        return tokens.joinToString("") { (kind, text) ->
            val escaped = SiteTemplate.escape(text)
            if (kind == null) escaped else "<span class=\"tok-$kind\">$escaped</span>"
        }
    }

    private val BLOCK = Regex(
        "<pre><code class=\"(?:[^\"]*\\s)?(?:language|lang)-([\\w.+#-]+)[^\"]*\"[^>]*>(.*?)</code></pre>",
        RegexOption.DOT_MATCHES_ALL,
    )

    /**
     * [html] with the content of every `<pre><code class="language-x">` block highlighted; the blocks of other
     * languages stay as they are.
     */
    fun highlightBlocks(html: String): String = BLOCK.replace(html) { match ->
        val language = match.groupValues[1]
        val code = unescape(match.groupValues[2])
        val highlighted = highlight(code, language) ?: return@replace match.value
        "<pre><code class=\"language-${SiteTemplate.escape(language)} highlighted\">$highlighted</code></pre>"
    }

    private fun unescape(html: String): String = html
        .replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&#39;", "'")
        .replace("&amp;", "&")

    private data class Token(val kind: String?, val text: String)

    /** Collects tokens; adjacent plain text is merged. */
    private class Tokens {
        val list = mutableListOf<Token>()
        private val plain = StringBuilder()

        fun plain(text: CharSequence) {
            plain.append(text)
        }

        fun add(kind: String?, text: String) {
            if (kind == null) {
                plain.append(text)
                return
            }
            flush()
            list += Token(kind, text)
        }

        fun result(): List<Token> {
            flush()
            return list
        }

        private fun flush() {
            if (plain.isNotEmpty()) list += Token(null, plain.toString())
            plain.clear()
        }
    }

    private val NUMBER = Regex("0[xX][0-9a-fA-F_]+[uUlL]*|0[bB][01_]+[uUlL]*|(?:\\d[\\d_]*\\.?[\\d_]*(?:[eE][+-]?\\d+)?|\\.\\d+)[fFdDlLuU]*")
    private val IDENTIFIER = Regex("[\\p{L}_$][\\p{L}\\p{Nd}_$]*")

    /** Scanner of the languages with C-like comments and strings. */
    private class CLike(
        private val keywords: Set<String>,
        private val annotations: Boolean = false,
        private val tripleQuotes: Boolean = false,
        private val templates: Boolean = false,
        private val preprocessor: Boolean = false,
        private val attributes: Boolean = false,
        private val atKeywords: Boolean = false,
    ) {
        fun scan(code: String): List<Token> {
            val out = Tokens()
            var i = 0
            var lineStart = true
            while (i < code.length) {
                val c = code[i]
                when {
                    c == '\n' -> {
                        out.plain("\n")
                        lineStart = true
                        i++
                        continue
                    }

                    c == ' ' || c == '\t' -> {
                        out.plain(c.toString())
                        i++
                        continue
                    }

                    preprocessor && lineStart && c == '#' -> {
                        val end = code.indexOf('\n', i).let { if (it < 0) code.length else it }
                        out.add("meta", code.substring(i, end))
                        i = end
                    }

                    code.startsWith("//", i) -> {
                        val end = code.indexOf('\n', i).let { if (it < 0) code.length else it }
                        out.add("comment", code.substring(i, end))
                        i = end
                    }

                    code.startsWith("/*", i) -> {
                        val end = code.indexOf("*/", i + 2).let { if (it < 0) code.length else it + 2 }
                        out.add("comment", code.substring(i, end))
                        i = end
                    }

                    tripleQuotes && code.startsWith("\"\"\"", i) -> {
                        val end = code.indexOf("\"\"\"", i + 3).let { if (it < 0) code.length else it + 3 }
                        out.add("string", code.substring(i, end))
                        i = end
                    }

                    c == '"' || c == '\'' -> {
                        val end = stringEnd(code, i, c)
                        out.add("string", code.substring(i, end))
                        i = end
                    }

                    c == '@' && (annotations || atKeywords) -> {
                        val match = IDENTIFIER.matchAt(code, i + 1)
                        if (match != null) {
                            val word = match.value
                            val kind = if (atKeywords && "@$word" in keywords) "keyword" else "meta"
                            out.add(kind, "@$word")
                            i = match.range.last + 1
                        } else {
                            out.plain("@")
                            i++
                        }
                    }

                    c.isDigit() -> {
                        val match = NUMBER.matchAt(code, i)
                        val text = match?.value ?: c.toString()
                        out.add("number", text)
                        i += text.length
                    }

                    c.isLetter() || c == '_' || (c == '$' && !templates) -> {
                        val word = IDENTIFIER.matchAt(code, i)?.value ?: c.toString()
                        val kind = when {
                            word in keywords -> "keyword"
                            word.first().isUpperCase() && word.any { it.isLowerCase() } -> "type"
                            attributes && i > 0 && code[i - 1] == '@' -> "meta"
                            else -> null
                        }
                        out.add(kind, word)
                        i += word.length
                    }

                    else -> {
                        out.plain(c.toString())
                        i++
                    }
                }
                lineStart = false
            }
            return out.result()
        }

        private fun stringEnd(code: String, start: Int, quote: Char): Int {
            var i = start + 1
            while (i < code.length) {
                when (code[i]) {
                    '\\' -> i += 2
                    quote -> return i + 1
                    '\n' -> return i
                    else -> i++
                }
            }
            return code.length
        }
    }

    private fun lineScanner(code: String, line: (String, Tokens) -> Unit): List<Token> {
        val out = Tokens()
        code.split('\n').forEachIndexed { index, text ->
            if (index > 0) out.plain("\n")
            line(text, out)
        }
        return out.result()
    }

    /** Splits [text] at the first unquoted [marker] outside strings: the comment of a line. */
    private fun commentStart(text: String, marker: Char): Int {
        var quote: Char? = null
        text.forEachIndexed { index, c ->
            when {
                quote != null && c == quote -> quote = null
                quote != null -> Unit
                c == '"' || c == '\'' -> quote = c
                c == marker && (index == 0 || text[index - 1].isWhitespace()) -> return index
            }
        }
        return -1
    }

    private val SHELL_TOKEN = Regex(
        "(\"(?:\\\\.|[^\"\\\\])*\"?|'[^']*'?)|(\\$\\{[^}]*}|\\$[\\w@#?*!-]+)|(?<![\\w-])(--?[\\w-]+=?)|([\\p{L}_][\\w.-]*)"
    )

    private fun shell(code: String): List<Token> = lineScanner(code) { line, out ->
        val comment = commentStart(line, '#').takeIf { !line.startsWith("#!") || it > 0 }
            ?: if (line.startsWith("#!")) 0 else -1
        val body = if (comment >= 0) line.substring(0, comment) else line
        var last = 0
        var commandExpected = true
        SHELL_TOKEN.findAll(body).forEach { match ->
            val separator = body.substring(last, match.range.first)
            if ('|' in separator || ';' in separator || '&' in separator || '(' in separator) commandExpected = true
            out.plain(separator)
            val (string, variable, option, word) = match.destructured
            val command = commandExpected && word.isNotEmpty() && !word.contains('=')
            when {
                string.isNotEmpty() -> out.add("string", string)
                variable.isNotEmpty() -> out.add("variable", variable)
                option.isNotEmpty() -> out.add("property", option)
                word in SHELL_KEYWORDS -> out.add("keyword", word)
                command -> out.add("type", word)
                else -> out.plain(word)
            }
            commandExpected = word in SHELL_KEYWORDS && word != "echo" && word != "export" && word != "cd"
            last = match.range.last + 1
        }
        out.plain(body.substring(last))
        if (comment >= 0) out.add("comment", line.substring(comment))
    }

    private val JSON_TOKEN = Regex(
        "(\"(?:\\\\.|[^\"\\\\])*\")(\\s*:)?|(-?\\d+(?:\\.\\d+)?(?:[eE][+-]?\\d+)?)|\\b(true|false|null)\\b|(//[^\\n]*)"
    )

    private fun json(code: String): List<Token> {
        val out = Tokens()
        var last = 0
        JSON_TOKEN.findAll(code).forEach { match ->
            out.plain(code.substring(last, match.range.first))
            val (string, colon, number, literal, comment) = match.destructured
            when {
                string.isNotEmpty() && colon.isNotEmpty() -> {
                    out.add("property", string)
                    out.plain(colon)
                }

                string.isNotEmpty() -> out.add("string", string)
                number.isNotEmpty() -> out.add("number", number)
                literal.isNotEmpty() -> out.add("keyword", literal)
                else -> out.add("comment", comment)
            }
            last = match.range.last + 1
        }
        out.plain(code.substring(last))
        return out.result()
    }

    private val YAML_KEY = Regex("^(\\s*-?\\s*)([^\\s#:\"'][^#:]*?|\"[^\"]*\"|'[^']*')(\\s*:)(?=\\s|$)")
    private val YAML_VALUE = Regex("^(\\s*)(\"(?:\\\\.|[^\"])*\"|'[^']*'|-?\\d+(?:\\.\\d+)?|true|false|null|~)(\\s*)$")

    private fun yaml(code: String): List<Token> = lineScanner(code) { line, out ->
        val comment = commentStart(line, '#')
        var body = if (comment >= 0) line.substring(0, comment) else line
        if (body.trimStart().startsWith("---") || body.trimStart().startsWith("...")) {
            out.add("meta", body)
            body = ""
        }
        YAML_KEY.find(body)?.let { key ->
            out.plain(key.groupValues[1])
            out.add("property", key.groupValues[2])
            out.plain(key.groupValues[3])
            body = body.substring(key.range.last + 1)
        }
        val value = YAML_VALUE.find(body)
        if (value != null) {
            out.plain(value.groupValues[1])
            val text = value.groupValues[2]
            val kind = when {
                text.first() == '"' || text.first() == '\'' -> "string"
                text.first().isDigit() || text.first() == '-' -> "number"
                else -> "keyword"
            }
            out.add(kind, text)
            out.plain(value.groupValues[3])
        } else {
            out.plain(body)
        }
        if (comment >= 0) out.add("comment", line.substring(comment))
    }

    private val XML_TOKEN = Regex(
        "(<!--.*?-->)|(<!\\[CDATA\\[.*?]]>)|(<[?!][^>]*>)|(</?)([\\w:.-]+)((?:\\s+[\\w:.-]+(?:\\s*=\\s*(?:\"[^\"]*\"|'[^']*'|[^\\s>]+))?)*)\\s*(/?>)",
        RegexOption.DOT_MATCHES_ALL,
    )
    private val XML_ATTRIBUTE = Regex("([\\w:.-]+)(?:(\\s*=\\s*)(\"[^\"]*\"|'[^']*'|[^\\s>]+))?")

    private fun xml(code: String): List<Token> {
        val out = Tokens()
        var last = 0
        XML_TOKEN.findAll(code).forEach { match ->
            out.plain(code.substring(last, match.range.first))
            val groups = match.groupValues
            when {
                groups[1].isNotEmpty() -> out.add("comment", groups[1])
                groups[2].isNotEmpty() -> out.add("string", groups[2])
                groups[3].isNotEmpty() -> out.add("meta", groups[3])
                else -> {
                    out.plain(groups[4])
                    out.add("tag", groups[5])
                    var position = 0
                    val attributes = groups[6]
                    XML_ATTRIBUTE.findAll(attributes).forEach { attribute ->
                        out.plain(attributes.substring(position, attribute.range.first))
                        out.add("property", attribute.groupValues[1])
                        out.plain(attribute.groupValues[2])
                        if (attribute.groupValues[3].isNotEmpty()) out.add("string", attribute.groupValues[3])
                        position = attribute.range.last + 1
                    }
                    out.plain(attributes.substring(position))
                    out.plain(match.value.removeSuffix(groups[7]).substring(groups[4].length + groups[5].length + groups[6].length))
                    out.plain(groups[7])
                }
            }
            last = match.range.last + 1
        }
        out.plain(code.substring(last))
        return out.result()
    }

    private val CMAKE_TOKEN = Regex(
        "(#[^\\n]*)|(\"(?:\\\\.|[^\"\\\\])*\")|(\\$\\{[^}]*}|\\\$ENV\\{[^}]*})|([A-Za-z_][\\w]*)(?=\\s*\\()|\\b([A-Z][A-Z0-9_]{1,})\\b"
    )

    private fun cmake(code: String): List<Token> {
        val out = Tokens()
        var last = 0
        CMAKE_TOKEN.findAll(code).forEach { match ->
            out.plain(code.substring(last, match.range.first))
            val (comment, string, variable, command, constant) = match.destructured
            when {
                comment.isNotEmpty() -> out.add("comment", comment)
                string.isNotEmpty() -> out.add("string", string)
                variable.isNotEmpty() -> out.add("variable", variable)
                command.isNotEmpty() -> out.add("keyword", command)
                else -> out.add("property", constant)
            }
            last = match.range.last + 1
        }
        out.plain(code.substring(last))
        return out.result()
    }

    private val PROPERTY_LINE = Regex("^(\\s*)([^=:\\s][^=:]*?)(\\s*[=:]\\s*)(.*)$")

    private fun properties(code: String): List<Token> = lineScanner(code) { line, out ->
        val trimmed = line.trimStart()
        when {
            trimmed.startsWith("#") || trimmed.startsWith("!") || trimmed.startsWith(";") -> out.add("comment", line)
            trimmed.startsWith("[") -> out.add("meta", line)
            else -> {
                val match = PROPERTY_LINE.find(line)
                if (match == null) {
                    out.plain(line)
                } else {
                    val (indent, key, separator, value) = match.destructured
                    out.plain(indent)
                    out.add("property", key)
                    out.plain(separator)
                    val v = value.trim()
                    val kind = when {
                        v.startsWith("\"") || v.startsWith("'") -> "string"
                        v == "true" || v == "false" -> "keyword"
                        v.isNotEmpty() && NUMBER.matches(v) -> "number"
                        else -> null
                    }
                    out.add(kind, value)
                }
            }
        }
    }

    private val PLANTUML_TOKEN = Regex(
        "(\"[^\"\\n]*\")|(![\\w]+|@\\w+)|(<[\\w/.]+>)|(\\$[\\w]+)|([\\p{L}_][\\w]*)"
    )

    private fun plantUml(code: String): List<Token> = lineScanner(code) { line, out ->
        if (line.trimStart().startsWith("'")) {
            out.add("comment", line)
            return@lineScanner
        }
        var last = 0
        PLANTUML_TOKEN.findAll(line).forEach { match ->
            out.plain(line.substring(last, match.range.first))
            val (string, directive, library, variable, word) = match.destructured
            when {
                string.isNotEmpty() -> out.add("string", string)
                directive.isNotEmpty() -> out.add("meta", directive)
                library.isNotEmpty() -> out.add("string", library)
                variable.isNotEmpty() -> out.add("variable", variable)
                word in PLANTUML_KEYWORDS -> out.add("keyword", word)
                word.first().isUpperCase() && '_' in word -> out.add("type", word)
                else -> out.plain(word)
            }
            last = match.range.last + 1
        }
        out.plain(line.substring(last))
    }

    private fun diff(code: String): List<Token> = lineScanner(code) { line, out ->
        val kind = when {
            line.startsWith("+++") || line.startsWith("---") || line.startsWith("diff ") ||
                line.startsWith("index ") -> "meta"

            line.startsWith("@@") -> "property"
            line.startsWith("+") -> "inserted"
            line.startsWith("-") -> "deleted"
            else -> null
        }
        out.add(kind, line)
    }

    private val KOTLIN = setOf(
        "as", "break", "class", "continue", "do", "else", "false", "for", "fun", "if", "in", "interface", "is", "null",
        "object", "package", "return", "super", "this", "throw", "true", "try", "typealias", "typeof", "val", "var",
        "when", "while", "by", "catch", "constructor", "delegate", "dynamic", "field", "file", "finally", "get",
        "import", "init", "param", "property", "receiver", "set", "setparam", "value", "where", "abstract", "actual",
        "annotation", "companion", "const", "crossinline", "data", "enum", "expect", "external", "final", "infix",
        "inline", "inner", "internal", "lateinit", "noinline", "open", "operator", "out", "override", "private",
        "protected", "public", "reified", "sealed", "suspend", "tailrec", "vararg", "it",
    )

    private val JAVA = setOf(
        "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class", "const", "continue",
        "default", "do", "double", "else", "enum", "extends", "final", "finally", "float", "for", "goto", "if",
        "implements", "import", "instanceof", "int", "interface", "long", "native", "new", "package", "private",
        "protected", "public", "return", "short", "static", "strictfp", "super", "switch", "synchronized", "this",
        "throw", "throws", "transient", "try", "void", "volatile", "while", "var", "record", "sealed", "permits",
        "yield", "true", "false", "null",
    )

    private val GROOVY = JAVA + setOf("def", "as", "in", "it", "trait")

    private val SWIFT = setOf(
        "associatedtype", "class", "deinit", "enum", "extension", "fileprivate", "func", "import", "init", "inout",
        "internal", "let", "open", "operator", "private", "protocol", "public", "rethrows", "static", "struct",
        "subscript", "typealias", "var", "break", "case", "continue", "default", "defer", "do", "else",
        "fallthrough", "for", "guard", "if", "in", "repeat", "return", "switch", "where", "while", "as", "catch",
        "false", "is", "nil", "self", "Self", "super", "throw", "throws", "true", "try", "async", "await", "actor",
        "some", "any", "final", "lazy", "mutating", "nonmutating", "override", "required", "weak", "unowned",
        "convenience", "dynamic", "optional", "get", "set", "willSet", "didSet",
    )

    private val C = setOf(
        "auto", "break", "case", "char", "const", "continue", "default", "do", "double", "else", "enum", "extern",
        "float", "for", "goto", "if", "inline", "int", "long", "register", "restrict", "return", "short", "signed",
        "sizeof", "static", "struct", "switch", "typedef", "union", "unsigned", "void", "volatile", "while", "bool",
        "true", "false", "NULL", "nullptr", "class", "namespace", "template", "typename", "public", "private",
        "protected", "virtual", "override", "new", "delete", "this", "using", "const_cast", "static_cast",
        "dynamic_cast", "reinterpret_cast", "constexpr", "noexcept", "throw", "try", "catch", "auto", "nil", "YES",
        "NO", "self", "id", "instancetype", "nonatomic", "strong", "weak", "readonly", "readwrite", "nullable",
        "nonnull", "@interface", "@implementation", "@end", "@property", "@protocol", "@class", "@import",
        "@selector", "@synthesize", "@optional", "@required",
    )

    private val SHELL_KEYWORDS = setOf(
        "if", "then", "else", "elif", "fi", "for", "in", "do", "done", "while", "until", "case", "esac", "function",
        "return", "export", "local", "readonly", "set", "unset", "source", "exit", "shift", "eval", "exec", "trap",
        "cd", "echo",
    )

    private val PLANTUML_KEYWORDS = setOf(
        "participant", "actor", "boundary", "control", "entity", "database", "collections", "queue", "class",
        "interface", "abstract", "enum", "annotation", "package", "namespace", "component", "node", "folder", "frame",
        "cloud", "rectangle", "artifact", "usecase", "state", "note", "end", "as", "of", "over", "left", "right",
        "top", "bottom", "title", "legend", "skinparam", "alt", "else", "opt", "loop", "par", "break", "critical",
        "group", "activate", "deactivate", "return", "autonumber", "hide", "show", "box", "ref", "start", "stop",
        "if", "then", "endif", "while", "endwhile", "repeat", "fork", "split", "partition", "together", "extends",
        "implements",
    )
}
