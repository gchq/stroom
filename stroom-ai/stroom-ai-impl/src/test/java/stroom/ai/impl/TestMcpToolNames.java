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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class TestMcpToolNames {

    @ParameterizedTest
    @CsvSource({
            "Stroom MCP, stroom_mcp",
            "stroom-mcp, stroom_mcp",
            "  Elastic (prod)  , elastic_prod",
            "a.b.c, a_b_c",
            "'', mcp",
            "!!!, mcp",
            "A very long server name that goes on and on, a_very_long_server_name"
    })
    void testServerPrefix(final String label, final String expected) {
        assertThat(McpToolNames.serverPrefix(label)).isEqualTo(expected);
    }

    @Test
    void testPrefixedName() {
        assertThat(McpToolNames.prefixedName("stroom", "find_documents", Set.of()))
                .isEqualTo("stroom__find_documents");
        // Characters that model APIs reject are replaced.
        assertThat(McpToolNames.prefixedName("stroom", "find.documents v2", Set.of()))
                .isEqualTo("stroom__find_documents_v2");
    }

    @Test
    void testPrefixedName_clashesGetASuffix() {
        final Set<String> used = new HashSet<>();
        final String first = McpToolNames.prefixedName("s", "a.b", used);
        used.add(first);
        final String second = McpToolNames.prefixedName("s", "a_b", used);

        assertThat(first).isEqualTo("s__a_b");
        assertThat(second).isEqualTo("s__a_b_2");
    }

    @Test
    void testPrefixedName_longNamesAreShortenedButKeptApart() {
        final String longTool = "x".repeat(100);
        final String name1 = McpToolNames.prefixedName("server", longTool + "1", Set.of());
        final String name2 = McpToolNames.prefixedName("server", longTool + "2", Set.of());

        assertThat(name1).hasSize(McpToolNames.MAX_NAME_LENGTH).startsWith("server__xxx");
        assertThat(name2).hasSize(McpToolNames.MAX_NAME_LENGTH);
        assertThat(name1).isNotEqualTo(name2);
    }
}
