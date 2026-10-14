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

import stroom.openai.shared.McpToolPolicy;
import stroom.openai.shared.McpToolPolicyRule;
import stroom.openai.shared.McpToolServerConfig;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TestMcpToolFilter {

    @ParameterizedTest
    @CsvSource({
            "find_documents, find_documents, true",
            "find_*, find_documents, true",
            "FIND_*, find_documents, true",
            "*_documents, find_documents, true",
            "*, anything, true",
            "find*doc*, find_documents, true",
            "find_*, describe_document, false",
            "find_documents, find_documents_v2, false",
            "a*b*c, axxbyyc, true",
            "a*b*c, axxbyy, false",
            "'', '', true",
            "'', x, false"
    })
    void testMatches(final String pattern, final String name, final boolean expected) {
        assertThat(McpToolFilter.matches(pattern, name)).isEqualTo(expected);
    }

    @Test
    void testNoListsAllowsEverything() {
        final McpToolFilter filter = McpToolFilter.forServer(McpToolServerConfig.builder().build());

        assertThat(filter.isAllowed("anything")).isTrue();
        assertThat(filter.isAllowed(null)).isFalse();
    }

    @Test
    void testAllowList() {
        final McpToolFilter filter = McpToolFilter.forServer(McpToolServerConfig.builder()
                .allowedTools(List.of("find_*", " describe_document "))
                .build());

        assertThat(filter.isAllowed("find_documents")).isTrue();
        assertThat(filter.isAllowed("describe_document")).isTrue();
        assertThat(filter.isAllowed("create_feed")).isFalse();
    }

    @Test
    void testDenyWinsOverAllow() {
        final McpToolFilter filter = McpToolFilter.forServer(McpToolServerConfig.builder()
                .allowedTools(List.of("*"))
                .deniedTools(List.of("reprocess_*", ""))
                .build());

        assertThat(filter.isAllowed("find_documents")).isTrue();
        assertThat(filter.isAllowed("reprocess_streams")).isFalse();
    }

    @Test
    void testDenyPolicy() {
        final McpToolFilter filter = McpToolFilter.forServer(McpToolServerConfig.builder()
                .toolPolicies(List.of(
                        new McpToolPolicyRule("reprocess_streams", McpToolPolicy.ASK_ALWAYS),
                        new McpToolPolicyRule("reprocess_*", McpToolPolicy.DENY),
                        new McpToolPolicyRule("*", McpToolPolicy.ALLOW)))
                .build());

        // The first matching rule applies.
        assertThat(filter.policyFor("reprocess_streams")).isEqualTo(McpToolPolicy.ASK_ALWAYS);
        assertThat(filter.isAllowed("reprocess_streams")).isTrue();
        assertThat(filter.policyFor("reprocess_all")).isEqualTo(McpToolPolicy.DENY);
        assertThat(filter.isAllowed("reprocess_all")).isFalse();
        assertThat(filter.policyFor("find_documents")).isEqualTo(McpToolPolicy.ALLOW);
        assertThat(McpToolFilter.forServer(null).policyFor("x")).isNull();
    }
}
