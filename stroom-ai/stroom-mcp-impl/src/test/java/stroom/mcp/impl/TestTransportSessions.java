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

import io.modelcontextprotocol.server.transport.HttpServletStreamableServerTransportProvider;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TestTransportSessions {

    @Test
    void theTransportsSessionsCanBeReachedInTheSdkInUse() {
        // If an MCP SDK upgrade changes how the transport keeps its sessions, this fails, rather than sessions
        // ended by Stroom silently staying in the transport.
        final TransportSessions sessions = new TransportSessions(HttpServletStreamableServerTransportProvider
                .builder()
                .build());

        assertThat(sessions.isLinked()).isTrue();
        assertThat(sessions.ids()).isEmpty();
        assertThat(sessions.has("s1")).isFalse();
    }
}
