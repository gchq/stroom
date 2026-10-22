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

package stroom.mcp.impl.tools;

import stroom.ai.impl.AiAuditLog;
import stroom.ai.impl.tools.ToolCall;
import stroom.ai.impl.tools.ToolCallContext;
import stroom.ai.impl.tools.ToolCaller;
import stroom.ai.impl.tools.ToolRefusal;
import stroom.ai.impl.tools.ToolSession;
import stroom.ai.impl.tools.UserQuestions;

import io.modelcontextprotocol.spec.McpSchema.ElicitResult;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/// The user's agreement to an XSLT calling Stroom's DNS lookup functions: asked in a form, remembered for the
/// session by document and function. Not in the Stroom MCP server, which let them through.
class TestLookupAgreements {

    private static final ToolCall CALL = new ToolCall("call-1", SaveXsltTool.NAME, "{}");
    private static final String SHEET = "<xsl:stylesheet xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\" "
                                        + "version=\"3.0\" xmlns:stroom=\"stroom\"><xsl:template match=\"/\">"
                                        + "{BODY}</xsl:template></xsl:stylesheet>";
    private static final String HOST_NAME = SHEET.replace("{BODY}", "<xsl:value-of select=\"stroom:host-name(.)\"/>");
    private static final String BOTH = SHEET.replace("{BODY}", "<xsl:value-of select=\"stroom:host-name(.), "
                                                               + "stroom:host-address(.)\"/>");

    private final LookupAgreements lookups = new LookupAgreements(new Consent(() -> Mockito.mock(AiAuditLog.class)));
    private final List<String> asked = new ArrayList<>();
    private boolean agree = true;

    @Test
    void anAgreementIsRememberedForTheDocumentInTheSession() {
        final ToolCallContext context = context("s1");

        require(context, "x-1", HOST_NAME);
        require(context, "x-1", HOST_NAME);

        assertThat(asked).hasSize(1);
        // Another document is asked about, and so is the same one in another session.
        require(context, "x-2", HOST_NAME);
        require(context("s2"), "x-1", HOST_NAME);
        assertThat(asked).hasSize(3);
    }

    @Test
    void aFunctionNotAgreedToYetIsAskedAboutAlone() {
        final ToolCallContext context = context("s1");
        require(context, "x-1", HOST_NAME);

        require(context, "x-1", BOTH);

        assertThat(asked).hasSize(2);
        assertThat(asked.get(1)).startsWith("Save XSLT 'X', which calls stroom:host-address(): it ")
                .doesNotContain("stroom:host-name()");
        require(context, "x-1", BOTH);
        assertThat(asked).hasSize(2);
    }

    @Test
    void aDeclinedLookupIsRefusedAndNotRemembered() {
        agree = false;
        final ToolCallContext context = context("s1");

        assertThatThrownBy(() -> require(context, "x-1", HOST_NAME))
                .isInstanceOf(ToolRefusal.class)
                .hasMessageStartingWith("XSLT not saved: the user declined stroom:host-name()");
        assertThatThrownBy(() -> require(context, "x-1", HOST_NAME)).isInstanceOf(ToolRefusal.class);
        assertThat(asked).hasSize(2);
    }

    @Test
    void anXsltWithNoNewLookupIsNotAskedAbout() {
        final ToolCallContext context = context("s1");

        require(context, "x-1", SHEET.replace("{BODY}", "<xsl:value-of select=\"stroom:lookup('m', .)\"/>"));
        lookups.require(CALL, context, "x-2", "Save XSLT 'X'", "XSLT not saved", BOTH, BOTH);

        assertThat(asked).isEmpty();
    }

    private void require(final ToolCallContext context, final String document, final String code) {
        lookups.require(CALL, context, document, "Save XSLT 'X'", "XSLT not saved", code, null);
    }

    private ToolCallContext context(final String session) {
        return ToolCallContext.builder(new ToolSession(ToolSession.Kind.MCP, session,
                        new ToolCaller(ToolCaller.Kind.EXTERNAL_CLIENT, "A test client")))
                .userQuestions(new UserQuestions() {
                    @Override
                    public boolean canAsk() {
                        return true;
                    }

                    @Override
                    public ElicitResult ask(final String toolName,
                                            final String callId,
                                            final String intro,
                                            final Map<String, Object> form) {
                        asked.add(intro);
                        return new ElicitResult(ElicitResult.Action.ACCEPT, Map.of("value", agree));
                    }
                })
                .build();
    }
}
