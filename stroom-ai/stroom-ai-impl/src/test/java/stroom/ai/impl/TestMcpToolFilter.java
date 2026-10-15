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
            "find_.*, find_documents, true",
            "FIND_.*, find_documents, true",
            ".*_documents, find_documents, true",
            ".*, anything, true",
            "find.*doc.*, find_documents, true",
            "find_.*, describe_document, false",
            // The whole name must match, not just part of it.
            "find_documents, find_documents_v2, false",
            "documents, find_documents, false",
            "(get|list)_feeds?, list_feed, true",
            "(get|list)_feeds?, delete_feed, false"
    })
    void testPatternMatchesTheWholeNameIgnoringCase(final String pattern, final String name,
                                                    final boolean expected) {
        final McpToolFilter filter = filter(new McpToolPolicyRule(pattern, McpToolPolicy.ALLOW));

        assertThat(filter.policyFor(name) == McpToolPolicy.ALLOW).isEqualTo(expected);
    }

    @Test
    void testNoPoliciesAllowsEverything() {
        final McpToolFilter filter = McpToolFilter.forServer(McpToolServerConfig.builder().build());

        assertThat(filter.isAllowed("anything")).isTrue();
        assertThat(filter.policyFor("anything")).isNull();
        assertThat(filter.isAllowed(null)).isFalse();
        assertThat(McpToolFilter.forServer(null).policyFor("x")).isNull();
    }

    @Test
    void testFirstMatchingPolicyApplies() {
        final McpToolFilter filter = filter(
                new McpToolPolicyRule("reprocess_streams", McpToolPolicy.ASK_ALWAYS),
                new McpToolPolicyRule("reprocess_.*", McpToolPolicy.DENY),
                new McpToolPolicyRule(".*", McpToolPolicy.ALLOW));

        assertThat(filter.policyFor("reprocess_streams")).isEqualTo(McpToolPolicy.ASK_ALWAYS);
        assertThat(filter.isAllowed("reprocess_streams")).isTrue();
        assertThat(filter.policyFor("reprocess_all")).isEqualTo(McpToolPolicy.DENY);
        assertThat(filter.isAllowed("reprocess_all")).isFalse();
        assertThat(filter.policyFor("find_documents")).isEqualTo(McpToolPolicy.ALLOW);
    }

    @Test
    void testInvalidOrBlankPatternsAreIgnored() {
        final McpToolFilter filter = filter(
                new McpToolPolicyRule("find_(", McpToolPolicy.DENY),
                new McpToolPolicyRule("  ", McpToolPolicy.DENY),
                new McpToolPolicyRule(null, McpToolPolicy.DENY),
                new McpToolPolicyRule("find_.*", McpToolPolicy.ALLOW));

        assertThat(filter.policyFor("find_documents")).isEqualTo(McpToolPolicy.ALLOW);
        assertThat(filter.isAllowed("find_documents")).isTrue();
    }

    @Test
    void testPatternError() {
        assertThat(McpToolFilter.patternError("find_.*")).isNull();
        assertThat(McpToolFilter.patternError(" find_.* ")).isNull();
        assertThat(McpToolFilter.patternError("find_(")).isNotBlank();
        assertThat(McpToolFilter.patternError("")).isNotBlank();
        assertThat(McpToolFilter.patternError(null)).isNotBlank();
        assertThat(McpToolFilter.patternError("a".repeat(McpToolFilter.MAX_PATTERN_LENGTH + 1))).isNotBlank();
    }

    @Test
    void testUnsetPolicyAsksEveryTime() {
        assertThat(new McpToolPolicyRule("x", null).getPolicy()).isEqualTo(McpToolPolicy.ASK_ALWAYS);
    }

    private static McpToolFilter filter(final McpToolPolicyRule... rules) {
        return McpToolFilter.forServer(McpToolServerConfig.builder()
                .name("s")
                .toolPolicies(List.of(rules))
                .build());
    }
}
