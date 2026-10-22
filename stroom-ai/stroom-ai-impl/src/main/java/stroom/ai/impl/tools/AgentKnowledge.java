/*
 * Copyright 2026 Crown Copyright
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

package stroom.ai.impl.tools;

import java.util.List;
import java.util.Optional;

/// The knowledge bundled with Stroom for agents: the working guides (how to write XSLT, a Data Splitter, an
/// indexing pipeline, and so on) and the field convention profiles (e.g. ECS). They are served to MCP clients as
/// MCP resources (`stroom://guide/<name>`, `stroom://conventions/<name>`), and to the chat by `get_instructions`.
///
/// It is bound by the module that bundles them (`stroom-mcp-impl`); without that module there are none, and the
/// tools say so. Inject it as `Optional<AgentKnowledge>` ([BuiltInToolsModule] declares it optional).
public interface AgentKnowledge {

    /// @return The names of the guides, e.g. `xslt`, sorted.
    List<String> guideNames();

    /// @param name A guide's name, e.g. `xslt`.
    /// @return Its text (Markdown), or empty if there is no guide of that name.
    Optional<String> guide(String name);

    /// @param name A guide's name, e.g. `xslt`.
    /// @return Its title, the first line of its text without the heading's `#`s, or empty if there is no guide of
    /// that name.
    default Optional<String> guideTitle(final String name) {
        return guide(name).map(text -> text.lines()
                .findFirst()
                .orElse("")
                .replaceFirst("^[#\\s]+", "")
                .strip());
    }

    /// @return The names of the field convention profiles, e.g. `ecs`, sorted.
    List<String> conventionNames();

    /// @param name A convention profile's name, e.g. `ecs`.
    /// @return Its text (YAML), or empty if there is no profile of that name.
    Optional<String> convention(String name);
}
