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

import stroom.ai.impl.AiConfig;
import stroom.ai.impl.McpEndpointConfig;
import stroom.util.shared.UserRef;
import stroom.util.time.StroomDuration;

import io.modelcontextprotocol.server.McpAsyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema.ClientCapabilities;
import io.modelcontextprotocol.spec.McpSchema.Implementation;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;

class TestMcpApprovals {

    private static final UserRef USER = UserRef.builder().uuid("user1").build();

    private final McpAsyncServerExchange exchange = Mockito.mock(McpAsyncServerExchange.class);

    @Test
    void anApprovalIdIsForItsOwnSessionAndCanBeUsedOnce() {
        final McpApprovals approvals = approvals(StroomDuration.ofMinutes(10));
        final McpSession session = session("s1");
        final McpSession other = session("s2");

        final String reply = approvals.approve(session, exchange, "write_thing", "{\"v\":1}").orElseThrow();
        final String approvalId = reply.replaceAll(".*approval_id='([^']+)'.*", "$1");

        assertThat(reply).contains("write_thing with {\"v\":1}", "within 10 minutes");
        assertThat(approvals.take(other, approvalId)).isEmpty();
        assertThat(approvals.take(session, " " + approvalId + " "))
                .map(McpApprovals.PendingCall::arguments)
                .contains("{\"v\":1}");
        assertThat(approvals.take(session, approvalId)).isEmpty();
        assertThat(approvals.take(session, null)).isEmpty();
    }

    @Test
    void anApprovalIdExpires() throws InterruptedException {
        final McpApprovals approvals = approvals(StroomDuration.ZERO);
        final McpSession session = session("s1");
        final String reply = approvals.approve(session, exchange, "write_thing", "{}").orElseThrow();
        final String approvalId = reply.replaceAll(".*approval_id='([^']+)'.*", "$1");

        Thread.sleep(5);

        assertThat(approvals.take(session, approvalId)).isEmpty();
    }

    @Test
    void longArgumentsAreShortenedWhenShownSayingSo() {
        final String arguments = "x".repeat(McpApprovals.MAX_SHOWN_ARGUMENTS + 100);

        final String shown = McpApprovals.shown(arguments);

        assertThat(shown).contains("x".repeat(McpApprovals.MAX_SHOWN_ARGUMENTS) + "…")
                .doesNotContain("x".repeat(McpApprovals.MAX_SHOWN_ARGUMENTS + 1))
                .endsWith("(cut: only the first 1500 of their 1600 characters are shown)");
    }

    @Test
    void whatTheCallActsOnIsShownFirst() {
        // Seen in review: the target's uuid came after a long XSLT, past the cut, so the user never saw it.
        final String arguments = "{\"xslt\":\"" + "<x/>".repeat(500) + "\",\"pipeline_uuid\":\"p-1\","
                                 + "\"build\":\"fortios\",\"options\":{\"name\":\"nested\"}}";

        final String shown = McpApprovals.shown(arguments);

        assertThat(shown).startsWith("pipeline_uuid=p-1, build=fortios; all arguments: {\"xslt\":")
                .contains("(cut: only the first 1500 of their");
    }

    @Test
    void aCallWhoseArgumentsAreCutGetsNoApprovalId() {
        // Without a form, the user would approve what they couldn't see.
        final McpApprovals approvals = approvals(StroomDuration.ofMinutes(1));
        final McpSession session = session("s1");
        final String arguments = "{\"name\":\"" + "x".repeat(McpApprovals.MAX_SHOWN_ARGUMENTS) + "\"}";

        final String reply = approvals.approve(session, exchange, "write_thing", arguments).orElseThrow();

        assertThat(reply).startsWith("Not run: this call's arguments are 1511 characters long, more than the 1500")
                .doesNotContain("approval_id");
        assertThat(session.pendingCalls()).isEmpty();
    }

    private McpApprovals approvals(final StroomDuration timeout) {
        final AiConfig aiConfig = Mockito.mock(AiConfig.class);
        Mockito.when(aiConfig.getMcpEndpoint()).thenReturn(new McpEndpointConfig(null, null, null, timeout, null,
                null, null));
        return new McpApprovals(new McpForms(() -> aiConfig), () -> aiConfig);
    }

    private static McpSession session(final String id) {
        return new McpSession(id, USER, new Implementation("client", "1"), ClientCapabilities.builder().build());
    }
}
