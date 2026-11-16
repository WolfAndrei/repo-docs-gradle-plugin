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

package io.github.wolfandrei.repodocs.plantuml

import java.io.File
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

internal class IncludeCheckTest {
    @TempDir
    lateinit var dir: File

    private fun write(path: String, text: String) = dir.resolve(path).apply { parentFile.mkdirs() }.writeText(text)

    @Test
    fun `the standard library and the files of the repository are allowed, at any depth`() {
        write("repo/docs/defines.iuml", "!include_once ../shared/style.iuml\n")
        write("repo/shared/style.iuml", "skinparam shadowing false\n")
        write("repo/docs/a.puml", "@startuml\n!include <C4/C4_Context>\n!include_once defines.iuml\n!includesub \"defines.iuml!PART\"\n@enduml\n")

        assertEquals(emptyList<String>(), IncludeCheck(dir.resolve("repo")).problems(listOf(dir.resolve("repo/docs/a.puml"))))
    }

    @Test
    fun `addresses, files outside the repository and computed paths are reported, through includes too`() {
        write("secret.txt", "token\n")
        write("repo/docs/defines.iuml", "!include ../../secret.txt\n")
        write(
            "repo/docs/a.puml",
            "@startuml\n!include https://example.com/x.iuml\n!include_once defines.iuml\n!include \$DIR/x.iuml\n" +
                "!\$data = %load_json(\"/etc/passwd\")\n!include ~/.gradle/gradle.properties\n@enduml\n",
        )

        val problems = IncludeCheck(dir.resolve("repo")).problems(listOf(dir.resolve("repo/docs/a.puml")))

        assertEquals(
            listOf(
                "docs/a.puml:2: `https://example.com/x.iuml` is an address; a diagram reads only the repository",
                "docs/defines.iuml:1: `../../secret.txt` is outside the repository",
                "docs/a.puml:4: `\$DIR/x.iuml` is computed, the plugin cannot check that it stays in the repository",
                "docs/a.puml:5: `/etc/passwd` is outside the repository",
                "docs/a.puml:6: `~/.gradle/gradle.properties` is outside the repository",
            ),
            problems,
        )
    }
}
