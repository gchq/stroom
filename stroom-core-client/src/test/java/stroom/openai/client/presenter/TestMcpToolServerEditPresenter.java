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

import stroom.openai.shared.McpToolPolicy;
import stroom.openai.shared.McpToolPolicyRule;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TestMcpToolServerEditPresenter {

    @Test
    void testPoliciesError_validPatterns() {
        assertThat(McpToolServerEditPresenter.policiesError(null)).isNull();
        assertThat(McpToolServerEditPresenter.policiesError(List.of())).isNull();
        assertThat(McpToolServerEditPresenter.policiesError(List.of(
                new McpToolPolicyRule("find_.*", McpToolPolicy.ALLOW),
                new McpToolPolicyRule("(get|list)_feeds?", McpToolPolicy.ASK_ALWAYS),
                new McpToolPolicyRule("delete_.*", McpToolPolicy.DENY))))
                .isNull();
    }

    @Test
    void testPoliciesError_invalidPattern() {
        assertThat(McpToolServerEditPresenter.policiesError(List.of(
                new McpToolPolicyRule("find_.*", McpToolPolicy.ALLOW),
                new McpToolPolicyRule("find_(", McpToolPolicy.DENY))))
                .contains("'find_('")
                .contains("not a valid regular expression");
    }

    @Test
    void testPoliciesError_tooLong() {
        assertThat(McpToolServerEditPresenter.policiesError(List.of(new McpToolPolicyRule(
                "a".repeat(McpToolServerEditPresenter.MAX_PATTERN_LENGTH + 1), McpToolPolicy.DENY))))
                .contains("longer than");
    }
}
