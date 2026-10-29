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

package stroom.ai.impl;

import java.util.Collection;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/// The names built-in tools are offered by at the start of a question with no groups on: a tool in a group (see
/// [BuiltInToolGroups]) as the tool that turns its group on, and any other by its own name.
final class OfferedToolNames {

    private OfferedToolNames() {
        // Static only.
    }

    /// @param toolNames Built-in tools' names.
    /// @return The names they are offered by, sorted.
    static Set<String> withNoGroupsOn(final Collection<String> toolNames) {
        return toolNames.stream()
                .map(name -> BuiltInToolGroups.groupOf(name)
                        .map(group -> ToolGroups.ACTIVATE_PREFIX + group)
                        .orElse(name))
                .collect(Collectors.toCollection(TreeSet::new));
    }

    /// @param name A name a tool is offered by.
    /// @return True if it is the name of a tool that turns on a group of built-in tools.
    static boolean isBuiltInActivation(final String name) {
        return ToolGroups.isActivationTool(name)
               && BuiltInToolGroups.PURPOSES.containsKey(name.substring(ToolGroups.ACTIVATE_PREFIX.length()));
    }
}
