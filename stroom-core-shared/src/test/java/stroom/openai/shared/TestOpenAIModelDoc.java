/*
 * Copyright 2016-2026 Crown Copyright
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

package stroom.openai.shared;

import stroom.util.json.JsonUtil;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TestOpenAIModelDoc {

    private static final String LEGACY_JSON = """
            {
                "type":"OpenAIModel",
                "uuid":"abc",
                "name":"legacy",
                "version":"1",
                "modelId":"gpt-4o"
            }""";

    @Test
    void legacyJsonWithoutMcpToolServersDeserialises() {
        final OpenAIModelDoc doc = JsonUtil.readValue(LEGACY_JSON, OpenAIModelDoc.class);
        assertThat(doc).isNotNull();
        assertThat(doc.getMcpToolServers()).isEmpty();
    }

    @Test
    void roundTripsMcpToolServers() {
        final OpenAIModelDoc doc = OpenAIModelDoc.builder()
                .uuid("abc")
                .name("model")
                .modelId("gpt-4o")
                .mcpToolServers(List.of(
                        McpToolServerConfig.builder()
                                .name("elastic")
                                .endpoint("https://mcp.example.com/mcp")
                                .authMode(McpAuthMode.BEARER_TOKEN)
                                .credentialName("elastic-token")
                                .build(),
                        McpToolServerConfig.builder()
                                .name("internal")
                                .endpoint("http://localhost:9000/mcp")
                                .authMode(McpAuthMode.API_KEY)
                                .apiKeyHeaderName("X-Api-Key")
                                .apiKeyValuePrefix("key ")
                                .credentialName("internal-token")
                                .build(),
                        McpToolServerConfig.builder()
                                .name("session")
                                .endpoint("https://mcp.example.com/session")
                                .authMode(McpAuthMode.STROOM_SESSION_TOKEN)
                                .build()))
                .build();

        final String json = JsonUtil.writeValueAsString(doc);
        final OpenAIModelDoc read = JsonUtil.readValue(json, OpenAIModelDoc.class);

        assertThat(read).isEqualTo(doc);
        assertThat(read.getMcpToolServers()).hasSize(3);
        assertThat(read.getMcpToolServers().get(0).getAuthMode()).isEqualTo(McpAuthMode.BEARER_TOKEN);
        assertThat(read.getMcpToolServers().get(1).getApiKeyHeaderName()).isEqualTo("X-Api-Key");
        assertThat(read.getMcpToolServers().get(2).getAuthMode()).isEqualTo(McpAuthMode.STROOM_SESSION_TOKEN);
    }
}
