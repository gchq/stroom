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

import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.spec.McpSchema.ElicitFormRequest;
import io.modelcontextprotocol.spec.McpSchema.ElicitRequest;
import io.modelcontextprotocol.spec.McpSchema.ElicitResult;
import io.modelcontextprotocol.spec.McpSchema.ElicitResult.Action;
import io.modelcontextprotocol.spec.McpSchema.ElicitUrlRequest;
import io.modelcontextprotocol.spec.McpSchema.JSONRPCRequest;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestMcpJson {

    private static final Map<String, Object> SCHEMA = Map.of("type", "object", "properties", Map.of());

    @Test
    void aFormRequestIsWrittenWithItsModeOnce() throws IOException {
        final String json = McpJson.mapper().writeValueAsString(new JSONRPCRequest("2.0", "elicitation/create", "1",
                new ElicitFormRequest("Create the feed?", SCHEMA, null)));

        assertThat(json)
                .contains("\"message\":\"Create the feed?\"", "\"requestedSchema\":")
                .containsOnlyOnce("\"mode\":\"form\"");
    }

    @Test
    void aFormRequestIsReadByItsMode() throws IOException {
        final ElicitRequest form = McpJson.mapper().readValue(
                "{\"mode\":\"form\",\"message\":\"Create the feed?\",\"requestedSchema\":{\"type\":\"object\"}}",
                ElicitRequest.class);
        final ElicitRequest url = McpJson.mapper().readValue(
                "{\"mode\":\"url\",\"message\":\"Sign in\",\"url\":\"https://example.com\",\"elicitationId\":\"e1\"}",
                ElicitRequest.class);
        // Before modes, a form had none.
        final ElicitRequest noMode = McpJson.mapper().readValue(
                "{\"message\":\"Create the feed?\",\"requestedSchema\":{\"type\":\"object\"}}", ElicitRequest.class);

        assertThat(form).isInstanceOf(ElicitFormRequest.class);
        assertThat(form.message()).isEqualTo("Create the feed?");
        assertThat(url).isInstanceOf(ElicitUrlRequest.class);
        assertThat(noMode).isInstanceOf(ElicitFormRequest.class);
    }

    @Test
    void aFormsAnswerRoundTrips() throws IOException {
        final String json = McpJson.mapper().writeValueAsString(new ElicitResult(Action.ACCEPT, Map.of("q1", "yes"),
                null));

        assertThat(McpJson.mapper().readValue(json, ElicitResult.class).content()).containsEntry("q1", "yes");
    }

    @Test
    void theSdksOwnMapperStillCannotWriteAForm() {
        // If this fails, the SDK (or Jackson) has fixed it, and McpJson's mix-in can go.
        assertThatThrownBy(() -> McpJsonDefaults.getMapper().writeValueAsString(
                new ElicitFormRequest("Create the feed?", SCHEMA, null)))
                .hasStackTraceContaining("Conflict between type id property 'mode'");
    }
}
