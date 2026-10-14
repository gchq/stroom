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

package stroom.openai.client.presenter;

import stroom.openai.shared.McpToolGroupRule;
import stroom.openai.shared.McpToolPolicy;
import stroom.openai.shared.McpToolPolicyRule;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestMcpToolServerEditPresenter {

    @Test
    void testSplitPatterns() {
        assertThat(McpToolServerEditPresenter.splitPatterns(null)).isNull();
        assertThat(McpToolServerEditPresenter.splitPatterns("  ")).isNull();
        assertThat(McpToolServerEditPresenter.splitPatterns(" , ")).isNull();
        assertThat(McpToolServerEditPresenter.splitPatterns("find_*, describe_document\nreprocess_*"))
                .containsExactly("find_*", "describe_document", "reprocess_*");
    }

    @Test
    void testJoinPatterns() {
        assertThat(McpToolServerEditPresenter.joinPatterns(null)).isEmpty();
        assertThat(McpToolServerEditPresenter.joinPatterns(List.of("a*", "b"))).isEqualTo("a*, b");
    }

    @Test
    void testParsePolicies() {
        assertThat(McpToolServerEditPresenter.parsePolicies(null)).isNull();
        assertThat(McpToolServerEditPresenter.parsePolicies(" \n ")).isNull();
        assertThat(McpToolServerEditPresenter.parsePolicies(
                "find_* = Allow\n\nreprocess_* = ask every time\ndelete_* = DENY\nx=ASK_ALWAYS"))
                .containsExactly(
                        new McpToolPolicyRule("find_*", McpToolPolicy.ALLOW),
                        new McpToolPolicyRule("reprocess_*", McpToolPolicy.ASK_ALWAYS),
                        new McpToolPolicyRule("delete_*", McpToolPolicy.DENY),
                        new McpToolPolicyRule("x", McpToolPolicy.ASK_ALWAYS));
    }

    @Test
    void testParsePolicies_invalid() {
        assertThatThrownBy(() -> McpToolServerEditPresenter.parsePolicies("find_* = Sometimes"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("'find_* = Sometimes' is not understood");
        assertThatThrownBy(() -> McpToolServerEditPresenter.parsePolicies("= Allow"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> McpToolServerEditPresenter.parsePolicies("find_*"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testParseAndFormatGroups() {
        assertThat(McpToolServerEditPresenter.parseGroups(" ")).isNull();
        final List<McpToolGroupRule> rules = McpToolServerEditPresenter.parseGroups(
                "pipeline_* = pipelines\n\nwhoami = -");
        assertThat(rules).containsExactly(
                new McpToolGroupRule("pipeline_*", "pipelines"),
                new McpToolGroupRule("whoami", McpToolGroupRule.ALWAYS_OFFERED));
        assertThat(McpToolServerEditPresenter.formatGroups(rules)).isEqualTo("pipeline_* = pipelines\nwhoami = -");
        assertThat(McpToolServerEditPresenter.formatGroups(null)).isEmpty();
        assertThatThrownBy(() -> McpToolServerEditPresenter.parseGroups("pipeline_*"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("is not understood");
    }

    @Test
    void testFormatPolicies() {
        assertThat(McpToolServerEditPresenter.formatPolicies(null)).isEmpty();
        assertThat(McpToolServerEditPresenter.formatPolicies(List.of(
                new McpToolPolicyRule("find_*", McpToolPolicy.ALLOW),
                new McpToolPolicyRule("reprocess_*", McpToolPolicy.ASK_ALWAYS))))
                .isEqualTo("find_* = Allow\nreprocess_* = Ask every time");
    }
}
