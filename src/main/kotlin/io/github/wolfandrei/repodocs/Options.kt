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

package io.github.wolfandrei.repodocs

/** Rules of the heading ids, see [DocumentationSiteExtension.anchorStyle]. */
enum class AnchorStyle {
    /** The rules of GitHub: every hyphen and space stays, an id of digits only stays as it is. */
    GITHUB,

    /** The rules of GitLab: a run of hyphens becomes one, an id of digits only gets the `anchor-` prefix. */
    GITLAB,
}

/** Layout engine of the diagrams drawn at build time, see [DocumentationSiteExtension.PlantUml.layout]. */
enum class DiagramLayout {
    /** Graphviz `dot` if it is found, otherwise [SMETANA]. */
    AUTO,

    /**
     * Graphviz `dot` only; the build fails without it. Unlike [SMETANA] it draws `skinparam linetype ortho`, the
     * right-angled lines of class diagrams; the diagrams match between machines with one version of Graphviz.
     */
    DOT,

    /**
     * Smetana, the port of Graphviz built into PlantUML: the same diagram on every machine. It ignores
     * `skinparam linetype ortho`: lines go straight or curved.
     */
    SMETANA,
}
