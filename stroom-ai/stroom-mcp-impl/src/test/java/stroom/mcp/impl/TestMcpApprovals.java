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

import stroom.mcp.impl.tools.FormsRequired;
import stroom.util.shared.UserRef;

import io.modelcontextprotocol.server.McpAsyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema.ClientCapabilities;
import io.modelcontextprotocol.spec.McpSchema.ElicitResult;
import io.modelcontextprotocol.spec.McpSchema.Implementation;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;

class TestMcpApprovals {

    private static final UserRef USER = UserRef.builder().uuid("user1").build();

    private final McpAsyncServerExchange exchange = Mockito.mock(McpAsyncServerExchange.class);

    @Test
    void aClientThatCantShowFormsIsRefusedAndGivenNoApprovalId() {
        // The model must never be handed an id it could pass back to approve its own call.
        final McpForms forms = Mockito.mock(McpForms.class);
        final McpApprovals approvals = new McpApprovals(forms);

        final String reply = approvals.approve(session(false), exchange, "write_thing", "{\"v\":1}").orElseThrow();

        assertThat(reply).isEqualTo(FormsRequired.noForms("write_thing"))
                .doesNotContain("approval_id", "approve_call");
        Mockito.verifyNoInteractions(forms);
    }

    @Test
    void aClientThatShowsFormsAsksTheUser() {
        final McpForms forms = Mockito.mock(McpForms.class);
        final McpSession session = session(true);
        Mockito.when(forms.ask(Mockito.eq(session), Mockito.eq(exchange), Mockito.anyString(), Mockito.any()))
                .thenReturn(new ElicitResult(ElicitResult.Action.ACCEPT, null),
                        new ElicitResult(ElicitResult.Action.DECLINE, null),
                        new ElicitResult(ElicitResult.Action.CANCEL, null));
        final McpApprovals approvals = new McpApprovals(forms);

        assertThat(approvals.approve(session, exchange, "write_thing", "{\"pipeline_uuid\":\"p-1\"}")).isEmpty();
        assertThat(approvals.approve(session, exchange, "write_thing", "{}")).get(InstanceOfAssertFactories.STRING)
                .startsWith("Not run: the user did not allow this call.");
        assertThat(approvals.approve(session, exchange, "write_thing", "{}")).get(InstanceOfAssertFactories.STRING)
                .startsWith("Not run: the user was asked to allow this call")
                .contains("did not answer");
        Mockito.verify(forms).ask(session, exchange, "client wants to use Stroom's write_thing tool, which changes "
                                                     + "something in Stroom, as you, with pipeline_uuid=p-1; all "
                                                     + "arguments: {\"pipeline_uuid\":\"p-1\"}. Allow it?",
                McpForms.NO_FIELDS);
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

    private static McpSession session(final boolean showsForms) {
        final ClientCapabilities capabilities = showsForms
                ? ClientCapabilities.builder().elicitation().build()
                : ClientCapabilities.builder().build();
        return new McpSession("s1", USER, new Implementation("client", "1"), capabilities);
    }
}
