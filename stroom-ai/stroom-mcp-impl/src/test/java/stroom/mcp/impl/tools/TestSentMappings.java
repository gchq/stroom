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

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/// The mappings kept in the session's workspace for changes= (the Stroom MCP server's `tools/generation.py`
/// `_remember_mapping` and `_sent`, which kept them in the replica's memory).
class TestSentMappings {

    private final TranslationWorld world = new TranslationWorld();

    @Test
    void aMappingSentIsReadBackForTheSameTarget() {
        SentMappings.remember(world.context(), "x-1", Map.of("input", "json"));

        assertThat(SentMappings.sent(world.context(), "x-1")).contains(Map.of("input", "json"));
        assertThat(SentMappings.sent(world.context(), "x-2")).isEmpty();
        // Nothing is kept for no target.
        SentMappings.remember(world.context(), "", Map.of("input", "json"));
        assertThat(SentMappings.sent(world.context(), "")).isEmpty();
    }

    @Test
    void eachTargetHasAFileTheAgentCanRead() {
        assertThat(SentMappings.path("x-1")).hasToString("/outputs/mappings/x-1.json");
        assertThat(SentMappings.path(SentMappings.DRAFT)).hasToString("/outputs/mappings/draft.json");
        assertThat(SentMappings.path(SentMappings.DRAFT + "acme-v1"))
                .hasToString("/outputs/mappings/draft-acme-v1.json");
        // A build and name holds a slash and maybe spaces: its file is named by its digest.
        assertThat(SentMappings.path("acme-v1/ACME Events").toString())
                .matches("/outputs/mappings/[0-9a-f]{16}\\.json");
    }

    @Test
    void aSessionWithoutAWorkspaceRemembersNothing() {
        SentMappings.remember(PortedToolCalls.context(), "x-1", Map.of("input", "json"));

        assertThat(SentMappings.sent(PortedToolCalls.context(), "x-1")).isEmpty();
    }
}
