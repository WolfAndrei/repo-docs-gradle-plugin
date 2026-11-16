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

import com.dylibso.chicory.build.time.compiler.Config;
import com.dylibso.chicory.build.time.compiler.Generator;
import com.dylibso.chicory.compiler.InterpreterFallback;
import java.nio.file.Path;
import java.util.Set;

/**
 * Compiles a WebAssembly module to JVM classes at the build of the plugin, with the build-time compiler of Chicory:
 * {@code <wasm> <class name> <classes dir> <sources dir> <resources dir>}. Functions too large for the JVM stay
 * interpreted; the module of the resources keeps them.
 */
public final class CompileWasm {
    public static void main(String[] args) throws Exception {
        Config config = Config.builder()
            .withWasmFile(Path.of(args[0]))
            .withName(args[1])
            .withTargetClassFolder(Path.of(args[2]))
            .withTargetSourceFolder(Path.of(args[3]))
            .withTargetWasmFolder(Path.of(args[4]))
            .withInterpreterFallback(InterpreterFallback.SILENT)
            .build();
        Generator generator = new Generator(config);
        Set<Integer> interpreted = generator.generateResources();
        generator.generateMetaWasm(interpreted);
        generator.generateSources();
        System.out.println(args[1] + ": " + interpreted.size() + " function(s) interpreted");
    }
}
