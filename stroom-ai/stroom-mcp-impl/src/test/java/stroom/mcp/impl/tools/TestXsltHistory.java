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

import stroom.ai.impl.tools.ChatModelNames;
import stroom.ai.impl.tools.ToolCaller;
import stroom.ai.impl.tools.ToolSession;
import stroom.security.api.SecurityContext;
import stroom.util.shared.BuildInfo;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Clock;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class TestXsltHistory {

    private final SecurityContext securityContext = Mockito.mock(SecurityContext.class);
    private final ChatModelNames chatModelNames = Mockito.mock(ChatModelNames.class);
    private final XsltHistory history = new XsltHistory(securityContext, () -> new BuildInfo(0L, "7.13.0", 0L),
            () -> chatModelNames, Clock.systemUTC());

    @Test
    void aChatsChangeNamesItsModel() {
        // Asked for by the user: "Stroom AI chat" said nothing of what made the change; an external client is named
        // by its own name.
        Mockito.when(securityContext.getUserIdentityForAudit()).thenReturn("admin");
        Mockito.when(chatModelNames.of(7)).thenReturn(Optional.of("claude-opus-5-5"));

        assertThat(history.changedBy(ToolSession.chat(7))).isEqualTo("admin (agent: Stroom 7.13.0, claude-opus-5-5)");
        assertThat(history.changedBy(new ToolSession(ToolSession.Kind.MCP, "s1",
                new ToolCaller(ToolCaller.Kind.EXTERNAL_CLIENT, "Claude Code"))))
                .isEqualTo("admin (agent: Stroom 7.13.0, Claude Code)");
        // A chat whose model can't be told is a chat.
        Mockito.when(chatModelNames.of(8)).thenReturn(Optional.empty());
        assertThat(history.changedBy(ToolSession.chat(8))).isEqualTo("admin (agent: Stroom 7.13.0, Stroom AI chat)");
    }
}
