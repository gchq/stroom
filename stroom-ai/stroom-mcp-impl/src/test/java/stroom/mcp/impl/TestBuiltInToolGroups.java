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

package stroom.mcp.impl;

import stroom.ai.impl.BuiltInToolGroups;

import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class TestBuiltInToolGroups {

    @Test
    void everyToolInAGroupIsATool() {
        // A tool renamed, or taken away, would otherwise leave its group without it, and offered always.
        final Set<String> tools = new EveryTool().tools().stream()
                .map(tool -> tool.definition().name())
                .collect(Collectors.toSet());

        assertThat(tools).containsAll(BuiltInToolGroups.groupedTools());
    }
}
